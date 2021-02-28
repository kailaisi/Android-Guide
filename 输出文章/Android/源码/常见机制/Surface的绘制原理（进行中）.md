Surface的绘制原理（进行中）

在surface的跨进程传递原理中，我们讲过，surface本身并不是buffer，surface跨进程传输时是没有buffer信息的，但是surface绘制的时候是有buffer信息的，那么本文主要就是讲解两个问题：

* surface绘制的buffer是怎么来的
* buffer绘制完又是如何提交的

对于绘制，入口在ViewRootImpl的**performTraversals()**方法中所调用的**performDraw()**，所以这里我们也是从这里开始

```java
//frameworks\base\core\java\android\view\ViewRootImpl.java
	private void performDraw() {
        ...
            //重点方法**
            boolean canUseAsync = draw(fullRedrawNeeded);
        ...
    }


    private boolean draw(boolean fullRedrawNeeded) {
        Surface surface = mSurface;
        ...
        //如果没有启用硬件加速，则使用软件方式去绘制
        drawSoftware(surface, ...)
    }

    private boolean drawSoftware(Surface surface, AttachInfo attachInfo,...) {
          	final Canvas canvas;
        	...
        	//重点方法1        获取到surface，锁定画布
            canvas = mSurface.lockCanvas(dirty);
        	...
		    //重点方法2      进行view的绘制，这里会调用到我们View里面所覆写的onDraw()方法
             mView.draw(canvas);
			//重点方法3        解除锁定
             surface.unlockCanvasAndPost(canvas);
    }
```

当不启动硬件加速的情况，会使用drawSoftware方法来进行绘制工作。

* 获取canvas
* 通过View的draw方法绘制到canvas上
* 释放canvas

#### 获取canvas

对于canvas的获取，是通过surface的方法来实现的。

```java
//Surface.java
	public Canvas lockCanvas(Rect inOutDirty) {
			//通过native层的方法，给mCanvas赋值，
            mLockedObject = nativeLockCanvas(mNativeObject, mCanvas, inOutDirty);
            return mCanvas;
    }
```

这里调用了native层的方法

```c++
//android_view_Surface.cpp
static jlong nativeLockCanvas(JNIEnv* env, jclass clazz,jlong nativeObject, jobject canvasObj, jobject dirtyRectObj) {
    //获取到nativie层的surface对象
    sp<Surface> surface(reinterpret_cast<Surface *>(nativeObject));
    ANativeWindow_Buffer outBuffer;
    //根据绘制的区域大小，通过surface对象获取一块buffer
    status_t err = surface->lock(&outBuffer, dirtyRectPtr);
    //将申请的buffer对象设置为bitmap的参数
    SkBitmap bitmap;
    ssize_t bpr = outBuffer.stride * bytesPerPixel(outBuffer.format);
    bitmap.setInfo(info, bpr);
        bitmap.setPixels(outBuffer.bits);

    //创建一个native层的Canvas对象，然后将bitmap进行赋值
    Canvas* nativeCanvas = GraphicsJNI::getNativeCanvas(env, canvasObj);
    nativeCanvas->setBitmap(bitmap);
    //创建一个新的指针，指向surface，并增加引用计数器
    sp<Surface> lockedSurface(surface);
    lockedSurface->incStrong(&sRefBaseOwner);
    return (jlong) lockedSurface.get();
}
```

我们这里主要分析对于buffer的申请，后面的复制和引用计数器不再关注。

```c++
//Surface.cpp
status_t Surface::lock(ANativeWindow_Buffer* outBuffer, ARect* inOutDirtyBounds)
{
    if (mLockedBuffer != nullptr) {
        ALOGE("Surface::lock failed, already locked");
    ANativeWindowBuffer* out;
    int fenceFd = -1;
    //创建ANativeWindowBuffer对象，并赋值
    status_t err = dequeueBuffer(&out, &fenceFd);
        //转为GraphicBuffer对象。这里申请的是后台buffer，主要用于在后台进行绘制。
        sp<GraphicBuffer> backBuffer(GraphicBuffer::getSelf(out));
        const Rect bounds(backBuffer->width, backBuffer->height);

        Region newDirtyRegion;
        if (inOutDirtyBounds) {
            newDirtyRegion.set(static_cast<Rect const&>(*inOutDirtyBounds));
            newDirtyRegion.andSelf(bounds);
        } else {
            newDirtyRegion.set(bounds);
        }
        ...
        void* vaddr;
        //锁定buffer，并且获取对应的buffer的地址，vaddr
        status_t res = backBuffer->lockAsync(
                GRALLOC_USAGE_SW_READ_OFTEN | GRALLOC_USAGE_SW_WRITE_OFTEN,
                newDirtyRegion.bounds(), &vaddr, fenceFd);
        //mLockedBuffer是surface中表示后台的buffer变量
        mLockedBuffer = backBuffer;
        //将申请的buffer的地址赋值给outbuffer的bits变量
        outBuffer->bits   = vadd
    }
    return err;
}
```

其中的**dequeueBuffer**方法是真正进行buffer申请的地方。

```c++
//Surface.cpp
int Surface::dequeueBuffer(android_native_buffer_t** buffer, int* fenceFd) {
    //最核心的方法   通过GBP从远端的buffer中申请一段buffer信息，并放到buf中
    status_t result = mGraphicBufferProducer->dequeueBuffer(&buf, &fence,...);
    //gbuf是对应的buffer的句柄
    sp<GraphicBuffer>& gbuf(mSlots[buf].buffer);
    if ((result & IGraphicBufferProducer::BUFFER_NEEDS_REALLOCATION) || gbuf == nullptr) {
        //如果本地的buffer是null或者远端的buffer更新了，则更新buffer
        result = mGraphicBufferProducer->requestBuffer(buf, &gbuf);
    }
    *buffer = gbuf.get();
    if (mSharedBufferMode && mAutoRefresh) {
        mSharedBufferSlot = buf;
        mSharedBufferHasBeenQueued = false;
    } else if (mSharedBufferSlot == buf) {
        mSharedBufferSlot = BufferItem::INVALID_BUFFER_SLOT;
        mSharedBufferHasBeenQueued = false;
    }
    return OK;
}
```

这里会通过GBP从SurfaceFling去申请buffer，然后保存到backBuffer中，用于进行在后台进行图像的绘制。



#### 释放canvas

对于canvas的释放，则主要是通过**unlockCanvasAndPost**方法。

```java
//Surface.java
	public void unlockCanvasAndPost(Canvas canvas) {
           unlockSwCanvasAndPost(canvas);
    }
```



```c++
//android_view_Surface.cpp
static void nativeUnlockCanvasAndPost(JNIEnv* env, jclass clazz,
        jlong nativeObject, jobject canvasObj) {
    sp<Surface> surface(reinterpret_cast<Surface *>(nativeObject));
    Canvas* nativeCanvas = GraphicsJNI::getNativeCanvas(env, canvasObj);
    //给canvas设置一个空的bitmap
    nativeCanvas->setBitmap(SkBitmap());
    //解除锁定，并将buffer提交
    status_t err = surface->unlockAndPost();
}
```
这里的unlockAndPost方法是将绘制之后的buffer区域数据返回给SurfaceFling进行绘制的。

```c++
//Surface.cpp
status_t Surface::unlockAndPost()
{
    int fd = -1;
    //解除锁定
    status_t err = mLockedBuffer->unlockAsync(&fd);
    ALOGE_IF(err, "failed unlocking buffer (%p)", mLockedBuffer->handle);
    //
    err = queueBuffer(mLockedBuffer.get(), fd);
    //将后台buffer，升级为前台buffer，
    mPostedBuffer = mLockedBuffer;
    //后台buffer进行清空c++
    mLockedBuffer = nullptr;
    return err;
}
```





### 总结

Surface的整个绘制原理如下图：

![image-20210228224834268](http://cdn.qiniu.kailaisii.com/typora/20210228224836-510535.png)

**讲解：应用层要绘制图像的话，首先会创建Surface，Surface通过producer来和SurfaceFlinger进行交互，从BufferQueue中申请buffer，然后canvas绘制完成之后，会将buffer数据返回给BufferQueue中，然后通知consumer端，将BufferQueue中的数据进行绘制，显示到屏幕上。**

对于surface的绘制所使用的生产者和消费者，都是运行在不同的进程的。

![image-20210228224754506](http://cdn.qiniu.kailaisii.com/typora/20210228224755-614401.png)



- **生产者请求一块空闲的缓存区:*dequeueBuffer()\***
- **生产者填充缓存区并返回给队列: \**queueBuffer()\****
- **消费者获取一块缓存区: \**acquireBuffer()\***
- **消费者使用完毕,则返回给队列: \**releaseBuffer()\****

#### 总结：

buffer有两种，一种是backbuffer，用于在后台进行绘制，另一种是前台buffer，用于显示。


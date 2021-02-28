Surface的跨进程传递原理

问题：

1. 怎么理解Surface，它是一个buffer么？
2. 如果是，那么我们现在的屏幕分辨率那么大，针率那么高，buffer数据肯定特别大，那么跨进程传递是怎么带上这个buffer的？
3. 如果不是，那么Surface和buffer又有什么关系呢？那surface又是如何快进程传递的呢？

首先我们看一下Surface类：

```java
public class Surface implements Parcelable {
   public void readFromParcel(Parcel source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        synchronized (mLock) {
            mName = source.readString();
            mIsSingleBuffered = source.readInt() != 0;
            setNativeObjectLocked(nativeReadFromParcel(mNativeObject, source));
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (dest == null) {
            throw new IllegalArgumentException("dest must not be null");
        }
        synchronized (mLock) {
            //写了一个名字
            dest.writeString(mName);
            //写了一个int数据，标明是否是SingleBuffer
            dest.writeInt(mIsSingleBuffered ? 1 : 0);
            //调用了一个native函数，写入了mNativeObject
            nativeWriteToParcel(mNativeObject, dest);
        }
        if ((flags & Parcelable.PARCELABLE_WRITE_RETURN_VALUE) != 0) {
            release();
        }
    }
}
```

可以看到Surface是继承自Parcelable的，那么就可以支持跨进程的通讯。而跨进程通信主要就是依靠writeToParcel和readFromParcel两个函数。

Java层的Surface的跨进程传输基本没有任何buffer的影子，很多都是在Native来进行处理的。

### Activity的Surface传递

在[View的绘制]()一文中，我们讲过，对于View的绘制工作，是在performTraversals()方法中进行绘制工作的。那么这里的Surface是如何传递到Activity层的呢？

而Activity需要有一个DectorView，而View的绘制，是需要Surface。而Surface的使用需要向系统申请，系统生成Surface之后，将起返回给应用。



```java
//framework\base\core\java\android\view\ViewRootImpl.java
    private void performTraversals() {
		//这里如果窗口个各种信息发生了变化，就需要进行测量工作
        if (mFirst || windowShouldResize || insetsChanged ||viewVisibilityChanged || params != null || mForceNextWindowRelayout) {
            mForceNextWindowRelayout = false;
				    //请求WMS计算Activity窗口大小及边衬区域大小
            relayoutResult = relayoutWindow(params, viewVisibility, insetsPending);
    }
      
   private int relayoutWindow(WindowManager.LayoutParams params,...) throws RemoteException {
		//通过mWindowSession这个binder对象向WMS发起relayout调用，强制进行重新layout操作
        //mWindowSession是AP和WMS一个打通的通道，具体的实现是在WMS中
        //这里传入了一个mSurfaceControl对象，在该方法中，会将创建的Surface对象保存到mSurfaceControl中。
        int relayoutResult = mWindowSession.relayout(mWindow, mSeq, params,
                (int) (mView.getMeasuredWidth() * appScale + 0.5f),
                (int) (mView.getMeasuredHeight() * appScale + 0.5f), viewVisibility,
                insetsPending ? WindowManagerGlobal.RELAYOUT_INSETS_PENDING : 0, frameNumber,
                mTmpFrame, mPendingOverscanInsets, mPendingContentInsets, mPendingVisibleInsets,
                mPendingStableInsets, mPendingOutsets, mPendingBackDropFrame, mPendingDisplayCutout,
                mPendingMergedConfiguration, mSurfaceControl, mTempInsets);
        if (mSurfaceControl.isValid()) {
            mSurface.copyFrom(mSurfaceControl);
        } else {
            destroySurface();
        }
        return relayoutResult;
    }  
```

这里调用了relayout方法。

### mWindowSession

这里我们看一下mWindowSession对象，它是在ViewRootImpl的构造函数中进行初始化的

```java
//framework\base\core\java\android\view\ViewRootImpl.java
	public ViewRootImpl(){
        mWindowSession = WindowManagerGlobal.getWindowSession();
	}
```

继续跟踪

```java
 //framework\base\core\java\android\view\WindowManagerGlobal.java
    public static IWindowSession getWindowSession() {
        synchronized (WindowManagerGlobal.class) {
            if (sWindowSession == null) {
                try {
                    InputMethodManager.ensureDefaultInstanceForDefaultDisplayIfNecessary();
                    //获取了WMS对应的Binder句柄
                    IWindowManager windowManager = getWindowManagerService();
                    //返回一个IWindowSession对象
                    sWindowSession = windowManager.openSession(
                            new IWindowSessionCallback.Stub() {
                                @Override
                                public void onAnimatorScaleChanged(float scale) {
                                    ValueAnimator.setDurationScale(scale);
                                }
                            });
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
            return sWindowSession;
        }
    }

//WindowManagerService.java
    public IWindowSession openSession(IWindowSessionCallback callback) {
        //返回一个Session的句柄
        return new Session(this, callback);
    }


//Session.java
class Session extends IWindowSession.Stub implements IBinder.DeathRecipient {
   public Session(WindowManagerService service, IWindowSessionCallback callback) {
        mService = service;
        mCallback = callback;
        ...
}
```

最后可以看到，这个类继承IWindowSession.Stub对象，是Binder的服务端。

### relayout()

```java
 //framework\base\services\core\java\com\android\server\wm\Session.java
		public int relayout(IWindow window,...,SurfaceControl outSurfaceControl, InsetsState outInsetsState) {
        //调用了mService的relayoutWindow方法。这里的mService是WMS对象，是在构造函数中进行的赋值
        int res = mService.relayoutWindow(this, window, ..., outSurfaceControl, outInsetsState);
        return res;
    }
```

调用了WMS的relayoutWindow方法。

```java
 //framework\base\services\core\java\com\android\server\wm\WindowManagerService.java
	public int relayoutWindow(Session session, IWindow client, int seq, LayoutParams attrs,
            int requestedWidth, int requestedHeight, int viewVisibility, int flags,
            long frameNumber, Rect outFrame, Rect outOverscanInsets, Rect outContentInsets,
            Rect outVisibleInsets, Rect outStableInsets, Rect outOutsets, Rect outBackdropFrame,
            DisplayCutout.ParcelableWrapper outCutout, MergedConfiguration mergedConfiguration,
            SurfaceControl outSurfaceControl, InsetsState outInsetsState) {
    				...
            final WindowState win = windowForClientLocked(session, client, false);
    				...
            //重点方法   创建一个SurfaceControl，这里的outSurfaceControl是在Activity中传入的一个空壳对象
            result = createSurfaceControl(outSurfaceControl, result, win, winAnimator);
  }

   private int createSurfaceControl(SurfaceControl outSurfaceControl, WindowState win,...) {
        		WindowSurfaceController surfaceController;
            //获取一个surfaceController对象
            surfaceController = winAnimator.createSurfaceLocked(win.mAttrs.type, win.mOwnerUid);
     				...
            //这里会将surfaceController的一些数据拷贝到outSurfaceControl中，也就是会传输给我们Activit中的surfaceControl，从而能够获取Surface
            surfaceController.getSurfaceControl(outSurfaceControl);
        return result;
    }
```

##### createSurfaceLocked

createSurfaceLocked方法会创建一个WindowSurfaceController对象。

```java
   
	WindowSurfaceController createSurfaceLocked(int windowType, int ownerUid) {
        final WindowState w = mWin;
        if (mSurfaceController != null) {
            //如果已经存在了，则直接返回
            return mSurfaceController;
        }
            //创建一个WindowSurfaceController对象
            mSurfaceController = new WindowSurfaceController(mSession.mSurfaceSession,
                    attrs.getTitle().toString(), width, height, format, flags, this,
                    windowType, ownerUid);
     				...
   }
```

继续跟踪WindowSurfaceController的构造函数

```java
    public WindowSurfaceController(SurfaceSession s, String name, int w, int h, int format,
            int flags, WindowStateAnimator animator, int windowType, int ownerUid) {
       
        mService = animator.mService;
        final WindowState win = animator.mWin;
        mWindowType = windowType;
        mWindowSession = win.mSession;
        final SurfaceControl.Builder b = win.makeSurface()
                .setParent(win.getSurfaceControl())
                .setName(name)
                .setBufferSize(w, h)
                .setFormat(format)
                .setFlags(flags)
                .setMetadata(METADATA_WINDOW_TYPE, windowType)
                .setMetadata(METADATA_OWNER_UID, ownerUid);
        //创建一个SurfaceControl
        mSurfaceControl = b.build();
        Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
    }
```



```java
//SurfaceControl.java
        public SurfaceControl build() {
            return new SurfaceControl(mSession, mName, mWidth, mHeight, mFormat, mFlags, mParent, mMetadata);
        }
        
   private SurfaceControl(SurfaceSession session, String name, SurfaceControl parent...){
        mName = name;
        mWidth = w;
        mHeight = h;
        Parcel metaParcel = Parcel.obtain();
        //创建一个Native层的SurfaceContorl对象
        mNativeObject = nativeCreate(session, name, w, h, format, flags,
                    parent != null ? parent.mNativeObject : 0, metaParcel);
    }
```

创建了SurfaceControl对象，而且对象内部包含了Native层的SurfaceControl对象，我们这里跟踪一下Native的创建方法

```c++
static jlong nativeCreate(JNIEnv* env, jclass clazz, jobject sessionObj,..., jlong parentObject...) {
    ScopedUtfChars name(env, nameStr);
    sp<SurfaceComposerClient> client;
    if (sessionObj != NULL) {
        //该方法的功能是，获取sessionObj中的SurfaceComposerClient对象中的mNativeClient
        client = android_view_SurfaceSession_getClient(env, sessionObj);
    } else {
        client = SurfaceComposerClient::getDefault();
    }
    SurfaceControl *parent = reinterpret_cast<SurfaceControl*>(parentObject);
    sp<SurfaceControl> surface;
    LayerMetadata metadata;
    Parcel* parcel = parcelForJavaObject(env, metadataParcel);
    //创建了一个nativce层的SurfaceControl对象
    status_t err = client->createSurfaceChecked(String8(name.c_str()),..., &surface, flags, parent, std::move(metadata));
    surface->incStrong((void *)nativeCreate);
    return reinterpret_cast<jlong>(surface.get());
}
```

这里有两个方法我们需要跟踪处理。

* sp<SurfaceComposerClient>对象的获取
*  sp<SurfaceControl>对象的获取。

##### sp<SurfaceComposerClient>对象的获取

对于sp<SurfaceComposerClient>对象的获取，我们

```java
sp<SurfaceComposerClient> android_view_SurfaceSession_getClient(JNIEnv* env, jobject surfaceSessionObj) {
    //获取surfaceSessionObj对象的中的mNativeClient对象
    return reinterpret_cast<SurfaceComposerClient*>(env->GetLongField(surfaceSessionObj, gSurfaceSessionClassInfo.mNativeClient));
}
```

这里的surfaceSessionObj是Java层的SurfaceSession对象，我们看一下这个SurfaceSession对象的创建。

##### SurfaceSession创建

在[View的绘制流程]()一文中，我们讲过，页面的东西，会通过**setView()**方法来显示出来。而SurfaceSession其实就是在这个方法里面去创建的

```java
    public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
        synchronized (this) {
            if (mView == null) {
                //初始化一些信息
                ...
                //mWindowSession是Session的IBinder代理，通过IPC机制调用Session的addToDisplay方法
                //addToDisplay方法内部通过IPC机制调用WMS的addWindow方法
                //通过IPC机制，将AMS中的addWindow方法来在系统进程中执行相关加载Window的操作。
                res = mWindowSession.addToDisplay(mWindow, mSeq,...,mTempInsets);
                setFrame(mTmpFrame);
```

这里会调用Session内的addToDisplay方法。

```java
//Session.java
	public int addToDisplay(IWindow window, int seq, WindowManager.LayoutParams attrs,
            int viewVisibility, int displayId, Rect outFrame, Rect outContentInsets,
            Rect outStableInsets, Rect outOutsets,
            DisplayCutout.ParcelableWrapper outDisplayCutout, InputChannel outInputChannel,
            InsetsState outInsetsState) {
        //mService是WMS
        return mService.addWindow(this, window, seq, attrs, viewVisibility, displayId, outFrame,
                outContentInsets, outStableInsets, outOutsets, outDisplayCutout, outInputChannel,
                outInsetsState);
    }
	
//WMS.java
    public int addWindow(Session session, IWindow client, ...) {
            //将Session对象封装到一个WindowState对象中
            final WindowState win = new WindowState(this, session, client, token, parentWindow,
                    appOp[0], seq, attrs, viewVisibility, session.mUid,
                    session.mCanAddInternalSystemWindow);
            //重点方法  
            win.attach();
        }


```



* 在native层中创建SurfaceSession，在其中创建了SurfaceComposerClient对象。在SurfaceComposerClient的构造函数中他连接了native层中的SurfaceFlinger。

* SurfaceComposerClient对象可以创建SurfaceClient对象。

* mWindowSession.relayout方法则会将创建的SurfaceControl对象传给WMS层，然后WMS层通过createSurfaceControl方法，创建一个SurfaceController，然后将创建的surfaceController对象的数据拷贝到ViewRootImpl中

* Android系统可能存在多个app来请求SurfaceFlinger的业务，而每个app又可能存在多个surface，而这些surface对象如果分散起来又不好管理，所以android系统为他们构建了一个Client类来负责与Surffaceflinger来交互，并且负责管理自身的Surface对象。

* SurfaceControl这个类起到了连接java层与native层的作用，在native层创建Surface对象返回java层进行显示画面。

在SurfaceControl的构造函数中，会根据Session



类功能总结：

* SurfaceControl用来存储Surface对象，关联Java层和Native曾
* SurfaceSession用来创建SurfaceComposerClient，并利用binder和SurfaceFlinger建立连接
* SurfaceFling是一个独立的进程，靠Client来管理每个app的Surface。
* 一个app只有一个Client，一个Client可以通过Binder管理多个Layer，Layer在创建时会构造一个IGraphicBufferProducer对象。
* GraphicBufferProducer对象是用来申请Buffer进行绘制的
* SurfaceSession实际上的具体实现类是WindowSurfaceSession，会被保存到WidowState类中。







https://blog.csdn.net/a501216475/article/details/77187119

https://www.jianshu.com/p/64e5c866b4ae
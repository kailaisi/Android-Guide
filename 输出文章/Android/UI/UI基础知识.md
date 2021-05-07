UI基础知识

### 绘制篇

#### Paint

```java
paint.setColorFilter //设置过滤色
```

##### PorterDuffXfermode能够进行图形的混合

应用场景：

圆角

![](https://img-blog.csdn.net/20160109223310858)

#### Shade

##### BitmapShader

主要对图片进行一些处理：重复(Shader.TileMode.CLAMP)，倒影(Shader.TileMode.MIRROR)，拉伸(Shader.TileMode.CLAMP)等等

mPaint.setShader(new BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.CLAMP));



##### LinearGradient

线性渐变效果

![](https://img-blog.csdn.net/20141208094854902?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvYWlnZXN0dWRpbw==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/Center)

##### SweepGradient

梯度渐变，也称之为扫描式渐变，因为其效果有点类似雷达的扫描效果

```
mPaint.setShader(new SweepGradient(screenX, screenY, Color.RED, Color.YELLOW));
```

![](https://img-blog.csdn.net/20141208095059780?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvYWlnZXN0dWRpbw==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/Center)

##### RadialGradient

径向渐变，径向渐变说的简单点就是个圆形中心向四周渐变的效果

##### ComposeShader

用于实现Shader的组合。

#### Mixra

matri是对坐标的一种平移、缩放、旋转的操作处理。

而对于每一次操作，其实都有pre\*\*\*（即先执行）和post\*\*\*（后执行）。所以

```java
matrix.preScale(0.5f, 1); 
matrix.setScale(1, 0.6f); 
matrix.postScale(0.7f, 1); 
matrix.preTranslate(15, 0);
```

那么Matrix的计算过程即为：translate (15, 0) -> scale (1, 0.6f) -> scale (0.7f, 1)，我们说过set会重置数据，所以最开始的

```java
matrix.preScale(0.5f, 1);
```

也就无效了。

#### Canvas

画布，所有的信息都是在canvas中进行绘制的。

常用的canvas处理方式：

旋转，切，平移，变换（通过Matrix）

常用的操作类型：**draw\*\*，clip\*\*,scale，trans，setMatrix。**

##### path

path代表的是路径。用来绘制（或者剪切）不规则类型。常用的有reset，lineTo，moveTo，quadTo(塞贝尔曲线)，rLineTo（相对当前点移动）等等。

##### save and restore

能够进行画布的保存和恢复。当使用save之后，之后所有的绘制都在其图层上进行绘制。

比如：

```java
protected void onDraw(Canvas canvas) {
	/*
	 * 保存并裁剪画布填充绿色
	 */
	int saveID1 = canvas.save(Canvas.CLIP_SAVE_FLAG);
	canvas.clipRect(mViewWidth / 2F - 300, mViewHeight / 2F - 300, mViewWidth / 2F + 300, mViewHeight / 2F + 300);
	canvas.drawColor(Color.YELLOW);
 
	/*
	 * 保存并裁剪画布填充绿色
	 */
	int saveID2 = canvas.save(Canvas.CLIP_SAVE_FLAG);
	canvas.clipRect(mViewWidth / 2F - 200, mViewHeight / 2F - 200, mViewWidth / 2F + 200, mViewHeight / 2F + 200);
	canvas.drawColor(Color.GREEN);
 
	/*
	 * 保存画布并旋转后绘制一个蓝色的矩形
	 */
	int saveID3 = canvas.save(Canvas.MATRIX_SAVE_FLAG);
 
	// 旋转画布
	canvas.rotate(5);
	mPaint.setColor(Color.BLUE);
	canvas.drawRect(mViewWidth / 2F - 100, mViewHeight / 2F - 100, mViewWidth / 2F + 100, mViewHeight / 2F + 100, mPaint);
	
	mPaint.setColor(Color.CYAN);
	canvas.drawRect(mViewWidth / 2F, mViewHeight / 2F, mViewWidth / 2F + 200, mViewHeight / 2F + 200, mPaint);
}

```

这段代码，**saveID2生成之后，进行了剪切，只要没有进行restore，那么后面所有的旋转，绘制，都会在这个剪切之后的画布上进行。**

### 测量篇

对于测量，我们都知道是通过**onMeasure**方法来处理

```java
@Override
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
	super.onMeasure(widthMeasureSpec, heightMeasureSpec);
}
```

那么顶层的这个方法是谁来调用呢？

我们先看看我们自己的actvity在屏幕中的位置：

![](https://img-blog.csdn.net/20150121232534329)

我们的顶层是DecorView。他的onMeasure方法又是由谁来测量并调用的呢？

这时候我们就需要知道ViewRootImpl了。

```java
private void performTraversals() {
	// ………省略宇宙尘埃数量那么多的代码………
 
	if (!mStopped) {
		// ……省略一些代码
 				//限制最大的宽高是我们的屏幕大小。
				int childWidthMeasureSpec = getRootMeasureSpec(mWidth, lp.width);
        int childHeightMeasureSpec = getRootMeasureSpec(mHeight, lp.height);
 
        // ……省省省
 
        performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);
	}
 
	// ………省略人体细胞数量那么多的代码………
}

```

在进行绘制之前，会进行root的测量，然后将其传给我们的顶层DectorView来进行处理。

##### onLayout

onLayout确定子控件的具体的布局。在进行布局的时候，padding数据可以直接在ViewGroup中获取，而对于子控件的margin数据，则需要通过子控件来获取。通过获取对应的layoutparam来进行获取。然后在onLayout的时候，要考虑子控件的margin信息。

当布局发生变化的时候，如果大小发生变化，则需要**requestLayout()**方法，请求进行重新测量绘制。如果只是字体颜色变化，只需要**invalidate()**即可。

### 自定义篇

##### 继承View

##### 继承ViewGroup

##### 组合控件

* 编写xml文件，将对应的效果进行组合，然后通过Java文件，绘制xml文件并设置相关的信息。
* 直接通过new来创建子元素，并设置其相关属性（相对于第一种的xml，因为反射比较耗时，所以性能更好，但是不太适合逻辑比较复杂的布局）。

参考：

https://blog.csdn.net/iispring/article/details/50472485
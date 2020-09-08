### 引言

在之前的[Android布局窗口绘制分析](https://mp.weixin.qq.com/s?__biz=MzUzOTE4MTQzNQ==&mid=2247483756&idx=1&sn=864c8dd5815aa0fa4ff973f9f290831d&chksm=facd2978cdbaa06efd6147f373de477554731a4c5bbc55075614bd1f42987b62161944e73cb1&token=1643951990&lang=zh_CN#rd)一篇文章中，我们介绍过如何将布局加载到PhoneWindows窗口中并显示。而在[Android的inflate源详解](https://mp.weixin.qq.com/s?__biz=MzUzOTE4MTQzNQ==&tempkey=MTA1N19JQmIzc1h5Nm90QVIrV2Z3czBBblN0MWlEY2xLRHpjR0RXVk9aNUphd3liV0tHSGxRQnIyWVdYd1FqSFRQLVdIcTBHOWcwV25sSDd4ektoTGN5Qnc4T2VheVlESmhTS1Q0UjVLM28zNWlELXVQWVd3TEE0NE5Yc0FCaldpYzlvd1R3Ti1lTk5aZ2gtLVpfeWtWMDR1MGxyWklHZ1d1Q2tXZzR0VEpnfn4%3D&chksm=7acd297c4dbaa06a5b3d3cf8e1b07406c763f3a6e3f6de326aa55fb34f288e5d158f0b13a3d7&__mpa_temp_link_flag=1&token=972610219#rd)中，我们则分析了如何将xml的布局文件转化为View树。但是View树具体以何种位置、何种大小展现给我们，没有具体讲解的。那么这篇文章，我们就在上两章的基础上继续研究View是如何进行布局和绘制的。

还记得我们在【Android布局窗口绘制分析】一文中的最后的addView代码块中重点标注的requestLayout()方法么？

 不记得了也没关系，我把代码贴出来就是了~

       //ViewRootImpl.java
       public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
            synchronized (this) {
                if (mView == null) {
                    ...
                    // 这里调用异步刷新请求，最终会调用performTraversals方法来完成View的绘制
                    //重点方法  这里面会进行view的 测量，layout，以及绘制工作
                    requestLayout();
                    ...
            }
        }

这句代码就是View的绘制的入口，经过measure,layout,draw最终将我们在【Android的inflate源详解】中所形成的View树绘制出来。当这篇文章完成之后，安卓如何从xml到view树，然后将view树进行绘制，然后将view添加到DecterView并显示出来，这一整套流程就可以结束了。

### 基础知识

Android View的绘制过程分为3步： 测量、布局、绘制

### 源码

```java
   //ViewRootImpl.java 
    public void requestLayout() {
    	//该boolean变量会在ViewRootImpl.performLayout()开始时置为ture，结束置false
        //表示当前不处于Layout过程
        if (!mHandlingLayoutInLayoutRequest) {
			//检测线程安全，只有创建这个view的线程才能操作这个线程(也就是主线程)。
            checkThread();
			//标记请求进行绘制
            mLayoutRequested = true;
            //进行调度绘制工作
            scheduleTraversals();
        }
    }
```
这段代码主要就是一个检测，如果当前正在进行layout，那么就不处理。否则就进行调度绘制。

```java
//ViewRootImpl.java 
void scheduleTraversals() {
    if (!mTraversalScheduled) {
		///表示在排好这次绘制请求前，不再排其它的绘制请求
        mTraversalScheduled = true;
		//Handler 的同步屏障,拦截 Looper 对同步消息的获取和分发,只能处理异步消息
		//也就是说，对View的绘制渲染操作优先处理
        mTraversalBarrier = mHandler.getLooper().getQueue().postSyncBarrier();
		//mChoreographer能够接收系统的时间脉冲，统一动画、输入和绘制时机,实现了按帧进行绘制的机制
		//这里增加了一个事件回调的类型。在绘制时，会调用mTraversalRunnable方法
        mChoreographer.postCallback(Choreographer.CALLBACK_TRAVERSAL, mTraversalRunnable, null);
        if (!mUnbufferedInputDispatch) {
            scheduleConsumeBatchedInput();
        }
        notifyRendererOfFramePending();
        pokeDrawLockIfNeeded();
    }
}
```

这个函数里面，将我们要执行的绘制工作交给 **mChoreographer** 来进行调度处理。这个对象的主要工作就是根据系统的时间脉冲，将输入、动画、绘制等工作按照帧进行切割绘制。这里的入参的回调对象 **mTraversalRunnable** 就是当对应的帧的周期到来时执行的对象。我们跟踪看一下里面执行了什么操作。

```java
	//ViewRootImpl.java
	final TraversalRunnable mTraversalRunnable = new TraversalRunnable();

    final class TraversalRunnable implements Runnable {
        @Override
        public void run() {
            doTraversal();
        }
    }

    void doTraversal() {
        if (mTraversalScheduled) {
            mTraversalScheduled = false;
			//移除同步屏障
            mHandler.getLooper().getQueue().removeSyncBarrier(mTraversalBarrier);

            if (mProfile) {
                Debug.startMethodTracing("ViewAncestor");
            }
			//重点方法   执行绘制工作
            performTraversals();

            if (mProfile) {
                Debug.stopMethodTracing();
                mProfile = false;
            }
        }
    }
```

所以最后执行的就是 **doTraversal()** 这个方法。然后里面调用了 **performTraversals()** 。这个方法就是我们最终要剖析的函数对象了，可以说这个函数属于 **View绘制的核心代码** 。所有的测量、布局、绘制的工作都是在这个函数里面来调用执行的。

对于一个将近千行的代码，我们只能逐一拆分来进行解析了。

```java
//ViewRootImpl.java
		private void performTraversals() {
        //将mView缓存，用final修饰，避免运行过程中修改
        final View host = mView;
		//如果没有添加到DecorView,则直接返回
        if (host == null || !mAdded)
            return;
		//设置正在遍历标志位
        mIsInTraversal = true;
        //标记马上就要进行View的绘制工作
        mWillDrawSoon = true;
        //视图大小是否改变的标志位
        boolean windowSizeMayChange = false;
        boolean surfaceChanged = false;
		//属性
        WindowManager.LayoutParams lp = mWindowAttributes;
		//顶层Decor的宽高
        int desiredWindowWidth;
        int desiredWindowHeight;
		//顶层Decor是否可见
        final int viewVisibility = getHostVisibility();
		//视图Decor的可见性改变了
        final boolean viewVisibilityChanged = !mFirst&& (mViewVisibility != viewVisibility || mNewSurfaceNeeded || mAppVisibilityChanged);
        mAppVisibilityChanged = false;
        final boolean viewUserVisibilityChanged = !mFirst &&((mViewVisibility == View.VISIBLE) != (viewVisibility == View.VISIBLE));
		...
        requestLayout = 0;
		//用来表示当前绘制的Activity窗口的宽高信息
        Rect frame = mWinFrame;
		//在构造方法中，设置为了true，表示是否是第一次请求执行测量、布局绘制工作
        if (mFirst) {
            //是否需要全部绘制
            mFullRedrawNeeded = true;
            //是否需要执行layout请求
            mLayoutRequested = true;
            final Configuration config = mContext.getResources().getConfiguration();
			//如果有状态栏或者输入框，那么Activity窗口的宽度和高度刨除状态栏
            if (shouldUseDisplaySize(lp)) {
                Point size = new Point();
                mDisplay.getRealSize(size);
                desiredWindowWidth = size.x;
                desiredWindowHeight = size.y;
            } else {
				//否则就是整个屏幕的宽高
                desiredWindowWidth = mWinFrame.width();
                desiredWindowHeight = mWinFrame.height();
            }
             /**
             * 因为第一次遍历，View树第一次显示到窗口
             * 然后对mAttachinfo进行一些赋值
             * AttachInfo是View类中的静态内部类AttachInfo类的对象
             * 它主要储存一组当View attach到它的父Window的时候视图信息
             */
            mAttachInfo.mUse32BitDrawingCache = true;
            mAttachInfo.mWindowVisibility = viewVisibility;
            mAttachInfo.mRecomputeGlobalAttributes = false;
            mLastConfigurationFromResources.setTo(config);
            mLastSystemUiVisibility = mAttachInfo.mSystemUiVisibility;
            //设置布局方向
            if (mViewLayoutDirectionInitial == View.LAYOUT_DIRECTION_INHERIT) {
                host.setLayoutDirection(config.getLayoutDirection());
            }
            host.dispatchAttachedToWindow(mAttachInfo, 0);
            mAttachInfo.mTreeObserver.dispatchOnWindowAttachedChange(true);
            dispatchApplyInsets(host);
        } else {//如果不是第一次请求执行测量、布局的操作，直接使用frame的宽高信息即可，也就是上一次储存的宽高
            desiredWindowWidth = frame.width();
            desiredWindowHeight = frame.height();
			//这个mWidth和mHeight也是用来描述Activity窗口当前宽度和高度的.它们的值是由应用程序进程上一次主动请求WindowManagerService服务计算得到的，并且会一直保持不变到应用程序进程下一次再请求WindowManagerService服务来重新计算为止
			//desiredWindowWidth和desiredWindowHeight代表着Activity窗口的当前宽度
            if (desiredWindowWidth != mWidth || desiredWindowHeight != mHeight) {
				//mWidth此时代表的是上一次执行该方法的时候的frame.width()值，如果此时两值不相等，那就说明视图改变需要重新测量绘制了。
                if (DEBUG_ORIENTATION) Log.v(mTag, "View " + host + " resized to: " + frame);
				//需要重新绘制标标志位
                mFullRedrawNeeded = true;
				//需要执行layout标志位
                mLayoutRequested = true;
				//标记窗口的宽高变化了
                windowSizeMayChange = true;
            }
        }
```

这段代码的主要工作是为了计算当前Activity的宽高。涉及的知识点相对来说还是少一些的

1. 如果是第一次被请求绘制，会根据屏幕信息来进行设置。如果窗口是有状态栏的，那么Activity的宽高就会从Decor中剔除状态栏的高度，否则的话，就设置为整个屏幕的宽高
2. 如果不是第一次执行，那么Activity的宽高是上一次测量、布局绘制时保存的值。也就是frame成员变量中的宽高信息。
3. frame中的mWidth和mHeight是由WMS计算得到的一个值，一直会保留到下一个WMS计算才会改变。而 **desiredWindowWidth** 和 **desiredWindowHeight** 则是当前Activity的宽高。如果二者不同，说明窗口发生了变化，这时候就需要将 **mLayoutRequested** 和 **windowSizeMayChange** 进行设置，表示在后面的处理中需要进行布局的工作。
4. 这里有一个 **mAttachInfo** 对象的相关赋值处理。它是 **View.AttachInfo** 类，主要负责将当前的View视图附加到其父窗口时的一系列信息。

我们继续看第二段代码。

```java
//ViewRootImpl.java
		// 如果窗口不可见了，去掉可访问性焦点
        if (mAttachInfo.mWindowVisibility != View.VISIBLE) {
            host.clearAccessibilityFocus();
        }
        //执行HandlerActionQueue中HandlerAction数组保存的Runnable
        getRunQueue().executeActions(mAttachInfo.mHandler);
        boolean insetsChanged = false;
		//是否需要重新执行layout(执行了layoutRequest请求，并且当前页面没有停止)。
        boolean layoutRequested = mLayoutRequested && (!mStopped || mReportNextDraw);
        if (layoutRequested) {
			//获取Resources
            final Resources res = mView.getContext().getResources();
            if (mFirst) {
                //指示View所处的Window是否处于触摸模式
                mAttachInfo.mInTouchMode = !mAddedTouchMode;
				//确保这个Window的触摸模式已经被设置
                ensureTouchModeLocally(mAddedTouchMode);
            } else {
				//判断几个insects是否发生了变化
                ...
				//如果当前窗口的根布局是wrap，比如dialog，给它尽量大的宽高，这里会将屏幕的宽高赋值给它
                if (lp.width == ViewGroup.LayoutParams.WRAP_CONTENT|| lp.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
                    windowSizeMayChange = true;
					//如果有状态栏或者输入框，那么Activity窗口的宽度和高度刨除状态栏
                    if (shouldUseDisplaySize(lp)) {
                        // NOTE -- system code, won't try to do compat mode.
                        Point size = new Point();
                        mDisplay.getRealSize(size);
                        desiredWindowWidth = size.x;
                        desiredWindowHeight = size.y;
                    } else {
						//获取手机的配置信息
                        Configuration config = res.getConfiguration();
                        desiredWindowWidth = dipToPx(config.screenWidthDp);
                        desiredWindowHeight = dipToPx(config.screenHeightDp);
                    }
                }
            }
            //进行预测量窗口大小，以达到更好的显示大小。比如dialog，如果设置了wrap，我们会给出最大的宽高，但是如果只是显示一行字，显示肯定不会特别优雅，所以会使用
            //measureHierarchy来进行一下优化，尽量展示出一个舒适的UI效果出来
            windowSizeMayChange |= measureHierarchy(host, lp, res,desiredWindowWidth, desiredWindowHeight);
        }
```

这段代码主要是对一些视图的可见性的处理等

1. 设置焦点
2. 设置触摸模式
3. 如果窗口的跟布局使用了wrap，那么会给尽量大的宽高，然后使用 **measureHierarchy()** 方法进行重新处理。

这里面的 **measureHierarchy()** 方法使我们可以研究的一个地方

```java
//ViewRootImpl.java
	//测量层次结构
    private boolean measureHierarchy(final View host, final WindowManager.LayoutParams lp,final Resources res, final int desiredWindowWidth, final int desiredWindowHeight) {
        //用于描述最终宽度的spec
        int childWidthMeasureSpec;
        //用于描述最终高度的spec
        int childHeightMeasureSpec;
        boolean windowSizeMayChange = false;
        boolean goodMeasure = false;
        if (lp.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
            //在大屏幕上，我们不希望让对话框仅仅拉伸来填充整个屏幕的宽度来显示一行文本。首先尝试在一个较小的尺寸布局，看看它是否适合
            final DisplayMetrics packageMetrics = res.getDisplayMetrics();
            //通过assertManager获取config_prefDialogWidth设置的宽高，相关信息会保存在mTmpValue中
            res.getValue(com.android.internal.R.dimen.config_prefDialogWidth, mTmpValue, true);
            int baseSize = 0;
            if (mTmpValue.type == TypedValue.TYPE_DIMENSION) {
                //获取dim中设置的高度值
                baseSize = (int) mTmpValue.getDimension(packageMetrics);
            }
            if (DEBUG_DIALOG) Log.v(mTag, "Window " + mView + ": baseSize=" + baseSize+ ", desiredWindowWidth=" + desiredWindowWidth);
            if (baseSize != 0 && desiredWindowWidth > baseSize) {
                //组合SPEC_MODE与SPEC_SIZE为一个MeasureSpec
                childWidthMeasureSpec = getRootMeasureSpec(baseSize, lp.width);
                childHeightMeasureSpec = getRootMeasureSpec(desiredWindowHeight, lp.height);
                //执行一次测量
                performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);
                if (DEBUG_DIALOG) Log.v(mTag, "Window " + mView + ": measured ("
                        + host.getMeasuredWidth() + "," + host.getMeasuredHeight()
                        + ") from width spec: " + MeasureSpec.toString(childWidthMeasureSpec)
                        + " and height spec: " + MeasureSpec.toString(childHeightMeasureSpec));
                //getMeasuredWidthAndState获取绘制结果，如果绘制的结果不满意会设置MEASURED_STATE_TOO_SMALL，
                if ((host.getMeasuredWidthAndState() & View.MEASURED_STATE_TOO_SMALL) == 0) {
					//绘制满意
                    goodMeasure = true;
                } else {
                	//绘制不满意
                    //宽度重新设置一个平均值？
                    baseSize = (baseSize + desiredWindowWidth) / 2;
                    if (DEBUG_DIALOG) Log.v(mTag, "Window " + mView + ": next baseSize="+ baseSize);
                    childWidthMeasureSpec = getRootMeasureSpec(baseSize, lp.width);
					//再次进行测量
                    performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);
                    if (DEBUG_DIALOG) Log.v(mTag, "Window " + mView + ": measured ("+ host.getMeasuredWidth() + "," + host.getMeasuredHeight() + ")");
                    if ((host.getMeasuredWidthAndState() & View.MEASURED_STATE_TOO_SMALL) == 0) {
                        if (DEBUG_DIALOG) Log.v(mTag, "Good!");
                        goodMeasure = true;
                    }
                }
            }
        }

        if (!goodMeasure) {
			//如果经过两次都不满意，那么就只能全屏显示了
            childWidthMeasureSpec = getRootMeasureSpec(desiredWindowWidth, lp.width);
            childHeightMeasureSpec = getRootMeasureSpec(desiredWindowHeight, lp.height);
			//执行第三次测量
            performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);
            if (mWidth != host.getMeasuredWidth() || mHeight != host.getMeasuredHeight()) {
                windowSizeMayChange = true;
            }
        }

        return windowSizeMayChange;
    }
```

可以看到如果是Dialog设置的宽高都是wrap的情况下，会首先使用系统的 **com.android.internal.R.dimen.config_prefDialogWidth** 的值来进行试探，如果合适就使用，如果不合适的话就是用这个值和视图高度的平均值来进行试探，如果还不行，那就只能设置视图的宽高了。可以看到这里面可能会多次执行 **performMeasure** 方法，但是这三次的执行并不属于View三大的绘制流程，仅仅只是为了确定Windows的大小而进行的辅助处理。

到现在为止，一切的准备工作都做完了，那么后面就是进入主题，进行顶层View树的测量、布局和绘制工作了。

#### 测量

##### 进行测量的条件：

```java
//ViewRootImpl.java
//清除layoutRequested，这样如果再有layoutRequested=true的情况，我们就可以认为是有了新的layout请求。
        if (layoutRequested) {
            mLayoutRequested = false;
        }
        //用来确定窗口是否需要更改。
        //layoutRequested 为true，说明view正在调用自身的requestLayout。
        // windowSizeMayChange：说明View树所需大小与窗口大小不一致
        boolean windowShouldResize = layoutRequested && windowSizeMayChange
                //判断上面测量后View树的大小与窗口大小值是否相等
                && ((mWidth != host.getMeasuredWidth() || mHeight != host.getMeasuredHeight())
                //窗口的宽变化了。窗口设置的wrap。计算出来的窗口大小desiredWindowWidth 与上一次测量保存的frame.width()大，同时与WindowManagerService服务计算的宽度mWidth和高度mHeight也不一致，说明窗口大小变化了
                || (lp.width == ViewGroup.LayoutParams.WRAP_CONTENT && frame.width() < desiredWindowWidth && frame.width() != mWidth)
                || (lp.height == ViewGroup.LayoutParams.WRAP_CONTENT && frame.height() < desiredWindowHeight && frame.height() != mHeight));//窗口的高变化了。
        windowShouldResize |= mDragResizing && mResizeMode == RESIZE_MODE_FREEFORM;

        //如果activity进行了重新启动，那么通过wms强制进行resize
        windowShouldResize |= mActivityRelaunched;

        final boolean computesInternalInsets =mAttachInfo.mTreeObserver.hasComputeInternalInsetsListeners()|| mAttachInfo.mHasNonEmptyGivenInternalInsets;

        boolean insetsPending = false;
        int relayoutResult = 0;
        boolean updatedConfiguration = false;

        final int surfaceGenerationId = mSurface.getGenerationId();

        final boolean isViewVisible = viewVisibility == View.VISIBLE;
        final boolean windowRelayoutWasForced = mForceNextWindowRelayout;
        boolean surfaceSizeChanged = false;
		//这里如果窗口个各种信息发生了变化，就需要进行测量工作
        if (mFirst || windowShouldResize || insetsChanged ||viewVisibilityChanged || params != null || mForceNextWindowRelayout) {
```

有时候我们的界面没有必要进行测量工作，毕竟测量属于一个比较耗时而又繁琐的工作。所以对于测量工作的进行，是有一定的执行条件的，而上面的代码就能够告诉我们什么情况下才会进行整个页面的测量工作。

* mFirst为true。表示窗口是第一次执行测量、布局和绘制操作。
* windowShouldResize标志位为true。而这个标志位主要就是判断窗口大小是否发生了变化。
* insetsChanged为true。这个表示此次窗口overscan等一些边衬区域发生了改变
* viewVisibilityChanged为true。这个标志位是View的可见性发生了变化
* params说明窗口的属性发生了变化。
* mForceNextWindowRelayout为true。表示设置了要强制了layout操作

当我们确定需要进行测量的话，下一步就是进行具体的测量工作了。

##### 测量的执行：


```java
//ViewRootImpl.java
				...
				//请求WMS计算Activity窗口大小及边衬区域大小
                relayoutResult = relayoutWindow(params, viewVisibility, insetsPending);

                if (!mPendingMergedConfiguration.equals(mLastReportedMergedConfiguration)) {
                    if (DEBUG_CONFIGURATION) Log.v(mTag, "Visible with new config: "
                            + mPendingMergedConfiguration.getMergedConfiguration());
                    performConfigurationChange(mPendingMergedConfiguration, !mFirst,INVALID_DISPLAY /* same display */);
                    updatedConfiguration = true;
                }
            	//进行一些边界的处理
                final boolean overscanInsetsChanged = !mPendingOverscanInsets.equals(mAttachInfo.mOverscanInsets);
                contentInsetsChanged = !mPendingContentInsets.equals(mAttachInfo.mContentInsets);
                final boolean visibleInsetsChanged = !mPendingVisibleInsets.equals(mAttachInfo.mVisibleInsets);
                final boolean stableInsetsChanged = !mPendingStableInsets.equals(mAttachInfo.mStableInsets);
                final boolean cutoutChanged = !mPendingDisplayCutout.equals(mAttachInfo.mDisplayCutout);
                final boolean outsetsChanged = !mPendingOutsets.equals(mAttachInfo.mOutsets);
                surfaceSizeChanged = (relayoutResult& WindowManagerGlobal.RELAYOUT_RES_SURFACE_RESIZED) != 0;
                surfaceChanged |= surfaceSizeChanged;
                final boolean alwaysConsumeSystemBarsChanged =mPendingAlwaysConsumeSystemBars != mAttachInfo.mAlwaysConsumeSystemBars;
                final boolean colorModeChanged = hasColorModeChanged(lp.getColorMode());
                ...
            //从window session获取最大size作为当前窗口大小
            if (mWidth != frame.width() || mHeight != frame.height()) {
                mWidth = frame.width();
                mHeight = frame.height();
            }
			....
			//当前页面处于非暂停状态，或者接收到了绘制的请求
            if (!mStopped || mReportNextDraw) {
				//获取焦点
                boolean focusChangedDueToTouchMode = ensureTouchModeLocally((relayoutResult & WindowManagerGlobal.RELAYOUT_RES_IN_TOUCH_MODE) != 0);
				//宽高有变化了
                if (focusChangedDueToTouchMode || mWidth != host.getMeasuredWidth()|| mHeight != host.getMeasuredHeight() || contentInsetsChanged ||updatedConfiguration) {
                    //获得view宽高的测量规格，lp.width和lp.height表示DecorView根布局宽和高
                    int childWidthMeasureSpec = getRootMeasureSpec(mWidth, lp.width);
                    int childHeightMeasureSpec = getRootMeasureSpec(mHeight, lp.height);
                    //执行测量工作
                    performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);
                    int width = host.getMeasuredWidth();
                    int height = host.getMeasuredHeight();
                    //需要重新测量标记
                    boolean measureAgain = false;
                    //lp.horizontalWeight表示将多少额外空间水平地(在水平方向上)分配给与这些LayoutParam关联的视图。如果
                    //视图不应被拉伸，请指定0。否则，将在所有权重大于0的视图中分配额外的像素。
                    if (lp.horizontalWeight > 0.0f) {
                        width += (int) ((mWidth - width) * lp.horizontalWeight);
                        childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY);
                        measureAgain = true;
                    }
                    if (lp.verticalWeight > 0.0f) {
                        height += (int) ((mHeight - height) * lp.verticalWeight);
                        childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY);
                        measureAgain = true;
                    }
                    //有变化了，就再次执行测量
                    if (measureAgain) {
                        performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);
                    }
                    //设置请求layout标志位
                    layoutRequested = true;
                }
            }
        } else {
            maybeHandleWindowMove(frame);
        }

```

在执行测量操作之前做了一系列的边界处理。然后如果页面是可见的，那么就调用 **performMeasure()** 方法进行测量，当测量完成以后再根据是否设置了 **weight** 来确定是否需要执行二次测量。这里我们去看一下 **performMeasure()** 函数执行。这里的两个参数是根据屏幕的宽度以及高度生成的 MeasureSpec。

```java
	//执行测量工作
    private void performMeasure(int childWidthMeasureSpec, int childHeightMeasureSpec) {
        if (mView == null) {
            return;
        }
        try {
            //调用measure方法
            mView.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        }
    }
```

这里的mView是DecorView根布局,记录ViewRootImpl管理的View树的根节点，也就是一个 **ViewGroup** 。然后调用了 **measure()** 方法。

```java
//View.java
	//测量view使用的具体的宽高，参数是父类的宽高信息
    public final void measure(int widthMeasureSpec, int heightMeasureSpec) {
        ...
        long key = (long) widthMeasureSpec << 32 | (long) heightMeasureSpec & 0xffffffffL;
        if (mMeasureCache == null) mMeasureCache = new LongSparseLongArray(2);

        final boolean forceLayout = (mPrivateFlags & PFLAG_FORCE_LAYOUT) == PFLAG_FORCE_LAYOUT;
        //观察spec是否发生了变化
        final boolean specChanged = widthMeasureSpec != mOldWidthMeasureSpec|| heightMeasureSpec != mOldHeightMeasureSpec;
        //是否是固定宽高
        final boolean isSpecExactly = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY;
        //上次测量的高度和现在的最大高度相同
        final boolean matchesSpecSize = getMeasuredWidth() == MeasureSpec.getSize(widthMeasureSpec)&& getMeasuredHeight() == MeasureSpec.getSize(heightMeasureSpec);
        //是否需要layout
        final boolean needsLayout = specChanged&& (sAlwaysRemeasureExactly || !isSpecExactly || !matchesSpecSize);
        //如果需要绘制或者设置了强制layout
        if (forceLayout || needsLayout) {
            // 先清除测量尺寸标记
            mPrivateFlags &= ~PFLAG_MEASURED_DIMENSION_SET;
            resolveRtlPropertiesIfNeeded();

            int cacheIndex = forceLayout ? -1 : mMeasureCache.indexOfKey(key);
            if (cacheIndex < 0 || sIgnoreMeasureCache) {
                //***重点方法   测量我们自己
                onMeasure(widthMeasureSpec, heightMeasureSpec);
                mPrivateFlags3 &= ~PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT;
            } else {
                long value = mMeasureCache.valueAt(cacheIndex);
                setMeasuredDimensionRaw((int) (value >> 32), (int) value);
                mPrivateFlags3 |= PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT;
            }
            //如果开发者自己设置了onMeasure，但是里面没有调用setMeasuredDimension()方法，这时候就会报错
            //setMeasuredDimension()方法会将PFLAG_MEASURED_DIMENSION_SET设置进mPrivateFlags
            if ((mPrivateFlags & PFLAG_MEASURED_DIMENSION_SET) != PFLAG_MEASURED_DIMENSION_SET) {
                throw new IllegalStateException("View with id " + getId() + ": "+ getClass().getName() + "#onMeasure() did not set the" + " measured dimension by calling"+ " setMeasuredDimension()");
            }
            //设置请求layout标志位，为了进行下一步的layout工作
            mPrivateFlags |= PFLAG_LAYOUT_REQUIRED;
        }
        //记录父控件给予的MeasureSpec值，便于以后判断父控件是否变化了
        mOldWidthMeasureSpec = widthMeasureSpec;
        mOldHeightMeasureSpec = heightMeasureSpec;
        //缓存起来
        mMeasureCache.put(key, ((long) mMeasuredWidth) << 32 |(long) mMeasuredHeight & 0xffffffffL); // suppress sign extension
    }
```

可以看到这个方法里面并没有进行任何的测量工作，真正的测量操作是交给了 **onMeasure()** 来进行处理。而这个函数的作用只是对于 **onMeasure** 方法的正确性进行检测

1. 如果上次传过来的父类的宽高信息等各种情况都没发生变化，就不进行测量工作。
2. 因为 **onMeasure** 方法是可以被子类覆写的，子类在进行覆写的时候，必须调用 **setMeasuredDimension()** 方法，否则就会报错
3. 这里有个缓存机制，如果不是强制执行测量工作，那么可以从缓存来获取之前的测量信息。

```java
//View.java
	//onMeasure方法的具体的实现应该是由子类去重写的，提供更加合理、高效的实现
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //保存测量结果。
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }
```

在View的实现中，onMeasure内部只是调用了 **setMeasuredDimension** 方法。

```java
//View.java
	//onMeasue方法必须调用这个方法来进行测量数据的保存
    protected final void setMeasuredDimension(int measuredWidth, int measuredHeight) {
        boolean optical = isLayoutModeOptical(this);
        //有光学边界，则对光学边界做一些处理
        if (optical != isLayoutModeOptical(mParent)) {
            Insets insets = getOpticalInsets();
            int opticalWidth  = insets.left + insets.right;
            int opticalHeight = insets.top  + insets.bottom;

            measuredWidth  += optical ? opticalWidth  : -opticalWidth;
            measuredHeight += optical ? opticalHeight : -opticalHeight;
        }
        setMeasuredDimensionRaw(measuredWidth, measuredHeight);
    }
    
    private void setMeasuredDimensionRaw(int measuredWidth, int measuredHeight) {
        //保存测量的宽高信息
        mMeasuredWidth = measuredWidth;
        mMeasuredHeight = measuredHeight;
        //向mPrivateFlags中添加PFALG_MEASURED_DIMENSION_SET，以此证明onMeasure()保存了测量结果
        mPrivateFlags |= PFLAG_MEASURED_DIMENSION_SET;
    }
```

反过来我们看一下 **setMeasuredDimension** 的入参是怎么获取的。也就是 **getDefaultSize** 和 **getSuggestedMinimumWidth** 

```java
//ViewRootImpl.java	    
	//返回建议的视图应该使用的最小宽度。
    protected int getSuggestedMinimumWidth() {
        //如果没有背景，直接返回最小宽度，如果有背景，那么使用mMinWidth和背景的最小宽度，二者的最大值
        return (mBackground == null) ? mMinWidth : max(mMinWidth, mBackground.getMinimumWidth());
    }

    //根据建议的size和当前控件的模式返回最终确定的宽高信息
    public static int getDefaultSize(int size, int measureSpec) {
        int result = size;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        switch (specMode) {
        case MeasureSpec.UNSPECIFIED://未指明(wrap_content)的情况下，使用建议的size值
            result = size;
            break;
        case MeasureSpec.AT_MOST://设置使用最大值(match_parent)
        case MeasureSpec.EXACTLY://设置了确定的宽高信息(width="20dp")的情况下，使用父类传入的大小值
            result = specSize;
            break;
        }
        return result;
    }
```

所以这里都计算一次控件的最小宽高值，然后根据父类传入的measureSpec信息来进行不同的取值。

这里面只是对View的测量工作进行了解析，其实在实际使用中，更多的是对ViewGroup的子类的测量，其实现更加复杂一些，这些留在以后进行处理，我们这里只是跟踪View的绘制流程。

到目前为止，整个的的测量工作完成了，我们继续回到主线，看一下当测量完成以后又做了哪些工作。

#### 布局

```java
//ViewRootImpl.java
		//非暂停状态或者请求绘制，而且设置了请求layout标志位.
        final boolean didLayout = layoutRequested && (!mStopped || mReportNextDraw);
        boolean triggerGlobalLayoutListener = didLayout || mAttachInfo.mRecomputeGlobalAttributes;
        //需要进行layout布局操作
        if (didLayout) {
            //重点方法****执行layout,内部会调用View的layout方法，从而调用onLayout方法来实现布局
            performLayout(lp, mWidth, mHeight);
            //到现在为止，所有的view已经进行过了测量和定位。可以计算透明区域了
            if ((host.mPrivateFlags & View.PFLAG_REQUEST_TRANSPARENT_REGIONS) != 0) {
                ...
        }
        //触发全局的layout监听器，也就是我们设置的mTreeObserver
        if (triggerGlobalLayoutListener) {
            mAttachInfo.mRecomputeGlobalAttributes = false;
            mAttachInfo.mTreeObserver.dispatchOnGlobalLayout();
        }

```

对于控件的布局操作，代码量还是比较少的。主要就是判断是否需要进行layout操作，然后调用 **performLayout** 方法来进行布局。这里的 **performLayout** 会调用View的 **layout()** 方法，然后调用其 **onLayout()** 方法，具体分析与measure类似。所以这里不再进行分析了，有兴趣的朋友可以自己看一下。或者关注我的github中的[源码解析项目](https://github.com/kailaisi/android-29-framwork.git)，里面会不定期的更新对于源码的注释。

#### 绘制

```java
//ViewRootImpl.java
        boolean cancelDraw = mAttachInfo.mTreeObserver.dispatchOnPreDraw() || !isViewVisible;
        //没有取消绘制
        if (!cancelDraw) {
            //存在动画则执行动画效果
            if (mPendingTransitions != null && mPendingTransitions.size() > 0) {
                for (int i = 0; i < mPendingTransitions.size(); ++i) {
                    mPendingTransitions.get(i).startChangingAnimations();
                }
                mPendingTransitions.clear();
            }
			//重点方法 ***执行绘制工作
            performDraw();
        } else {//取消了绘制工作。
            if (isViewVisible) {
                // Try again
                //如果当前页面是可见的，那么重新进行调度
                scheduleTraversals();
            } else if (mPendingTransitions != null && mPendingTransitions.size() > 0) {
                //存在动画效果则取消动画
                for (int i = 0; i < mPendingTransitions.size(); ++i) {
                    mPendingTransitions.get(i).endChangingAnimations();
                }
                mPendingTransitions.clear();
            }
        }
        //清除正在遍历标志位
        mIsInTraversal = false;
    }
```

上面这些代码实现了对于View的绘制工作。里面的重点方法就是 **performDraw()** 。

对于绘制工作，不像测量和布局那么简单，是需要交给 **ThreadedRenderer** 这个线程渲染器来进行渲染工作的，我们跟踪一下主要的流程

```java
//ViewRootViewImpl 类
 private void performDraw() {
    ....
    draw(fullRedrawNeeded);
    ....
 }
-------------------------------------------------------------------------
//ViewRootViewImpl 类
private void draw(boolean fullRedrawNeeded) {
    ....
    mAttachInfo.mThreadedRenderer.draw(mView, mAttachInfo, this);
    ....
}
 
-------------------------------------------------------------------------
//ThreadedRenderer 类
void draw(View view, AttachInfo attachInfo, DrawCallbacks callbacks) {
    ....
    updateRootDisplayList(view, callbacks);
    ....
}
-------------------------------------------------------------------------
//ThreadedRenderer 类
private void updateRootDisplayList(View view, DrawCallbacks callbacks) {
    ....
    updateViewTreeDisplayList(view);
    ....
}
-------------------------------------------------------------------------
//ThreadedRenderer 类
private void updateViewTreeDisplayList(View view) {
    view.mPrivateFlags |= View.PFLAG_DRAWN;
    view.mRecreateDisplayList = (view.mPrivateFlags & View.PFLAG_INVALIDATED)
            == View.PFLAG_INVALIDATED;
    view.mPrivateFlags &= ~View.PFLAG_INVALIDATED;
    //这里调用了 View 的 updateDisplayListIfDirty 方法 
    //这个 View 其实就是 DecorView
    view.updateDisplayListIfDirty();
    view.mRecreateDisplayList = false;
}
```

可以看到，最后会调用其参数view（也就是DecorView）的 **updateDisplayListIfDirty** 方法。

```java
//View.java
 public RenderNode updateDisplayListIfDirty() {
    	...
        draw(canvas);
        ...
    }
```

在这个方法里会调用View的draw(canvas)绘制方法，由于DecorView方法重写了draw方法，所以先执行DecorView的draw方法。

```java
//DecorView
	@Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mMenuBackground != null) {
            mMenubackground.draw(canvas);
        }
    }
```

所以最终还是调用View类中的draw方法。

```java
    public void draw(Canvas canvas) {
        final int privateFlags = mPrivateFlags;
        mPrivateFlags = (privateFlags & ~PFLAG_DIRTY_MASK) | PFLAG_DRAWN;
        int saveCount;
        //步骤1  绘制背景
        drawBackground(canvas);
        //一般情况下跳过步骤2和步骤5
        final int viewFlags = mViewFlags;
        boolean horizontalEdges = (viewFlags & FADING_EDGE_HORIZONTAL) != 0;
        boolean verticalEdges = (viewFlags & FADING_EDGE_VERTICAL) != 0;
        if (!verticalEdges && !horizontalEdges) {
            //步骤3 绘制内容
            onDraw(canvas);
            //步骤4 绘制children
            dispatchDraw(canvas);

            //步骤6 绘制装饰(前景，滚动条)
            onDrawForeground(canvas);

            // Step 7, draw the default focus highlight
            //步骤7，绘制默认的焦点突出显示
            drawDefaultFocusHighlight(canvas);


            // we're done...
            return;
        }

```

在绘制方法中，将绘制过程分为了7个步骤，其中步骤2和步骤5一般情况下是跳过的

我们看一下里面的执行的步骤。具体的绘制流程不再逐一分析了。

1. 绘制背景。
2. 如果有必要，保存画布的图层以备淡入
3.  绘制视图的内容
4. 画Children
5. 如有必要，绘制淡入边缘并恢复图层
6. 绘制装饰(例如，滚动条)
7. 绘制默认的焦点突出显示

到这里为止，我们的整个View的绘制流程就全部完成了。里面具体的细节还有很多挖掘的地方。等以后有机会慢慢再分析吧。

### 总结

1. handler是有一种同步屏障机制的，能够屏蔽同步消息(有什么用图以后再开发)。
2. 对于屏幕的帧绘制是通过choreographer来进行的，它来进行屏幕的刷新，帧的丢弃等工作。
3. 如果Dialog的宽高设置的wrap，会先用默认的高度试试是否可行，不可行就(屏幕高+默认高)/2来进行试验，再不行就直接给屏幕高了。
4. 对于测量工作，在整个过程中会发生很多次。
5. 在整个View的绘制过程中，都有对于mTreeObserver的回调。这里我们可以根据我们的需要进行各种监听工作。
6. 自定义控件继承View必须覆写onMeasure方法。因为View默认的onMeasure中，如果使用了wrap，那么会MeasureSpec为AT+MOST。而且最大值为父类的高度，也就是相当于match_parent。所以必须重写onMeasure方法。这一点在TextView，Button等控件里面都能看到。我们只需要给自定义的View一个默认的内部宽高，当使用wrap_context的时候设置宽高即可。
7. 只有onMeasure之后才能获取到控件的宽高值，但是在实际中会存在多次测量，onMeasure又是在onResume中调用。所以如果获取宽高需要通过其他途径。这里提供四种
   * onWindowFocusChanged中获取
   * view.post中获取
   * ViewTreeObserver监听
   * 手动调用view.measure



> 本文由 [开了肯](http://www.kailaisii.com/) 发布！ 
>
> 同步公众号[开了肯]

![image-20200404120045271](http://cdn.qiniu.kailaisii.com/typora/20200404120045-194693.png)
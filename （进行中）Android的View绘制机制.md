## Android的View分发绘制过程源码解析（待写）

#### 引言

在之前的【Android布局窗口绘制分析】一篇文章中，我们介绍过如何将布局加载到PhoneWindows窗口中并显示。而在【Android的inflate源详解】中，我们则分析了如何将xml的布局文件转化为View树。但是View树具体以何种位置、何种大小展现给我们，没有具体讲解的。那么这篇文章，我们就在上两章的基础上继续研究View是如何进行布局和绘制的。

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
				//需要从新绘制标标志位
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
					//再次这行测量
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

```java
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

* 第一次绘制
* windowShouldResize标志位为true。而这个标志位主要就是判断窗口大小是否发生了变化。
* viewVisibilityChanged为true。这个标志位是


```
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

			//界面有自己的surface
            if (mSurfaceHolder != null) {
                // The app owns the surface; tell it about what is going on.
                if (mSurface.isValid()) {
                    // XXX .copyFrom() doesn't work!
                    //mSurfaceHolder.mSurface.copyFrom(mSurface);
                    mSurfaceHolder.mSurface = mSurface;
                }
                mSurfaceHolder.setSurfaceFrameSize(mWidth, mHeight);
                mSurfaceHolder.mSurfaceLock.unlock();
                if (mSurface.isValid()) {
                    if (!hadSurface) {
                        mSurfaceHolder.ungetCallbacks();

                        mIsCreating = true;
                        SurfaceHolder.Callback callbacks[] = mSurfaceHolder.getCallbacks();
                        if (callbacks != null) {
                            for (SurfaceHolder.Callback c : callbacks) {
                                c.surfaceCreated(mSurfaceHolder);
                            }
                        }
                        surfaceChanged = true;
                    }
                    if (surfaceChanged || surfaceGenerationId != mSurface.getGenerationId()) {
                        SurfaceHolder.Callback callbacks[] = mSurfaceHolder.getCallbacks();
                        if (callbacks != null) {
                            for (SurfaceHolder.Callback c : callbacks) {
                                c.surfaceChanged(mSurfaceHolder, lp.format,
                                        mWidth, mHeight);
                            }
                        }
                    }
                    mIsCreating = false;
                } else if (hadSurface) {
                    notifySurfaceDestroyed();
                    mSurfaceHolder.mSurfaceLock.lock();
                    try {
                        mSurfaceHolder.mSurface = new Surface();
                    } finally {
                        mSurfaceHolder.mSurfaceLock.unlock();
                    }
                }
            }

            final ThreadedRenderer threadedRenderer = mAttachInfo.mThreadedRenderer;
            if (threadedRenderer != null && threadedRenderer.isEnabled()) {
                if (hwInitialized
                        || mWidth != threadedRenderer.getWidth()
                        || mHeight != threadedRenderer.getHeight()
                        || mNeedsRendererSetup) {
                    threadedRenderer.setup(mWidth, mHeight, mAttachInfo,
                            mWindowAttributes.surfaceInsets);
                    mNeedsRendererSetup = false;
                }
            }
			//当前页面处于非暂停状态，或者接收到了绘制的请求
            if (!mStopped || mReportNextDraw) {
				//获取焦点
                boolean focusChangedDueToTouchMode = ensureTouchModeLocally((relayoutResult & WindowManagerGlobal.RELAYOUT_RES_IN_TOUCH_MODE) != 0);
				//宽高有变化了
                if (focusChangedDueToTouchMode || mWidth != host.getMeasuredWidth()|| mHeight != host.getMeasuredHeight() || contentInsetsChanged ||updatedConfiguration) {
                    int childWidthMeasureSpec = getRootMeasureSpec(mWidth, lp.width);
                    int childHeightMeasureSpec = getRootMeasureSpec(mHeight, lp.height);

                    if (DEBUG_LAYOUT) Log.v(mTag, "Ooops, something changed!  mWidth="
                            + mWidth + " measuredWidth=" + host.getMeasuredWidth()
                            + " mHeight=" + mHeight
                            + " measuredHeight=" + host.getMeasuredHeight()
                            + " coveredInsetsChanged=" + contentInsetsChanged);

                    // Ask host how big it wants to be
                    //执行测量工作
                    performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);

                    // Implementation of weights from WindowManager.LayoutParams
                    // We just grow the dimensions as needed and re-measure if
                    // needs be
                    int width = host.getMeasuredWidth();
                    int height = host.getMeasuredHeight();
                    //需要重新测量标记
                    boolean measureAgain = false;
                    //如果测量出来的水平宽度需要拉伸（设置了weight） 需要重新测量
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

                    if (measureAgain) {
                        if (DEBUG_LAYOUT) Log.v(mTag,"And hey let's measure once more: width=" + width + " height=" + height);
                        performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);
                    }
                    //设置请求layout标志位
                    layoutRequested = true;
                }
            }
        } else {
            // Not the first pass and no window/insets/visibility change but the window
            // may have moved and we need check that and if so to update the left and right
            // in the attach info. We translate only the window frame since on window move
            // the window manager tells us only for the new frame but the insets are the
            // same and we do not want to translate them more than once.
            maybeHandleWindowMove(frame);
        }

```







### 总结

1. handler是有一种同步屏障机制的，能够屏蔽同步消息(有什么用图以后再开发)。
2. 对于屏幕的帧绘制是通过choreographer来进行的，它来进行屏幕的刷新，帧的丢弃等工作。
3. 如果Dialog的宽高设置的wrap，那么会首先用


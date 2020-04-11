# Android布局窗口绘制分析

## Activity的启动

在之前的分析中，我们了解到，当使用 **startActivity()** 以后，经过一些流程的处理之后，会通过跨进程的方式调用 **AcitivtyThread.handleLauncherActivity()** 方法来进行Activity的启动。那么，我们这里的绘制就从这个方法来入手，进行源码的解析。

## handleLaunchActivity

```java
#AcitivtyThread.java
public Activity handleLaunchActivity(ActivityClientRecord r,PendingTransactionActions pendingActions, Intent customIntent) {
	...
    //初始化WindowsManger。通过mBinder获取到WindowManager
    WindowManagerGlobal.initialize();
    //执行启动
    final Activity a = performLaunchActivity(r, customIntent);

    if (a != null) {
        r.createdConfig = new Configuration(mConfiguration);
        reportSizeConfigurations(r);
        if (!r.activity.mFinished && pendingActions != null) {
            pendingActions.setOldState(r.state);
            pendingActions.setRestoreInstanceState(true);
            pendingActions.setCallOnPostCreate(true);
        }
    } else {
        //启动发生异常，调动finish方法
        try {
            //通过IPC机制获取到ActivityTaskManager的代理对象。调用其finishActivity方法
            ActivityTaskManager.getService().finishActivity(r.token, Activity.RESULT_CANCELED, null, Activity.DONT_FINISH_TASK_WITH_ACTIVITY);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }
    return a;
}
```

这里主要进行了准备工作，初始化了windowmanager，然后通过 **performLaunchActivity()** 来进行主要的加载工作。如果加载出现异常，就通过调用 **finishActivity** 来结束acitivity。我们看一下 **performLaunchActivity()** 中主要做了什么工作

#### performLaunchActivity

```java
#AcitivtyThread.java
//启动Activity的核心代码
private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {
    //一系列的准备工作
    ActivityInfo aInfo = r.activityInfo;
    ...
    ContextImpl appContext = createBaseContextForActivity(r);
    Activity activity = null;
    try {
        //classLoader
        java.lang.ClassLoader cl = appContext.getClassLoader();
        //通过反射创建activity
        //Activity其实就一个普普通通的Java对象，利用反射创建，然后由ClassLoader加载进去，之后由框架层的调用，从而具有了生命周期，成为了一个组件，从而也可以知道在插件化中，仅仅加载Activity是不行的，还必须交给框架层去调用才具有生命力，不然没意义，当然了，不仅是Activity,其实，Service,BroadCase,等都是这样由反射创建，然后加载由框架层调用的，无一例外
        activity = mInstrumentation.newActivity(cl, component.getClassName(), r.intent);
        //活动的activity计数器+1
        StrictMode.incrementExpectedActivityCount(activity.getClass());
        //设置相关cl和线程信息
        r.intent.setExtrasClassLoader(cl);
        //进行相关准备工作
        r.intent.prepareToEnterProcess();
        if (r.state != null) {
            r.state.setClassLoader(cl);
        }
    } catch (Exception e) {
        if (!mInstrumentation.onException(activity, e)) {
            throw new RuntimeException(
                "Unable to instantiate activity " + component
                + ": " + e.toString(), e);
        }
    }

    try {
        //创建包名所对应的Application对象，如果存在，则直接返回
        Application app = r.packageInfo.makeApplication(false, mInstrumentation);
        if (activity != null) {
            ...
            //获取window对象
            Window window = null;
            if (r.mPendingRemoveWindow != null && r.mPreserveWindow) {
                window = r.mPendingRemoveWindow;
                r.mPendingRemoveWindow = null;
                r.mPendingRemoveWindowManager = null;
            }
            appContext.setOuterContext(activity);
            //进行attach绑定，主要是为oncreate进行一些准备工作。包括phonewindow，mdecorview等
            activity.attach(appContext, this, getInstrumentation(), r.token, r.ident, app, r.intent, r.activityInfo, title, r.parent, r.embeddedID, r.lastNonConfigurationInstances, config, r.referrer, r.voiceInteractor, window, r.configCallback, r.assistToken);
            ...
            int theme = r.activityInfo.getThemeResource();
            if (theme != 0) {
                //设置主题
                activity.setTheme(theme);
            }
            ...
            //调用onCreate方法
            if (r.isPersistable()) {
                mInstrumentation.callActivityOnCreate(activity, r.state, r.persistentState);
            } else {
                mInstrumentation.callActivityOnCreate(activity, r.state);
            }
        }
        //设置当前状态
        r.setState(ON_CREATE);
        synchronized (mResourcesManager) {
        	//将activity放到集合中
            mActivities.put(r.token, r);
        }
        ...
    return activity;
}
```

在这个方法中，主要做了3个工作

1. 通过反射创建了一个activity对象
2. 调用activity的attach方法，创建其对应的PhoneWindow对象
3. 通过**IPC**机制，调用 **callActivityOnCreate**方法。

我们这里主要看一下后面两个做的一些处理。也就是attach方法和调用的

```java
#Acitivty.java
final void attach(Context context, ActivityThread aThread,
        Instrumentation instr, IBinder token, int ident,
        Application application, Intent intent, ActivityInfo info,
        CharSequence title, Activity parent, String id,
        NonConfigurationInstances lastNonConfigurationInstances,
        Configuration config, String referrer, IVoiceInteractor voiceInteractor,
        Window window, ActivityConfigCallback activityConfigCallback, IBinder assistToken) {
    attachBaseContext(context);
    mFragments.attachHost(null /*parent*/);
    //创建PhoneWindow，并设置相关数据
    mWindow = new PhoneWindow(this, window, activityConfigCallback);
    //将Activity设置为window的控制回调对象
    mWindow.setWindowControllerCallback(this);
    //将Activity设置为window的回调对象
    mWindow.setCallback(this);
    //设置dissmissed的回调对象
    mWindow.setOnWindowDismissedCallback(this);
    mWindow.getLayoutInflater().setPrivateFactory(this);
    if (info.softInputMode != WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED) {
    	//是否有键盘的设置
        mWindow.setSoftInputMode(info.softInputMode);
    }
    //初始化相关属性
    ...
    //设置对应的windowmanager
    mWindow.setWindowManager((WindowManager)context.getSystemService(Context.WINDOW_SERVICE), mToken, mComponent.flattenToString(), (info.flags & ActivityInfo.FLAG_HARDWARE_ACCELERATED) != 0)
    if (mParent != null) {
    	//如果parent不为空，将当前window设置到parent的window下
        mWindow.setContainer(mParent.getWindow());
    }
    mWindowManager = mWindow.getWindowManager();
    mCurrentConfig = config;
    mWindow.setColorMode(info.colorMode);

    setAutofillOptions(application.getAutofillOptions());
    setContentCaptureOptions(application.getContentCaptureOptions());
}
```

在**attach**中，主要是进行了一些属性的赋值工作。创建PhoneWindow，并设置相关数据。最后设置了其对应的windowmanager。

我们继续看看**callActivityOnCreate**是如何进行调用的。

```java
#Instrumentation.java
public void callActivityOnCreate(Activity activity, Bundle icicle) {
    prePerformCreate(activity);
    activity.performCreate(icicle);
    postPerformCreate(activity);
}
```

主要的就是调用了**performCreate()**方法。

```java
#Activity.java   
   //执行create操作
    @UnsupportedAppUsage
    final void performCreate(Bundle icicle, PersistableBundle persistentState) {
        ...
        //调用onCreate方法
        if (persistentState != null) {
            onCreate(icicle, persistentState);
        } else {
            onCreate(icicle);
        }
        ...
    }
```

调用了我们经常写的**onCreate**方法。

## setContentView

当我们实现 **onCreate** 方法的时候，需要使用 **setConteView()** 方法来进行页面的设置。那么这时候是如何将我们的布局文件加载显示出来的呢？还有我们设置的一些titlebar等，又是如何实现的呢？

```java
    #Activity.java 
	public void setContentView(@LayoutRes int layoutResID) {
        //getWindow()，实际返回的是PhoneWindow
        getWindow().setContentView(layoutResID);
        initWindowDecorActionBar();
    }
```

调用了PhoneWindow的**setContentView()**方法

```java
   #PhoneWindow.java
   public void setContentView(int layoutResID) {
        if (mContentParent == null) {//一般启动activity，mContentParent是空
            //进行Decor的创建工作
            installDecor();
        } else if (!hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
            mContentParent.removeAllViews();
        }
        if (hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
            //过度动画
            final Scene newScene = Scene.getSceneForLayout(mContentParent, layoutResID, getContext());
            transitionTo(newScene);
        } else {
            //进行布局文件的加载，将布局加载进mContentParent对应的布局中
            mLayoutInflater.inflate(layoutResID, mContentParent);
        }
        mContentParent.requestApplyInsets();
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) {
            cb.onContentChanged();
        }
        mContentParentExplicitlySet = true;
    }
```

这里主要的流程有2个

1. 通过**installDecor()**进行Decor的处理准备工作，进行标题等的设置，然后根据具体的id，对mContentParent这个View进行赋值，后面我们自己的布局文件都会在这个View下面进行绘制。
2. 通过inflate方法，将我们自己的布局文件绘制到mContentParent内部。

这里我们最需要关心的是**installDecor()**，在函数内部会进行Decor的处理工作。

### installDecor()

```java
   #PhoneWindow.java
   //进行Decor的处理工作
    private void installDecor() {
        mForceDecorInstall = false;
        if (mDecor == null) {
            mDecor = generateDecor(-1);//创建DecorView
            mDecor.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            mDecor.setIsRootNamespace(true);
            if (!mInvalidatePanelMenuPosted && mInvalidatePanelMenuFeatures != 0) {
                mDecor.postOnAnimation(mInvalidatePanelMenuRunnable);
            }
        } else {
            mDecor.setWindow(this);
        }
        if (mContentParent == null) {
            //mContentParent用来加载我们设置的布局。是用户设置的布局的父布局
            //里面根据主题等一些信息来绘制一个根布局，然后在根布局中找到一个id为content的布局，用来方式我们的setContentView中的布局文件
            mContentParent = generateLayout(mDecor);
            .....
        }
    }

```

当我们第一次启动的activity的时候，mDecor会为空，然后通过 **generateDecor()** 进行mDecor的创建。在创建完成以后，根据主题等一些信息来绘制一个根布局，然后在根布局中找到一个id为content的布局，命名为mContentParent，用来放置我们的setContentView中的布局文件。

### generateDecor

这里我们跟踪一下**generateDecor()**方法。

```java
#PhoneWindow.java
protected DecorView generateDecor(int featureId) {
    ...
    return new DecorView(context, featureId, this, getAttributes());
}
```

 **generateDecor()** 方法比较简单，主要是通过new方法进行对象的创建。

### generateLayout()

```java
#PhoneWindow.java
protected ViewGroup generateLayout(DecorView decor) {
    //获取主题相关属性
    TypedArray a = getWindowStyle();
    //一堆属性的获取，然后赋值给PhoneWindow对象
    ...
    //键盘模式    
    if (!hasSoftInputMode()) {
        params.softInputMode = a.getInt(R.styleable.Window_windowSoftInputMode,params.softInputMode);
    }
    // 根据PhoneWindow的设定好的属性（features和mIsFloating）的判断，为layoutResource进行赋值，
    //值可以为R.layout.screen_custom_title、R.layout.screen_action_bar等，属于我们的acitivty的基类布局
    int layoutResource;
    int features = getLocalFeatures();
    ....
    mDecor.startChanging();
    //将layoutResource基类布局文件加载进mDecor中
    mDecor.onResourcesLoaded(mLayoutInflater, layoutResource);
    //在加载给DecorView的布局文件中有一块id为ID_ANDROID_CONTENT(R.id.content)的区域是用于用户显示自己布局的，也是setContextView传入的布局显示的地方
    // 这块区域会以ViewGroup的形式赋值给mContentParent变量，这个ViewGroup即是用户布局的父布局节点。
    ViewGroup contentParent = (ViewGroup)findViewById(ID_ANDROID_CONTENT);
    ...
    //一堆设置的东西，包括背景和title等
    if (getContainer() == null) {
        mDecor.setWindowBackground(mBackgroundDrawable);
        ...
    }
    mDecor.finishChanging();
    return contentParent;
}
```

generateLayout方法主要就是根据我们设置的主题信息，获取到对应的最重要展示的布局样式。然后将布局样式加载进mDecor中，然后在布局中找到一个id为ID_ANDROID_CONTENT(R.id.content)的区域，用来放置我们自己的布局文件。这里会根据主题，对PhoneWindow进行一些列的配置操作，也会根据主题进行背景，titlebar等的设置处理。

到这里为止，**intsallDecor**的操作已经完成了，剩下的就是通过mLayoutInflater.inflate(layoutResID, mContentParent)方法将用户传入的布局转化为view再加入到mContentParent上。这样就完成了setContentView()流程。

到这里，Activity的整个视图结构就已经准备好了，但是这时候它还只是一个View，并没有添加到Window上。下面就是通过**handleResumeActivity()**来展示到Window上了。

## handleResumeActivity

```java
#AcitivityThread.java
public void handleResumeActivity(IBinder token, boolean finalStateRequest, boolean isForward,
            String reason) {
        ...
        //重点方法  内部将会调用onResume，并根据参数判断是否调用onRestart,onStart方法
        final ActivityClientRecord r = performResumeActivity(token, finalStateRequest, reason);
        ...
        ctivity a = r.activity;
        final int forwardBit = isForward ? WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION : 0;
        // 判断该Acitivity是否可见，mStartedAcitity记录的是一个Activity是否还处于启动状态
        // 如果还处于启动状态则mStartedAcitity为true，表示该activity还未启动好，则该Activity还不可见
        boolean willBeVisible = !a.mStartedActivity;
        if (!willBeVisible) {
            try {
                willBeVisible = ActivityTaskManager.getService().willActivityBeVisible(a.getActivityToken());
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
        if (r.window == null && !a.mFinished && willBeVisible) {
            r.window = r.activity.getWindow();
            //decor是activity的setContentView时候生成的DecorView
            View decor = r.window.getDecorView();
            //开始执行resume之前，界面是不可见的
            decor.setVisibility(View.INVISIBLE);
            //获取activity的WindowManager
            ViewManager wm = a.getWindowManager();
            WindowManager.LayoutParams l = r.window.getAttributes();
            a.mDecor = decor;
            l.type = WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
            l.softInputMode |= forwardBit;
            ...
            if (a.mVisibleFromClient) {
                if (!a.mWindowAdded) {
                    a.mWindowAdded = true;
                    //重点方法  将decor添加到wm中
                    wm.addView(decor, l);
                } else {
                    a.onWindowAttributesChanged(l);
                }
        	...
    }
```

在  **handleResumeActivity()** 方法中需要主要的方法有2个：

1. 通过 **performResumeActivity()** 进行了相关生命周期的调用。
2. 通过**wm.addView()**方法将activity的decor(也就是包含了我们的布局的一个整体文件)添加到**wm**中，通过这种方式将**Window**和我们的**DecorView**进行了关联，从而能够显示出我们的页面。

现在我们先看看**performResumeActivity()**中做了什么处理

#### performResumeActivity

```java
#AcitivityThread.java
public ActivityClientRecord performResumeActivity(IBinder token, boolean finalStateRequest,
        String reason) {
    //获取要处理的activity
    final ActivityClientRecord r = mActivities.get(token);
    if (r.getLifecycleState() == ON_RESUME) {
        //如果当前已经是ON_RESUME状态了，直接返回
        return null;
    }
    try {
        //调用activity的onStateNotSaved方法
        r.activity.onStateNotSaved();
        //调用activity的mFragments的onStateNotSaved方法
        r.activity.mFragments.noteStateNotSaved();
        checkAndBlockForNetworkAccess();
        if (r.pendingIntents != null) {
            //调用activity中的onNewIntent方法
            deliverNewIntents(r, r.pendingIntents);
            r.pendingIntents = null;
        }
        if (r.pendingResults != null) {
            //如果是startActivityForResult方法后，界面返回了数据，需要分发onActivityResult结果
            deliverResults(r, r.pendingResults, reason);
            r.pendingResults = null;
        }
        //重点方法    真正的调用resume的方法的位置
        r.activity.performResume(r.startsNotResumed, reason);
        r.state = null;
        r.persistentState = null;
        //修改状态位ON_RESUME
        r.setState(ON_RESUME);
        reportTopResumedActivityChanged(r, r.isTopResumedActivity, "topWhenResuming");
    } catch (Exception e) {
        if (!mInstrumentation.onException(r.activity, e)) {
            throw new RuntimeException("Unable to resume activity "
                    + r.intent.getComponent().toShortString() + ": " + e.toString(), e);
        }
    }
    return r;
}
```

performResumeActivity()方法主要进行了一些生命周期的调用以及接口的回调处理等。包括onResume()，onNewIntent()的分发处理工作。

我们这里看一下**performResume()**方法的处理

```java
#Activity.java
final void performResume(boolean followedByPause, String reason) {
    //分发PreResumed，调用注册的相关的监听函数，包括Application中注册的监听和当前activity注册的监听
    dispatchActivityPreResumed();
    //根据入参调用相关的onRestart和onStart方法，
    performRestart(true /* start */, reason);
    mCalled = false;
    //通过mInstrumentation调用OnResume方法
    mInstrumentation.callActivityOnResume(this);
    mCalled = false;
    //分发进行内部fragment的onResume()操作
    mFragments.dispatchResume();
    mFragments.execPendingActions();
    onPostResume();
    if (!mCalled) {
        throw new SuperNotCalledException(
            "Activity " + mComponent.toShortString() +
            " did not call through to super.onPostResume()");
    }
    //分发PostResumed，调用注册的相关的监听函数
    dispatchActivityPostResumed();
}
```

这里其实主要是进行了一些生命周期的调用。

回到上一级的主干，我们看一下wm.addView()方法是如何将我们的activity绑定并进行显示的。

#### wm.addView()

在进行这个方法分析之前我们先看看wm.addView(decor, l)这几个对象都是什么。**wm**是是我们在**performLaunchActivity**方法中调用attach方法里创建的**WindowManagerImpl**对象。**docor**则是Activity对应的Window中的视图**DecorView**

```java
#WindowManagerImpl.java 
private final WindowManagerGlobal mGlobal = WindowManagerGlobal.getInstance();
public void addView(@NonNull View view, @NonNull ViewGroup.LayoutParams params) {
    applyDefaultToken(params);
    mGlobal.addView(view, params, mContext.getDisplay(), mParentWindow);
}
```

这里调用了mGlobal的addView方法。WindowManagerGlobal负责对窗口的统一管理，是一个真正做实事的人。

```java
public void addView(View view, ViewGroup.LayoutParams params, Display display, Window parentWindow) {
    ...
    ViewRootImpl root;
    View panelParentView = null;
    synchronized (mLock) {
        ...
        //如果这是一个面板窗口，那么查找它所连接的窗口以供将来参考。
        if (wparams.type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW && wparams.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
            final int count = mViews.size();
            for (int i = 0; i < count; i++) {//这里遍历所有的已经添加的window的binder，看是否和当前参数的binder一直来查找
                if (mRoots.get(i).mWindow.asBinder() == wparams.token) {
                    panelParentView = mViews.get(i);
                }
            }
        }

        root = new ViewRootImpl(view.getContext(), display);
        view.setLayoutParams(wparams);
        //保存互相对应的View、ViewRootImpl、WindowManager.LayoutParams到数组中
        mViews.add(view);
        mRoots.add(root);
        mParams.add(wparams);
        try {
            root.setView(view, wparams, panelParentView);
        } catch (RuntimeException e) {
            // BadTokenException or InvalidDisplayException, clean up.
            if (index >= 0) {
                removeViewLocked(index, true);
            }
            throw e;
        }
    }
}
```

在**addView()**方法中，会首先创建**ViewRootImpl**对象，然后通过ViewRootImpl.setView方法将我们的view设置进去。这里的panelParentView参数是空。

我们继续跟踪一下 **setView()** 方法

```java
#ViewRootImpl.java
public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
    synchronized (this) {
        if (mView == null) {
            //初始化一些信息
            mView = view;
            mAttachInfo.mDisplayState = mDisplay.getState();
            mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);

            mViewLayoutDirectionInitial = mView.getRawLayoutDirection();
            mFallbackEventHandler.setView(view);
            mWindowAttributes.copyFrom(attrs);
            
            mSoftInputMode = attrs.softInputMode;
            mWindowAttributesChanged = true;
            mWindowAttributesChangesFlag = WindowManager.LayoutParams.EVERYTHING_CHANGED;
            mAttachInfo.mRootView = view;
            mAttachInfo.mScalingRequired = mTranslator != null;
            mAttachInfo.mApplicationScale = mTranslator == null ? 1.0f : mTranslator.applicationScale;
            if (panelParentView != null) {
                mAttachInfo.mPanelParentWindowToken = panelParentView.getApplicationWindowToken();
            }
            mAdded = true;
            int res; /* = WindowManagerImpl.ADD_OKAY; */
            // 这里调用异步刷新请求，最终会调用performTraversals方法来完成View的绘制
            requestLayout();
            ...
            try {
                //mWindowSession是Session的IBinder代理，通过IPC机制调用Session的addToDisplay方法
                //addToDisplay方法内部通过IPC机制调用WMS的addWindow方法
                //通过IPC机制，将AMS中的addWindow方法来在系统进程中执行相关加载Window的操作。
                res = mWindowSession.addToDisplay(mWindow, mSeq, mWindowAttributes,
                        getHostVisibility(), mDisplay.getDisplayId(), mTmpFrame,
                        mAttachInfo.mContentInsets, mAttachInfo.mStableInsets,
                        mAttachInfo.mOutsets, mAttachInfo.mDisplayCutout, mInputChannel,
                        mTempInsets);
                setFrame(mTmpFrame);
            } 
            ...
        }
    }
}
```

这里主要进行了一下对象的初始化工作，然后调用**requestLayout()**方法来进行当前view的绘制工作。最后通过IPC机制，执行相关加载Window的操作。

到此为止页面的加载以及绘制，显示工作完成。

![image-20200406092622588](https://user-gold-cdn.xitu.io/2020/4/6/1714d1728f293578?w=946&h=675&f=png&s=87941)

图片来源[https://www.cnblogs.com/tiger-wang-ms/p/6517048.html]
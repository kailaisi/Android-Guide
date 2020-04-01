### Android启动流程，窗口绘制分析

### Activity的启动

在之前的分析中，我们了解到，当使用**startActivity()**以后，经过一些流程的处理之后，会通过跨进程的方式调用**AcitivtyThread.handleLauncherActivity()**方法来进行Activity的启动。那么，我们这里的绘制就从这个方法来入手，进行源码的解析。

```
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

这里主要进行了准备工作，初始化了windowmanager，然后通过**performLaunchActivity()**来进行主要的加载工作。如果加载出现异常，就通过调用**finishActivity**来结束acitivity。我们看一下**performLaunchActivity()**中主要做了什么工作

```
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
3. 通过**IPC**机制，调用**callActivityOnCreate**方法。

我们这里主要看一下后面两个做的一些处理。也就是attach方法和调用的

```
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

```
public void callActivityOnCreate(Activity activity, Bundle icicle) {
    prePerformCreate(activity);
    activity.performCreate(icicle);
    postPerformCreate(activity);
}
```

主要的就是调用了**performCreate()**方法。

```
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

### setContentView

当我们实现**onCreate**方法的时候，需要使用**setConteView()**方法来进行页面的设置。那么这时候是如何将我们的布局文件加载显示出来的呢？还有我们设置的一些titlebar等，又是如何实现的呢？

```
    public void setContentView(int layoutResID) {
        // Note: FEATURE_CONTENT_TRANSITIONS may be set in the process of installing the window
        // decor, when theme attributes and the like are crystalized. Do not check the feature
        // before this happens.
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


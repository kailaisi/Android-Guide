Context知根知底

Context，肯定都用过，那么Context到底是什么呢？

![image-20201022103816172](http://cdn.qiniu.kailaisii.com/typora/202010/22/103817-938052.png)

Context类本身是个抽象类，具体的子类实现有两个：**ContextImpl**和**ContextWrapper**

#### ContextWrapper

代理类，内部持有Context的具体代理对象，内部的实现方法也都是调用的被代理对象的数据。

```java
public class ContextWrapper extends Context {
    Context mBase;

    public ContextWrapper(Context base) {
        mBase = base;
    }

    protected void attachBaseContext(Context base) {
        if (mBase != null) {
            throw new IllegalStateException("Base context already set");
        }
        mBase = base;
    }

    public Context getBaseContext() {
        return mBase;
    }

    @Override
    public AssetManager getAssets() {
        return mBase.getAssets();
    }

    @Override
    public Resources getResources() {
        return mBase.getResources();
    }

    @Override
    public PackageManager getPackageManager() {
        return mBase.getPackageManager();
    }

    @Override
    public ContentResolver getContentResolver() {
        return mBase.getContentResolver();
    }

    @Override
    public Looper getMainLooper() {
        return mBase.getMainLooper();
    }
    
    @Override
    public Context getApplicationContext() {
        return mBase.getApplicationContext();
    }
    
    @Override
    public void setTheme(int resid) {
        mBase.setTheme(resid);
    }

```

#### Application中的Context创建

```java
    public Application makeApplication(boolean forceDefaultAppClass,
            Instrumentation instrumentation) {
        if (mApplication != null) {
            return mApplication;
        }

        Application app = null;

        String appClass = mApplicationInfo.className;
        if (forceDefaultAppClass || (appClass == null)) {
            appClass = "android.app.Application";
        }

        try {
            java.lang.ClassLoader cl = getClassLoader();
            if (!mPackageName.equals("android")) {
                Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                        "initializeJavaContextClassLoader");
                initializeJavaContextClassLoader();
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            }
			//1. 创建applicaton对应的ContextImpl
            ContextImpl appContext = ContextImpl.createAppContext(mActivityThread, this);
			//2. 通过mInstrumentation创建application
            app = mActivityThread.mInstrumentation.newApplication(cl, appClass, appContext);
			// 把application的引用赋值给ContextImpl。这样，ContextImpl就可以方便的访问Application对象了
            appContext.setOuterContext(app);
        ...
        mActivityThread.mAllApplications.add(app);
		//赋值给mApplication
        mApplication = app;

        if (instrumentation != null) {
            try {
				//3. 调用onCteate方法
                instrumentation.callApplicationOnCreate(app);
            } catch (Exception e) {
                if (!instrumentation.onException(app, e)) {
                    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    throw new RuntimeException(
                        "Unable to create application " + app.getClass().getName()
                        + ": " + e.toString(), e);
                }
            }
        }

        return app;
    }

```

##### 第一步：ContextImpl的创建。

```java
    static ContextImpl createAppContext(ActivityThread mainThread, LoadedApk packageInfo) {
        return createAppContext(mainThread, packageInfo, null);
    }
	//创建Application对应的Context对象
    static ContextImpl createAppContext(ActivityThread mainThread, LoadedApk packageInfo,
            String opPackageName) {
        if (packageInfo == null) throw new IllegalArgumentException("packageInfo");
		//创建Impl对象。对象持有了ActivityThread，所以可以通过该对象访问系统级别的相关资源
        ContextImpl context = new ContextImpl(null, mainThread, packageInfo, null, null, null, 0,
                null, opPackageName);
		//设置对应的Resource资源信息，通过packageInof获取
        context.setResources(packageInfo.getResources());
        return context;
    }
```

##### 第二步：创建Application

```java
    public Application newApplication(ClassLoader cl, String className, Context context)
            throws InstantiationException, IllegalAccessException, 
            ClassNotFoundException {
        Application app = getFactory(context.getPackageName())
                .instantiateApplication(cl, className);
		//调用attach方法设置对应的context。这里的context是创建的ContextImpl
        app.attach(context);
        return app;
    }
   
	//base\core\java\android\app\Application.java
    final void attach(Context context) {
        //将attachBaseContext将创建的ContextImpl赋值
        attachBaseContext(context);
        mLoadedApk = ContextImpl.getImpl(context).mPackageInfo;
    }
```

##### 第三步：调用OnCteate方法

```java
    public void callApplicationOnCreate(Application app) {
        app.onCreate();
    }
```

简单粗暴~~~。直接调用onCreate方法。

很多时候我们去使用Application，但我们需要获取对应的Application所对应的Context实例时，都会强调在**attachBaseContext()**的super方法之后才可以使用。这里可以知道，是以为我们在这个方法中才进行的Context的赋值工作。

#### Activity中的Context创建

在之前我们分析Activity的启动流程时，知道一个Activity是通过**performLaunchActivity()**来进行启动的。

```java
//base\core\java\android\app\ActivityThread.java
	//启动Activity的核心代码
    private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {
        //一系列的准备工作
        ActivityInfo aInfo = r.activityInfo;
        ...
		//重点方法1.       创建对应的ContextImpl
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
        ...
                appContext.setOuterContext(activity);
                //重点方法2  进行attach绑定，主要是为oncreate进行一些准备工作。包括phonewindow，mdecorview等
                //这里会进行ContextImpl的赋值工作
                activity.attach(appContext, this, getInstrumentation(), r.token, r.ident, app, r.intent, r.activityInfo, title, r.parent, r.embeddedID, r.lastNonConfigurationInstances, config, r.referrer, r.voiceInteractor, window, r.configCallback, r.assistToken);

                if (customIntent != null) {
                    activity.mIntent = customIntent;
                }
        return activity;
    }

```

这里精简了代码，有兴趣的可以看一下之前的关于Activity的启动的文章分析。

##### 第一步：   ContextImpl的创建

```java
    private ContextImpl createBaseContextForActivity(ActivityClientRecord r) {
        final int displayId;
        ...
		//创建ContextImpl
        ContextImpl appContext = ContextImpl.createActivityContext(
                this, r.packageInfo, r.activityInfo, r.token, displayId, r.overrideConfig);
        return appContext;
    }

    static ContextImpl createActivityContext(ActivityThread mainThread,
            LoadedApk packageInfo, ActivityInfo activityInfo, IBinder activityToken, int displayId,
            Configuration overrideConfiguration) {
        if (packageInfo == null) throw new IllegalArgumentException("packageInfo");
		...
		//创建
        ContextImpl context = new ContextImpl(null, mainThread, packageInfo, activityInfo.splitName,
                activityToken, null, 0, classLoader, null);
		...
        //设置Resource资源。所以我们在Activity中是可以通过getResource来获取资源文件的。
        context.setResources(resourcesManager.createBaseActivityResources(activityToken,
                packageInfo.getResDir(),
                splitDirs,
                packageInfo.getOverlayDirs(),
                packageInfo.getApplicationInfo().sharedLibraryFiles,
                displayId,
                overrideConfiguration,
                compatInfo,
                classLoader));
        context.mDisplay = resourcesManager.getAdjustedDisplay(displayId,
                context.getResources());
        return context;
    }
```

这里通过ContextImpl的静态方法创建对应ContextImpl。跟之前的Application中的创建是很相似的。

##### 第二步：  绑定

```java
//\base\core\java\android\app\Activity.java 
	final void attach(Context context, ActivityThread aThread,
                      Instrumentation instr, IBinder token, int ident,
                      Application application, Intent intent, ActivityInfo info,
                      CharSequence title, Activity parent, String id,
                      NonConfigurationInstances lastNonConfigurationInstances,
                      Configuration config, String referrer, IVoiceInteractor voiceInteractor,
                      Window window, ActivityConfigCallback activityConfigCallback, IBinder assistToken) {
        
        attachBaseContext(context);
		...
```

这里我们又看到了**attachBaseContext()**方法，和我们的Application中的处理方式是一样的，都是给我mBase进行赋值。

#### Service中的Context创建

Service的启动流程和Activity是相似的，最后会通过AMS执行到**handleCreateService**方法。

```java
//frameworks\base\core\java\android\app\ActivityThread.java
    @UnsupportedAppUsage
    private void handleCreateService(CreateServiceData data) {
        // If we are getting ready to gc after going to the background, well
        // we are back active so skip it.
        unscheduleGcIdler();

        LoadedApk packageInfo = getPackageInfoNoCheck(
                data.info.applicationInfo, data.compatInfo);
        Service service = null;
      
            java.lang.ClassLoader cl = packageInfo.getClassLoader();
			//1.  通过反射创建一个Service对象
            service = packageInfo.getAppFactory()
                    .instantiateService(cl, data.info.name, data.intent);
        ...
            if (localLOGV) Slog.v(TAG, "Creating service " + data.info.name);
			//2.   创建一个ContextImpl对象
            ContextImpl context = ContextImpl.createAppContext(this, packageInfo);
            context.setOuterContext(service);
			//这里如果Application没有创建的话，会进行创建，如果已经存在了，则不会再重复创建了。
            Application app = packageInfo.makeApplication(false, mInstrumentation);
			//3.  绑定
            service.attach(context, this, data.info.name, data.token, app,
                    ActivityManager.getService());
            service.onCreate();
            ...
        }
    }

```

这里的步骤和Activity是相似的。我们重点看一下绑定方法**attach()**

```java
//frameworks\base\core\java\android\app\Service.java
	public final void attach(Context context,ActivityThread thread, String className, IBinder token,Application application, Object activityManager) {
    	//调用attachBaseContext方法
        attachBaseContext(context);
        mThread = thread;           // NOTE:  unused - remove?
        mClassName = className;
        mToken = token;
        mApplication = application;
        mActivityManager = (IActivityManager)activityManager;
        mStartCompatibility = getApplicationInfo().targetSdkVersion
                < Build.VERSION_CODES.ECLAIR;
    }
```

这里我们又看到了**attachBaseContext()**方法，和我们的Application中的处理方式是一样的，都是给我mBase进行赋值。

#### 总结

* 对于Context，是典型的装饰者模式。
* Activity是有UI的，所以继承的是ContextThemeWrapper。
* Activity中的this返回的是本身，而getBaseContext则是返回的ContextWrapper中的mBase对象。
* getApplicationContext()方法是Context中的一个抽象方法，他的真正实现是在ContextImpl中，返回的是Applicaiton对象；getApplication()返回的是Applicaiton对象，但是该方法是Activity和Service中所特有的，只能在这两个类中才能调用
* 应用组件的调用顺序：构造函数->attachBaseContext()->onCreate()
* Android系统中，Context其实是一种身份的象征。在安卓系统中，程序无法像在Win系统中似的，随意的访问文件，然后进行修改等等。**任何程序的运行都需要经过系统的调用**。而想要调用系统的资源，都需要通过Context来进行，否则都属于“非法公民”。

参考文章：

https://blog.csdn.net/weixin_43766753/article/details/109017196
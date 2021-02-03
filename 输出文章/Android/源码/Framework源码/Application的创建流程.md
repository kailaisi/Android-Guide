Application的创建流程

对于一个应用程序，当fork出来一个进程的时候，会首先调用ActivityThread的main方法，从而启动应用。

```java
//frameworks\base\core\java\android\app\ActivityThread.java
    //入口程序
    public static void main(String[] args) {
        ...
        //准备主线程的looper
        Looper.prepareMainLooper();
		....
        //创建ActivityThread
        ActivityThread thread = new ActivityThread();
        //将thread绑定到AMS中
        thread.attach(false, startSeq);
        ...
        if (sMainThreadHandler == null) {
            sMainThreadHandler = thread.getHandler();
        }
        
        //开启Looper循环
        Looper.loop();

        throw new RuntimeException("Main thread loop unexpectedly exited");
    }
```

在**main()**方法中的代码量比较少，主要工作一个是创建了主线程的Looper，另一个就是将创建的ActivityThread对象绑定到AMS中。

而绑定过程则是通过**attach()**方法处理的

##### attach

```java
//frameworks\base\core\java\android\app\ActivityThread.java
    //一个IBinder对象，AMS持有该对象的代理对象。AMS能够通过它通知ActivityThread管理其他事情
    final ApplicationThread mAppThread = new ApplicationThread();
private void attach(boolean system, long startSeq) {
       		...
			//获取到AMS的Binder对象
            final IActivityManager mgr = ActivityManager.getService();
            try {
				//AMS里面的方法，mAppThread是个IBinder对象，
				//这里将Ibinder对象关联到了AMS，这样AMS就可以通过这个对象进行Application的创建、生命周期的管理等等。
                mgr.attachApplication(mAppThread, startSeq);
            } catch (RemoteException ex) {
                throw ex.rethrowFromSystemServer();
            }
            ....
        ViewRootImpl.addConfigCallback(configChangedCallback);
    }


```

这里面的mAppThread，我们可以看下他的定义

```java
//frameworks\base\core\java\android\app\ActivityThread.java
    private class ApplicationThread extends IApplicationThread.Stub {
        ...
```

这个类继承了Stub类的一个对象，在我们之前讲解Binder机制的时候说过，这种实际是一个Binder对象，通过它可以远程访问。所以在attach函数中，将这个对象传给了AMS，这样AMS就可以通过Binder机制进行Application的创建、生命周期的管理等等。

```java
//frameworks\base\services\core\java\com\android\server\am\ActivityManagerService.java
	//绑定Application
    @Override
    public final void attachApplication(IApplicationThread thread, long startSeq) {
        synchronized (this) {
            int callingPid = Binder.getCallingPid();
            final int callingUid = Binder.getCallingUid();
            final long origId = Binder.clearCallingIdentity();
			//重点方法
            attachApplicationLocked(thread, callingPid, callingUid, startSeq);
            Binder.restoreCallingIdentity(origId);
        }
    }

    private final boolean attachApplicationLocked(IApplicationThread thread,int pid, int callingUid, long startSeq) {
        ...
        				//重点方法  这里的thread是IBinder对象，可以调用ApplicationThread的bindApplication方法
                thread.bindApplication(processName, appInfo, providers, null, profilerInfo,
                        null, null, null, testMode,
                        mBinderTransactionTrackingEnabled, enableTrackAllocation,
                        isRestrictedBackupMode || !normalMode, app.isPersistent(),
                        new Configuration(app.getWindowProcessController().getConfiguration()),
                        app.compat, getCommonServicesLocked(app.isolated),
                        mCoreSettingsObserver.getCoreSettingsLocked(),
                        buildSerial, autofillOptions, contentCaptureOptions);
        ...
    }
```

在attach的过程中，AMS调用了IApplicationThread的的**bindApplication()**方法。

我们回到ApplicationThread去查看该方法。

```java
//frameworks\base\core\java\android\app\ActivityThread.java	
//绑定Application
    public final void bindApplication(...) {
        ...
            //发送Handler消息
            sendMessage(H.BIND_APPLICATION, data);
    }

    private void sendMessage(int what, Object obj, int arg1, int arg2, boolean async) {
        ....
        mH.sendMessage(msg);
    }
```

这里发送了一个Handler消息。我们去mH这个对象中去查看对应的**handleMessage()**方法。

```java
//frameworks\base\core\java\android\app\ActivityThread.java	
	public void handleMessage(Message msg) {
            if (DEBUG_MESSAGES) Slog.v(TAG, ">>> handling: " + codeToString(msg.what));
            switch (msg.what) {
                case BIND_APPLICATION://绑定Application的消息
                    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "bindApplication");
                    AppBindData data = (AppBindData) msg.obj;
                    handleBindApplication(data);
                    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    break;
```

通过Handler调用了**handleBindApplication()**方法

```java
//frameworks\base\core\java\android\app\ActivityThread.java	    
private void handleBindApplication(AppBindData data) {
        ...
        
        ...
        //重点方法        通过LoadApk的makeApplication方法创建Application实例
            app = data.info.makeApplication(data.restrictedBackupMode, null);
		...
		
    }
```

这个方法特别长，所以我们这里精简一下，只讲和Application的创建过程相关的方法。

```java
//frameworks\base\core\java\android\app\LoadedApk.java
	public Application makeApplication(boolean forceDefaultAppClass,
            Instrumentation instrumentation) {
        //已经创建，直接返回
        if (mApplication != null) {
            return mApplication;
        }
        Application app = null;
		//拿到对应的类名称
        String appClass = mApplicationInfo.className;
       ...
			//1. 创建applicaton对应的ContextImpl
            ContextImpl appContext = ContextImpl.createAppContext(mActivityThread, this);
			//2. 通过mInstrumentation创建application
            app = mActivityThread.mInstrumentation.newApplication(cl, appClass, appContext);
			...
				//调用onCteate方法
                instrumentation.callApplicationOnCreate(app);
        return app;
    }

```

这里主要有3个重点方法，创建Context、创建Application，赋值ContextImpl，调用生命周期函数。

##### ContextImpl的创建

```java
//frameworks\base\core\java\android\app\ActivityThread.java	    
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

##### 创建Application

```java
//frameworks\base\core\java\android\app\LoadedApk.java
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

##### 生命周期函数调用

```java
    public void callApplicationOnCreate(Application app) {
        app.onCreate();
    }
```

到现在为止就创建完成整个Application对象了。
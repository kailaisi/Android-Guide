深入理解AMS

在[Android系统启动流程中]()中我们提到过，AMS是在system_service中启动的，其中

```java
  //frameworks/base/services/java/corri/android/server/SystemServer.java

//该方法主要启动服务 ActivityManagerService，PowerManagerService，LightsService，DisplayManagerService，PackageManagerService，UserManagerService。
//设置 ActivityManagerService，启动传感器服务。
startBootstrapServices(); // 启动引导服务

//该方法主要
//启动服务 BatteryService 用于统计电池电量，需要 LightService。
//启动服务 UsageStatsService，用于统计应用使用情况。
//启动服务 WebViewUpdateService。
startCoreServices();      // 启动核心服务

//该方法主要启动服务 InputManagerService，WindowManagerService。
//等待 ServiceManager，SurfaceFlinger启动完成，然后显示启动界面。
//启动服务 StatusBarManagerService，
//准备好 window, power, package, display 服务：
//	- WindowManagerService.systemReady()
//	- PowerManagerService.systemReady()
//	- PackageManagerService.systemReady()
//	- DisplayManagerService.systemReady()
startOtherServices();     // 启动其他服务


```

其中在启动核心服务功能中，会进行AMS的启动。

```java
   //frameworks/base/services/java/corri/android/server/SystemServer.java
    private void startBootstrapServices() {
      ...
			traceBeginAndSlog("StartActivityManager");
        //这里会将ATMS注册到ServiceManager中，然后调用ATMS的start方法。
        ActivityTaskManagerService atm = mSystemServiceManager.startService(ActivityTaskManagerService.Lifecycle.class).getService();
        mActivityManagerService = ActivityManagerService.Lifecycle.startService(mSystemServiceManager, atm);
        mActivityManagerService.setSystemServiceManager(mSystemServiceManager);
        mActivityManagerService.setInstaller(installer);
      ...
    }
```

这里我们只截取了ATMS的启动代码。我们在[ServiceManager的工作原理]()中讲解过，systemServiceManager.startService方法会将对应的服务注册到ServiceManager中，然后再调用start方法。

```java
//frameworks/base/services/core/java/com/android/server/SystemServiceManager.java
		public SystemService startService(String className) {
        final Class<SystemService> serviceClass;
        serviceClass = (Class<SystemService>)Class.forName(className);
        return startService(serviceClass);
    }

    @SuppressWarnings("unchecked")
    public <T extends SystemService> T startService(Class<T> serviceClass) {
        try {
            final String name = serviceClass.getName();
            final T service;
            try {
                //反射构造函数
                Constructor<T> constructor = serviceClass.getConstructor(Context.class);
                //创建服务
                service = constructor.newInstance(mContext);
            ...
            //启动服务
            startService(service);
            return service;
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
        }
    }

    public void startService(@NonNull final SystemService service) {
        // Register it.
        //注册到ServiceManager列表中
        mServices.add(service);
        //启动服务
        service.onStart();
    }
```

在启动ATMS的时候传入的参数是：**ActivityTaskManagerService.Lifecycle.class**

```java
   public static final class Lifecycle extends SystemService {
        private final ActivityTaskManagerService mService;
        public Lifecycle(Context context) {
            super(context);
            //构造函数创建ATMS实例
            mService = new ActivityTaskManagerService(context);
        }
        @Override
        public void onStart() {
            publishBinderService(Context.ACTIVITY_TASK_SERVICE, mService);
            //调用ATMS的start方法
            mService.start();
        }

        @Override
        public void onCleanupUser(int userId) {
            synchronized (mService.getGlobalLock()) {
                mService.mStackSupervisor.mLaunchParamsPersister.onCleanupUser(userId);
            }
        }

        public ActivityTaskManagerService getService() {
            //返回了ATMS实例
            return mService;
        }
    }
```

所以通过这个方法会创建一个ATMS的实例，然后调用了其start方法。并且将ATMS实例通过getService方法进行返回。
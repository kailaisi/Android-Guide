深入理解AMS

### 启动

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
        //这里会将ATMS注册到ServiceManager中，然后调用ATMS的start方法。
        ActivityTaskManagerService atm = mSystemServiceManager.startService(ActivityTaskManagerService.Lifecycle.class).getService();
        //注册AMS服务，并返回对应的对象信息
        mActivityManagerService = ActivityManagerService.Lifecycle.startService(mSystemServiceManager, atm);
        mActivityManagerService.setSystemServiceManager(mSystemServiceManager);
        //设置app安装器
        mActivityManagerService.setInstaller(installer);
        ...
        //2]向ServiceManager中注册Binder服务
        mActivityManagerService.setSystemProcess();
    }
```

这里我们只截取了AMS的启动代码。

这里会通过startService方法来进行AMS的注册和启动过程。我们看一下具体的ActivityManagerService中的startService方法

```java
//    
	public static ActivityManagerService startService(SystemServiceManager ssm, ActivityTaskManagerService atm) {
            sAtm = atm;
            //启动AMS
            return ssm.startService(ActivityManagerService.Lifecycle.class).getService();
        }
```

我们在[ServiceManager的工作原理]()中讲解过，systemServiceManager.startService方法会将对应的服务注册到ServiceManager中，然后再调用start方法。

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

在启动AMS的时候传入的参数是：**ActivityManagerService.Lifecycle.class**

```java
   public static final class Lifecycle extends SystemService {
        private final ActivityTaskManagerService mService;
        public Lifecycle(Context context) {
            super(context);
            //创建AMS对象
            mService = new ActivityManagerService(context, sAtm);
        }
        @Override
        public void onStart() {
            //调用ATMS的start方法
            mService.start();
        }

        public ActivityManagerService getService() {
            //返回了AMS实例
            return mService;
        }
    }
```

在Lifecycle对象的创建过程中，会创建AMS对象，然后通过start()方法进行了启动。

#### AMS的创建

对于AMS对象的创建是通过构造函数来创建的。

```java
    //构造方法，
    public ActivityManagerService(Context systemContext, ActivityTaskManagerService atm) {
        //获取系统的ActivityThread
        mSystemThread = ActivityThread.currentActivityThread();
        //创建一个ServiceThread用来处理AMS接收到的命令
        mHandlerThread = new ServiceThread(TAG,THREAD_PRIORITY_FOREGROUND, false /*allowIo*/);
        mHandlerThread.start();
        mHandler = new MainHandler(mHandlerThread.getLooper());
        mUiHandler = mInjector.getUiHandler(this);
        //低内存监控
        mLowMemDetector = new LowMemDetector(this);
        //初始化广播队列。这里包含了前台广播，后台广播等
        mFgBroadcastQueue = new BroadcastQueue(this, mHandler, "foreground", foreConstants, false);
        mBgBroadcastQueue = new BroadcastQueue(this, mHandler, "background", backConstants, true);
        mOffloadBroadcastQueue = new BroadcastQueue(this, mHandler, "offload", offloadConstants, true);
        mBroadcastQueues[0] = mFgBroadcastQueue;
        mBroadcastQueues[1] = mBgBroadcastQueue;
        mBroadcastQueues[2] = mOffloadBroadcastQueue;
        //用于保存注册的Service
        mServices = new ActiveServices(this);
        //map，用于保存注册的ContentProvider
        mProviderMap = new ProviderMap(this);
        mPackageWatchdog = PackageWatchdog.getInstance(mUiContext);
        mAppErrors = new AppErrors(mUiContext, this, mPackageWatchdog);

        //创建 /data/system目录
        final File systemDir = SystemServiceManager.ensureSystemDir();
        //创建进程统计服务，保存在/data/system/proccstats目录中。
        mProcessStats = new ProcessStatsService(this, new File(systemDir, "procstats"));
        //赋值ATM，并进行初始化
        mActivityTaskManager = atm;
        mActivityTaskManager.initialize(mIntentFirewall, mPendingIntentController, DisplayThread.get().getLooper());
        //CPU追踪器进程
        mProcessCpuThread = new Thread("CpuTracker") {
            @Override
            public void run() {
                ...
            }
        };

    }

```

在AMS的构造函数中进行了一些初始化的东西：比如说**启动CPU监控、启动进程统计服务、启动低内存监控、初始化Service和ContentProvider对应的保存类**等等。

#### start()

当AMS类创建完成之后，会调用start()方法。

```java
   private void start() {
    	 //移除所有的进程组
        removeAllProcessGroups();
        //启动CpuTracker线程
        mProcessCpuThread.start();
        //启动电池统计服务，能够统计具体的应用的电池消耗，从而来进行一定的电量统计
        mBatteryStatsService.publish();
        //创建LocalService，并添加到LocalServices列表中
        LocalServices.addService(ActivityManagerInternal.class, new LocalService());
        mActivityTaskManager.onActivityManagerInternalAdded();
        mUgmInternal.onActivityManagerInternalAdded();
        mPendingIntentController.onActivityManagerInternalAdded();
   }
```

在start方法中，会将在构造函数中创建的一些线程进行启动。

#### setSystemProcess

在创建并启动完成之后，会通过setSystemProcess方法来向ServiceManager中注册一些系统相关的服务。

```java
    public void setSystemProcess() {
        try {
        	//注册ActivityService服务
            ServiceManager.addService(Context.ACTIVITY_SERVICE, this, /* allowIsolated= */ true,
                    DUMP_FLAG_PRIORITY_CRITICAL | DUMP_FLAG_PRIORITY_NORMAL | DUMP_FLAG_PROTO);
            //注册进程状态服务
            ServiceManager.addService(ProcessStats.SERVICE_NAME, mProcessStats);
            //注册内存Binder
            ServiceManager.addService("meminfo", new MemBinder(this), /* allowIsolated= */ false,DUMP_FLAG_PRIORITY_HIGH);
            //注册图像Binder
            ServiceManager.addService("gfxinfo", new GraphicsBinder(this));
            //注册SQLite DB binder
            ServiceManager.addService("dbinfo", new DbBinder(this));
            if (MONITOR_CPU_USAGE) {
            	//注册CPU使用情况的Binder
                ServiceManager.addService("cpuinfo", new CpuBinder(this),/* allowIsolated= */ false, DUMP_FLAG_PRIORITY_CRITICAL);
            }
            //注册权限控制Binder
            ServiceManager.addService("permission", new PermissionController(this));
            //注册进程管理Binder
            ServiceManager.addService("processinfo", new ProcessInfoService(this));
            //获取“android”应用的ApplicationInfo，并装载到mSystemThread
            ApplicationInfo info = mContext.getPackageManager().getApplicationInfo("android", STOCK_PM_FLAGS | MATCH_SYSTEM_ONLY);
            mSystemThread.installSystemApplicationInfo(info, getClass().getClassLoader());
            //创建ProcessRecord维护进程的相关信息
            synchronized (this) {
                ProcessRecord app = mProcessList.newProcessRecordLocked(info, info.processName,...);
                app.setPersistent(true);
                app.pid = MY_PID;
                app.getWindowProcessController().setPid(MY_PID);
                app.maxAdj = ProcessList.SYSTEM_ADJ;
                app.makeActive(mSystemThread.getApplicationThread(), mProcessStats);
                mPidsSelfLocked.put(app);
                mProcessList.updateLruProcessLocked(app, false, null);
                updateOomAdjLocked(OomAdjuster.OOM_ADJ_REASON_NONE);
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(
                    "Unable to find android system package", e);
        }
    }
```

在这个方法中会设置一些系统进程，主要功能为：

- 注册一些服务:activity、procstats、meminfo、gfxinfo、dbinfo、cpuinfo、permission、processinfo等。
- 
  获取包名为“android”的应用的ApplicationInfo对象，并将该ApplicationInfo信息安装设置到SystemThread(系统进程主线程)。即可以理解，系统也是一个特殊的应用。
- 创建ProcessRecord维护进程的相关信息，这里MY_PID即为SystemServer进程ID。
- 启动 检测应用运行和交互。

### 后续

当AMS创建并启动之后，会有一系列的后续的工作需要处理。这些操作都是在**startOtherServices()**中去调用的

```java

     private void startOtherServices() {
            //注册系统的ContentProvider信息
            mActivityManagerService.installSystemProviders();
       
            mActivityManagerService.setWindowManager(wm);
           	mActivityManagerService.systemReady(() -> {
                ......//goingCallback
            }, BOOT_TIMINGS_TRACE_LOG);
     }
```

```java
   public void systemReady(final Runnable goingCallback, TimingsTraceLog traceLog) {
        traceLog.traceBegin("PhaseActivityManagerReady");
        synchronized(this) {
        	//第一次进入的时候为false
            if (mSystemReady) {
                //如果AMS已经准备好了，那么会调用goingCallback的run方法，然后返回
                if (goingCallback != null) {
                    goingCallback.run();
                }
                return;
            }
            mLocalDeviceIdleController = LocalServices.getService(DeviceIdleController.LocalService.class);
            //调用ATMS的onSystemReady方法
            mActivityTaskManager.onSystemReady();
            mSystemReady = true;
        }
     
        //关闭procsToKill中的所有进程
        ArrayList<ProcessRecord> procsToKill = null;
        synchronized(mPidsSelfLocked) {
            for (int i=mPidsSelfLocked.size()-1; i>=0; i--) {
                ProcessRecord proc = mPidsSelfLocked.valueAt(i);
                if (!isAllowedWhileBooting(proc.info)){
                    if (procsToKill == null) {
                        procsToKill = new ArrayList<ProcessRecord>();
                    }
                    procsToKill.add(proc);
                }
            }
        }
        synchronized(this) {
            if (procsToKill != null) {
                for (int i=procsToKill.size()-1; i>=0; i--) {
                    ProcessRecord proc = procsToKill.get(i);
                    Slog.i(TAG, "Removing system update proc: " + proc);
                    mProcessList.removeProcessLocked(proc, true, false, "system update done");
                }
            }
            //到这里为止，整个系统已经准备完毕了。可以进行进程的创建等工作。
            mProcessesReady = true;
        }
     		...
        //运行goingCallback
        if (goingCallback != null) goingCallback.run();
     		...
        //启动Launcher的activity
        mAtmInternal.startHomeOnAllDisplays(currentUserId, "systemReady");
     		...
        }
    }
```

这里的主要功能是：

* 关键服务继续进行初始化
* 已经启动的进程，如果没有FLAG_PERSISTENT标志位，则会被kill掉
* 运行goingCallBack
* 启动launcher的Activity，即桌面应用。

这里继续跟踪一下goingCallBack的具体执行内容。

**goingCallBack**

```java
       mActivityManagerService.systemReady(() -> {
            try {
                //启动NativeCrash的监测
                mActivityManagerService.startObservingNativeCrashes();
            } catch (Throwable e) {
                reportWtf("observing native crashes", e);
            }
            if (!mOnlyCore && mWebViewUpdateService != null) {
                webviewPrep = SystemServerInitThreadPool.get().submit(() -> {
                    //启动WebView相关
                    mWebViewUpdateService.prepareWebViewInSystemServer();
                }, WEBVIEW_PREPARATION);
            }

            try {
                //启动systemUI
                startSystemUi(context, windowManagerF);
            } catch (Throwable e) {
                reportWtf("starting System UI", e);
            }
            ...
    }
```

在这个里面会继续进行一些初始化的工作：

* 启动NativeCrash监测
* 启动WebView相关服务
* 启动SystemUI

**startHomeOnAllDisplays**

该功能主要是进行桌面程序的启动，和AMS的启动流程关联不大，在这里不再详细进行解析。

### 知识点：

* AMS是在SystemServer进程中进行创建并启动的
* 在AMS的服务启动过程中，通过构造函数进行了一些对象的创建和初始化工作（初Activity外其他3大组件的挂你和调度对象的创建；内存、电池、权限、CPU等的监控等等相关对象的创建），并且通过start()方法启动服务（移除进程组、启动CPU线程、全县注册、电池服务等等）。
* AMS创建并将对应服务启动之后，会通过setSystemProcess方法，将framework-res.apk的信息加入到SystemServer进程的LoadedApk中，并创建了SystemServer进程的ProcessRecord，加入到了mPidsSelfLocked，交给AMS来统一管理
* AMS启动之后的后续工作，主要调用systemReady()和传入的goingCallBack来执行。主要是各种服务或者进程，等AMS启动完成后需要进一步完成的工作以及系统相关的初始化。
* 桌面应用是在systemReady()方法中启动，systemUI是在goingCallback中完成。
* 当桌面应用启动完成以后，发送开机广播ACTION_BOOT_COMPLETED。



https://blog.csdn.net/weixin_34037173/article/details/88003374

https://www.cnblogs.com/fanglongxiang/p/13594986.html
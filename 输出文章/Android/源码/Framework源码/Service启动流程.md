Service启动流程

Service的启动，一般是在Activity中通过**startService()**启动的。我们以此为入口来整理整个Servcie的启动流程。startServcie是在ContextWrapper中来实现的。

### StartService

```java
//frameworks\base\core\java\android\content\ContextWrapper.java
    public ComponentName startService(Intent service) {
    	//这里的mBase是ContextImpl对象
        return mBase.startService(service);
    }

```

在[Context必知必会]()中我们讲过，这个mBase是ContextImpl对象。

```java
//frameworks\base\core\java\android\app\ContextImpl.java
	//启动Service服务
    @Override
    public ComponentName startService(Intent service) {
        warnIfCallingFromSystemProcess();
        return startServiceCommon(service, false, mUser);
    }
    private ComponentName startServiceCommon(Intent service, boolean requireForeground,
            UserHandle user) {
        try {
			//binder机制调用AMS中的startService方法
            ComponentName cn = ActivityManager.getService().startService(
                mMainThread.getApplicationThread(), service, service.resolveTypeIfNeeded(
                            getContentResolver()), requireForeground,getOpPackageName(), user.getIdentifier());
            return cn;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
```

这里通过IBinder机制调用了AMS的startService方法。

```java
//frameworks\base\services\core\java\com\android\server\am\ActivityManagerService.java
public ComponentName startService(IApplicationThread caller, Intent service,String resolvedType, boolean requireForeground, String callingPackage, int userId)throws TransactionTooLargeException {
    ...
        try {
			//调用了startServiceLocked方法
            res = mServices.startServiceLocked(caller, service,
                    resolvedType, callingPid, callingUid,
                    requireForeground, callingPackage, userId);
      ...
    }
}
```

```java
//frameworks\base\services\core\java\com\android\server\am\ActiveServices.java
    ComponentName startServiceLocked(IApplicationThread caller, Intent service, String resolvedType,int callingPid, int callingUid, boolean fgRequired, String callingPackage,final int userId, boolean allowBackgroundActivityStarts)throws TransactionTooLargeException {

		//重点方法1         这里会查找service所对应的ServiceRecord。如果没有找到的话，会从PackageManagerService中查找service所对应的Service
		//信息，封装为ServiceRecord。然后将ServiceRecord作为ServiceLookupResult的record字段来保存。
        ServiceLookupResult res = retrieveServiceLocked(service, null, resolvedType, callingPackage,
                    callingPid, callingUid, userId, true, callerFg, false, false);

		//重点方法       启动Service
        ComponentName cmp = startServiceInnerLocked(smap, service, r, callerFg, addToStarting);
        return cmp;
    }
```

```java
//frameworks\base\services\core\java\com\android\server\am\ActiveServices.java
ComponentName startServiceInnerLocked(ServiceMap smap, Intent service, ServiceRecord r,
        boolean callerFg, boolean addToStarting) throws TransactionTooLargeException {
    ...
	//启动Service
    String error = bringUpServiceLocked(r, service.getFlags(), callerFg, false, false);
    if (error != null) {
        return new ComponentName("!!", error);
    }
	...
    return r.name;
}
```
```java
//frameworks\base\services\core\java\com\android\server\am\ActiveServices.java
	private String bringUpServiceLocked(ServiceRecord r, int intentFlags, boolean execInFg,
            boolean whileRestarting, boolean permissionsReviewRequired)
            throws TransactionTooLargeException {
        if (r.app != null && r.app.thread != null) {
			//情况1：如果service所在进程和Service都已经启动了，则直接调用sendServiceArgsLocked方法，主要用于触发Service端的onStartCommind方法
            //这里的r.app是指Service所在的进程，当Service启动之后，会设置其所在的进程信息。
            sendServiceArgsLocked(r, execInFg, false);
            return null;
        }
        ProcessRecord app;
			//获取service所在的进程，
            app = mAm.getProcessRecordLocked(procName, r.appInfo.uid, false);
            if (app != null && app.thread != null) {
					//情况2：进程已经启动了，但是service未启动，那么就调用realStartServiceLocked来启动Service
                    app.addPackage(r.appInfo.packageName, r.appInfo.longVersionCode, mAm.mProcessStats);
                    realStartServiceLocked(r, app, execInFg);
                    return null;
            }
        
        if (app == null && !permissionsReviewRequired) {
			//情况3.如果Service所在的进程未启动，则通过startProcessLocked启动所在的进程
            app=mAm.startProcessLocked(procName, r.appInfo, true, intentFlags,
                    hostingRecord, false, isolated, false)) == null)
        if (!mPendingServices.contains(r)) {
			//放入到mPendingService中。
			//因为这时候进程未启动，所以需要等进程启动之后才能启动Servcie,而这些启动的Service则都保存在mPendingServices
			//当进程启动的时候，会调用AMS的attachApplicationLocked()方法，
			//其中会调用mServices.attachApplicationLock()方法，也就是本类中的attachApplicationLock方法
            mPendingServices.add(r);
        }
        return null;
    }
```

对于Service的启动，这里分了3种情况。

1. Service所在的进程和Service都已经启动了，则直接调用sendServiceArgsLocked，进行绑定。
2. Servcie所在的进程启动了，但是Service未启动，则调用realStartServiceLocked启动Service服务
3. Service所在的进程都未启动，则通过startProcessLocked启动进程，并且将待启动的Service放入到mPendingServices列表中。

我们可以从第3种情况分析，这种情况下，realStartServiceLocked，和sendServiceArgsLocked这两个方法都会被调用。第3种涉及到进程的启动。当进程启动完成之后，会调用AMS的attachApplicationLocked()方法。而方法中则会调用ActiveService中的**attachApplicationLock()**方法。

```java
	//将启动的Service和对应的Application进行绑定
    boolean attachApplicationLocked(ProcessRecord proc, String processName)throws RemoteException {
			...
                ServiceRecord sr = null;
                //遍历所有要启动的Service去启动。
                for (int i=0; i<mPendingServices.size(); i++) {
                    ...
                    //启动Service
                    realStartServiceLocked(sr, proc, sr.createdFromFg);
    }
```

这里调用了**realStartServiceLocked**方法

```java
 //真正的去启动Service
private final void realStartServiceLocked(ServiceRecord r,ProcessRecord app, boolean execInFg) throws RemoteException {
        app.forceProcessStateUpTo(ActivityManager.PROCESS_STATE_SERVICE);
		//重点方法       去创建Servcie对象，然后执行Servcie的onCreate方法。这里的app.thread是IApplicationThread，是一个IBinder对象，能够发起远程调用。
		//它的实现类ActivityThread的内部类ApplicationThread
        app.thread.scheduleCreateService(r, r.serviceInfo,mAm.compatibilityInfoForPackage(r.serviceInfo.applicationInfo),
                app.getReportedProcState());
	//这里会调用onBind方法
    sendServiceArgsLocked(r, execInFg, true);
}
```
这个方法里面主要有2个操作：

* 创建Service。
* 调用绑定。

对应Service的创建过程，我们在[Context必知必会]()中讲过，这里就不再赘述了。我们将关注点放在绑定方法上scheduleCreateService上。

```java
//frameworks\base\core\java\android\app\ActivityThread.java   
	public final void scheduleServiceArgs(IBinder token, ParceledListSlice args) {
            List<ServiceStartArgs> list = args.getList();
            for (int i = 0; i < list.size(); i++) {
                ServiceStartArgs ssa = list.get(i);
				//封装为ServiceArgsData对象，然后发送
                ServiceArgsData s = new ServiceArgsData();
                s.token = token;
                s.taskRemoved = ssa.taskRemoved;
                s.startId = ssa.startId;
                s.flags = ssa.flags;
                s.args = ssa.args;
                sendMessage(H.SERVICE_ARGS, s);
            }
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
            switch (msg.what) {
                case SERVICE_ARGS:
                    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, ("serviceStart: " + String.valueOf(msg.obj)));
                    handleServiceArgs((ServiceArgsData) msg.obj);
                    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    break;
```

通过Handler调用了**handleServiceArgs**方法

```java
    private void handleServiceArgs(ServiceArgsData data) {
    	//获取到对应的Service对象
        Service s = mServices.get(data.token);
        if (s != null) {
					//调用onStartCommand()方法
                    res = s.onStartCommand(data.args, data.flags, data.startId);
        }
    }
```

调用onStartCommand()方法。

这里我们梳理一下总体的流程：

![image-20210203160418898](http://cdn.qiniu.kailaisii.com/typora/20210203160422-192015.png)

### BService的绑定原理

大体流程

![image-20210203161112732](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210203161112732.png)
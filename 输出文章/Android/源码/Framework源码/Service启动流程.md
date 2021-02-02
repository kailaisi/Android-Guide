Service启动流程

Service的启动，一般是在Activity中通过**startService()**启动的。我们以此为入口来整理整个Servcie的启动流程。startServcie是在ContextWrapper中来实现的。

```java
//frameworks\base\core\java\android\content\ContextWrapper.java
    public ComponentName startService(Intent service) {
    	//这里的mBase是ContextImpl对象
        return mBase.startService(service);
    }

```

在[Context必知必会]()中我们讲过，这个mBase是ContextImpl对象。

```java
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
public ComponentName startService(IApplicationThread caller, Intent service,
        String resolvedType, boolean requireForeground, String callingPackage, int userId)
        throws TransactionTooLargeException {
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
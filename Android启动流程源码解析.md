## Android启动流程源码解析

我们的桌面其实也是一个应用。使用的Activity是**LauncherActivity**，通过获取安装的应用以及图标信息，将我们的应用展示在桌面上。当我们点击图标时，通过相关的代码进行启动。

```
public abstract class LauncherActivity extends ListActivity {
    Intent mIntent;
    PackageManager mPackageManager;
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mPackageManager = getPackageManager();
        onSetContentView();
        mAdapter = new ActivityAdapter(mIconResizer);
        setListAdapter(mAdapter);
        getListView().setTextFilterEnabled(true);
    }

    protected Intent intentForPosition(int position) {
        ActivityAdapter adapter = (ActivityAdapter) mAdapter;
        return adapter.intentForPosition(position);
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        //根据点击的位置获取到intent对象，里面包含了包名和对应的要启动的Activity的名称
        Intent intent = intentForPosition(position);
        startActivity(intent);
    }
    
    protected Intent intentForPosition(int position) {
        Intent intent = new Intent(mIntent);
            ListItem item = mActivitiesList.get(position);
            intent.setClassName(item.packageName, item.className);
            if (item.extras != null) {
                intent.putExtras(item.extras);
            }
            return intent;
    }
}
```

可以看到这里面的代码就是设置了点击事件，然后当点击按钮时，设置对应的**packageName**和**className**，然后**startActivity**即可。

那么这个**startActivity**又做了什么处理呢？

```
    public void startActivity(Intent intent, @Nullable Bundle options) {
        if (options != null) {
            startActivityForResult(intent, -1, options);
        } else {
            //没有bundle，按照-1的请求处理
            startActivityForResult(intent, -1);
        }
    }
    public void startActivityForResult(@RequiresPermission Intent intent, int requestCode,
            @Nullable Bundle options) {
        //activity parent。其实就是判断Activity是否有父类，没有父类，将操作交给mInstrumentation来处理
        if (mParent == null) {
            options = transferSpringboardActivityOptions(options);
            Instrumentation.ActivityResult ar =mInstrumentation.execStartActivity(this, mMainThread.getApplicationThread(), mToken, this, intent, requestCode, options);
            if (ar != null) {
                //如果有返回值，则通过mainThread进行处理
                mMainThread.sendActivityResult(mToken, mEmbeddedID, requestCode, ar.getResultCode(), ar.getResultData());
            }
            if (requestCode >= 0) {
                mStartedActivity = true;
            }
            cancelInputsAndStartExitTransition(options);
        } else {
            if (options != null) {
                mParent.startActivityFromChild(this, intent, requestCode, options);
            } else {
                mParent.startActivityFromChild(this, intent, requestCode);
            }
        }
    }
    
    public void startActivityFromChild(@NonNull Activity child, @RequiresPermission Intent intent,
            int requestCode, @Nullable Bundle options) {
        options = transferSpringboardActivityOptions(options);
        Instrumentation.ActivityResult ar =
            mInstrumentation.execStartActivity(this, mMainThread.getApplicationThread(), mToken, child,intent, requestCode, options);
        if (ar != null) {
            mMainThread.sendActivityResult(
                mToken, child.mEmbeddedID, requestCode,
                ar.getResultCode(), ar.getResultData());
        }
        cancelInputsAndStartExitTransition(options);
    }
```

可以看到，不管是否有mParent，其实最后都是需要交给**mInstrumentation**通过**execStartActivity**来进行处理。

```
    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Activity target, Intent intent, int requestCode, Bundle options) {
        IApplicationThread whoThread = (IApplicationThread) contextThread;
        ...
        try {
            //对intent中的数据做一些处理
            intent.migrateExtraStreamToClipData();
            //根据当前who和intent的包名来判断是否需要启动新的进程，如果是的话，进行一些准备工作
            intent.prepareToLeaveProcess(who);
            int result = ActivityTaskManager.getService()//通过Ibinder获取ATMS的代理类。具体的实现类是继承了IActivityTaskManager.Stub的类，也就是ActivityTaskManagerService
                .startActivity(whoThread, who.getBasePackageName(), intent,
                        intent.resolveTypeIfNeeded(who.getContentResolver()),
                        token, target != null ? target.mEmbeddedID : null,
                        requestCode, 0, null, options);
            checkStartActivityResult(result, intent);
        } catch (RemoteException e) {
            throw new RuntimeException("Failure from system", e);
        }
        return null;
    }
```

现在我们去**ActivityTaskManagerService**中看一下**startActivity()**方法的具体实现

```
    @Override
    public final int startActivity(IApplicationThread caller, String callingPackage, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int startFlags, ProfilerInfo profilerInfo, Bundle bOptions) {
        //UserHandle.getCallingUserId获取的是用户id。因为安卓4.2以后支持多用户
        return startActivityAsUser(caller, callingPackage, intent, resolvedType, resultTo, resultWho, requestCode, startFlags, profilerInfo, bOptions, UserHandle.getCallingUserId());
    }

    @Override
    public int startActivityAsUser(IApplicationThread caller, String callingPackage, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
            int startFlags, ProfilerInfo profilerInfo, Bundle bOptions, int userId) {
        return startActivityAsUser(caller, callingPackage, intent, resolvedType, resultTo, resultWho, requestCode, startFlags, profilerInfo, bOptions, userId, true /*validateIncomingUser*/);
    }

    int startActivityAsUser(IApplicationThread caller, String callingPackage, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int startFlags, ProfilerInfo profilerInfo, Bundle bOptions, int userId, boolean validateIncomingUser) {
        enforceNotIsolatedCaller("startActivityAsUser");
        //Binder是有权限控制的。这里校验Binder调用方是否有权限调用当前activity。并且返回当前的userId
        // Binder.getCallingPid()：Binder调用方的Pid, Binder.getCallingUid():Binder调用方的Uid
        userId = getActivityStartController().checkTargetUser(userId, validateIncomingUser,
                Binder.getCallingPid(), Binder.getCallingUid(), "startActivityAsUser");
        //创建ActivityStart,设置相关信息，然后执行其execute()方法，切换到用户应用程序栈
        return getActivityStartController().obtainStarter(intent, "startActivityAsUser")
                ....
                .execute();
    }

```

我们这里看一下**execute()**方法。

```
    int execute() {
        try {
            if (mRequest.mayWait) {//因为调用了setMayWait().这里会为true
                return startActivityMayWait(mRequest.caller, mRequest.callingUid,
                        ...
            } else {
                return startActivity(mRequest.caller, mRequest.intent, mRequest.ephemeralIntent,
                      	...
            }
        } finally {
            onExecutionComplete();
        }
    }
```

这里会走**startActivityMayWait()**方法




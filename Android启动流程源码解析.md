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

        // TODO: Switch to user app stacks here.
        //实际上工厂设计模式+享元设计+链式调用
        //创建ActivityStart,设置相关信息，然后执行其execute()方法，切换到用户应用程序栈
        return mActivityStartController.obtainStarter(intent, "startActivityAsUser")
                .setCaller(caller)//调用方的AppThread的IBinder
                .setCallingPackage(callingPackage)//调用方的包名
                .setResolvedType(resolvedType)//调用type
                .setResultTo(resultTo)//调用方的ActivityClientRecord的binder（实际上是AMS的ActivityRecord对应在App端的binder对象）
                .setResultWho(resultWho)//调用方的标示
                .setRequestCode(requestCode)//需要返回的requestCode
                .setStartFlags(startFlags)//启动标志位
                .setProfilerInfo(profilerInfo)//启动时带上的权限文件对象
                .setActivityOptions(bOptions)//ActivityOptions的Activity的启动项,在一般的App中此时是null，不需要关注
                .setMayWait(userId)//是否是同步打开Actvivity 默认一般是true
                .execute();//执行方法。

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

```
private int startActivityMayWait(IApplicationThread caller, int callingUid,
                                 String callingPackage, int requestRealCallingPid, int requestRealCallingUid,
                                 Intent intent, String resolvedType, IVoiceInteractionSession voiceSession,
                                 IVoiceInteractor voiceInteractor, IBinder resultTo, String resultWho, int requestCode,
                                 int startFlags, ProfilerInfo profilerInfo, WaitResult outResult,
                                 Configuration globalConfig, SafeActivityOptions options, boolean ignoreTargetSecurity,
                                 int userId, TaskRecord inTask, String reason,
                                 boolean allowPendingRemoteAnimationRegistryLookup,
                                 PendingIntentRecord originatingPendingIntent, boolean allowBackgroundActivityStart) {
    // Refuse possible leaked file descriptors
    //intent不能传递文件的句柄，如果有，则抛出异常
    if (intent != null && intent.hasFileDescriptors()) {
        throw new IllegalArgumentException("File descriptors passed in Intent");
    }
    //日志跟踪，通知activity正在启动
    mSupervisor.getActivityMetricsLogger().notifyActivityLaunching(intent);
    boolean componentSpecified = intent.getComponent() != null;
    //调用当前intent的pid
    final int realCallingPid = requestRealCallingPid != Request.DEFAULT_REAL_CALLING_PID
            ? requestRealCallingPid
            : Binder.getCallingPid();
    //调用当前intent的uid
    final int realCallingUid = requestRealCallingUid != Request.DEFAULT_REAL_CALLING_UID
            ? requestRealCallingUid
            : Binder.getCallingUid();

    int callingPid;
    if (callingUid >= 0) {
        callingPid = -1;
    } else if (caller == null) {
        callingPid = realCallingPid;
        callingUid = realCallingUid;
    } else {
        callingPid = callingUid = -1;
    }

    // Save a copy in case ephemeral needs it
    //临时意图
    final Intent ephemeralIntent = new Intent(intent);
    // Don't modify the client's object!
    intent = new Intent(intent);
    if (componentSpecified
            && !(Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() == null)
            && !Intent.ACTION_INSTALL_INSTANT_APP_PACKAGE.equals(intent.getAction())
            && !Intent.ACTION_RESOLVE_INSTANT_APP_PACKAGE.equals(intent.getAction())
            && mService.getPackageManagerInternalLocked()
            .isInstantAppInstallerComponent(intent.getComponent())) {
        intent.setComponent(null /*component*/);
        componentSpecified = false;
    }
    //方法分析1：
    //a.此方法实际上的调用PackageManagerService的resolveIntent
    //b.根据intent的信息查找最匹配的ResolveInfo信息。
    ResolveInfo rInfo = mSupervisor.resolveIntent(intent, resolvedType, userId, 0 /* matchFlags */, computeResolveFilterUid(callingUid, realCallingUid, mRequest.filterCallingUid));
    if (rInfo == null) {
        //如果没有获取到匹配的ResolveInfo信息，那么就通过其他方式来获取
        UserInfo userInfo = mSupervisor.getUserInfo(userId);
        //通过isManagedProfile()方法来判断这份UserInfo是否只是一个profile(Android中允许一个用户还拥有另一份profile)
        if (userInfo != null && userInfo.isManagedProfile()) {
            UserManager userManager = UserManager.get(mService.mContext);
            boolean profileLockedAndParentUnlockingOrUnlocked = false;
            long token = Binder.clearCallingIdentity();
            try {
                UserInfo parent = userManager.getProfileParent(userId);
                profileLockedAndParentUnlockingOrUnlocked = (parent != null)
                        && userManager.isUserUnlockingOrUnlocked(parent.id)
                        && !userManager.isUserUnlockingOrUnlocked(userId);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
            if (profileLockedAndParentUnlockingOrUnlocked) {
                rInfo = mSupervisor.resolveIntent(intent, resolvedType, userId,
                        PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                        computeResolveFilterUid(
                                callingUid, realCallingUid, mRequest.filterCallingUid));
            }
        }
    }
    //收集有关意向目标的ActivityInfo信息。其实ActivityInfo是直接存放在ResolveInfo中的一个变量值
    //从rInfo中拿到ActivityInfo以后设置一些其他的信息
    ActivityInfo aInfo = mSupervisor.resolveActivity(intent, rInfo, startFlags, profilerInfo);

    synchronized (mService.mGlobalLock) {
        //获取当前使用的activity栈
        final ActivityStack stack = mRootActivityContainer.getTopDisplayFocusedStack();
        stack.mConfigWillChange = globalConfig != null && mService.getGlobalConfiguration().diff(globalConfig) != 0;
        if (DEBUG_CONFIGURATION) Slog.v(TAG_CONFIGURATION,
                "Starting activity when config will change = " + stack.mConfigWillChange);
        //origId保存调用的uid和pid。然后将当前uid和pid进行使用
        final long origId = Binder.clearCallingIdentity();
        if (aInfo != null && (aInfo.applicationInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_CANT_SAVE_STATE) != 0 && mService.mHasHeavyWeightFeature) {
            //这里主要是对一些重量型的进行进行特殊处理。系统只允许运行一个重量级的应用。当已经有一个的时候，再启动一个，会弹出对话框进行选择
            //调用的不是private的标记，而且是个重量级的应用。这时候需要检测是否有其他重量级进程在执行。
            if (aInfo.processName.equals(aInfo.applicationInfo.packageName)) {//进程名等于Application中的包名
                //得到当前运行的重量级进程的记录
                final WindowProcessController heavy = mService.mHeavyWeightProcess;
                	...
                }
            }
        }
        //创建ActivityRecord
        final ActivityRecord[] outRecord = new ActivityRecord[1];
        //执行startActivity
        // 最重点的地方
        int res = startActivity(caller, intent, ephemeralIntent, resolvedType, aInfo, rInfo,
                voiceSession, voiceInteractor, resultTo, resultWho, requestCode, callingPid,
                callingUid, callingPackage, realCallingPid, realCallingUid, startFlags, options,
                ignoreTargetSecurity, componentSpecified, outRecord, inTask, reason,
                allowPendingRemoteAnimationRegistryLookup, originatingPendingIntent,
                allowBackgroundActivityStart);
        //将调用者的uid和pid恢复
        Binder.restoreCallingIdentity(origId);
        if (stack.mConfigWillChange) {
            mService.mAmInternal.enforceCallingPermission(android.Manifest.permission.CHANGE_CONFIGURATION,
                    "updateConfiguration()");
            stack.mConfigWillChange = false;
            if (DEBUG_CONFIGURATION) Slog.v(TAG_CONFIGURATION,
                    "Updating to new configuration after starting activity.");
            mService.updateConfigurationLocked(globalConfig, null, false);
        }

        // Notify ActivityMetricsLogger that the activity has launched. ActivityMetricsLogger
        // will then wait for the windows to be drawn and populate WaitResult.
        //跟踪日志记录activity启动的结果
        mSupervisor.getActivityMetricsLogger().notifyActivityLaunched(res, outRecord[0]);
        if (outResult != null) {
            //需要监听activity启动后的监听
            outResult.result = res;

            final ActivityRecord r = outRecord[0];
            //对不同的启动结果进行处理
            switch (res) {
                case START_SUCCESS: {//启动成功了
                    mSupervisor.mWaitingActivityLaunched.add(outResult);
                    do {//这里应该是通过outResult的结果来进行activity的启动结果监听。通过状态循环，一直等到result=START_TASK_TO_FRONT或者超时。
                        try {
                            //ATMS等待
                            mService.mGlobalLock.wait();
                        } catch (InterruptedException e) {
                        }
                    } while (outResult.result != START_TASK_TO_FRONT && !outResult.timeout && outResult.who == null);
                    if (outResult.result == START_TASK_TO_FRONT) {
                        res = START_TASK_TO_FRONT;
                    }
                    break;
                }
                case START_DELIVERED_TO_TOP: {
                    outResult.timeout = false;
                    outResult.who = r.mActivityComponent;
                    outResult.totalTime = 0;
                    break;
                }
                case START_TASK_TO_FRONT: {//activity被带到前台，但是还没有完全启动\
                    //记录是冷启动还是热启动
                    outResult.launchState = r.attachedToProcess() ? LAUNCH_STATE_HOT : LAUNCH_STATE_COLD;
                    // ActivityRecord may represent a different activity, but it should not be
                    // in the resumed state.
                    if (r.nowVisible && r.isState(RESUMED)) {//如果当前状态为可见，直接设置结果
                        outResult.timeout = false;
                        outResult.who = r.mActivityComponent;
                        outResult.totalTime = 0;
                    } else {//否则等待Acitivity可见
                        final long startTimeMs = SystemClock.uptimeMillis();
                        //Activity对应的task拉到前台以后，一直要等到该界面被加载
                        mSupervisor.waitActivityVisible(r.mActivityComponent, outResult, startTimeMs);
                        // Note: the timeout variable is not currently not ever set.
                        do {
                            try {
                                mService.mGlobalLock.wait();
                            } catch (InterruptedException e) {
                            }
                        } while (!outResult.timeout && outResult.who == null);
                    }
                    break;
                }
            }
        }

        return res;
    }
}
```

1. 通过PMS获取rInfo，aInfo信息。
2. 如果是重量级的应用，进行特殊处理
3. 通过**startActivity**启动activiy，并将结果返回
4. 通过**outResult**来对启动的结果状态码进行不同处理

这里是通过**startActivity**进行应用的启动，我们

```
private int startActivity(IApplicationThread caller, Intent intent, Intent ephemeralIntent,
                          String resolvedType, ActivityInfo aInfo, ResolveInfo rInfo,
                          IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
                          IBinder resultTo, String resultWho, int requestCode, int callingPid, int callingUid,
                          String callingPackage, int realCallingPid, int realCallingUid, int startFlags,
                          SafeActivityOptions options,
                          boolean ignoreTargetSecurity, boolean componentSpecified, ActivityRecord[] outActivity,
                          TaskRecord inTask, boolean allowPendingRemoteAnimationRegistryLookup,
                          PendingIntentRecord originatingPendingIntent, boolean allowBackgroundActivityStart) {
    //日志跟踪，记录正在启动
    mSupervisor.getActivityMetricsLogger().notifyActivityLaunching(intent);
    //记录启动结果
    int err = ActivityManager.START_SUCCESS;
    // Pull the optional Ephemeral Installer-only bundle out of the options early.
    final Bundle verificationBundle
            = options != null ? options.popAppVerificationBundle() : null;
    //窗口控制器
    WindowProcessController callerApp = null;
    if (caller != null) {
        //caller是IApplicationThread。
        //1.  通过caller获取当前Activity所处在的进程数据
        callerApp = mService.getProcessController(caller);
        if (callerApp != null) {
            callingPid = callerApp.getPid();
            callingUid = callerApp.mInfo.uid;
        } else {
            Slog.w(TAG, "Unable to find app for caller " + caller
                    + " (pid=" + callingPid + ") when starting: "
                    + intent.toString());
            err = ActivityManager.START_PERMISSION_DENIED;
        }
    }
    //获取userId
    final int userId = aInfo != null && aInfo.applicationInfo != null
            ? UserHandle.getUserId(aInfo.applicationInfo.uid) : 0;
    //如果有权限信息，那么记录日志：   哪个uerid启动通过哪个uid应用启动了intent
    if (err == ActivityManager.START_SUCCESS) {
        Slog.i(TAG, "START u" + userId + " {" + intent.toShortString(true, true, true, false)
                + "} from uid " + callingUid);
    }
    //SourceRecord为桌面展示的Activity的AMS端的描述对象
    ActivityRecord sourceRecord = null;
    ActivityRecord resultRecord = null;
    if (resultTo != null) {
        //resultTo参数  launcher进程当前展示的Activity的mToken对象，此处isInAnyStackLocked方法是找到桌面Activity对应在AMS端的记录ActivityRecord对象
        sourceRecord = mRootActivityContainer.isInAnyStack(resultTo);
        if (DEBUG_RESULTS) Slog.v(TAG_RESULTS,
                "Will send result to " + resultTo + " " + sourceRecord);
        if (sourceRecord != null) {
            if (requestCode >= 0 && !sourceRecord.finishing) {
                resultRecord = sourceRecord;
            }
        }
    }
    //intent设置的标志位
    final int launchFlags = intent.getFlags();

    if ((launchFlags & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0 && sourceRecord != null) {
        //对FLAG_ACTIVITY_FORWARD_RESULT的启动模式进行特殊处理
        // Transfer the result target from the source activity to the new
        // one being started, including any failures.
        if (requestCode >= 0) {
            //FLAG_ACTIVITY_FORWARD_RESULT和requestCode不能同时使用
            SafeActivityOptions.abort(options);
            return ActivityManager.START_FORWARD_AND_REQUEST_CONFLICT;
        }
        resultRecord = sourceRecord.resultTo;
        if (resultRecord != null && !resultRecord.isInStackLocked()) {
            resultRecord = null;
        }
        resultWho = sourceRecord.resultWho;
        requestCode = sourceRecord.requestCode;
        sourceRecord.resultTo = null;
        if (resultRecord != null) {
            resultRecord.removeResultsLocked(sourceRecord, resultWho, requestCode);
        }
        if (sourceRecord.launchedFromUid == callingUid) {
            // The new activity is being launched from the same uid as the previous
            // activity in the flow, and asking to forward its result back to the
            // previous.  In this case the activity is serving as a trampoline between
            // the two, so we also want to update its launchedFromPackage to be the
            // same as the previous activity.  Note that this is safe, since we know
            // these two packages come from the same uid; the caller could just as
            // well have supplied that same package name itself.  This specifially
            // deals with the case of an intent picker/chooser being launched in the app
            // flow to redirect to an activity picked by the user, where we want the final
            // activity to consider it to have been launched by the previous app activity.
            callingPackage = sourceRecord.launchedFromPackage;
        }
    }

    if (err == ActivityManager.START_SUCCESS && intent.getComponent() == null) {
        // We couldn't find a class that can handle the given Intent.
        // That's the end of that!
        err = ActivityManager.START_INTENT_NOT_RESOLVED;
    }

    if (err == ActivityManager.START_SUCCESS && aInfo == null) {
        // We couldn't find the specific class specified in the Intent.
        // Also the end of the line.
        err = ActivityManager.START_CLASS_NOT_FOUND;
    }

    if (err == ActivityManager.START_SUCCESS && sourceRecord != null
            && sourceRecord.getTaskRecord().voiceSession != null) {
        // If this activity is being launched as part of a voice session, we need
        // to ensure that it is safe to do so.  If the upcoming activity will also
        // be part of the voice session, we can only launch it if it has explicitly
        // said it supports the VOICE category, or it is a part of the calling app.
        if ((launchFlags & FLAG_ACTIVITY_NEW_TASK) == 0
                && sourceRecord.info.applicationInfo.uid != aInfo.applicationInfo.uid) {
            try {
                intent.addCategory(Intent.CATEGORY_VOICE);
                if (!mService.getPackageManager().activitySupportsIntent(
                        intent.getComponent(), intent, resolvedType)) {
                    Slog.w(TAG,
                            "Activity being started in current voice task does not support voice: "
                                    + intent);
                    err = ActivityManager.START_NOT_VOICE_COMPATIBLE;
                }
            } catch (RemoteException e) {
                Slog.w(TAG, "Failure checking voice capabilities", e);
                err = ActivityManager.START_NOT_VOICE_COMPATIBLE;
            }
        }
    }

    if (err == ActivityManager.START_SUCCESS && voiceSession != null) {
        // If the caller is starting a new voice session, just make sure the target
        // is actually allowing it to run this way.
        try {
            if (!mService.getPackageManager().activitySupportsIntent(intent.getComponent(),
                    intent, resolvedType)) {
                Slog.w(TAG,
                        "Activity being started in new voice task does not support: "
                                + intent);
                err = ActivityManager.START_NOT_VOICE_COMPATIBLE;
            }
        } catch (RemoteException e) {
            Slog.w(TAG, "Failure checking voice capabilities", e);
            err = ActivityManager.START_NOT_VOICE_COMPATIBLE;
        }
    }

    final ActivityStack resultStack = resultRecord == null
            ? null : resultRecord.getActivityStack();

    if (err != START_SUCCESS) {
        if (resultRecord != null) {
            resultStack.sendActivityResultLocked(
                    -1, resultRecord, resultWho, requestCode, RESULT_CANCELED, null);
        }
        SafeActivityOptions.abort(options);
        return err;
    }

    boolean abort = !mSupervisor.checkStartAnyActivityPermission(intent, aInfo, resultWho,
            requestCode, callingPid, callingUid, callingPackage, ignoreTargetSecurity,
            inTask != null, callerApp, resultRecord, resultStack);
    abort |= !mService.mIntentFirewall.checkStartActivity(intent, callingUid,
            callingPid, resolvedType, aInfo.applicationInfo);
    abort |= !mService.getPermissionPolicyInternal().checkStartActivity(intent, callingUid,
            callingPackage);

    boolean restrictedBgActivity = false;
    if (!abort) {
        try {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                    "shouldAbortBackgroundActivityStart");
            restrictedBgActivity = shouldAbortBackgroundActivityStart(callingUid,
                    callingPid, callingPackage, realCallingUid, realCallingPid, callerApp,
                    originatingPendingIntent, allowBackgroundActivityStart, intent);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        }
    }

    // Merge the two options bundles, while realCallerOptions takes precedence.
    ActivityOptions checkedOptions = options != null
            ? options.getOptions(intent, aInfo, callerApp, mSupervisor) : null;
    if (allowPendingRemoteAnimationRegistryLookup) {
        checkedOptions = mService.getActivityStartController()
                .getPendingRemoteAnimationRegistry()
                .overrideOptionsIfNeeded(callingPackage, checkedOptions);
    }
    if (mService.mController != null) {
        try {
            // The Intent we give to the watcher has the extra data
            // stripped off, since it can contain private information.
            Intent watchIntent = intent.cloneFilter();
            abort |= !mService.mController.activityStarting(watchIntent,
                    aInfo.applicationInfo.packageName);
        } catch (RemoteException e) {
            mService.mController = null;
        }
    }

    mInterceptor.setStates(userId, realCallingPid, realCallingUid, startFlags, callingPackage);
    if (mInterceptor.intercept(intent, rInfo, aInfo, resolvedType, inTask, callingPid,
            callingUid, checkedOptions)) {
        // activity start was intercepted, e.g. because the target user is currently in quiet
        // mode (turn off work) or the target application is suspended
        intent = mInterceptor.mIntent;
        rInfo = mInterceptor.mRInfo;
        aInfo = mInterceptor.mAInfo;
        resolvedType = mInterceptor.mResolvedType;
        inTask = mInterceptor.mInTask;
        callingPid = mInterceptor.mCallingPid;
        callingUid = mInterceptor.mCallingUid;
        checkedOptions = mInterceptor.mActivityOptions;
    }

    if (abort) {
        if (resultRecord != null) {
            resultStack.sendActivityResultLocked(-1, resultRecord, resultWho, requestCode,
                    RESULT_CANCELED, null);
        }
        // We pretend to the caller that it was really started, but
        // they will just get a cancel result.
        ActivityOptions.abort(checkedOptions);
        return START_ABORTED;
    }

    // If permissions need a review before any of the app components can run, we
    // launch the review activity and pass a pending intent to start the activity
    // we are to launching now after the review is completed.
    if (aInfo != null) {
        if (mService.getPackageManagerInternalLocked().isPermissionsReviewRequired(
                aInfo.packageName, userId)) {
            IIntentSender target = mService.getIntentSenderLocked(
                    ActivityManager.INTENT_SENDER_ACTIVITY, callingPackage,
                    callingUid, userId, null, null, 0, new Intent[]{intent},
                    new String[]{resolvedType}, PendingIntent.FLAG_CANCEL_CURRENT
                            | PendingIntent.FLAG_ONE_SHOT, null);

            Intent newIntent = new Intent(Intent.ACTION_REVIEW_PERMISSIONS);

            int flags = intent.getFlags();
            flags |= Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;

            /*
             * Prevent reuse of review activity: Each app needs their own review activity. By
             * default activities launched with NEW_TASK or NEW_DOCUMENT try to reuse activities
             * with the same launch parameters (extras are ignored). Hence to avoid possible
             * reuse force a new activity via the MULTIPLE_TASK flag.
             *
             * Activities that are not launched with NEW_TASK or NEW_DOCUMENT are not re-used,
             * hence no need to add the flag in this case.
             */
            if ((flags & (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_NEW_DOCUMENT)) != 0) {
                flags |= Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
            }
            newIntent.setFlags(flags);

            newIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, aInfo.packageName);
            newIntent.putExtra(Intent.EXTRA_INTENT, new IntentSender(target));
            if (resultRecord != null) {
                newIntent.putExtra(Intent.EXTRA_RESULT_NEEDED, true);
            }
            intent = newIntent;

            resolvedType = null;
            callingUid = realCallingUid;
            callingPid = realCallingPid;

            rInfo = mSupervisor.resolveIntent(intent, resolvedType, userId, 0,
                    computeResolveFilterUid(
                            callingUid, realCallingUid, mRequest.filterCallingUid));
            aInfo = mSupervisor.resolveActivity(intent, rInfo, startFlags,
                    null /*profilerInfo*/);

            if (DEBUG_PERMISSIONS_REVIEW) {
                final ActivityStack focusedStack =
                        mRootActivityContainer.getTopDisplayFocusedStack();
                Slog.i(TAG, "START u" + userId + " {" + intent.toShortString(true, true,
                        true, false) + "} from uid " + callingUid + " on display "
                        + (focusedStack == null ? DEFAULT_DISPLAY : focusedStack.mDisplayId));
            }
        }
    }

    // If we have an ephemeral app, abort the process of launching the resolved intent.
    // Instead, launch the ephemeral installer. Once the installer is finished, it
    // starts either the intent we resolved here [on install error] or the ephemeral
    // app [on install success].
    if (rInfo != null && rInfo.auxiliaryInfo != null) {
        intent = createLaunchIntent(rInfo.auxiliaryInfo, ephemeralIntent,
                callingPackage, verificationBundle, resolvedType, userId);
        resolvedType = null;
        callingUid = realCallingUid;
        callingPid = realCallingPid;

        aInfo = mSupervisor.resolveActivity(intent, rInfo, startFlags, null /*profilerInfo*/);
    }

    ActivityRecord r = new ActivityRecord(mService, callerApp, callingPid, callingUid,
            callingPackage, intent, resolvedType, aInfo, mService.getGlobalConfiguration(),
            resultRecord, resultWho, requestCode, componentSpecified, voiceSession != null,
            mSupervisor, checkedOptions, sourceRecord);
    if (outActivity != null) {
        outActivity[0] = r;
    }

    if (r.appTimeTracker == null && sourceRecord != null) {
        // If the caller didn't specify an explicit time tracker, we want to continue
        // tracking under any it has.
        r.appTimeTracker = sourceRecord.appTimeTracker;
    }

    final ActivityStack stack = mRootActivityContainer.getTopDisplayFocusedStack();

    // If we are starting an activity that is not from the same uid as the currently resumed
    // one, check whether app switches are allowed.
    if (voiceSession == null && (stack.getResumedActivity() == null
            || stack.getResumedActivity().info.applicationInfo.uid != realCallingUid)) {
        if (!mService.checkAppSwitchAllowedLocked(callingPid, callingUid,
                realCallingPid, realCallingUid, "Activity start")) {
            if (!(restrictedBgActivity && handleBackgroundActivityAbort(r))) {
                mController.addPendingActivityLaunch(new PendingActivityLaunch(r,
                        sourceRecord, startFlags, stack, callerApp));
            }
            ActivityOptions.abort(checkedOptions);
            return ActivityManager.START_SWITCHES_CANCELED;
        }
    }

    mService.onStartActivitySetDidAppSwitch();
    mController.doPendingActivityLaunches(false);

    final int res = startActivity(r, sourceRecord, voiceSession, voiceInteractor, startFlags,
            true /* doResume */, checkedOptions, inTask, outActivity, restrictedBgActivity);
    mSupervisor.getActivityMetricsLogger().notifyActivityLaunched(res, outActivity[0]);
    return res;
}
```

1. 根据caller获取当前Activity所在的进程
2. 
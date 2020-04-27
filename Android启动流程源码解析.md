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
//ActivityTaskManagerService.java
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
//ActivityStarter.java
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
//ActivityStarter.java
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
2. 如果是重量级的应用，进行特殊处理。一般系统只允许启动一个重量级的应用
3. 通过**startActivity**启动activiy，并将结果返回
4. 通过**outResult**来对启动的结果状态码进行不同处理

这里是通过**startActivity**进行应用的启动，我们

```java
	//ActivityStarter.java
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
        final Bundle verificationBundle= options != null ? options.popAppVerificationBundle() : null;
        //窗口控制器
        WindowProcessController callerApp = null;
        if (caller != null) {
            //caller是IApplicationThread。
            //1.  通过caller获取当前Activity所处在的进程数据
            callerApp = mService.getProcessController(caller);
            ...
        }
        //获取userId
        final int userId = aInfo != null && aInfo.applicationInfo != null? UserHandle.getUserId(aInfo.applicationInfo.uid) : 0;
        ...
        //SourceRecord为桌面展示的Activity的AMS端的描述对象
        ActivityRecord sourceRecord = null;
        ActivityRecord resultRecord = null;
        if (resultTo != null) {
            //resultTo参数是启动Activity的时候，绑定的WindowsMangerBinder对象
            //此处isInAnyStackLocked方法是找到桌面Activity对应在AMS端的记录ActivityRecord对象
            sourceRecord = mRootActivityContainer.isInAnyStack(resultTo);
        }
        //intent设置的标志位
        final int launchFlags = intent.getFlags();
        if ((launchFlags & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0 && sourceRecord != null) {
            //对FLAG_ACTIVITY_FORWARD_RESULT的启动模式进行特殊处理。这个是透传模式
            ...
        }
        //后面对各种错误信息进行处理。包括没有compontent,没有找到aInfo，没有堆栈信息
        ...
        if (err == ActivityManager.START_SUCCESS && sourceRecord != null&& sourceRecord.getTaskRecord().voiceSession != null) {
            //如果这个活动是作为语音会话的一部分启动的，我们需要确保这样做是安全的。如果即将到来的活动也将
            // 是语音会话的一部分，我们只能在它明确地 表示它支持语音类别，或者它是调用应用程序的一部分时才能启动它
            if ((launchFlags & FLAG_ACTIVITY_NEW_TASK) == 0&& sourceRecord.info.applicationInfo.uid != aInfo.applicationInfo.uid) {
                try {
                    intent.addCategory(Intent.CATEGORY_VOICE);
                    ...
                }
            }
        }
        ...
        final ActivityStack resultStack = resultRecord == null? null : resultRecord.getActivityStack();
        if (err != START_SUCCESS) {
            if (resultRecord != null) {
                resultStack.sendActivityResultLocked(-1, resultRecord, resultWho, requestCode, RESULT_CANCELED, null);
            }
            SafeActivityOptions.abort(options);
            return err;
        }
        //权限的校验
        boolean abort = !mSupervisor.checkStartAnyActivityPermission(intent, aInfo, resultWho,
                requestCode, callingPid, callingUid, callingPackage, ignoreTargetSecurity,
                inTask != null, callerApp, resultRecord, resultStack);
        abort |= !mService.mIntentFirewall.checkStartActivity(intent, callingUid,callingPid, resolvedType, aInfo.applicationInfo);
        abort |= !mService.getPermissionPolicyInternal().checkStartActivity(intent, callingUid,callingPackage);

        boolean restrictedBgActivity = false;
        if (!abort) {
            try {
                restrictedBgActivity = shouldAbortBackgroundActivityStart(callingUid,callingPid, callingPackage, realCallingUid, realCallingPid, callerApp,originatingPendingIntent, allowBackgroundActivityStart, intent);
            } 
        }

        ActivityOptions checkedOptions = options != null? options.getOptions(intent, aInfo, callerApp, mSupervisor) : null;
        if (allowPendingRemoteAnimationRegistryLookup) {
            checkedOptions = mService.getActivityStartController().getPendingRemoteAnimationRegistry().overrideOptionsIfNeeded(callingPackage, checkedOptions);
        }
		//ActivityController不为空的情况，比如monkey测试过程
        if (mService.mController != null) {
            try {
                Intent watchIntent = intent.cloneFilter();
                abort |= !mService.mController.activityStarting(watchIntent,aInfo.applicationInfo.packageName);
            } catch (RemoteException e) {
                mService.mController = null;
            }
        }
        mInterceptor.setStates(userId, realCallingPid, realCallingUid, startFlags, callingPackage);
        if (mInterceptor.intercept(intent, rInfo, aInfo, resolvedType, inTask, callingPid,callingUid, checkedOptions)) {
            //activity的start被拦截
            intent = mInterceptor.mIntent;
            rInfo = mInterceptor.mRInfo;
            aInfo = mInterceptor.mAInfo;
            resolvedType = mInterceptor.mResolvedType;
            inTask = mInterceptor.mInTask;
            callingPid = mInterceptor.mCallingPid;
            callingUid = mInterceptor.mCallingUid;
            checkedOptions = mInterceptor.mActivityOptions;
        }
		//如果被终止，则直接返回
        if (abort) {
            if (resultRecord != null) {
                resultStack.sendActivityResultLocked(-1, resultRecord, resultWho, requestCode, RESULT_CANCELED, null);
            }
            //将会获取一个cancel的结果
            ActivityOptions.abort(checkedOptions);
            return START_ABORTED;
        }
        if (aInfo != null) {
			//如果需要重新进行权限的检测，那么就进行一次检测
            if (mService.getPackageManagerInternalLocked().isPermissionsReviewRequired(aInfo.packageName, userId)) {
                IIntentSender target = mService.getIntentSenderLocked( ActivityManager.INTENT_SENDER_ACTIVITY, callingPackage,
                        callingUid, userId, null, null, 0, new Intent[]{intent},
                        new String[]{resolvedType}, PendingIntent.FLAG_CANCEL_CURRENT
                                | PendingIntent.FLAG_ONE_SHOT, null);

                Intent newIntent = new Intent(Intent.ACTION_REVIEW_PERMISSIONS);
                int flags = intent.getFlags();
                flags |= Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
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
                    final ActivityStack focusedStack =mRootActivityContainer.getTopDisplayFocusedStack();
                    Slog.i(TAG, "START u" + userId + " {" + intent.toShortString(true, true,
                            true, false) + "} from uid " + callingUid + " on display "
                            + (focusedStack == null ? DEFAULT_DISPLAY : focusedStack.mDisplayId));
                }
            }
        }

        if (rInfo != null && rInfo.auxiliaryInfo != null) {
            intent = createLaunchIntent(rInfo.auxiliaryInfo, ephemeralIntent,callingPackage, verificationBundle, resolvedType, userId);
            resolvedType = null;
            callingUid = realCallingUid;
            callingPid = realCallingPid;
            aInfo = mSupervisor.resolveActivity(intent, rInfo, startFlags, null /*profilerInfo*/);
        }
		//创建Activity的记录类，这个里面包含了Activity的所有信息，包括callerApp，uid，pid，requestCode，resultWho等
        ActivityRecord r = new ActivityRecord(mService, callerApp, callingPid, callingUid,
                callingPackage, intent, resolvedType, aInfo, mService.getGlobalConfiguration(),
                resultRecord, resultWho, requestCode, componentSpecified, voiceSession != null,
                mSupervisor, checkedOptions, sourceRecord);
        if (outActivity != null) {
            outActivity[0] = r;
        }
        ...
        final ActivityStack stack = mRootActivityContainer.getTopDisplayFocusedStack();
        //如果我们启动的活动与当前reusume的应用activity的的uid不同，检查是否允许应用程序切换。
        if (voiceSession == null && (stack.getResumedActivity() == null|| stack.getResumedActivity().info.applicationInfo.uid != realCallingUid)) {
			...
        }
        mService.onStartActivitySetDidAppSwitch();
		//处理PendingActivity的启动
        mController.doPendingActivityLaunches(false);
        //重点方法   重载方法的调用
        final int res = startActivity(r, sourceRecord, voiceSession, voiceInteractor, startFlags,
                true /* doResume */, checkedOptions, inTask, outActivity, restrictedBgActivity);
        mSupervisor.getActivityMetricsLogger().notifyActivityLaunched(res, outActivity[0]);
        return res;
    }
```

这个方法比较长，做的相关的校验处理也不少，但是其实最重要的地方是最后重载方法的调用

1. 根据caller获取当前Activity所在的进程
2. 对语音会话做一些特殊处理
3. 权限的校验
4. 创建ActivityRecord对象，来记录要启动的Activity的相关信息。
5. 调用**startActivity**重载方法

我们主要看一下最后一个方法。

```java
//ActivityStarter.java
private int startActivity(final ActivityRecord r, ActivityRecord sourceRecord,
                          IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
                          int startFlags, boolean doResume, ActivityOptions options, TaskRecord inTask,
                          ActivityRecord[] outActivity, boolean restrictedBgActivity) {
    int result = START_CANCELED;
    final ActivityStack startedActivityStack;
    try {
		//通知暂停布局，因为启动的过程中会有多个更改会会触发到surfacelayout。通过这种方式，能够避免重复刷新，在最后通过continueSurfaceLayout进行布局的统一刷新。
		//相当于进行了优化工作
        mService.mWindowManager.deferSurfaceLayout();
		//重点方法      启动activity。这里不会再进行繁琐的检测工作了。
        result = startActivityUnchecked(r, sourceRecord, voiceSession, voiceInteractor,
                startFlags, doResume, options, inTask, outActivity, restrictedBgActivity);
    } finally {
		//获取当前的Activity栈
        final ActivityStack currentStack = r.getActivityStack();
        startedActivityStack = currentStack != null ? currentStack : mTargetStack;
        if (ActivityManager.isStartResultSuccessful(result)) {
			//如果启动成功了  做一些处理  todo
            if (startedActivityStack != null) {
                // If there is no state change (e.g. a resumed activity is reparented to
                // top of another display) to trigger a visibility/configuration checking,
                // we have to update the configuration for changing to different display.
                final ActivityRecord currentTop =startedActivityStack.topRunningActivityLocked();
                if (currentTop != null && currentTop.shouldUpdateConfigForDisplayChanged()) {
                    mRootActivityContainer.ensureVisibilityAndConfig(currentTop, currentTop.getDisplayId(),
                            true /* markFrozenIfConfigChanged */, false /* deferResume */);
                }
            }
        } else {
        	//启动失败了。做一些处理  todo
            // If we are not able to proceed, disassociate the activity from the task.
            // Leaving an activity in an incomplete state can lead to issues, such as
            // performing operations without a window container.
            final ActivityStack stack = mStartActivity.getActivityStack();
            if (stack != null) {
                stack.finishActivityLocked(mStartActivity, RESULT_CANCELED,null /* intentResultData */, "startActivity", true /* oomAdj */);
            }

            // Stack should also be detached from display and be removed if it's empty.
            if (startedActivityStack != null && startedActivityStack.isAttached()&& startedActivityStack.numActivities() == 0&& !startedActivityStack.isActivityTypeHome()) {
                startedActivityStack.remove();
            }
        }
		//恢复暂停的布局
        mService.mWindowManager.continueSurfaceLayout();
    }

    postStartActivityProcessing(r, result, startedActivityStack);

    return result;
}
```

这里有一个优化的点，就是进行activity的启动调用之前，暂停了布局的绘制，然后在方法调用完成后，统一进行绘制工作。

该方法的重点方法是对 **startActivityUnchecked()** 方法的调用，然后对启动的结果进行了处理。

1. 启动成功，调用 **topRunningActivityLocked()** 将当前activity放置到栈顶，进行显示。
2. 启动失败 ，调用 **finishActivityLocked()** 方法进行结束调用。

同样的，我们也只跟踪主要代码 **startActivityUnchecked()** 

###  **startActivityUnchecked()** 

```java
    //这个方法只能从startActivity调用，这个方法就是几种启动模式的，在栈内的清空处理
    private int startActivityUnchecked(final ActivityRecord r, ActivityRecord sourceRecord,
                                       IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
                                       int startFlags, boolean doResume, ActivityOptions options, TaskRecord inTask,
                                       ActivityRecord[] outActivity, boolean restrictedBgActivity) {
        //初始化一些状态，这里主要是根据启动模式的相关设置进行了一些变量的处理。比如newtask，document等等
        //初始化Activity启动状态，获取launchmode flag 同时解决一些falg和launchmode的冲突
        setInitialState(r, options, inTask, doResume, startFlags, sourceRecord, voiceSession,voiceInteractor, restrictedBgActivity);
		...
		//根据是否存在指定的目标task来设定启动flag。 比如说指定的task不存在，那么就直接设置flag是否为newtask，如果存在则根据其启动模式来进行变化处理 
        computeLaunchingTaskFlags();
		//处理调用方的任务栈信息。通过mSourceRecord获取到调用方的任务栈。如果调用方已经finish了，那么启动一个newtask任务栈来作为调用方任务栈
        computeSourceStack();
		//设置启动flag
        mIntent.setFlags(mLaunchFlags);
		//查找可以复用的activityrecord，比如singleTop，singleTask等，都是可以进行复用的，不能每次都创建新的实例。
        ActivityRecord reusedActivity = getReusableIntentActivity();

        mSupervisor.getLaunchParamsController().calculate(reusedActivity != null ? reusedActivity.getTaskRecord() : mInTask,r.info.windowLayout, r, sourceRecord, options, PHASE_BOUNDS, mLaunchParams);
		//设置显示屏幕id
        mPreferredDisplayId =mLaunchParams.hasPreferredDisplay() ? mLaunchParams.mPreferredDisplayId: DEFAULT_DISPLAY;
        if (r.isActivityTypeHome() && !mRootActivityContainer.canStartHomeOnDisplay(r.info,mPreferredDisplayId, true /* allowInstrumenting */)) {
            Slog.w(TAG, "Cannot launch home on display " + mPreferredDisplayId);
            return START_CANCELED;
        }

        if (reusedActivity != null) {
			//**重点关注内容**   存在可复用的resueActivity
            ....
        }

        //这里对packageName为空做处理，直接返回调用出错
        if (mStartActivity.packageName == null) {
            final ActivityStack sourceStack = mStartActivity.resultTo != null? mStartActivity.resultTo.getActivityStack() : null;
            if (sourceStack != null) {
                //如果知道调用方的信息，那么
                sourceStack.sendActivityResultLocked(-1 /* callingUid */, mStartActivity.resultTo,mStartActivity.resultWho, mStartActivity.requestCode, RESULT_CANCELED, null /* data */);
            }
            ActivityOptions.abort(mOptions);
            return START_CLASS_NOT_FOUND;
        }

        // If the activity being launched is the same as the one currently at the top, then
        // we need to check if it should only be launched once.
        //如果要启动的activity和当前栈顶的activity是一样的，安么我们需要检测是否只需要启动一次
        final ActivityStack topStack = mRootActivityContainer.getTopDisplayFocusedStack();
        final ActivityRecord topFocused = topStack.getTopActivity();
        final ActivityRecord top = topStack.topRunningNonDelayedActivityLocked(mNotTop);
        //是否需要启动新的标记为
        final boolean dontStart = top != null && mStartActivity.resultTo == null
                && top.mActivityComponent.equals(mStartActivity.mActivityComponent)
                && top.mUserId == mStartActivity.mUserId
                && top.attachedToProcess()
                && ((mLaunchFlags & FLAG_ACTIVITY_SINGLE_TOP) != 0
                || isLaunchModeOneOf(LAUNCH_SINGLE_TOP, LAUNCH_SINGLE_TASK))
                // This allows home activity to automatically launch on secondary display when
                // display added, if home was the top activity on default display, instead of
                // sending new intent to the home activity on default display.
                && (!top.isActivityTypeHome() || top.getDisplayId() == mPreferredDisplayId);
        if (dontStart) {//不需要重新启动，那么使用复用逻辑，将当前activity显示到前端即可
            // For paranoia, make sure we have correctly resumed the top activity.
            topStack.mLastPausedActivity = null;
            if (mDoResume) {
                mRootActivityContainer.resumeFocusedStacksTopActivities();
            }
            ActivityOptions.abort(mOptions);
            if ((mStartFlags & START_FLAG_ONLY_IF_NEEDED) != 0) {
                // We don't need to start a new activity, and the client said not to do
                // anything if that is the case, so this is it!
                return START_RETURN_INTENT_TO_CALLER;
            }

            deliverNewIntent(top);

            // Don't use mStartActivity.task to show the toast. We're not starting a new activity
            // but reusing 'top'. Fields in mStartActivity may not be fully initialized.
            mSupervisor.handleNonResizableTaskIfNeeded(top.getTaskRecord(), preferredWindowingMode,mPreferredDisplayId, topStack);

            return START_DELIVERED_TO_TOP;
        }

        boolean newTask = false;
        final TaskRecord taskToAffiliate = (mLaunchTaskBehind && mSourceRecord != null)? mSourceRecord.getTaskRecord() : null;

        // Should this be considered a new task?
        int result = START_SUCCESS;
        if (mStartActivity.resultTo == null && mInTask == null && !mAddingToTask
                && (mLaunchFlags & FLAG_ACTIVITY_NEW_TASK) != 0) {
            newTask = true;
            result = setTaskFromReuseOrCreateNewTask(taskToAffiliate);
        } else if (mSourceRecord != null) {
            result = setTaskFromSourceRecord();
        } else if (mInTask != null) {
            result = setTaskFromInTask();
        } else {
            // This not being started from an existing activity, and not part of a new task...
            // just put it in the top task, though these days this case should never happen.
            result = setTaskToCurrentTopOrCreateNewTask();
        }
        if (result != START_SUCCESS) {
            return result;
        }

        mService.mUgmInternal.grantUriPermissionFromIntent(mCallingUid, mStartActivity.packageName,
                mIntent, mStartActivity.getUriPermissionsLocked(), mStartActivity.mUserId);
        mService.getPackageManagerInternalLocked().grantEphemeralAccess(
                mStartActivity.mUserId, mIntent, UserHandle.getAppId(mStartActivity.appInfo.uid),
                UserHandle.getAppId(mCallingUid));
        if (newTask) {
            EventLog.writeEvent(EventLogTags.AM_CREATE_TASK, mStartActivity.mUserId,mStartActivity.getTaskRecord().taskId);
        }
        ActivityStack.logStartActivity(EventLogTags.AM_CREATE_ACTIVITY, mStartActivity, mStartActivity.getTaskRecord());
        mTargetStack.mLastPausedActivity = null;

        mRootActivityContainer.sendPowerHintForLaunchStartIfNeeded(
                false /* forceSend */, mStartActivity);

        mTargetStack.startActivityLocked(mStartActivity, topFocused, newTask, mKeepCurTransition,
                mOptions);
        if (mDoResume) {
            final ActivityRecord topTaskActivity =mStartActivity.getTaskRecord().topRunningActivityLocked();
            if (!mTargetStack.isFocusable()|| (topTaskActivity != null && topTaskActivity.mTaskOverlay&& mStartActivity != topTaskActivity)) {
                // If the activity is not focusable, we can't resume it, but still would like to
                // make sure it becomes visible as it starts (this will also trigger entry
                // animation). An example of this are PIP activities.
                // Also, we don't want to resume activities in a task that currently has an overlay
                // as the starting activity just needs to be in the visible paused state until the
                // over is removed.
                mTargetStack.ensureActivitiesVisibleLocked(mStartActivity, 0, !PRESERVE_WINDOWS);
                // Go ahead and tell window manager to execute app transition for this activity
                // since the app transition will not be triggered through the resume channel.
                mTargetStack.getDisplay().mDisplayContent.executeAppTransition();
            } else {
                // If the target stack was not previously focusable (previous top running activity
                // on that stack was not visible) then any prior calls to move the stack to the
                // will not update the focused stack.  If starting the new activity now allows the
                // task stack to be focusable, then ensure that we now update the focused stack
                // accordingly.
                if (mTargetStack.isFocusable()
                        && !mRootActivityContainer.isTopDisplayFocusedStack(mTargetStack)) {
                    mTargetStack.moveToFront("startActivityUnchecked");
                }
                mRootActivityContainer.resumeFocusedStacksTopActivities(
                        mTargetStack, mStartActivity, mOptions);
            }
        } else if (mStartActivity != null) {
            mSupervisor.mRecentTasks.add(mStartActivity.getTaskRecord());
        }
        mRootActivityContainer.updateUserStack(mStartActivity.mUserId, mTargetStack);

        mSupervisor.handleNonResizableTaskIfNeeded(mStartActivity.getTaskRecord(),
                preferredWindowingMode, mPreferredDisplayId, mTargetStack);

        return START_SUCCESS;
    }
```

这几个方法比较长了，有将近900行的代码，我们一个个分析就是了。

#### setInitialState数据初始化

这个函数主要是对启动时的一些数据进行初始化工作。

```java
    //ActivityStarter.java
    private void setInitialState(ActivityRecord r, ActivityOptions options, TaskRecord inTask,
                                 boolean doResume, int startFlags, ActivityRecord sourceRecord,
                                 IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
                                 boolean restrictedBgActivity) {
        reset(false /* clearRequest */);
        //整个参数赋值给ActivityStarter的全局变量，以供之后所有的流程使用
        mStartActivity = r;
        mIntent = r.intent;
        mOptions = options;
        mCallingUid = r.launchedFromUid;
        mSourceRecord = sourceRecord;
        mVoiceSession = voiceSession;
        mVoiceInteractor = voiceInteractor;
        mRestrictedBgActivity = restrictedBgActivity;

        mLaunchParams.reset();
        //根据启动模式计算launchparam
        mSupervisor.getLaunchParamsController().calculate(inTask, r.info.windowLayout, r,sourceRecord, options, PHASE_DISPLAY, mLaunchParams);
		//获取一个显示id。这个DisplayId是我们要启动的activity具体要现实在那个屏幕上。因为android支持多屏幕，但是一般只使用第一个屏幕
        mPreferredDisplayId =mLaunchParams.hasPreferredDisplay() ? mLaunchParams.mPreferredDisplayId: DEFAULT_DISPLAY;

        mLaunchMode = r.launchMode;
		//这里会重新处理启动模式  如果intent中的和mainfest中的冲突，那么manfest的启动模式优先
        mLaunchFlags = adjustLaunchFlagsToDocumentMode(r, LAUNCH_SINGLE_INSTANCE == mLaunchMode,LAUNCH_SINGLE_TASK == mLaunchMode, mIntent.getFlags());
		//FLAG_ACTIVITY_NEW_DOCUMENT是打开一个文件的标识
        mLaunchTaskBehind = r.mLaunchTaskBehind&& !isLaunchModeOneOf(LAUNCH_SINGLE_TASK, LAUNCH_SINGLE_INSTANCE)&& (mLaunchFlags & FLAG_ACTIVITY_NEW_DOCUMENT) != 0;
		//如果是newTask的启动模式，那么会将resultTo设置为null。
		//这里做了一个处理。这个活动被启动到一个新的任务中，而且还需要得到请求结果。那么，这是相当混乱的，因此，立即发送回一个取消，让新的任务继续启动像往常一样，不依赖于它的发起者
		//也就是newTask的启动模式，是无法获取到请求结果的
        sendNewTaskResultRequestIfNeeded();
        ...
        mDoResume = doResume;
        if (!doResume || !r.okToShowLocked()) {
			//当本次不需要resume时，则设置为延迟resume的状态
            r.delayedResume = true;
            mDoResume = false;
        }

        if (mOptions != null) {
            if (mOptions.getLaunchTaskId() != -1 && mOptions.getTaskOverlay()) {
                r.mTaskOverlay = true;
                if (!mOptions.canTaskOverlayResume()) {
                    final TaskRecord task = mRootActivityContainer.anyTaskForId(mOptions.getLaunchTaskId());
                    final ActivityRecord top = task != null ? task.getTopActivity() : null;
                    if (top != null && !top.isState(RESUMED)) {
                        // The caller specifies that we'd like to be avoided to be moved to the
                        // front, so be it!
                        mDoResume = false;
                        mAvoidMoveToFront = true;
                    }
                }
            } else if (mOptions.getAvoidMoveToFront()) {
                mDoResume = false;
                mAvoidMoveToFront = true;
            }
        }
		//如果设置FLAG_ACTIVITY_PREVIOUS_IS_TOP，当前Activity不会作为栈顶来启动新的Activity而是当前Activity的前一个作为栈顶.简而言之，栈ABC启动D则栈变成ABD。所以sourceRecord设置为null
        mNotTop = (mLaunchFlags & FLAG_ACTIVITY_PREVIOUS_IS_TOP) != 0 ? sourceRecord : null;

        mInTask = inTask;
        if (inTask != null && !inTask.inRecents) {
            mInTask = null;
        }
        mStartFlags = startFlags;
        if ((startFlags & START_FLAG_ONLY_IF_NEEDED) != 0) {
            ActivityRecord checkedCaller = sourceRecord;
            if (checkedCaller == null) {
                checkedCaller = mRootActivityContainer.getTopDisplayFocusedStack().topRunningNonDelayedActivityLocked(mNotTop);
            }
            if (!checkedCaller.mActivityComponent.equals(r.mActivityComponent)) {
                // Caller is not the same as launcher, so always needed.
                mStartFlags &= ~START_FLAG_ONLY_IF_NEEDED;
            }
        }
		//是否有动画
        mNoAnimation = (mLaunchFlags & FLAG_ACTIVITY_NO_ANIMATION) != 0;

        if (mRestrictedBgActivity && !mService.isBackgroundActivityStartsEnabled()) {
            mAvoidMoveToFront = true;
            mDoResume = false;
        }
    }
```

在这个方法里面主要对一些数据初始化，在函数刚开始的位置，通过reset，直接将所需要修改的变量进行了重置，然后对变量进行了赋值

1. 通过 **mSupervisor.getLaunchParamsController().calculate** 计算其在屏幕上的展示区域。
2.  **getPreferedDisplayId()** 获取启动的activity所处的屏幕ID，因为可能存在多屏幕（VR等）。
3. 处理启动模式
   1. 处理文件的打开表示
   2. 如果是newTask，则将 **resultTo** 设置为空。
   3. 进行 **FLAG_ACTIVITY_NEW_DOCUMENT** 的文档标识的处理
   4. 进行 **FLAG_ACTIVITY_PREVIOUS_IS_TOP** 启动标志的处理
   5. 记录动画标识

可以看到这个函数内部主要是将一些变量进行赋值工作。

我们跟踪下一个函数**computeLaunchingTaskFlags** 

#### computeLaunchingTaskFlags计算Task的flag标识

这个方法比较重要

```java
    private void computeLaunchingTaskFlags() {
        //知道要指定运行的任务栈，而且mSourceRecord不存在
        if (mSourceRecord == null && mInTask != null && mInTask.getStack() != null) {
			//标记着启动该任务栈时，那个使用的intent
            final Intent baseIntent = mInTask.getBaseIntent();
			//taskRecord由多个activityRecord组成，是我们平时所说的任务栈，里面包含着它所管理的activity列表
			//这里返回第一个没有结束的activity。
            final ActivityRecord root = mInTask.getRootActivity();
            if (baseIntent == null) {
                ActivityOptions.abort(mOptions);
                throw new IllegalArgumentException("Launching into task without base intent: "+ mInTask);
            }

            // If this task is empty, then we are adding the first activity -- it
            // determines the root, and must be launching as a NEW_TASK.
            //如果启动模式是LAUNCH_SINGLE_INSTANCE或者LAUNCH_SINGLE_TASK，那么必须保证堆栈是他们所运行的堆栈，否则就抛出异常
            if (isLaunchModeOneOf(LAUNCH_SINGLE_INSTANCE, LAUNCH_SINGLE_TASK)) {
                if (!baseIntent.getComponent().equals(mStartActivity.intent.getComponent())) {
                    ActivityOptions.abort(mOptions);
                    throw new IllegalArgumentException("Trying to launch singleInstance/Task "+ mStartActivity + " into different task " + mInTask);
                }
                if (root != null) {
                    ActivityOptions.abort(mOptions);
                    throw new IllegalArgumentException("Caller with mInTask " + mInTask + " has root " + root + " but target is singleInstance/Task");
                }
            }

            //如果根部为空，说明里面还没有activity,可以把我们要启动的activity作为它的rootTask启动,所以会对这个task做初始化操作
            if (root == null) {
                final int flagsOfInterest = FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK| FLAG_ACTIVITY_NEW_DOCUMENT | FLAG_ACTIVITY_RETAIN_IN_RECENTS;
                mLaunchFlags = (mLaunchFlags & ~flagsOfInterest)| (baseIntent.getFlags() & flagsOfInterest);
                mIntent.setFlags(mLaunchFlags);
                mInTask.setIntent(mStartActivity);
				//标记是否增加到栈中
                mAddingToTask = true;
            } else if ((mLaunchFlags & FLAG_ACTIVITY_NEW_TASK) != 0) {
            	//当前栈根部不为空，但是启动模式是FLAG_ACTIVITY_NEW_TASK，那么不需要添加新的activity，只要直接把当前task带到前台显示即可。
                mAddingToTask = false;
            } else {
				//不是一个空的task,并且也没有设置FLAG_ACTIVITY_NEW_TASK启动参数,所以需要添加一个activity到这个task中,设置 mAddingToTask = true
                mAddingToTask = true;
            }
			//说明用户指定的task是可用的,设置mReuseTask = mInTask
            mReuseTask = mInTask;
        } else {
			//说明sourceRecord不为空或者用户没有指定mInTask。这种情况就需要设置mInTask为null,因为sourceRecord优先级大于mInTask. 这个条件还对特殊情况做了处理,保证要启动的activity尽量放到SourceRecord 之上
            mInTask = null;
            if ((mStartActivity.isResolverActivity() || mStartActivity.noDisplay) && mSourceRecord != null&& mSourceRecord.inFreeformWindowingMode()) {
                mAddingToTask = true;
            }
        }
		//指定运行的任务栈为空
        if (mInTask == null) {
			//根据调用方和要启动的activty的启动模式来进行调整。将acitivty启动模式调整为为newTask
            if (mSourceRecord == null) {//如果其源任务栈也不存在，无法附加要启动的activity到sourceRecord的task中,
                // This activity is not being started from another...  in this
                // case we -always- start a new task.
                if ((mLaunchFlags & FLAG_ACTIVITY_NEW_TASK) == 0 && mInTask == null) {
                    Slog.w(TAG, "startActivity called from non-Activity context; forcing " +"Intent.FLAG_ACTIVITY_NEW_TASK for: " + mIntent);
                    mLaunchFlags |= FLAG_ACTIVITY_NEW_TASK;
                }
            } else if (mSourceRecord.launchMode == LAUNCH_SINGLE_INSTANCE) {
            	//如果sourceRecord存在但是lunchMode为singleInstance的,这种activity只能自己独自在一个task上,
            	//所以新启动的activity也要添加FLAG_ACTIVITY_NEW_TASK参数,在新的task上启动activity 
                // The original activity who is starting us is running as a single
                // instance...  this new activity it is starting must go on its
                // own task.
                mLaunchFlags |= FLAG_ACTIVITY_NEW_TASK;
            } else if (isLaunchModeOneOf(LAUNCH_SINGLE_INSTANCE, LAUNCH_SINGLE_TASK)) {
				//要启动的activity是LAUNCH_SINGLE_INSTANCE或者LAUNCH_SINGLE_TASK
                // The activity being started is a single instance...  it always
                // gets launched into its own task.
                mLaunchFlags |= FLAG_ACTIVITY_NEW_TASK;
            }
        }
    }
```

这个里面主要进行了两种情况的处理。

1. 如果没有调用者记录信息，但是知道要启动的任务所在的任务栈。
   1. 要使用的任务栈必须有baseIntent。也就是必须有任务栈创建时所使用的intent信息。否则抛异常
   2. 如果启动模式是 **singleInstance** 或者 **singleTask** ，那么要使用的任务栈的根ActivityRecorkd必须为空。而且启动任务栈所使用的Component必须是当前Component。否则扔异常
   3. 如果任务栈的根AcitivityRecord为空，那么其实就是一个新的任务栈，增加启动标识newTask。并且标记mAddingToTask为true
   4. 如果任务栈的根AcitivityRecord不为空，并且启动标识有newTask。那么标记mAddingToTask为false
   5. 如果任务栈的根AcitivityRecord不为空，并且启动标识不为newTask。那么标记mAddingToTask为true
   6. 将要启动的任务栈赋值给可复用的任务栈 **mReuseTask** 。
2. 剩下的情况就是有调用者记录信息或者没有指定mInTask。这种情况直接将指定的mInTask清空。

当对这两种情况处理完以后，会进行一次判断处理，如果指定运行的任务栈为空(包括没有设置，或者后来清空)，那么会分情况对启动标识进行调整：

1. 如果调用方为空，这时候就将启动的acitivity既无法附加到调用方的任务栈中，也没有指定的执行的任务栈，那么这时候直接将其增加newTaks启动标识，在新的任务栈中启动
2. 如果调用方的启动标识位singleInstance，那么说明调用方需要独自在一个任务栈上，要启动的acitivity也无法附加到其任务栈，那么这时候直接将其增加newTaks启动标识，在新的任务栈中启动。
3. 如果启动模式是singTask,或者singleInstance。那么增加newTaks启动标识

可以看到 **computeLaunchingTaskFlags** 的主要功能就是对于启动标识的调整处理。

#### computeSourceStack获取调用方Acitivity栈

```
    private void computeSourceStack () {
		//通过mSourceRecord获取到调用方的Acitivity栈。因为activityRecord持有Acitivity栈信息
        if (mSourceRecord == null) {
            mSourceStack = null;
            return;
        }
        if (!mSourceRecord.finishing) {
            mSourceStack = mSourceRecord.getActivityStack();
            return;
        }

        if ((mLaunchFlags & FLAG_ACTIVITY_NEW_TASK) == 0) {
			//如果调用方已经finish了，那么就无法将其作为我们的源任务栈了，这时候，要强行添加FLAG_ACTIVITY_NEW_TASK标志使activity启动到一个新的task中
            mLaunchFlags |= FLAG_ACTIVITY_NEW_TASK;
			//
            mNewTaskInfo = mSourceRecord.info;

            //保存task的intent信息和taskinfo信息是为了新建task的时候尝试恢复这个task
            final TaskRecord sourceTask = mSourceRecord.getTaskRecord();
            mNewTaskIntent = sourceTask != null ? sourceTask.intent : null;
        }
        mSourceRecord = null;
        mSourceStack = null;
    }
```

可以看到，如果调用方没有结束的话，直接是从ActivityRecord拿到ActivityStack对象。而如果调用方已经结束了，则添加newTask标识来启动新的任务。

#### getReusableIntentActivity 找到可复用Activity

这个方法主要是获取一个能够复用的Activity，一般情况下对于singleTask和singleTop这种栈内唯一的启动模式，ActivityRecord肯定是需要复用的，而不能直接创建新的ActivityRecord。

```java
    private ActivityRecord getReusableIntentActivity() {

        //标识是否可以放入一个已经存在的栈。
        // 判断方法是设置了FLAG_ACTIVITY_NEW_TASK，但是并非MULTIPLE_TASK。或者LAUNCH_SINGLE_INSTANCE或者LAUNCH_SINGLE_TASK模式
        boolean putIntoExistingTask = ((mLaunchFlags & FLAG_ACTIVITY_NEW_TASK) != 0 &&(mLaunchFlags & FLAG_ACTIVITY_MULTIPLE_TASK) == 0)|| isLaunchModeOneOf(LAUNCH_SINGLE_INSTANCE, LAUNCH_SINGLE_TASK);
        //还要保证目标任务栈是空
        putIntoExistingTask &= mInTask == null && mStartActivity.resultTo == null;
        //intentActivity记录可以复用的那个ActivityRecord。
        ActivityRecord intentActivity = null;
        if (mOptions != null && mOptions.getLaunchTaskId() != -1) {
            final TaskRecord task = mRootActivityContainer.anyTaskForId(mOptions.getLaunchTaskId());
            intentActivity = task != null ? task.getTopActivity() : null;
        } else if (putIntoExistingTask) {
			//如果可以复用
            if (LAUNCH_SINGLE_INSTANCE == mLaunchMode) {
				//启动模式是LAUNCH_SINGLE_INSTANCE，那么因为其是一种全局唯一的，需要进行搜索遍历
                intentActivity = mRootActivityContainer.findActivity(mIntent, mStartActivity.info,mStartActivity.isActivityTypeHome());
            } else if ((mLaunchFlags & FLAG_ACTIVITY_LAUNCH_ADJACENT) != 0) {
            	//分屏多窗口模式
                intentActivity = mRootActivityContainer.findActivity(mIntent, mStartActivity.info,!(LAUNCH_SINGLE_TASK == mLaunchMode));
            } else {
				//其他形式，传入当前的主要逻辑显示器id来进行搜索  。mPreferredDisplayId有待研究。情况只是找到合适的task,并不要求task中包含对应的activity。
                intentActivity =mRootActivityContainer.findTask(mStartActivity, mPreferredDisplayId);
            }
        }

        if (intentActivity != null&& (mStartActivity.isActivityTypeHome() || intentActivity.isActivityTypeHome()) && intentActivity.getDisplayId() != mPreferredDisplayId) {
            //不能再其他屏幕上复用桌面Activity
            intentActivity = null;
        }

        return intentActivity;
    }
```

这里面首先根据对应的标志位来判断是否需要进行可复用ActivityRecord的查找。查找的依据是要启动的ActivityRecord是否满足一些启动标识：newTask并且不为multiTask，而且目标任务栈为空。

如果可以进行复用，那么就根据情况进行不同的遍历查找。

1. 启动模式是singleInstance。这种启动模式属于全局唯一的。通过RootActivityContainer的 **findActivity** 方法来查找，参数为目标ActivityRecord是否为ActivityTypeHome的boolean的判断值。
2. 如果启动表示有 **FLAG_ACTIVITY_LAUNCH_ADJACENT** ，也是通过通过RootActivityContainer的 **findActivity** 方法来搜索，参数为是否为singleTask的boolean判断值。
3. 其他情况则调用RootActivityContainer的 **findTask** 方法来查找来搜索。

对于任务栈的查找后面会单独开一张进行分析。这里就不再进行深入讲解了。

#### 可复用reuseActivity不为空

```java
        if (reusedActivity != null) {//存在可复用的resueActivity
            //是否清空可复用的activity上面的标识
            final boolean clearTopAndResetStandardLaunchMode =(mLaunchFlags & (FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))== (FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)&& mLaunchMode == LAUNCH_MULTIPLE;

            //mStartActivity是我们要启动的acitivity
            if (mStartActivity.getTaskRecord() == null && !clearTopAndResetStandardLaunchMode) {
				//如果我们的目标activity没有目标栈。则将复用的栈信息赋值给我们的目标activity
                mStartActivity.setTask(reusedActivity.getTaskRecord());
            }
			...
            // 清除task中复用的activity上面的activity
            if ((mLaunchFlags & FLAG_ACTIVITY_CLEAR_TOP) != 0|| isDocumentLaunchesIntoExisting(mLaunchFlags)|| isLaunchModeOneOf(LAUNCH_SINGLE_INSTANCE, LAUNCH_SINGLE_TASK)) {
				//获取复用的activity的堆栈信息
                final TaskRecord task = reusedActivity.getTaskRecord();
                //执行清除目标activity上面所有的activitys的操作。
                //函数内部如果和mStartActivity相同compoentname的activity的启动模式是默认的ret.launchMode == ActivityInfo.LAUNCH_MULTIPLE，则也会将这个activity销毁
				//对于SingleInstance || SingleTask|| singleTop启动模式的则不会被销毁。
				//对于要启动的activity的启动模式为LAUNCH_MULTIPLE的，performClearTaskForReuseLocked返回值top肯定是空的
				final ActivityRecord top = task.performClearTaskForReuseLocked(mStartActivity,mLaunchFlags);
                if (reusedActivity.getTaskRecord() == null) {
					//重新进行赋值
                    reusedActivity.setTask(task);
                }

                if (top != null) {
                    if (top.frontOfTask) {
                        //如果是任务栈的root activity
                        top.getTaskRecord().setIntent(mStartActivity);
                    }
					//调用onNewIntent方法
                    deliverNewIntent(top);
                }
            }
			
            mRootActivityContainer.sendPowerHintForLaunchStartIfNeeded(false /* forceSend */, reusedActivity);
			//复用的ActivityRecord找到了，为了保证其正确性，就要对TaskRecord做重新处理，把当前的TaskRecord放到最顶部
            reusedActivity = setTargetStackAndMoveToFrontIfNeeded(reusedActivity);

            final ActivityRecord outResult =outActivity != null && outActivity.length > 0 ? outActivity[0] : null;
            if (outResult != null && (outResult.finishing || outResult.noDisplay)) {
				//如果需要返回值，那么设置复用的activity作为启动返回值
                outActivity[0] = reusedActivity;
            }
			//START_FLAG_ONLY_IF_NEEDED这种情况不需要去真的启动activity，只需要使task放到前台就可以了，这种情况多是从桌面点击图标恢复task的情况。                
			if ((mStartFlags & START_FLAG_ONLY_IF_NEEDED) != 0) {
                //resume显示到前台
                resumeTargetStackIfNeeded();
                return START_RETURN_INTENT_TO_CALLER;
            }

            if (reusedActivity != null) {
				//根据复用情况设置task
                setTaskFromIntentActivity(reusedActivity);
				//mAddingToTask为true表示要新建，mReuseTask为空表示task被清除了
                if (!mAddingToTask && mReuseTask == null) {
                    //调用显示到前台
                    resumeTargetStackIfNeeded();

                        outActivity[0] = reusedActivity.finishing? reusedActivity.getTaskRecord().getTopActivity() : reusedActivity;
                    }

                    return mMovedToFront ? START_TASK_TO_FRONT : START_DELIVERED_TO_TOP;
                }
            }
        }
```

当存在可复用的Activity的时候，肯定是需要一些复用的处理逻辑的。而这段代码就是我们复用的逻辑处理的地方。

1. 如果当前的启动模式是LAUNCH_MULTIPLE 而且开启了FLAG_ACTIVITY_CLEAR_TOP和FLAG_ACTIVITY_RESET_TASK_IF_NEEDED标识，那么说明我们需要清空所有可复用的ActivityRecord到栈顶的所有数据。这时候设置了 **clearTopAndResetStandardLaunchMode** 这个标志位。
2. 当打开了FLAG_ACTIVITY_CLEAR_TOP标志位，或者打开了document标志位，或者打开了singleTask / singleInstance说明此时需要清掉TaskRecord的数据。这时候会调用 **performClearTaskForReuseLocked** 方法将目标Activity上面的所有的activity，并将当前activity置顶并返回顶部的ActivityRecord信息。最后调用 **deliverNewIntent** () 方法。

我们这里深入跟踪一下 **performClearTaskForReuseLocked** 方法。

##### **performClearTaskForReuseLocked** 

```java
 //TaskRecord.java
	ActivityRecord performClearTaskForReuseLocked(ActivityRecord newR, int launchFlags) {
        mReuseTask = true;
        //真正的执行代码
        final ActivityRecord result = performClearTaskLocked(newR, launchFlags);
        mReuseTask = false;
        return result;
    }

    final ActivityRecord performClearTaskLocked(ActivityRecord newR, int launchFlags) {
        int numActivities = mActivities.size();
        //从顶部开始遍历
        for (int activityNdx = numActivities - 1; activityNdx >= 0; --activityNdx) {
            ActivityRecord r = mActivities.get(activityNdx);
            if (r.finishing) {
                continue;
            }
            if (r.mActivityComponent.equals(newR.mActivityComponent)) {
				//获取到了目标activity，那么所有位于它上面的目标都需要结束
                final ActivityRecord ret = r;
                //查找到以后从当前位置开始朝顶部开始遍历关闭
                for (++activityNdx; activityNdx < numActivities; ++activityNdx) {
                    r = mActivities.get(activityNdx);
                    if (r.finishing) {
                        continue;
                    }
				   ...
                    if (mStack != null && mStack.finishActivityLocked(r, Activity.RESULT_CANCELED, null, "clear-task-stack", false)) {
                        --activityNdx;
                        --numActivities;
                    }
                }

                //如果要启动的activity是multi模式，那么也会调用finish结束掉
                if (ret.launchMode == ActivityInfo.LAUNCH_MULTIPLE&& (launchFlags & Intent.FLAG_ACTIVITY_SINGLE_TOP) == 0&& !ActivityStarter.isDocumentLaunchesIntoExisting(launchFlags)) {
                    if (!ret.finishing) {
                        if (mStack != null) {
                            mStack.finishActivityLocked(ret, Activity.RESULT_CANCELED, null, "clear-task-top", false);
                        }
                        return null;
                    }
                }

                return ret;
            }
        }

        return null;
    }

```

这里的 **mActivities** 是当前的任务栈中所保存的ActivityRecord的列表。进行遍历清除的方法为：

1. 从mActivities的尾部开始遍历，查找到被复用的Activity
2. 找到被复用的Activity以后，往尾部一次循环，调用ActivityStack的 **finishActivityLocked** 方法来结束掉对应的Activity，并减少引用数。

可以看到最后的位置是有一个特殊的处理的，也就是SingleTop标识打开的话，也会结束掉当前这个复用的Activity。并且返回null，如果这个返回了null的话，就不会调用复用的activity的 **deliverNewIntent** 方法，相当于会重新启动。

经过上面两步以后，不管是否进行了栈顶数据的清除。接下来就要将我们可以复用的Activity所在的TaskRecord移动到其所在的ActivityStack的顶部。

##### setTargetStackAndMoveToFrontIfNeeded

这个方法属于重中之重了，它展现了如何将我们启动的Activity通过堆栈的各种处理，展现在我们的面前。

```java
    private ActivityRecord setTargetStackAndMoveToFrontIfNeeded(ActivityRecord intentActivity) {
        //获取到其所在的ActivityStack
        mTargetStack = intentActivity.getActivityStack();
        mTargetStack.mLastPausedActivity = null;
        //标记位，标记当前顶部的栈是否与我们所复用的activity所在的栈不同
        final boolean differentTopTask;
        if (mPreferredDisplayId == mTargetStack.mDisplayId) {
			//获取当前屏幕栈顶的ActivityStack
            final ActivityStack focusStack = mTargetStack.getDisplay().getFocusedStack();
			//获取当前正在显示的activityRecord
            final ActivityRecord curTop = (focusStack == null)? null : focusStack.topRunningNonDelayedActivityLocked(mNotTop);
			//当前正在显示的ActivityRecord所属的TaskRecord
            final TaskRecord topTask = curTop != null ? curTop.getTaskRecord() : null;
			//判断顶部的栈是否符合要求(即判断现在栈顶的栈是否为能够复用的activityrecord所在的栈)
            differentTopTask = topTask != intentActivity.getTaskRecord()|| (focusStack != null && topTask != focusStack.topTask());
        } else {
            //表明要复用的task与正在显示的信息不在同一个栈中
            differentTopTask = true;
        }
        //如果当前栈顶的任务栈并不是我们可以复用的activity所在的任务栈，那么就需要将activity所在的任务栈移动到顶部（前面）
        if (differentTopTask && !mAvoidMoveToFront) {
			//增加一个标记，标识这个task是从任务栈的后面移动上来的
            mStartActivity.intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
            //判断合法性
            if (mSourceRecord == null || (mSourceStack.getTopActivity() != null &&mSourceStack.getTopActivity().getTaskRecord()== mSourceRecord.getTaskRecord())) {
                //willclearTask表明是否同时使用了 NEW_TASK 和 CLEAR_TASK的flag
                final boolean willClearTask =(mLaunchFlags & (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK))== (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
                if (!willClearTask) {//不需要清空，那么就需要将复用的task移至栈顶
                	//****重点方法****获取当前要启动activity所属的ActivityStack栈
                    final ActivityStack launchStack = getLaunchStack(mStartActivity, mLaunchFlags, mStartActivity.getTaskRecord(), mOptions);
                    //获取当前要启动activity所属的任务栈
                    final TaskRecord intentTask = intentActivity.getTaskRecord();
                    if (launchStack == null || launchStack == mTargetStack) {
                        //当要启动的栈与目标一致，或者要启动的栈为空。这是我们一般的标准流程。会调用moveTaskToFrontLocked方法，将当前栈移动到与用户交互的栈顶
                        mTargetStack.moveTaskToFrontLocked(intentTask, mNoAnimation, mOptions,mStartActivity.appTimeTracker, "bringingFoundTaskToFront");
                        mMovedToFront = true;
                    } else if (launchStack.inSplitScreenWindowingMode()) {
						//分屏模式下，
                        ...
                    }
                    mOptions = null;
		...
         mTargetStack = intentActivity.getActivityStack();
        mSupervisor.handleNonResizableTaskIfNeeded(intentActivity.getTaskRecord(),WINDOWING_MODE_UNDEFINED, DEFAULT_DISPLAY, mTargetStack);
        if ((mLaunchFlags & FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) != 0) {
            return mTargetStack.resetTaskIfNeededLocked(intentActivity, mStartActivity);
        }
        return intentActivity;
    }
```

我们先说下这个方法的大体流程，然后再逐个展开。

1. 从当前正在和用户交互的ActivityStack中找到正在和用户交互的ActivityRecord。同时找到其所在的任务栈（TaskRecord）。

2. 当发现当前栈顶的TaskRecord和我们要启动的Activity所使用的TaskRecord不是同一个时，这时候如果设置的标志位不会清空栈顶的信息的话，需要将要目标TaskRecord移动到栈顶位置。但是这个移动也需要分情况来进行。

   1. 首先通过 **getLaunchStack** 方法获取目标ActivityStcak信息intentTask。
   2. 这时候会比较我们要启动的ActivityStack和当前复用的ActivityRecord所对应的ActivityStack作比较。
      * 当要启动的栈（launchStack）与目标（mTargetStack）一致，或者要启动的栈为空。则调用 **moveTaskToFrontLocked** 将对应的TaskRecord移动到栈顶位置。
      * 当要启动的栈（launchStack）为分屏模式。
      * 要启动的栈displayId和目标栈的ActivityRecord不一致
      * 要启动的栈是Home，而当前的ActivityRecord不是。
   
   
   对于上述的不同情况，2-3会通过 **reparent()** ，将复用的Activity所在的Task迁移到 **lauchStack** 中。而1和4则直接使用 **moveTaskToFrontLocked** 将对应的TaskRecord移动到栈顶位置。

我们这里分析一下 **moveTaskToFrontLocked** 方法。

```java
    //ActivityStack.java
	//将任务栈（TaskRecord）移动到当前ActivityStack的栈顶位置
    final void moveTaskToFrontLocked(TaskRecord tr, boolean noAnimation, ActivityOptions options, AppTimeTracker timeTracker, String reason) {
        ...
        //获取到ActivityStack的栈顶activity
        final ActivityRecord topActivity = topStack != null ? topStack.getTopActivity() : null;
        //从taskHistory中查找到要移动到用户交互栈顶的TaskRecord（tr）
        final int numTasks = mTaskHistory.size();
        final int index = mTaskHistory.indexOf(tr);
        if (numTasks == 0 || index < 0) {
            //找不到则立刻返回
            if (noAnimation) {
                ActivityOptions.abort(options);
            } else {
                updateTransitLocked(TRANSIT_TASK_TO_FRONT, options);
            }
            return;
        }
		...
            //***重点关注**  将tr插入到mTaskHistory顶部
            insertTaskAtTop(tr, null);
		   ...
            //获取到ActivityStack中顶部正在运行的Activity
            final ActivityRecord r = topRunningActivityLocked();
            if (r != null) {
                //**重点关注**   将ActivityRecord移动到栈顶，并且设置可见，而且为FocusedStacks
                r.moveFocusableActivityToTop(reason);
            }
		   ...
            //调用持有焦点的任务栈的顶部Activity的onResume()方法
            mRootActivityContainer.resumeFocusedStacksTopActivities();
        } finally {
            getDisplay().continueUpdateImeTarget();
        }
    }

```

   将任务栈移动到ActivityStack顶部只需要三步。

1. 在AcitivityStack中寻找到对应的TaskRecord，然后将其移动到AcitivityRecord的顶部位置，
2. 寻找到ActivityStack中顶部正在运行的Activity，然后将其移动到Top位置并设置其持有焦点
3. 调用持有焦点的任务栈的顶部Activity的onResume()方法

回到我们的**setTargetStackAndMoveToFrontIfNeeded** 方法，当上面的操作执行完毕以后能够将TaskRecord移动到顶部，还需要将ActivityStack也移动到顶部位置，这样才能展示在用户的面前。这时候调用 **handleNonResizableTaskIfNeeded** 将其所在的ActivityStack也移动到顶部。

到现在为止，已经将我们的可以复用的ActivityRecord的TaskRecord和ActivityStack的移动到交互栈顶。这时候就会根据实际的情况将可复用的Activity信息，进行一些整理工作。

##### setTaskFromIntentActivity

##### ```

```java
 private void setTaskFromIntentActivity(ActivityRecord intentActivity) {
     if ((mLaunchFlags & (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK))== (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK)) {
         //设置了FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK标志位，那么mReuseTask设置为可以复用的intentActivity的任务栈
         final TaskRecord task = intentActivity.getTaskRecord();
         task.performClearTaskLocked();
         mReuseTask = task;
         mReuseTask.setIntent(mStartActivity);
     } else if ((mLaunchFlags & FLAG_ACTIVITY_CLEAR_TOP) != 0|| isLaunchModeOneOf(LAUNCH_SINGLE_INSTANCE, LAUNCH_SINGLE_TASK)) {
//清空栈，这里跟之前的操作相似，但是那里处理的是top不为空，这里处理的是top为空的情况，也就是launchMode == ActivityInfo.LAUNCH_MULTIPLE
         ....
     } else if (mStartActivity.mActivityComponent.equals(intentActivity.getTaskRecord().realActivity)) {//任务栈顶部的activity和要启动的activity是同一个。
         if (((mLaunchFlags & FLAG_ACTIVITY_SINGLE_TOP) != 0|| LAUNCH_SINGLE_TOP == mLaunchMode)&& intentActivity.mActivityComponent.equals(mStartActivity.mActivityComponent)) {
             //如果是sigleTop，那么就调用deliverNewIntent
             if (intentActivity.frontOfTask) {//如果是栈的根activity，那么设置
                 intentActivity.getTaskRecord().setIntent(mStartActivity);
             }
             deliverNewIntent(intentActivity);
         } else if (!intentActivity.getTaskRecord().isSameIntentFilter(mStartActivity)) {
             //如果不是singleTop，那么认为是需要启动一个新的activity，
             mAddingToTask = true;
             mSourceRecord = intentActivity;
         }
     } else if ((mLaunchFlags & FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) == 0) {
         // 对FLAG_ACTIVITY_RESET_TASK_IF_NEEDED标志出处理　，这个主要用于快捷图标和或者从通知启动，这种情况需要替换task最上面的activity，所以需要添加activity到task中，
         mAddingToTask = true;
         mSourceRecord = intentActivity;
     } else if (!intentActivity.getTaskRecord().rootWasReset) {
         intentActivity.getTaskRecord().setIntent(mStartActivity);
     }
 }
```

这里根据使用的情况进行了分类。

* 设置了FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK标志位，那么mReuseTask设置为可以复用的intentActivity的任务栈。
* 如果清空了栈时，如果可复用的activity也销毁了（clearTop 而且使用了singleInstance或者singleTask的一种），会拿到顶部的Task，然后重新添加到ActivityStack中
* 如果要启动的Activity和当前顶部的Activity是同一个，会根据情况调用onNewIntent方法。

到这里所有的可复用的Activity的逻辑都处理完成了。



* 

学习到的知识点：

1. 启动模式如果intent中的和mainfest中的冲突，那么manfest的启动模式优先。
2. **newIntent**启动模式是无法获取result结果的。

扩展知识点

1. 一个**ActivityRecord**对应一个**Activity**，保存了一个**Activity**的所有信息;但是一个**Activity**可能会有多个**ActivityRecord**,因为**Activity**可以被多次启动，这个主要取决于其启动模式。
2. 一个**TaskRecord**由一个或者多个**ActivityRecord**组成，这就是我们常说的任务栈，具有后进先出的特点。
3. **ActivityStack**则是用来管理**TaskRecord**的，包含了多个**TaskRecord**。
4. **singleTask** 和 **singleTop** 模式的都会去任务栈中遍历寻找是否已经启动了相应实例
5. **ActivityStackSupervisor** 用来管理 **ActivityStack** 的。
6. APP与 **ActivityStack** 之间并无必然的联系。有可能是一个APP对应一个 **ActivityStack** ，有可能是一个APP对应多个 **ActivityStack** ，也有可能是多个APP共用一个 **ActivityStack** 。
7. **ActivityDisplay** 表示一个屏幕，Android支持三种屏幕：主屏幕，外接屏幕（HDMI等），虚拟屏幕（投屏）。一般情况下，即只有主屏幕时， **ActivityStackSupervisor** 与 **ActivityDisplay** 都是系统唯一。 **ActivityDisplay** 持有着当前屏幕的 **ActivityStack** 列表信息。
8. **RootActivityContainer** 则是持有着屏幕 **ActivityDisplay** 信息。用来分担ActivityStackSupervisor的部分职责的，主要目的是使ActivityContainer的结构和WindowContainer的结构保持一致。
9. ![image-20200408150308700](http://cdn.qiniu.kailaisii.com/typora/202004/08/153428-322821.png)

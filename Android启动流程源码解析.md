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

        //这个方法只能从startActivity调用，这个方法就是几种启动模式的，在栈内的清空处理
        private int startActivityUnchecked(final ActivityRecord r, ActivityRecord sourceRecord,
                                           IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor,
                                           int startFlags, boolean doResume, ActivityOptions options, TaskRecord inTask,
                                           ActivityRecord[] outActivity, boolean restrictedBgActivity) {
            //初始化一些状态，这里主要是根据启动模式的相关设置进行了一些变量的处理。比如newtask，document等等
            //初始化Activity启动状态，获取launchmode flag 同时解决一些falg和launchmode的冲突
            setInitialState(r, options, inTask, doResume, startFlags, sourceRecord, voiceSession,voiceInteractor, restrictedBgActivity);
    		//获取显示windows的样式
            final int preferredWindowingMode = mLaunchParams.mWindowingMode;
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
    
            // Do not start home activity if it cannot be launched on preferred display. We are not
            // doing this in ActivityStackSupervisor#canPlaceEntityOnDisplay because it might
            // fallback to launch on other displays.
            if (r.isActivityTypeHome() && !mRootActivityContainer.canStartHomeOnDisplay(r.info,mPreferredDisplayId, true /* allowInstrumenting */)) {
                Slog.w(TAG, "Cannot launch home on display " + mPreferredDisplayId);
                return START_CANCELED;
            }
    
            if (reusedActivity != null) {
    			//存在可复用的resueActivity
                // When the flags NEW_TASK and CLEAR_TASK are set, then the task gets reused but
                // still needs to be a lock task mode violation since the task gets cleared out and
                // the device would otherwise leave the locked task.
                if (mService.getLockTaskController().isLockTaskModeViolation(reusedActivity.getTaskRecord(),(mLaunchFlags & (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK))== (FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK))) {
                    Slog.e(TAG, "startActivityUnchecked: Attempt to violate Lock Task Mode");
                    return START_RETURN_LOCK_TASK_MODE_VIOLATION;
                }
    
                // True if we are clearing top and resetting of a standard (default) launch mode
                // ({@code LAUNCH_MULTIPLE}) activity. The existing activity will be finished.
                //是否清空可复用的activity上面的表示
                final boolean clearTopAndResetStandardLaunchMode =(mLaunchFlags & (FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))== (FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)&& mLaunchMode == LAUNCH_MULTIPLE;
    
                // If mStartActivity does not have a task associated with it, associate it with the
                // reused activity's task. Do not do so if we're clearing top and resetting for a
                // standard launchMode activity.
                //mStartActivity是我们要启动的acitivity
                if (mStartActivity.getTaskRecord() == null && !clearTopAndResetStandardLaunchMode) {
    				//如果我们的目标activity没有目标栈。则将复用的栈信息赋值给我们的目标activity
                    mStartActivity.setTask(reusedActivity.getTaskRecord());
                }
    
                if (reusedActivity.getTaskRecord().intent == null) {
                    // This task was started because of movement of the activity based on affinity...
                    // Now that we are actually launching it, we can assign the base intent.
                    reusedActivity.getTaskRecord().setIntent(mStartActivity);
                } else {
                	//FLAG_ACTIVITY_TASK_ON_HOME :把当前新启动的任务置于Home任务之上，也就是按back键从这个任务返回的时候会回到home，即使这个不是他们最后看见的activity。注意这个标记必须和FLAG_ACTIVITY_NEW_TASK一起使用。
                    final boolean taskOnHome =(mStartActivity.intent.getFlags() & FLAG_ACTIVITY_TASK_ON_HOME) != 0;
                    if (taskOnHome) {
                        reusedActivity.getTaskRecord().intent.addFlags(FLAG_ACTIVITY_TASK_ON_HOME);
                    } else {
                        reusedActivity.getTaskRecord().intent.removeFlags(FLAG_ACTIVITY_TASK_ON_HOME);
                    }
                }
    
                // This code path leads to delivering a new intent, we want to make sure we schedule it
                // as the first operation, in case the activity will be resumed as a result of later
                // operations.
                // 清除task中复用的activity上面的activity
                if ((mLaunchFlags & FLAG_ACTIVITY_CLEAR_TOP) != 0|| isDocumentLaunchesIntoExisting(mLaunchFlags)|| isLaunchModeOneOf(LAUNCH_SINGLE_INSTANCE, LAUNCH_SINGLE_TASK)) {
    				//获取复用的activity的堆栈信息
                    final TaskRecord task = reusedActivity.getTaskRecord();
    
                    // In this situation we want to remove all activities from the task up to the one
                    // being started. In most cases this means we are resetting the task to its initial
                    // state.
                    //执行清除目标activity上面所有的activitys的操作。
                    //函数内部如果和mStartActivity相同compoentname的activity的启动模式是默认的ret.launchMode == ActivityInfo.LAUNCH_MULTIPLE，则也会将这个activity销毁
    				//对于SingleInstance || SingleTask|| singleTop启动模式的则不会被销毁。
    				//对于要启动的activity的启动模式为LAUNCH_MULTIPLE的，performClearTaskForReuseLocked返回值top肯定是空的
    				final ActivityRecord top = task.performClearTaskForReuseLocked(mStartActivity,mLaunchFlags);
    
                    // The above code can remove {@code reusedActivity} from the task, leading to the
                    // the {@code ActivityRecord} removing its reference to the {@code TaskRecord}. The
                    // task reference is needed in the call below to
                    // {@link setTargetStackAndMoveToFrontIfNeeded}.
                    if (reusedActivity.getTaskRecord() == null) {
    					//重新进行赋值
                        reusedActivity.setTask(task);
                    }
    
                    if (top != null) {
                        if (top.frontOfTask) {
                            //如果是任务栈的root activity
                            // Activity aliases may mean we use different intents for the top activity,
                            // so make sure the task now has the identity of the new intent.
                            top.getTaskRecord().setIntent(mStartActivity);
                        }
    					//调用onNewIntent方法
                        deliverNewIntent(top);
                    }
                }
    			
                mRootActivityContainer.sendPowerHintForLaunchStartIfNeeded(false /* forceSend */, reusedActivity);
    			//复用的task所在的stack设置为fourceStack并且把复用的task拿到栈顶
                reusedActivity = setTargetStackAndMoveToFrontIfNeeded(reusedActivity);
    
                final ActivityRecord outResult =outActivity != null && outActivity.length > 0 ? outActivity[0] : null;
    
                // When there is a reused activity and the current result is a trampoline activity,
                // set the reused activity as the result.
                if (outResult != null && (outResult.finishing || outResult.noDisplay)) {
    				//如果需要返回值，那么设置复用的activity作为启动返回值
                    outActivity[0] = reusedActivity;
                }
    			//START_FLAG_ONLY_IF_NEEDED这种情况不需要去真的启动activity，只需要使task放到前台就可以了，这种情况多是从桌面点击图标恢复task的情况。                
    			if ((mStartFlags & START_FLAG_ONLY_IF_NEEDED) != 0) {
    				 // We don't need to start a new activity, and the client said not to do anything
                    // if that is the case, so this is it!  And for paranoia, make sure we have
                    // correctly resumed the top activity.
                    //resume显示到前台
                    resumeTargetStackIfNeeded();
                    return START_RETURN_INTENT_TO_CALLER;
                }
    
                if (reusedActivity != null) {
    				//根据复用情况设置task
                    setTaskFromIntentActivity(reusedActivity);
    				//mAddingToTask为true表示要新建，mReuseTask为空表示task被清除了
                    if (!mAddingToTask && mReuseTask == null) {
                        // We didn't do anything...  but it was needed (a.k.a., client don't use that
                        // intent!)  And for paranoia, make sure we have correctly resumed the top activity.
                        //调用显示到前台
                        resumeTargetStackIfNeeded();
                        if (outActivity != null && outActivity.length > 0) {
                            // The reusedActivity could be finishing, for example of starting an
                            // activity with FLAG_ACTIVITY_CLEAR_TOP flag. In that case, return the
                            // top running activity in the task instead.
                            outActivity[0] = reusedActivity.finishing? reusedActivity.getTaskRecord().getTopActivity() : reusedActivity;
                        }
    
                        return mMovedToFront ? START_TASK_TO_FRONT : START_DELIVERED_TO_TOP;
                    }
                }
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





学习到的知识点：

1. 启动模式如果intent中的和mainfest中的冲突，那么manfest的启动模式优先
2. **newIntent**启动模式是无法获取result结果的
3. 一个**ActivityRecord**对应一个**Activity**，保存了一个**Activity**的所有信息;但是一个**Activity**可能会有多个**ActivityRecord**,因为**Activity**可以被多次启动，这个主要取决于其启动模式。
4. 一个**TaskRecord**由一个或者多个**ActivityRecord**组成，这就是我们常说的任务栈，具有后进先出的特点。
5. **ActivityStack**则是用来管理**TaskRecord**的，包含了多个**TaskRecord**。
6. **singleTask** 和 **singleTop** 模式的都会去任务栈中遍历寻找是否已经启动了相应实例
7. **ActivityStackSupervisor** 用来管理 **ActivityStack** 的。
8. APP与 **ActivityStack** 之间并无必然的联系。有可能是一个APP对应一个 **ActivityStack** ，有可能是一个APP对应多个 **ActivityStack** ，也有可能是多个APP共用一个 **ActivityStack** 。
9. **ActivityDisplay** 表示一个屏幕，Android支持三种屏幕：主屏幕，外接屏幕（HDMI等），虚拟屏幕（投屏）。一般情况下，即只有主屏幕时， **ActivityStackSupervisor** 与 **ActivityDisplay** 都是系统唯一。 **ActivityDisplay** 持有着当前屏幕的 **ActivityStack** 列表信息。
10.  **RootActivityContainer** 则是持有者屏幕 **ActivityDisplay** 信息。用来分担ActivityStackSupervisor的部分职责的，主要目的是使ActivityContainer的结构和WindowContainer的结构保持一致。
11. ![image-20200408150308700](http://cdn.qiniu.kailaisii.com/typora/202004/08/153428-322821.png)

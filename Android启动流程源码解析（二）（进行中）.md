## Android启动流程源码解析（二）

在之前的源码分析中，我们最后遗留下来一个问题。那就是我们的Activity的启动是啥时候处理的啊？上万行的分析，也没看到我们想要的**onCreate**啥的。其实就是**resumeFocusedStacksTopActivities**方法，所以我们这篇文章就从这个方法开始。

### resumeFocusedStacksTopActivities

```java
   boolean resumeFocusedStacksTopActivities() {
        return resumeFocusedStacksTopActivities(null, null, null);
    }

    boolean resumeFocusedStacksTopActivities(ActivityStack targetStack, ActivityRecord target, ActivityOptions targetOptions) {
        boolean result = false;
        if (targetStack != null && (targetStack.isTopStackOnDisplay()|| getTopDisplayFocusedStack() == targetStack)) {
			//******重点方法******如果当前的activitystack正好处于屏幕的顶部，那么直接调用将target设置到顶部显示
            result = targetStack.resumeTopActivityUncheckedLocked(target, targetOptions);
        }

        for (int displayNdx = mActivityDisplays.size() - 1; displayNdx >= 0; --displayNdx) {
            //标记是否已经显示在屏幕上
            boolean resumedOnDisplay = false;
            final ActivityDisplay display = mActivityDisplays.get(displayNdx);
            for (int stackNdx = display.getChildCount() - 1; stackNdx >= 0; --stackNdx) {
                final ActivityStack stack = display.getChildAt(stackNdx);
                //获取到当前ActivityStack顶部正在运行的Activity
                final ActivityRecord topRunningActivity = stack.topRunningActivityLocked();
                if (!stack.isFocusableAndVisible() || topRunningActivity == null) {
                    continue;
                }
                if (stack == targetStack) {
                    //上面已经做过resume处理了，所以这里我们就不再做处理了
                    resumedOnDisplay |= result;
                    continue;
                }
                if (display.isTopStack(stack) && topRunningActivity.isState(RESUMED)) {
                    stack.executeAppTransition(targetOptions);
                } else {
                    resumedOnDisplay |= topRunningActivity.makeActiveIfNeeded(target);
                }
            }
            //如果仍然没有显示在屏幕上，那么就获取到屏幕当前持有焦点的ActivityStack，然后将activity显示在上面
            if (!resumedOnDisplay) {
                final ActivityStack focusedStack = display.getFocusedStack();
                if (focusedStack != null) {
                    focusedStack.resumeTopActivityUncheckedLocked(target, targetOptions);
                }
            }
        }
        return result;
    }
```

这里我们只需要关注一个方法 **resumeTopActivityUncheckedLocked** ，这个方法也特别长，我们就拆分开，只关注重点方法即可

```java
    boolean resumeTopActivityUncheckedLocked(ActivityRecord prev, ActivityOptions options) {
        if (mInResumeTopActivity) {
            // Don't even start recursing.
            return false;
        }
        boolean result = false;
        try {
            // Protect against recursion.
            mInResumeTopActivity = true;
            //***重点关注******
            result = resumeTopActivityInnerLocked(prev, options);
            final ActivityRecord next = topRunningActivityLocked(true /* focusableOnly */);
            if (next == null || !next.canTurnScreenOn()) {
                checkReadyForSleep();
            }
        } finally {
            mInResumeTopActivity = false;
        }

        return result;
    }
```

这里我们也只关注**resumeTopActivityInnerLocked**方法

```java
    private boolean resumeTopActivityInnerLocked(ActivityRecord prev, ActivityOptions options) {
       ...	
       if (mResumedActivity != null) {
            //****重点方法 ****** 调用acitivity的pause方法
            pausing |= startPausingLocked(userLeaving, false, next, false);
        }
        ...
        //进行activity的创建处理
        mStackSupervisor.startSpecificActivityLocked(next, true, false);
        ...
	}
```

代码也很长，我们只提取了两个比较重要的函数，一个是调用**onPause**生命周期函数，另一个是调用**onCreate**生命周期函数的。

我们首先来看一下是如何一步步通过调度来执行**onPause**的生命周期调度的

```java
//ActivityStack.java
	final boolean startPausingLocked(boolean userLeaving, boolean uiSleeping, ActivityRecord resuming, boolean pauseImmediately) {
		...
		//当前正在显示的Activity
        ActivityRecord prev = mResumedActivity;
        //当前正在显示的Activity需要执行暂停操作了。
        //将其赋值给mPausingActivity成员变量。
        mPausingActivity = prev;
        mLastPausedActivity = prev;
        //Activity绑定了对应的APP？难道有不绑定的情况么？
        if (prev.attachedToProcess()) {
			   ...	
                //******重点方法****
 mService.getLifecycleManager().scheduleTransaction(prev.app.getThread(),prev.appToken, PauseActivityItem.obtain(prev.finishing, userLeaving,prev.configChangeFlags, pauseImmediately));
            ...

```

重点方法已经标注出来了。我们先看看它的参数的创建过程。

##### PauseActivityItem.obtain

```java
//PauseActivityItem.java
	//从池中取出一个PauseActivityItem类
    public static PauseActivityItem obtain(boolean finished, boolean userLeaving, int configChanges, boolean dontReport) {
        PauseActivityItem instance = ObjectPool.obtain(PauseActivityItem.class);
        if (instance == null) {
            instance = new PauseActivityItem();
        }
        instance.mFinished = finished;
        instance.mUserLeaving = userLeaving;
        instance.mConfigChanges = configChanges;
        instance.mDontReport = dontReport;
        return instance;
    }
//ObjectPool.java
    public static <T extends ObjectPoolItem> T obtain(Class<T> itemClass) {
        synchronized (sPoolSync) {
            @SuppressWarnings("unchecked")
            final ArrayList<T> itemPool = (ArrayList<T>) sPoolMap.get(itemClass);
            if (itemPool != null && !itemPool.isEmpty()) {
                return itemPool.remove(itemPool.size() - 1);
            }
            return null;
        }
    }
```

可以看到，对于**PauseActivityItem**的获取，是通过**享元模式** 来进行处理的。

回到主干。

这的mService是**ActivityTaskManagerService**，**getLifecycleManager**方法返回的是**ClientLifecycleManager**对象。

```java
    private final ClientLifecycleManager mLifecycleManager;
    mLifecycleManager = new ClientLifecycleManager();
    ClientLifecycleManager getLifecycleManager() {
        return mLifecycleManager;
    }
```

##### scheduleTransaction

```java
//ClientLifecycleManager.java
    //调用一次生命周期的调度请求
    void scheduleTransaction(@NonNull IApplicationThread client, @NonNull IBinder activityToken,@NonNull ActivityLifecycleItem stateRequest) throws RemoteException {
        final ClientTransaction clientTransaction = transactionWithState(client, activityToken,stateRequest);
        scheduleTransaction(clientTransaction);
    }
    //实际的调度方法
    void scheduleTransaction(ClientTransaction transaction) throws RemoteException {
        final IApplicationThread client = transaction.getClient();
        transaction.schedule();
        if (!(client instanceof Binder)) {
            //回收
            transaction.recycle();
        }
    }
    //创建一个持有状态的事务类
    private static ClientTransaction transactionWithState(@NonNull IApplicationThread client,
            @NonNull IBinder activityToken, @NonNull ActivityLifecycleItem stateRequest) {
        final ClientTransaction clientTransaction = ClientTransaction.obtain(client, activityToken);
        clientTransaction.setLifecycleStateRequest(stateRequest);
        return clientTransaction;
    }

```

这里最终会调用ClientTransaction对象的**schedule**方法。

```java
//ClientTransaction.java
	public void schedule() throws RemoteException {
        mClient.scheduleTransaction(this);
    }
```

这里的mClient是一个**IApplicationThread**，其Server端是**ActivityThread#ApplicationThread**。所以最终调用的是**ApplicationThread**的**scheduleTransaction**方法

```java
//ActivityThread.java
	public void scheduleTransaction(ClientTransaction transaction) throws RemoteException {
            //会调用ActivityThread.scheduleTransaction方法->该方法位于ActivityThread的父类中
            ActivityThread.this.scheduleTransaction(transaction);
        }

//ClientTransactionHandler.java
	void scheduleTransaction(ClientTransaction transaction) {
        //执行预处理
        transaction.preExecute(this);
        //通过Handler机制发送事务请求
        sendMessage(ActivityThread.H.EXECUTE_TRANSACTION, transaction);
    }
```

Handler消息机制就不贴出来了，直接看其是怎么处理的。

```java
//ActivityThread.java
    private final TransactionExecutor mTransactionExecutor = new TransactionExecutor(this);

			case EXECUTE_TRANSACTION://执行生命周期的调度工作
                    final ClientTransaction transaction = (ClientTransaction) msg.obj;
                    mTransactionExecutor.execute(transaction);

```

当接收到Handler以后，会调用**TransactionExecutor**的**execute()**方法。

```java
    public void execute(ClientTransaction transaction) {
        final IBinder token = transaction.getActivityToken();
        ...
        //循环遍历回调请求的所有状态，并在适当的时间执行它们
        executeCallbacks(transaction);
        //执行生命周期的改变
        executeLifecycleState(transaction);
    }
```

 ClientTransaction存在两种事务,

* 一种是通过setLifecycleStateRequest设置一个对象的事务类型，用于表示事务执行以后，客户端应该处于的生命周期状态
* 一种是addCallback，增加对客户端的事务类型回调，对客户端一系列的回调。

这两个不同的类型，在这里就会存在不同的处理方法。对于第一种会在**executeCallbacks**中进行处理，第二种则会在**executeLifecycleState**中进行处理。

我们这儿的暂停，是通过第二种来进行设置的，所以我们直接看**executeLifecycleState**这个方法。

```java
    //如果事务请求，则转换到最终状态
    private void executeLifecycleState(ClientTransaction transaction) {
        // ActivityStackSupervisor.java中进行了这个设置
        // final ActivityLifecycleItem lifecycleItem;
        //                if (andResume) {
        //                    lifecycleItem = ResumeActivityItem.obtain(dc.isNextTransitionForward());
        //                } else {
        //                    lifecycleItem = PauseActivityItem.obtain();
        //                }
        //                clientTransaction.setLifecycleStateRequest(lifecycleItem);
        //所以这里的lifecycleItem可能是ResumeActivityItem或者PauseActivityItem或者其他的生命周期相关类
        final ActivityLifecycleItem lifecycleItem = transaction.getLifecycleStateRequest();
        if (lifecycleItem == null) {
            //如果不是通过setLifecycleStateRequest设置的，那么该方法不需要处理，直接返回即可
            return;
        }
        //使用适当的参数执行最后的转换
        lifecycleItem.execute(mTransactionHandler, token, mPendingActions);
        lifecycleItem.postExecute(mTransactionHandler, token, mPendingActions);
    }
```

我们这里的**lifecycleItem**是我们刚才创建的**PauseActivityItem**，这里会执行其**execute**方法。


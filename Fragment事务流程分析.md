# Fragment事务流程分析

## 简言

简单的事务使用流程代码

```java
getSupportFragmentManager().beginTransaction().add(R.id.fl, ftMain).commit();
```

使用的方法很简单，但是**Activity**是如何实现事务的管理的呢？

我们先上一个简单的类图

![img](https://upload-images.jianshu.io/upload_images/714670-d6e25abd75cd0541.png?imageMogr2/auto-orient/strip|imageView2/2/format/webp)

这个里面**FragementManagerImpl**持有**mHost**，一开始不知道是如何设置的，后来才发现是通过**attachHost**方法进行设置的。

我们一步步去分析这个UML的视图是如何建立的

从**getSupportFragmentManager()**来看一下是如何获取的

## getSupportFragmentManager()

```java
// FragmentActivity.java
  final FragmentController mFragments = FragmentController.createController(new HostCallbacks());
   public FragmentManager getSupportFragmentManager() {
        return mFragments.getSupportFragmentManager();
    }
```

```java
//FragmentController.java
	//实际是HostCallbacks对象。
    private final FragmentHostCallback<?> mHost;

    public static final FragmentController createController(FragmentHostCallback<?> callbacks) {
        return new FragmentController(callbacks);
    }

    private FragmentController(FragmentHostCallback<?> callbacks) {
        mHost = callbacks;
    }

	public FragmentManager getSupportFragmentManager() {
        return mHost.getFragmentManagerImpl();
    }
```

这里可以看到我们的Activity持有**FragmentControl**对象。而**FragmentControl**持有了**HostCallbacks**对象，而**HostCallbacks**是继承自**FragmentHostCallback**的。

```java
    //FragmentHostCallback.java
	final FragmentManagerImpl mFragmentManager = new FragmentManagerImpl();
    FragmentManagerImpl getFragmentManagerImpl() {
        return mFragmentManager;
    }
```

可以看到这里**HostCallback**是持有**FragmenManagerImpl**的。而**FragmenManagerImpl**继承**FragmentManager**

所以其实我们的第一步骤getSupportFragmentManager()是获取到了一个**FragmenManagerImpl**对象。

## beginTransaction

```java
//FragmentManager.java
public FragmentTransaction beginTransaction() {
    return new BackStackRecord(this);
}
```

可以看到这里创建了一个**BackStackRecord**对象，对象持有了**FragmentManager**。这里的**BackStackRecord**是继承自**FragmentTransaction**类的。

到这里我们是获取获取到了一个事务记录类。也就是**BackStackRecord**

## add

当有了事务 记录类之后，就可以进行各种事务的记录信息，add,remove,replace等等。我们这里只是跟踪我们的测试代码，其他的是有相同的处理机制。

```java
//BackStackRecord.java
public FragmentTransaction add(int containerViewId, Fragment fragment) {
    doAddOp(containerViewId, fragment, null, OP_ADD);
    return this;
}

public FragmentTransaction add(int containerViewId, Fragment fragment, String tag) {
    doAddOp(containerViewId, fragment, tag, OP_ADD);
    return this;
}

private void doAddOp(int containerViewId, Fragment fragment, String tag, int opcmd) {
    fragment.mFragmentManager = mManager;
    //一系列检测
    ...
	//将当前的操作添加到队列中
    addOp(new Op(opcmd, fragment));
}
//增加到处理队列中
void addOp(Op op) {
    mOps.add(op);
    //动画的处理
    op.enterAnim = mEnterAnim;
    op.exitAnim = mExitAnim;
    op.popEnterAnim = mPopEnterAnim;
    op.popExitAnim = mPopExitAnim;
}
```
可以看到，其实BackStackRecord内部维护了一个要执行的队列，当进行事务的提交时，肯定是需要挨个将这个队列进行去除执行的。

## commit

现在我们已经准备好了一个队列了，当所有的事务内部要执行的操作处理完后，回通过**commit()**或者**commitNowAllowingStateLoss()**,**commitNow()**来进行提交。我们这里跟踪**commit()**的执行，剩下的留以后再慢慢分析。

```java
//BackStackRecord.java
    public int commit() {
        return commitInternal(false);
    }
    int commitInternal(boolean allowStateLoss) {
    	//防止重复提交
        if (mCommitted) {
            throw new IllegalStateException("commit already called");
        }
        ...
        mCommitted = true;
        if (mAddToBackStack) {//如果执行了addToBackStack,mAddToBackStack才会为true
            mIndex = mManager.allocBackStackIndex(this);
        } else {
            mIndex = -1;
        }
		//调用manager的入队操作
        mManager.enqueueAction(this, allowStateLoss);
        return mIndex;
    }
```
commit()主要执行了入队的操作。

```java
#FragmentManager.java
    public void enqueueAction(OpGenerator action, boolean allowStateLoss) {
        if (!allowStateLoss) {
            checkStateLoss();
        }
        //加锁
        synchronized (this) {
            ...
            mPendingActions.add(action);
            scheduleCommit();
        }
    }
    private void scheduleCommit() {
        synchronized (this) {
            ...
			//这里的mHost是Fragment中的HostCallBack,这里的getHandler获取到的是主线程Handler
            mHost.getHandler().removeCallbacks(mExecCommit);
            mHost.getHandler().post(mExecCommit);
        }
    }
    
    Runnable mExecCommit = new Runnable() {
        @Override
        public void run() {
            execPendingActions();
        }
    };
```
所以这里的是将要执行的BackStackRecord的记录信息存放到了mPendingActions队列之中，然后通过主线程的Looper机制来进行调用执行**execPendingActions()**方法。

### execPendingActions

```java
#FragmentManager.java
//只能从主线程调用
public boolean execPendingActions() {
	//进行一些线程的检测，是否销毁等的检测操作
    ensureExecReady(true);
    boolean didSomething = false;
	//generateOpsForPendingActions会将我们的PendingActions中的数据取出来，然后存放到临时的mTmpRecords中
    while (generateOpsForPendingActions(mTmpRecords, mTmpIsPop)) {
        mExecutingActions = true;
        try {
            removeRedundantOperationsAndExecute(mTmpRecords, mTmpIsPop);
        } finally {
            cleanupExec();
        }
        didSomething = true;
    }

    doPendingDeferredStart();
    burpActive();

    return didSomething;
}
```
我们看一下**generateOpsForPendingActions**是如何将数据获取并存放到临时list中的

```java
#FragmentManager.java
    //遍历循环，将我们的PendingActions中的数据取出来，然后存放到临时的mTmpRecords队列中
    private boolean generateOpsForPendingActions(ArrayList<BackStackRecord> records,ArrayList<Boolean> isPop) {
    boolean didSomething = false;
    synchronized (this) {
        final int numActions = mPendingActions.size();
        for (int i = 0; i < numActions; i++) {
            didSomething |= mPendingActions.get(i).generateOps(records, isPop);
        }
        mPendingActions.clear();
        //当队列中的数据已经保存到临时的records中以后，移除回调
        mHost.getHandler().removeCallbacks(mExecCommit);
    }
    return didSomething;
}
```
所以是通过循环，调用了**generateOps**方法

```java
//BackStackRecord.java
    public boolean generateOps(ArrayList<BackStackRecord> records, ArrayList<Boolean> isRecordPop) {
        if (FragmentManagerImpl.DEBUG) {
            Log.v(TAG, "Run: " + this);
        }
		//直接添加到list中
        records.add(this);
		//记录当前是非pop操作
        isRecordPop.add(false);
        if (mAddToBackStack) {//记录是否是入栈操作
            mManager.addBackStackState(this);
        }
        return true;
    }
```
其实这里面的存放到临时list中的操作相对来说比较简单。我们回到主线去看看将数据存放到临时list中以后，又对这些事务做了什么操作。也就是**removeRedundantOperationsAndExecute()**函数的作用。

### removeRedundantOperationsAndExecute

```java
//FragmentManager.java
//这里进行了事务的优化操作，事务的最终执行也由executeOpsTogether来执行的
    private void removeRedundantOperationsAndExecute(ArrayList<BackStackRecord> records, ArrayList<Boolean> isRecordPop) {

        //完成以前被推迟但现在已经准备好的事务的执行。
        executePostponedTransaction(records, isRecordPop);
        final int numRecords = records.size();
        int startIndex = 0;
        for (int recordNum = 0; recordNum < numRecords; recordNum++) {
			//是否允许重新排序
            final boolean canReorder = records.get(recordNum).mReorderingAllowed;
            if (!canReorder) {//如果不允许重排序，那么会获取这个位置之后的连续不允许排序的队列然后批量执行
                //执行之前的所有操作
                if (startIndex != recordNum) {
                    //如果不允许重排序，那么就将之前的所有的操作批量执行
                    executeOpsTogether(records, isRecordPop, startIndex, recordNum);
                }
                //reorderingEnd会从当前不能重排序的位置开始，遍历搜索，寻找到连续的不能重排序的pop操作的位置
                //然后将recordNum到reorderingEnd的位置进行批量执行
                int reorderingEnd = recordNum + 1;
                if (isRecordPop.get(recordNum)) {
                    //当前是个pop操作，那么会一直搜索到下一个非pop的位置，然后记录。然后会将这些所有的pop操作批量执行
                    while (reorderingEnd < numRecords && isRecordPop.get(reorderingEnd) && !records.get(reorderingEnd).mReorderingAllowed) {
                        //是pop操作，不允许排序
                        reorderingEnd++;
                    }
                }
                executeOpsTogether(records, isRecordPop, recordNum, reorderingEnd);
                //设置下一个批量执行的起始位置
                startIndex = reorderingEnd;
                recordNum = reorderingEnd - 1;
            }
        }
        if (startIndex != numRecords) {
            executeOpsTogether(records, isRecordPop, startIndex, numRecords);
        }
    }
```
这里，会将我们的队列进行切割拆分，将连续的允许重新排序和不允许重新排序的分别切割出来，然后通过**executeOpsTogether**对队列进行一个批量的操作。我们看看如何批量处理的。

    //执行BackStackRecords列表的一个子集集合，所有这些子集合要么都允许重新排序，要么都不允许排序。
    private void executeOpsTogether(ArrayList<BackStackRecord> records, ArrayList<Boolean> isRecordPop, int startIndex, int endIndex) {
        //获取当前批次是否允许重排序
        final boolean allowReordering = records.get(startIndex).mReorderingAllowed;
        boolean addToBackStack = false;
        if (mTmpAddedFragments == null) {
            mTmpAddedFragments = new ArrayList<>();
        } else {
            mTmpAddedFragments.clear();
        }
        //保存已经添加的fragment临时快照
        mTmpAddedFragments.addAll(mAdded);
        //获取当前栈顶的fragment
        Fragment oldPrimaryNav = getPrimaryNavigationFragment();
        for (int recordNum = startIndex; recordNum < endIndex; recordNum++) {
            //获取事务
            final BackStackRecord record = records.get(recordNum);
            //判断事务是否为pop
            final boolean isPop = isRecordPop.get(recordNum);
            if (!isPop) {//重点方法   不是pop，那么就对事务中的操作进行优化
                oldPrimaryNav = record.expandOps(mTmpAddedFragments, oldPrimaryNav);
            } else {//如果是pop操作，那么就执行反向操作。也就是add的反向操作和remove的反向操作
                record.trackAddedFragmentsInPop(mTmpAddedFragments);
            }
            addToBackStack = addToBackStack || record.mAddToBackStack;
        }
    	//这里直接clear了是什么鬼？难道只是为了进行优化用的临时表？？
        mTmpAddedFragments.clear();
        if (!allowReordering) {//不允许排序
        	//开始转换
            FragmentTransition.startTransitions(this, records, isRecordPop, startIndex, endIndex,
                    false);
        }
    	//重点方法  ，最重要的地方。是commit操作的最终执行的地方
        executeOps(records, isRecordPop, startIndex, endIndex);
        int postponeIndex = endIndex;
        if (allowReordering) {
            ArraySet<Fragment> addedFragments = new ArraySet<>();
            addAddedFragments(addedFragments);
            postponeIndex = postponePostponableTransactions(records, isRecordPop,startIndex, endIndex, addedFragments);
            makeRemovedFragmentsInvisible(addedFragments);
        }
    
        if (postponeIndex != startIndex && allowReordering) {
            // need to run something now
            FragmentTransition.startTransitions(this, records, isRecordPop, startIndex,
                    postponeIndex, true);
            moveToState(mCurState, true);
        }
    
        for (int recordNum = startIndex; recordNum < endIndex; recordNum++) {
            final BackStackRecord record = records.get(recordNum);
            final boolean isPop = isRecordPop.get(recordNum);
            if (isPop && record.mIndex >= 0) {
                freeBackStackIndex(record.mIndex);
                record.mIndex = -1;
            }
            record.runOnCommitRunnables();
        }
    
        if (addToBackStack) {
            reportBackStackChanged();
        }
    }
这个方法里面有两个需要关注的重点。

1. **expandOps()**函数，对执行的事务会进行一个优化。
2. **executeOps()**函数，用来执行最终的**commit**操作，将fragment和Activity进行关联绑定以及生命周期的同步。

这里我们可以简单看一下**expandOps()**函数中优化的逻辑。

    //BackStackRecords.java
    //对事务中的操作进行优化处理
    Fragment expandOps(ArrayList<Fragment> added, Fragment oldPrimaryNav) {
        for (int opNum = 0; opNum < mOps.size(); opNum++) {
            final Op op = mOps.get(opNum);
            switch (op.cmd) {
                case OP_ADD:
                case OP_ATTACH://如果是add或者attach，将当前fragment放入到added中
                    added.add(op.fragment);
                    break;
                case OP_REMOVE:
                case OP_DETACH: {//如果是移除操作，那么从added队列中移除fragment
                    added.remove(op.fragment);
                    if (op.fragment == oldPrimaryNav) {//如果移除的fragment是当前的oldPrimaryNav，需要做一个特殊处理，这里有点不太明白，以后再研究
                        mOps.add(opNum, new Op(OP_UNSET_PRIMARY_NAV, op.fragment));
                        opNum++;
                        oldPrimaryNav = null;
                    }
                }
                break;
                case OP_REPLACE: {//如果是replace操作，那么就将对应的mContainerId中的fragment移除，然后再添加当前的fragment
                    final Fragment f = op.fragment;
                    final int containerId = f.mContainerId;//获取到当前fragment占用的containerId信息
                    boolean alreadyAdded = false;
                    for (int i = added.size() - 1; i >= 0; i--) {
                        final Fragment old = added.get(i);
                        if (old.mContainerId == containerId) {//如果已经添加的fragment的containerId和当前containerId是相同的，那么说明其占用的是同一个containerId，需要将added里面的移除
                            if (old == f) {//如果这个fragment已经在added列表中，存在，设置一个标记位。
                                alreadyAdded = true;
                            } else {//将所有其他使用这个containerId的fragment都执行一个remove操作。这里是通过向mOps中增加新的操作来进行处理的，而不是直接移除。是因为在遍历列表的时候，不能执行移除操作
                                // This is duplicated from above since we only make
                                // a single pass for expanding ops. Unset any outgoing primary nav.
                                if (old == oldPrimaryNav) {
                                    mOps.add(opNum, new Op(OP_UNSET_PRIMARY_NAV, old));
                                    opNum++;
                                    oldPrimaryNav = null;
                                }
                                final Op removeOp = new Op(OP_REMOVE, old);
                                removeOp.enterAnim = op.enterAnim;
                                removeOp.popEnterAnim = op.popEnterAnim;
                                removeOp.exitAnim = op.exitAnim;
                                removeOp.popExitAnim = op.popExitAnim;
                                mOps.add(opNum, removeOp);
                                added.remove(old);
                                opNum++;
                            }
                        }
                    }
                    if (alreadyAdded) {//表明这个opNum位置的fragment已经在add列表中存在了，那么其实不需要再执行这个replace命令了，直接将这个replace操作移除掉，这样可以达到优化效果
                        mOps.remove(opNum);
                        opNum--;
                    } else {//如果不存在，证明这个fragment之前是不存在的，需要对它执行变为一个add操作
                        op.cmd = OP_ADD;
                        added.add(f);
                    }
                }
                break;
                case OP_SET_PRIMARY_NAV: {
                    // It's ok if this is null, that means we will restore to no active
                    // primary navigation fragment on a pop.
                    mOps.add(opNum, new Op(OP_UNSET_PRIMARY_NAV, oldPrimaryNav));
                    opNum++;
                    // Will be set by the OP_SET_PRIMARY_NAV we inserted before when run
                    oldPrimaryNav = op.fragment;
                }
                break;
            }
        }
        return oldPrimaryNav;
    }
这里注释很详细，那么我们回到主线，看一下**executeOps()**函数的执行。

```java
 //FragmentManager.java
 //执行操作
private static void executeOps(ArrayList<BackStackRecord> records,ArrayList<Boolean> isRecordPop, int startIndex, int endIndex) {
    for (int i = startIndex; i < endIndex; i++) {
        final BackStackRecord record = records.get(i);
        final boolean isPop = isRecordPop.get(i);
        if (isPop) {//如果是pop操作
        	//所有的管理的fragment后面的堆栈计数器-1
            record.bumpBackStackNesting(-1);
            // Only execute the add operations at the end of
            // all transactions.
            boolean moveToState = i == (endIndex - 1);
			//执行pop操作
            record.executePopOps(moveToState);
        } else {//如果是非pop操作
        	//所有的管理的fragment后面的堆栈计数器+1
            record.bumpBackStackNesting(1);
			//执行ops操作
            record.executeOps();
        }
    }
}
```
这里先对fragment中的表明后面的片段计数器进行了更新。然后根据是否是pop，分别执行了**executePopOps**和**executeOps**方法，我们这里只先跟踪一下**executeOps()**这个方法的执行。

```java
//BackStackRecords.java
//执行事务包含的操作，只有不允许优化的时候，fragment的状态才会被改变
void executeOps() {
    final int numOps = mOps.size();
    for (int opNum = 0; opNum < numOps; opNum++) {
        final Op op = mOps.get(opNum);
        final Fragment f = op.fragment;
        if (f != null) {//设置动画
            f.setNextTransition(mTransition, mTransitionStyle);
        }
        switch (op.cmd) {//通过manager来进行fragment的管理工作
            case OP_ADD:
                f.setNextAnim(op.enterAnim);
                mManager.addFragment(f, false);//这里我们队addFragment来进行一个跟踪处理
                break;
            case OP_REMOVE:
                f.setNextAnim(op.exitAnim);
                mManager.removeFragment(f);
                break;
            case OP_HIDE:
                f.setNextAnim(op.exitAnim);
                mManager.hideFragment(f);
                break;
            case OP_SHOW:
                f.setNextAnim(op.enterAnim);
                mManager.showFragment(f);
                break;
            case OP_DETACH:
                f.setNextAnim(op.exitAnim);
                mManager.detachFragment(f);
                break;
            case OP_ATTACH:
                f.setNextAnim(op.enterAnim);
                mManager.attachFragment(f);
                break;
            case OP_SET_PRIMARY_NAV:
                mManager.setPrimaryNavigationFragment(f);
                break;
            case OP_UNSET_PRIMARY_NAV:
                mManager.setPrimaryNavigationFragment(null);
                break;
            default:
                throw new IllegalArgumentException("Unknown cmd: " + op.cmd);
        }
        if (!mReorderingAllowed && op.cmd != OP_ADD && f != null) {
            mManager.moveFragmentToExpectedState(f);
        }
    }
    if (!mReorderingAllowed) {
        // Added fragments are added at the end to comply with prior behavior.
        //对FragmentManager所有管理的fragment进行一次生命周期的同步
        mManager.moveToState(mManager.mCurState, true);
    }
}
```
这里是通过FragmentManager来对事务中的fragment来进行了管理，然后最后通过状态的同步，将本次事务的所有变化的fragment的生命周期和绑定的activity的生命周期进行一次同步。

## 总结

每一次学习都要有新的收获，这次通过学习fragment的事务执行的方法学到了新的知识点

1. 事务的提交并不是直接就执行，而是通过主线程的Handler机制来执行的。我们经常遇到的add去显示的时候，有一个isAdd的崩溃，哪怕我们先进行了isAdd的判断，也拦截不住，就是因为Handler执行时，从队列取出消息，如果这时候还没有执行，那么isAdd就仍然是空。遇到过一种解决方案就是glide的，在add之前，将创建的fragment存放到缓存中，然后commit之后，再发送一个handler消息，从缓存中移除即可。
2. 事务的执行是有优化的，会将一些能够优化的地方进行处理。
3. 对于fragment的管理，是通过FragmentManager来统一进行的。
4. 当有多个事务时，会将事务按照是否能够优化和是否为pop来进行分批，进行分批处理。
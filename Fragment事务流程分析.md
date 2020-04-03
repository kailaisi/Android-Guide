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


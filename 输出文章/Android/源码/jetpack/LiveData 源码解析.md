## LiveData 源码解析

之前做过一篇关于[Lifecycle的源码解析](https://mp.weixin.qq.com/s/lbPbqMeVMtJaTbqo55-UXQ)，里面分析了

1. 生命周期拥有者如何进行生命周期的感知（通过Fragment）
2. 当生命周期变化时，如何进行进行通知：将Obsever进行包装，生成LifecycleEventObserver的具体实现来，然后在生命周期变化时，调用其对应的状态的分发。

在通常进行使用的过程中，我们都是将数据通过 **LiveData** 进行一层包装，然后就可以进行其数据的变化监听了，那么其具体是如何实现的呢？

惯例，先来个简单的测试demo

```java
object SplashViewModel{
    var logined = MutableLiveData<Boolean>()
    init {
        logined.postValue(true)
    }
}

class SplashActivity : BaseBindingActivity<ActivitySplashBinding, SplashViewModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SplashViewModel.logined.observe(this, Observer { print(it)})
    }
}
```

只需要一个简单的 **observe()** 方法，就可以实现生命周期的监听，然后将数据发送到我们的Activity中，我们看看这个方法里面到底做了什么

```java
public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
    assertMainThread("observe");
    if (owner.getLifecycle().getCurrentState() == DESTROYED) {
        // 页面销毁，直接返回
        return;
    }
    //包装
    LifecycleBoundObserver wrapper = new LifecycleBoundObserver(owner, observer);
    ObserverWrapper existing = mObservers.putIfAbsent(observer, wrapper);
    if (existing != null && !existing.isAttachedTo(owner)) {
        //如果已经存在，抛异常
        throw new IllegalArgumentException("Cannot add the same observer with different lifecycles");
    }
    if (existing != null) {
        return;
    }
    //增加一个监听者
    owner.getLifecycle().addObserver(wrapper);
}
```

可以看到，只是将我们的生命周期拥有者和监听者进行了一次包装，生成了 **LifecycleBoundObserver** 类，然后将它添加到监听者列表中。

在之前的[Lifecycle的源码解析](https://mp.weixin.qq.com/s/lbPbqMeVMtJaTbqo55-UXQ)文章中，我们了解到，当页面发生变化时，会调用监听者的 **onStateChanged()** 方法。

```java
        @Override
        boolean shouldBeActive() {//判断当前页面是否属于激活状态（即可见状态）
            return mOwner.getLifecycle().getCurrentState().isAtLeast(STARTED);
        }

        @Override
        public void onStateChanged(@NonNull LifecycleOwner source,
                                   @NonNull Lifecycle.Event event) {
            //如果页面销毁了，则直接移除当前对应的监听者
            if (mOwner.getLifecycle().getCurrentState() == DESTROYED) {
                removeObserver(mObserver);
                return;
            }
            //进行状态的变更
            activeStateChanged(shouldBeActive());
        }

```

所以当界面的生命周期变化时，会调用 **activeStateChanged()** 来进行状态的变更处理

```java
        //进行状态的转变
        void activeStateChanged(boolean newActive) {
            if (newActive == mActive) {
                return;
            }
            mActive = newActive;
            boolean wasInactive = LiveData.this.mActiveCount == 0;
            //LiveData的激活的观察者数量进行变化
            LiveData.this.mActiveCount += mActive ? 1 : -1;
            if (wasInactive && mActive) {
                //原来没有激活的观察者，现在有了新增的
                // 说明LiveData从无激活的观察者->有激活的观察者
                onActive();//留下钩子，给继承者使用
            }
            if (LiveData.this.mActiveCount == 0 && !mActive) {
                //当前页面未激活，并且变化后，LiveData中处于激活状态的观察者数量为0，
                // 说明LiveData从有激活的观察者->无激活的观察者
                onInactive();//留下钩子，给继承者使用
            }
            if (mActive) {//如果页面变化为了激活状态，那么进行数据的分发
                dispatchingValue(this);
            }
        }
    }
```

这里主要根据页面的激活数，预留了两个钩子函数，用户可以做一些自己的数据处理。最主要的还是 **dispatchingValue()** 中的数据处理。

```java
    //分发数据
    void dispatchingValue(@Nullable ObserverWrapper initiator) {
        if (mDispatchingValue) {
            //如果正在分发，则将mDispatchInvalidated置为true，那么在分发过程中，会根据这个标志位重新新数据的分发
            mDispatchInvalidated = true;
            return;
        }
        //标记正在进行数据的分发
        mDispatchingValue = true;
        do {
            mDispatchInvalidated = false;
            if (initiator != null) {//如果有对应的监听者，直接分发给对应的监听者
                considerNotify(initiator);
                initiator = null;
            } else {
                //遍历所有的观察者，然后进行数据的分发，
                // 如果分发过程中，发现mDispatchInvalidated变化了，那么说明有新的数据变更，则退出当前混选，然后从新分发新的数据
                for (Iterator<Map.Entry<Observer<? super T>, ObserverWrapper>> iterator =
                     mObservers.iteratorWithAdditions(); iterator.hasNext(); ) {
                    considerNotify(iterator.next().getValue());
                    if (mDispatchInvalidated) {
                        break;
                    }
                }
            }
        } while (mDispatchInvalidated);
        mDispatchingValue = false;
    }
    
    //通知某个观察者进行了数据的变化
    private void considerNotify(ObserverWrapper observer) {
        //观察者未激活，返回
        if (!observer.mActive) {
            return;
        }
        //观察者当前状态为激活，但是当前变为了不可见状态，那么调用
        //activeStateChanged方法
        if (!observer.shouldBeActive()) {
            observer.activeStateChanged(false);
            return;
        }
        //如果数据版本已经是最新的了，那么直接返回
        if (observer.mLastVersion >= mVersion) {
            return;
        }
        //修改数据版本号
        observer.mLastVersion = mVersion;
        //调用监听者的onChanged方法
        observer.mObserver.onChanged((T) mData);
    }
```

在数据分发过程中，根据相应的观察者数据版本号，然后和当前的数据的版本号进行比较，如果是新的数据，那么调用观察者的 **onChange()**方法，也就是我们在开始时写的测试demo中的 **print(it)** 。

总结一下页面发生变化时，数据的处理流程：

1. 当页面发生变化，从不可见变为可见时，会将LiveData中的数据版本号跟对应的观察者中的版本号进行比较，如果大，则调用onChanged()进行数据的回调。
2. 如果页面为不可见，那么不会进行数据的回调处理。



那么当我们使用  **setValue()** ，或者 **postValue()** 时，**LiveData** 又是做了什么处理呢？

我们先看看 **setValue()** 

```java
    protected void setValue(T value) {
        assertMainThread("setValue");
        //记录当前数据的版本号
        mVersion++;
        //记录设置的数据值
        mData = value;
        //进行数据的分发
        dispatchingValue(null);
    }
```

可以看到，直接将数据版本号+1，然后进行了数据的分发，**dispatchingValue()** 我们刚才进行过分析，如果参数为null，那么会遍历所有的监听者，逐个通知所有观察者进行了数据的变化（前提是观察者处于激活状态）。

我们再看看 **postValue()** 

```java
     protected void postValue(T value) {
        boolean postTask;
        synchronized (mDataLock) {
            postTask = mPendingData == NOT_SET;
            mPendingData = value;
        }
        if (!postTask) {
            return;
        }
        //通过线程池分发到主线程去处理
        ArchTaskExecutor.getInstance().postToMainThread(mPostValueRunnable);
    }
    
 	private final Runnable mPostValueRunnable = new Runnable() {
        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            Object newValue;
            synchronized (mDataLock) {
                newValue = mPendingData;
                mPendingData = NOT_SET;
            }
            setValue((T) newValue);
        }
    };
```

可以看到， **postValue()** 通过线程池技术，将数据在主线程进行了 setValue()。

### 汇总

1.当生命周期不可见时，会将最新的数据保存在LiveData中，然后保存相应的版本号，当其可见时，会将数据变化通知
2.当LiveData中的数据变化时，会遍历所有的监听页面，然后进行数据的变化通知。

附带一张`ObserverWrapper` 的结构图

![image-20200304141507165](http://cdn.qiniu.kailaisii.com/typora/20200304141509-851647.png)
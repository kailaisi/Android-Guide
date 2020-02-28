## Lifecycle源码解析

自从谷歌发布 **Android Architecture Components** 架构组件之后，基本所有的项目都在慢慢的像这种模式来进行转化，结合 **DataBinding** 用起来简直爽的起飞。在这个框架中，最基础的应该非 **Lifecycle** 莫属了，它能够自动感知生命周期状态的变化，从而避免了之前在 **MVP** 经常遇到的页面销毁后，回调导致的NEP问题。今天就对**Lifecycle** 下手，分析一下它是如何做到生命感知的。

按照惯例，先来个简单的使用案例

```
    class MyObserver : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        fun connectListener() {
            ...
        }
        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        fun disconnectListener() {
            ...
        }
    }
    
    class MyActivity : AppCompatActivity() {
        private lateinit var myObserver: MyObserver
        override fun onCreate(...) {
            getLifecycle().addObserver(MyObserver())
        }
    }
```

通过这种方式，我们就可以通过 **MyObserver** 中来监听 **Activity** 中相关生命周期的变化，从而自动的来进行一些事件的处理。

那么这种对生命周期自动感知是如何实现的呢？其实就是我们本次要讲的 **Lifecycle**

### 源码

![image-20200227101601756](http://cdn.qiniu.kailaisii.com/typora/20200227101606-253876.png)

查看一下我们的 **Activity** 继承关系，发现实现了 **LifecycleOwner** 接口，从命名上来看，就特别清晰，也就是生命周期的拥有者。

我们可以看下接口的方法

```
public interface LifecycleOwner {
    //返回了提供者的生命周期
    Lifecycle getLifecycle();
}
```

接口只有一个方法，就是获取提供者的生命周期，这个实现可能就很多了，有 **AppCompatActivity** 、**DialogFragment** 、**Fragment** 、**FragmentActivity**  等等（这里的具体实现，是和android的版本有关的，我们这里使用的androidx1.1.0）。

![image-20200227102415133](http://cdn.qiniu.kailaisii.com/typora/20200227102417-868501.png)

这里我们不过多讨论每个的实现，只是跟踪我们的测试代码中的 **AppCompatActivity** 。我们看一下它的接口实现是在父类 **ComponentActivity** 中来实现的。

```
private final LifecycleRegistry mLifecycleRegistry = new LifecycleRegistry(this);

public Lifecycle getLifecycle() {
    return mLifecycleRegistry;
}
```

可以看到，返回的是一个 **LifecycleRegistry** 类，这个类是一个 **Lifecycle(生命周期)** 的子类。先看看 **Lifecycle** 

    public abstract class Lifecycle {
    @MainThread
    public abstract void addObserver(@NonNull LifecycleObserver observer);
    
    @MainThread
    public abstract void removeObserver(@NonNull LifecycleObserver observer);
    
    @MainThread
    @NonNull
    
    public abstract State getCurrentState();
    
    @SuppressWarnings("WeakerAccess")
    public enum Event {
        ON_CREATE,
        ON_START,
        ON_RESUME,
        ON_PAUSE,
        ON_STOP,
        ON_DESTROY,
        ON_ANY
    }
    
    /**
     * 生命周期的状态
     */
    @SuppressWarnings("WeakerAccess")
    public enum State {       
        DESTROYED,        
        INITIALIZED,       
        CREATED,        
        STARTED,        
        RESUMED;     
        public boolean isAtLeast(@NonNull State state) {
            return compareTo(state) >= 0;
        }
    }
    }
其主要作用定义了抽象方法 ：增加和移除生命周期的监听者以及获取当前的状态。同时内部类中定义了枚举类型的生命周期状态和事件。

### 添加监听者

**LifecycleRegistry** 作为 **Lifecycle** 实现类 ，肯定会有其抽象方法的对应实现方式。我们先看看 **addObserver()** 方法，看一下如何进行一个监听者的增加

```
    @Override
    public void addObserver(@NonNull LifecycleObserver observer) {
        //初始化状态
        State initialState = mState == DESTROYED ? DESTROYED : INITIALIZED;
        //将观察者封装为有状态的观察者类：ObserverWithState
        ObserverWithState statefulObserver = new ObserverWithState(observer, initialState);
        //保存到观察者列表，如果已经存在了(可能存在多次add的情况)，那么返回已经存在的观察者类ObserverWithState
        ObserverWithState previous = mObserverMap.putIfAbsent(observer, statefulObserver);
        if (previous != null) {
            return;
        }
        //获取到生命周期的提供者
        LifecycleOwner lifecycleOwner = mLifecycleOwner.get();
        if (lifecycleOwner == null) {
            //如果生命周期的提供者为空，直接返回
            return;
        }
        boolean isReentrance = mAddingObserverCounter != 0 || mHandlingEvent;
        State targetState = calculateTargetState(observer);
        mAddingObserverCounter++;
        //如果初始化状态和目标状态不一致，那么需要进行状态的变更，通知监听者进行相关变化
        while ((statefulObserver.mState.compareTo(targetState) < 0
                && mObserverMap.contains(observer))) {
            pushParentState(statefulObserver.mState);
            statefulObserver.dispatchEvent(lifecycleOwner, upEvent(statefulObserver.mState));
            popParentState();
            //如果状态又进行了变化，则继续循环
            targetState = calculateTargetState(observer);
        }
        if (!isReentrance) {
            sync();
        }
        mAddingObserverCounter--;
    }
```

在这个方法中主要做了件事情

> 1. 将 **observer** 包装，生成了 **ObserverWithState** 的类对象 **statefulObserver**。
> 2. 将生成的类保存到 **mObserverMap** 中，在以后发生生命周期变化时，能够通过map来进行遍历发送通知事件。
> 3. 根据生命周期当前的状态大于**observer**初始化状态，通过**statefulObserver**进行状态的变化通知。

我们这里跟踪一下 **ObserverWithState** 这个类，看他如何将我们自己定义的 **LifecycleObserver**  进行了封装处理

```
static class ObserverWithState {
    State mState;
    LifecycleEventObserver mLifecycleObserver;

    ObserverWithState(LifecycleObserver observer, State initialState) {
        //将我们的观察者进行处理，转换为能够监听生命周期的观察者
        // 也就是LifecycleEventObserver具体的实现类
        mLifecycleObserver = Lifecycling.lifecycleEventObserver(observer);
        mState = initialState;
    }

    void dispatchEvent(LifecycleOwner owner, Event event) {
        //获取下一个状态
        State newState = getStateAfter(event);
        mState = min(mState, newState);
        //调用分发产生的时间
        mLifecycleObserver.onStateChanged(owner, event);
        mState = newState;
    }
}
```

可以看到，将 **LifecycleObserver** 的实现类通过加工处理，生成了一个 **LifecycleEventObserver** 的具体实现类。

```
static LifecycleEventObserver lifecycleEventObserver(Object object) {
    //类实现了LifecycleEventObserver接口，表明能够接收onStateChanged()
    boolean isLifecycleEventObserver = object instanceof LifecycleEventObserver;
    //类实现了FullLifecycleObserver接口，表明能够接收整个的生命周期的变化
    boolean isFullLifecycleObserver = object instanceof FullLifecycleObserver;
    if (isLifecycleEventObserver && isFullLifecycleObserver) {
        return new FullLifecycleObserverAdapter((FullLifecycleObserver) object,
                (LifecycleEventObserver) object);
    }
    if (isFullLifecycleObserver) {
        //包装者模式，返回FullLifecycleObserverAdapter实例
        return new FullLifecycleObserverAdapter((FullLifecycleObserver) object, null);
    }
    //如果只实现了LifecycleEventObserver接口，则直接返回实例
    if (isLifecycleEventObserver) {
        return (LifecycleEventObserver) object;
    }
    final Class<?> klass = object.getClass();
    int type = getObserverConstructorType(klass);
    if (type == GENERATED_CALLBACK) {
        //如果类型是通过构造函数，那么根据构造方法的数量来创建不同的AdapterObserver
        List<Constructor<? extends GeneratedAdapter>> constructors =
                sClassToAdapters.get(klass);
        if (constructors.size() == 1) {
            GeneratedAdapter generatedAdapter = createGeneratedAdapter(
                    constructors.get(0), object);
            return new SingleGeneratedAdapterObserver(generatedAdapter);
        }
        GeneratedAdapter[] adapters = new GeneratedAdapter[constructors.size()];
        for (int i = 0; i < constructors.size(); i++) {
            adapters[i] = createGeneratedAdapter(constructors.get(i), object);
        }
        return new CompositeGeneratedAdaptersObserver(adapters);
    }
    //通过反射得到相关的实现类
    return new ReflectiveGenericLifecycleObserver(object);
}
```

这里，首先根据入参实现的接口，将其分为了3类

1. 同时实现了 **FullLifecycleObserver** 的类，那么返回 **FullLifecycleObserverAdapter**
2. 只实现了 **LifecycleEventObserver** 的类，直接返回本身
3. 其他的依据类的相关属性或者参数返回不同的类
   1. 能通过构造方法生成的，根据Constructor的数量分别返回 **SingleGeneratedAdapterObserver** 或者 **CompositeGeneratedAdaptersObserver**
   2. 其他的返回 **ReflectiveGenericLifecycleObserver** 

不管最后生成的类是哪种，都会实现 **LifecycleEventObserver** 接口

```
public interface LifecycleEventObserver extends LifecycleObserver {
    void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event);
}
```

也就是会实现 **onStateChanged** ，在进行分发的时候，来告知具体的监听者，某个生命周期拥有者 **LifecycleOwner** 的当前生命周期事件 **Event**。

### 通知

到现在为止我们知道了增加 **LifecycleObserver** 的一系列操作，那么在我们生命周期进行变化的时候，又是如何进行通知变化的呢？

在 **ComponentActivity** 类中，我们发现，注入了一个 **ReportFragment** 

```
protected void onCreate(@Nullable Bundle savedInstanceState) {
    ....
    ReportFragment.injectIfNeededIn(this);
}
```

我们看看 **injectIfNeededIn** 方法主要做了什么操作

```
public static void injectIfNeededIn(Activity activity) {
    if (Build.VERSION.SDK_INT >= 29) {
        activity.registerActivityLifecycleCallbacks(
                new LifecycleCallbacks());
    }
    android.app.FragmentManager manager = activity.getFragmentManager();
    if (manager.findFragmentByTag(REPORT_FRAGMENT_TAG) == null) {
        //向activity中增加一个没有界面的Fragment
        manager.beginTransaction().add(new ReportFragment(), REPORT_FRAGMENT_TAG).commit();
        manager.executePendingTransactions();
    }
}
```

我们看一下他们的几个生命周期的实现。

```
@Override
public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    dispatchCreate(mProcessListener);
    dispatch(Lifecycle.Event.ON_CREATE);
}

@Override
public void onStart() {
    super.onStart();
    dispatchStart(mProcessListener);
    dispatch(Lifecycle.Event.ON_START);
}

@Override
public void onResume() {
    super.onResume();
    dispatchResume(mProcessListener);
    dispatch(Lifecycle.Event.ON_RESUME);
}

@Override
public void onPause() {
    super.onPause();
    dispatch(Lifecycle.Event.ON_PAUSE);
}

@Override
public void onStop() {
    super.onStop();
    dispatch(Lifecycle.Event.ON_STOP);
}

@Override
public void onDestroy() {
    super.onDestroy();
    dispatch(Lifecycle.Event.ON_DESTROY);
    mProcessListener = null;
}
```

所以其实是通过注入的Fragment的生命周期的变化了进行activity的生命周期的监听，从而来分发对应的生命周期状态。这种实现方式跟我们之前的[Glide的万字解密](https://mp.weixin.qq.com/s/e1E-S0jvCogHLOc6GjM2Og)中使用的是一样的实现方案，都是通过增加空白的Fragment来实现对于Activity生命周期的监听，从而减少代码之间的耦合。

```
static void dispatch(@NonNull Activity activity, @NonNull Lifecycle.Event event) {
    if (activity instanceof LifecycleRegistryOwner) {
    	//调用handleLifecycleEvent来进行状态的分发
        ((LifecycleRegistryOwner) activity).getLifecycle().handleLifecycleEvent(event);
        return;
    }

    if (activity instanceof LifecycleOwner) {
        Lifecycle lifecycle = ((LifecycleOwner) activity).getLifecycle();
        if (lifecycle instanceof LifecycleRegistry) {
        	//调用handleLifecycleEvent来进行状态的分发
            ((LifecycleRegistry) lifecycle).handleLifecycleEvent(event);
        }
    }
}
```

然后我们跟踪到 **handleLifecycleEvent** 的具体的实现

```
    public void handleLifecycleEvent(@NonNull Lifecycle.Event event) {
        //获取生命周期事件对应的状态
        State next = getStateAfter(event);
        //进行状态的变更
        moveToState(next);
    }

    private void moveToState(State next) {
        if (mState == next) {
            return;
        }
        mState = next;
        //如果正在进行addObserver操作或者正在进行事件处理，直接返回，因为这个过程执行完以后会自动调用一次sync方法
        //所以这里直接返回即可，不需要进行sync同步。
        // 同时通过mNewEventOccurred来告诉sync，有一个新的状态产生了，需要再调用一次sync
        if (mHandlingEvent || mAddingObserverCounter != 0) {
            mNewEventOccurred = true;
            // we will figure out what to do on upper level.
            return;
        }
        mHandlingEvent = true;
        //进行状态的同步
        sync();
        mHandlingEvent = false;
    }
    //状态同步方法
    private void sync() {
        LifecycleOwner lifecycleOwner = mLifecycleOwner.get();
        if (lifecycleOwner == null) {
            throw new IllegalStateException("LifecycleOwner of this LifecycleRegistry is already"
                    + "garbage collected. It is too late to change lifecycle state.");
        }
        //是否同步完成
        while (!isSynced()) {
            mNewEventOccurred = false;
            //当前实际的mState状态值比队列头的状态小，说明队列的状态值太大了，要后退变小
            if (mState.compareTo(mObserverMap.eldest().getValue().mState) < 0) {
                //进行后退操作(从队列头到队列尾进行更新)
                backwardPass(lifecycleOwner);
            }
            Entry<LifecycleObserver, ObserverWithState> newest = mObserverMap.newest();
            //mNewEventOccurred还是false，没有变为true，（如果是true了，说明有新的Event事件传入，那么这次更新也可以先结束了）
            //当前实际的mState状态值比队列尾的状态大，说明队列的状态值太小了，要前进变大
            if (!mNewEventOccurred && newest != null
                    && mState.compareTo(newest.getValue().mState) > 0) {
                //进行前进操作
                forwardPass(lifecycleOwner);
            }
        }
        mNewEventOccurred = false;
    }
```

在进行状态同步方法中，可以看到，对于不同的状态，需要进行不同方向的状态遍历。

1. 当下一个状态比当前队列头状态小时，就从队列头到队列尾依次进行状态的变更。
2. 当下一个状态比当前队列尾状态大时，就从队列尾到队列头依次进行状态的更新

因为在更新过程中可能会有新的事件产生，那么遍历就会停止，这时候的 **isSynced()**  会导致 while 的重新执行。从而再次进行状态的更新。

对于为什么不同的状态值要从不同的方向来进行变更，而且正向遍历完成以后还需要进行一次反向的遍历一直不理解，直到看到了一篇博客的讲解。

![image-20200227174528261](http://cdn.qiniu.kailaisii.com/typora/20200227174533-650127.png)

![image-20200227174558249](http://cdn.qiniu.kailaisii.com/typora/20200227174605-13287.png)

### 总结

Lifecycler 属于一种观察者模式，拥有生命周期的组件（包括 **Activity** 或者 **Fragment**）属于被观察者，我们通过将自定义的观察者，来进行对其生命周期的监听处理。

在生成 **LifecycleEventObserver** 的方式中，其各个子类的实现属于装饰者模式，将我们自定义的 **LifecycleObserver** 实现类进行了包装处理。


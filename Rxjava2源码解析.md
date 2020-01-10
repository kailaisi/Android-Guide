## RxJava2源码解析

### 基础解析

我们看下RxJava最简单的写法

```java
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                emitter.onNext("1");
                emitter.onComplete();
            }
        }).subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(Disposable d) {
                System.out.println("onSubscribe");
            }

            @Override
            public void onNext(String s) {
                System.out.println(s);
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("onError"+e.getLocalizedMessage());
            }

            @Override
            public void onComplete() {
                System.out.println("onComplete");
            }
        })
```

很简单的3个步骤：

1. 创建 **Observable** ：被观察者
2. 创建 **Observer** ：观察者
3. 通过 **subscribe()** 方法建立订阅关系

一个个来看  

#### 被观察者的创建

```java
public static <T> Observable<T> create(ObservableOnSubscribe<T> source) {
    ObjectHelper.requireNonNull(source, "source is null");
    //创建了一个ObservableCreate类，里面包装了我们传入的source参数
    return RxJavaPlugins.onAssembly(new ObservableCreate<T>(source));
}

public final class ObservableCreate<T> extends Observable<T> {
    final ObservableOnSubscribe<T> source;

    public ObservableCreate(ObservableOnSubscribe<T> source) {
        this.source = source;
    }
```

#### 观察者的创建

这里很简单，只是通过new方法生成了一个简单的Observer对象。

#### 订阅

订阅是通过subscribe方法来执行的，我们来跟踪一下，这个方法是属于Observable类的

```java
public final void subscribe(Observer<? super T> observer) {
    //校验观察者不为空
    ObjectHelper.requireNonNull(observer, "observer is null");
    try {
        observer = RxJavaPlugins.onSubscribe(this, observer);

        ObjectHelper.requireNonNull(observer, "Plugin returned null Observer");
        //调用subscribeActual方法，然后入参是observer（被观察者）。这个方法是抽象方法，具体的实现是交给子类的
        subscribeActual(observer);
    } catch (NullPointerException e) { // NOPMD
        throw e;
    } catch (Throwable e) {
        Exceptions.throwIfFatal(e);
        // can't call onError because no way to know if a Disposable has been set or not
        // can't call onSubscribe because the call might have set a Subscription already
        RxJavaPlugins.onError(e);

        NullPointerException npe = new NullPointerException("Actually not, but can't throw other exceptions due to RS");
        npe.initCause(e);
        throw npe;
    }
}
    /**
     * Operator implementations (both source and intermediate) should implement this method that
     * performs the necessary business logic.
     * <p>There is no need to call any of the plugin hooks on the current Observable instance or
     * the Subscriber.
     * @param observer the incoming Observer, never null
     */
    protected abstract void subscribeActual(Observer<? super T> observer);
```

最终通过 **subscribeActual(observer)** 来实现功能，而这个方法是有具体的子类去实现的。从第一步中我们通过Observable.create()来生成的被观察者。里面最终的生成的是 **ObservableCreate** 这个类。也就是说，这个**subscribeActual(observer)** 方法是由 **ObservableCreate** 这个类去实现的，我们去里面找一下。

```java
@Override
protected void subscribeActual(Observer<? super T> observer) {
    //这里将我们传入的被观察者进行了一层封装，里面实现了ObservableEmitter<T>, Disposable等接口->装饰者模式
    CreateEmitter<T> parent = new CreateEmitter<T>(observer);
    //调用被观察者的onSubscribe方法（这里很神奇，调起者是observer，而不是被订阅者，是为了兼容Rxajva1么？）
    observer.onSubscribe(parent);
    try {
        //这里的source就是我们自己写的那个ObservableOnSubscribe了，调用了里面的subscriber方法，然后参数是封装后的观察者。
        source.subscribe(parent);
    } catch (Throwable ex) {
        Exceptions.throwIfFatal(ex);
        parent.onError(ex);
    }
}

Observable.create(new ObservableOnSubscribe<String>() {
      	    //看到了哈，实际是执行的这个方法，这里面的emitter是我们封装之后的CreateEmitter,那么这里面的onNext()，onComplete()又是谁呢？
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                emitter.onNext("1");
                emitter.onComplete();
            }
        })
```

我们现在回到我们封装生成的 **CreateEmitter** 这个类

```java
static final class CreateEmitter<T>
extends AtomicReference<Disposable>
implements ObservableEmitter<T>, Disposable {
    private static final long serialVersionUID = -3434801548987643227L;
    final Observer<? super T> observer;
    //定义的观察者
    CreateEmitter(Observer<? super T> observer) {
        this.observer = observer;
    }
    
    @Override
    public void onNext(T t) {
        if (t == null) {
            onError(new NullPointerException("onNext called with null. Null values are generally not allowed in 2.x operators and sources."));
            return;
        }
        //调用的是观察者的onNext()方法
        if (!isDisposed()) {
            observer.onNext(t);
        }
    }

    @Override
    public void onComplete() {
        if (!isDisposed()) {
            try {
                //调用的是观察者的onComplete()方法
                observer.onComplete();
            } finally {
                //执行完onComplete()方法后要取消订阅
                dispose();
            }
        }
    }
    .....
}
```

到这里为知，最简单的一个流程基本已经走通了。。

### 高级用法

#### 线程切换

RxJava中我们使用的最多的应该就是进行线程切换了吧？通过 **subscribeOn()** 方法来进行线程的随意切换，舒舒服服，再也不用进行恶心的线程处理了。

```java
Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                emitter.onNext("1");
                emitter.onComplete();
            }
        }).observeOn(Schedulers.io())
```

 **observeOn()** 方法是属于Observable这个类的。我们跟踪进去这个方法去看看。

```java
public final Observable<T> observeOn(Scheduler scheduler, boolean delayError, int bufferSize) {
        //进行空校验
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        ObjectHelper.verifyPositive(bufferSize, "bufferSize");
        return RxJavaPlugins.onAssembly(new ObservableObserveOn<T>(this, scheduler, delayError, bufferSize));
    }
```

这里创建了一个 **ObservableObserveOn** 对象，所以和之前基础里面将的一样，当调用 **subscribe()** 方法的时候，会调用这个类里面的 **subscribeActual()** 方法。

```java
@Override
protected void subscribeActual(Observer<? super T> observer) {
    if (scheduler instanceof TrampolineScheduler) {//如果传入的scheduler是TrampolineScheduler，那么线程不需要切换，直接调用subscribe方法即可
        source.subscribe(observer);
    } else {
        //根据传入的scheduler，创建Worker
        Scheduler.Worker w = scheduler.createWorker();
        //将传入的observer进行包装，包装为ObserveOnObserver类。
        source.subscribe(new ObserveOnObserver<T>(observer, w, delayError, bufferSize));
    }
}
```

这里可以依据基础篇的进行整理一下，其实是对自定义的create()方法里面执行的代码块进行了封装。

 

这里看到，跟我们基础篇里面的 **create()** 方法有异曲同工之妙，这里面生成了一个ObservableSubscribeOn类，这个类也是继承Observable类的，我们跟踪进去看一下。

```java
public final class ObservableObserveOn<T> extends AbstractObservableWithUpstream<T, T> {
    final Scheduler scheduler;

    public ObservableSubscribeOn(ObservableSource<T> source, Scheduler scheduler) {
        super(source);
        this.scheduler = scheduler;
    }

    @Override
    public void subscribeActual(final Observer<? super T> observer) {
        final SubscribeOnObserver<T> parent = new SubscribeOnObserver<T>(observer);
        //调用订阅者的onSubscribe方法，这里的线程还未进行切换
        observer.onSubscribe(parent);
        //进行线程的切换处理
        //1.创造一个SubscribeTask的Runable方法
        //2.通过scheduler的scheduleDirect进行线程的切换
        //3.通过parent.setDisposable来进行Disposable的切换
        parent.setDisposable(scheduler.scheduleDirect(new SubscribeTask(parent)));
    }
```

看起来是不是很像？在基础篇我们知道了，这个 **subscribeActual** 方法里面的参数就是我们的观察者。

我们看一下里面和之前分析所不同的地方，也就是线程的切换

```java
final class SubscribeTask implements Runnable {
    ...
    @Override
    public void run() {
        //source是我们上一层的被观察者，parent是包装之后的观察者
        source.subscribe(parent);
    }
}
```

然后看一下这里面最重要的 **scheduler.scheduleDirect** 这个方法

```java
    @NonNull
    public Disposable scheduleDirect(@NonNull Runnable run) {
        return scheduleDirect(run, 0L, TimeUnit.NANOSECONDS);
    }
    @NonNull
    public Disposable scheduleDirect(@NonNull Runnable run, long delay, @NonNull TimeUnit unit) {
        final Worker w = createWorker();
        final Runnable decoratedRun = RxJavaPlugins.onSchedule(run);
        DisposeTask task = new DisposeTask(decoratedRun, w);
        w.schedule(task, delay, unit);
        return task;
    }
```




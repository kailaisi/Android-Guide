# RxJava2源码解析

## 基础解析

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

### 被观察者的创建

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

### 观察者的创建

这里很简单，只是通过new方法生成了一个简单的Observer对象。

### 订阅

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

## 高级用法

### 线程切换

#### 下层切换

RxJava中我们使用的最多的应该就是进行线程切换了吧？通过 **observeOn()** 方法来进行线程的随意切换，舒舒服服，再也不用进行恶心的线程处理了。

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

这里创建了一个 **ObservableObserveOn** 对象，所以和之前基础里面将的一样，当调用 **subscribe()** 方法的时候，会先调用观察者的 **onSubscribe()** 方法，然后通过subscribe的层层处理，调用这个被观察者里面的 **subscribeActual()** 方法。

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

这里可以依据基础篇的进行整理一下，这里将观察者进行了一层包装，也就是我们的观察者由原来的observaer变为了ObserveOnObserver对象。而被观察者还是之前的ObservableCreate（注意，这里只是依据基础中.create()创建的类，所以是ObservableCreate，如果是其他方式创建的被观察者，那么这里可能就是另一个具体的实现类了），并未改变。之前我们讲过，当调用subscribe方法的onNext()，onComplete()方法，其实是调用的观察者的方法。我们现在看一下ObserveOnObserver的onNext和onComplete方法又是做了什么神奇的操作。

```java
@Override
public void onNext(T t) {
    if (done) {//如果已经完成，直接返回
        return;
    }
    if (sourceMode != QueueDisposable.ASYNC) {
        //将onNext的数据放入队列queue
        queue.offer(t);
    }
    //进行线程切换
    schedule();
}

void schedule() {
    if (getAndIncrement() == 0) {
        //调用了worker的方法，这里通过调用线程池，调用了自身的run方法
        worker.schedule(this);
    }
}
```

这里我们使用的是IO线程，那么在 **scheduler.createWorker()** 中的生成worker时

```java
@NonNull
@Override
public Worker createWorker() {
    return new EventLoopWorker(pool.get());
}
```

那么跟到这个类里面的 **schedule** 方法

```java
@Override
public Disposable schedule(@NonNull Runnable action, long delayTime, @NonNull TimeUnit unit) {
    if (tasks.isDisposed()) {
        // don't schedule, we are unsubscribed
        return EmptyDisposable.INSTANCE;
    }
    //这里调用了线程worker的scheduleActual方法，并把Runable对象传进去
    return threadWorker.scheduleActual(action, delayTime, unit, tasks);
}

public ScheduledRunnable scheduleActual(final Runnable run, long delayTime, @NonNull TimeUnit unit, @Nullable DisposableContainer parent) {
        //留下钩子
        Runnable decoratedRun = RxJavaPlugins.onSchedule(run);
        ScheduledRunnable sr = new ScheduledRunnable(decoratedRun, parent);
        ....
        Future<?> f;
        try {
            if (delayTime <= 0) {
                //在线程池中调用封装之后的Runnable
                f = executor.submit((Callable<Object>)sr);
            } else {
                f = executor.schedule((Callable<Object>)sr, delayTime, unit);
            }
            sr.setFuture(f);
        } catch (RejectedExecutionException ex) {
            if (parent != null) {
                parent.remove(sr);
            }
            RxJavaPlugins.onError(ex);
        }
        return sr;
    }
```

可以看到，其实最终是通过线程池调用了 **ObserveOnObserver** 本身，这个类实现了 **Runnable** 接口，我们看一下run方法里面做了什么。

```java
@Override
public void run() {
    if (outputFused) {
        drainFused();
    } else {
        drainNormal();
    }
}
//具体的操作
void drainNormal() {
     int missed = 1;
     //被观察者onNext发送的数据队列
     final SimpleQueue<T> q = queue;
     //实际的观察者
     final Observer<? super T> a = downstream;
     for (;;) {
         //检测是否有异常信息
         if (checkTerminated(done, q.isEmpty(), a)) {
             return;
         }
         //遍历
         for (;;) {
             boolean d = done;
             T v;
             //取出队列中的数据
             try {
                 v = q.poll();
             } catch (Throwable ex) {
                 //发生异常，则直接调用dispose()和onError()方法
                 Exceptions.throwIfFatal(ex);
                 disposed = true;
                 upstream.dispose();
                 q.clear();
                 a.onError(ex);
                 worker.dispose();
                 return;
             }
             ....
             //调用实际的观察者的onNext()方法
             a.onNext(v);
         }
         ...
     }
 }
```

因为这个操作最终是在scheduler.createWorker()创建的地方进行了处理，才实现了对于之后代码处理都在io线程中进行了调用。从而实现线程的切换功能。这里我们对之前的测试代码流程做一个总结。

先看一下对于观察者的onSubscribe()方法的调用流程：

![image-20200114090701177](C:\Users\wu\AppData\Roaming\Typora\typora-user-images\image-20200114090701177.png)

这里面我们自己定义的观察者通过subscribe()方法层层往上调用，最后调用了我们定义的被观察者里面的onSubscribe方法，再一层层的往下调用，最后到我们自己定义的onSubscribe()方法，里面很少有线程的切换处理，所以这段代码在哪儿执行，那么这段代码在那里执行，这个onSubscribe()方法就是在哪个线程执行。

继续，我们看一下onNext()方法

![image-20200114094904024](C:\Users\wu\AppData\Roaming\Typora\typora-user-images\image-20200114094904024.png)

#### 上层切换

除了 **observeOn** 方法来处理我们操作流的下层线程处理之外，我们也可以通过 **subscribeOn** 方法来进行对上层流的线程处理。

测试用代码：

```java
Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                emitter.onNext("1");
                emitter.onComplete();
            }
}).subscribeOn(Schedulers.io())
```

现在我们跟踪进 **subscribeOn** 方法

```java
public final Observable<T> subscribeOn(Scheduler scheduler) {
    ObjectHelper.requireNonNull(scheduler, "scheduler is null");
    //
    return RxJavaPlugins.onAssembly(new ObservableSubscribeOn<T>(this, scheduler));
}
```

这里看到，跟我们基础篇里面的 **create()** 方法有异曲同工之妙，这里面生成了一个ObservableSubscribeOn类，这个类也是继承Observable类的，我们跟踪进去看一下。

```java
public final class ObservableSubscribeOn<T> extends AbstractObservableWithUpstream<T, T> {
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
        //source是我们上一层的被观察者，parent是包装之后的观察者.
        //所以会在相关的worker里面调用source的subscribe方法，
        //即上层的数据调用已经在woker里面了（如果是IoScheduler，那么这里就是在RxCachedThreadScheduler线程池调用了这个方法 ）
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
        //创建一个Worker，这个是有具体的实现类来实现的，比如我们的IOScheduler,ImmediateThinScheduler等，具体要看我们切换传参
        final Worker w = createWorker();
        final Runnable decoratedRun = RxJavaPlugins.onSchedule(run);
        DisposeTask task = new DisposeTask(decoratedRun, w);
        w.schedule(task, delay, unit);
        return task;
    }
```

这里我们对上层切换的流程做一个总结：当调用 **subscribeOn** 方法的时候，会在创建的调度器中来执行被观察者的执行代码，从而实现了对上层的线程切换功能。

先看一下测试代码中的onNext()方法的调用流程：

![image-20200114173137935](C:\Users\wu\AppData\Roaming\Typora\typora-user-images\image-20200114173137935.png)

#### 汇总

其实对于线程的切换，主要是根据里面传递的线程切换函数，将上游或者下游的代码在指定的线程里面去执行来实现。

![RxJava的线程切换 (1)](C:\Users\wu\Downloads\RxJava的线程切换 (1).png)


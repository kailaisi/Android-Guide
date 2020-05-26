## LeakCanary源码解析

### 前言

对于内存泄漏的检测，基于MAT起点较高，所以一般我们都使用**LeakCanary**来作为我们的内存泄漏检测工具来使用。

### 基础知识

##### 四种引用

LeakCanary主要是基于弱引用来进行对于已经销毁的Activity和Fragment的回收监控来实现的。

* 强引用：无论如何都不会回收。

* 软引用：内存足够不回收。内存不够时，就会回收。

* 弱引用：垃圾回收时直接回收，则直接回收。

* 虚引用：垃圾回收时直接回收。

##### 引用队列（ReferenceQueue）。

软引用和弱引用都可以关联一个引用队列。当引用的对象被回收以后，会将软引用加入到与之关联的引用队列中。**LeakCanary**的基础实现就是将已经销毁的**Activity**和**Fragment**所对应的实例放入到弱引用中，并关联一个引用队列。如果实例进行了回收，那么弱引用就会放入到**ReferenceQueue**中，如果一段时间后，所监控的实例还未在**ReferenceQueue**中出现，那么可以证明出现了内存泄漏导致了实例没有被回收。

### 使用方法

配置：

```java
dependencies {
  debugImplementation 'com.squareup.leakcanary:leakcanary-android:1.6.3'
  releaseImplementation 'com.squareup.leakcanary:leakcanary-android-no-op:1.6.3'
  // Optional, if you use support library fragments:
  debugImplementation 'com.squareup.leakcanary:leakcanary-support-fragment:1.6.3'
}
```

使用：

```java
public class ExampleApplication extends Application {
  @Override public void onCreate() {
    super.onCreate();
    LeakCanary.install(this);
  }
}

```

### Leakcanary原理解析

从程序的唯一入口来进行分析。本文是基于1.6.3版本来进行源码解析的。对应的解析源码地址为[leakcanary-source](https://github.com/kailaisi/leakcanary-source.git)。

#### 注册实例的监控

```java
    public static @NonNull RefWatcher install(@NonNull Application application) {
        return refWatcher(application)//创建一个Android端使用的引用监控的构造者
				.listenerServiceClass(DisplayLeakService.class)
                //设置不进行监控的类引用对象
                .excludedRefs(AndroidExcludedRefs.createAppDefaults().build())
                //创建对于引用的监控
                .buildAndInstall();
    }
```

这个方法比较简短，一个个进行解析吧。

##### 构造一个**AndroidRefWatcherBuilder**对象

```java
//创建一个AndroidRefWatcherBuilder对象   
public static @NonNull AndroidRefWatcherBuilder refWatcher(@NonNull Context context) {   
    return new AndroidRefWatcherBuilder(context);   
}
```

这里创建的AndroidRefWatcherBuilder对象是一个适用于Android端的引用监控的构造者。

##### 设置后台的监听类

```java
  //AndroidRefWatcherBuilder.java
  //设置一个类用来监听分析的结果。
  public @NonNull AndroidRefWatcherBuilder listenerServiceClass(@NonNull Class<? extends AbstractAnalysisResultService> listenerServiceClass) {
    enableDisplayLeakActivity = DisplayLeakService.class.isAssignableFrom(listenerServiceClass);
    //设置一个监听者
    return heapDumpListener(new ServiceHeapDumpListener(context, listenerServiceClass));
  }

  //RefWatcherBuilder.java
  //HeapDump的监听者
  public final T heapDumpListener(HeapDump.Listener heapDumpListener) {
    this.heapDumpListener = heapDumpListener;
    return self();
  }
```

这里将DisplayLeakService类作为了我们最终内存泄漏的分析者，并且该类能够进行内存泄漏消息的通知（一般是Notification）。

##### 不纳入监控的引用

**excludedRefs**方法能够将一些我们不关心的引用排除在我们的监控范围以外。这里这么处理，主要是因为一些系统级别的引用问题。我们可以具体看一下里面有哪些东西是我们不需要关注的。

```java
    //由于Android的AOSP本身可能会存在内存泄漏的东西，所以对于这些东西默认是不会进行提醒的。
    public static @NonNull ExcludedRefs.Builder createAppDefaults() {
        //将AndroidExcludedRefs所有的枚举类型都考虑在内。
        return createBuilder(EnumSet.allOf(AndroidExcludedRefs.class));
    }

    public static @NonNull ExcludedRefs.Builder createBuilder(EnumSet<AndroidExcludedRefs> refs) {
        ExcludedRefs.Builder excluded = ExcludedRefs.builder();
        //遍历所有的枚举类型
        for (AndroidExcludedRefs ref : refs) {
            //如果枚举类型执行引用的排除处理
            if (ref.applies) {
                //调用枚举的add方法，这里面会将所有需要排除的引用类都放到出入的excluede中
                ref.add(excluded);
                ((ExcludedRefs.BuilderWithParams) excluded).named(ref.name());
            }
        }
        return excluded;
    }

```

这个可能会有一些难以理解，我们先简单分析一下**AndroidExcludedRefs**这个类。

```java
public enum AndroidExcludedRefs {
    //参数，标识是否需要执行add方法
    final boolean applies;
    AndroidExcludedRefs() {
        this(true);
    }
    AndroidExcludedRefs(boolean applies) {
        this.applies = applies;
    }
    //枚举类需要实现的方法
    abstract void add(ExcludedRefs.Builder excluded);
}
```

AndroidExcludedRefs是一个枚举类型。含有成员变量**applies**以及**add()**方法。

我们再分析一个具体的枚举类型。

```java
//AndroidExcludedRefs.java
	ACTIVITY_CLIENT_RECORD__NEXT_IDLE(SDK_INT >= KITKAT && SDK_INT <= LOLLIPOP) {
        @Override
        void add(ExcludedRefs.Builder excluded) {
            //设置排除的类中的某个属性
            excluded.instanceField("android.app.ActivityThread$ActivityClientRecord", "nextIdle")
                	//设置排除的原因
                    .reason("Android AOSP sometimes keeps a reference to a destroyed activity as a"
                            + " nextIdle client record in the android.app.ActivityThread.mActivities map."
                            + " Not sure what's going on there, input welcome.");
        }
    },
```

**ACTIVITY_CLIENT_RECORD__NEXT_IDLE**就是一个具体的枚举类型。applies赋值为**SDK_INT >= KITKAT && SDK_INT <= LOLLIPOP**。也有add方法的具体实现。实现中将需要排除的引用类型添加到了**excluded**中。

所以当我们的系统版本号满足**SDK_INT >= KITKAT && SDK_INT <= LOLLIPOP**这个条件的时候，就会执行add方法。

AndroidExcludedRefs具有不同的枚举实例，会根据不同的系统版本来进行不同的处理。这里其实主要是保证对于一些系统级别的内存泄漏情况不再进行提示。

##### 创建引用的监控

我们直接看看**buildAndInstall**中是如何对已经执行onDestroy的Activity进行监控的。

```java
  //根据对应的设置信息，返回一个RefWatcher对象
  public @NonNull RefWatcher buildAndInstall() {
    if (LeakCanaryInternals.installedRefWatcher != null) {
      throw new UnsupportedOperationException("buildAndInstall() should only be called once.");
    }
    //通过构造者模式中的build()方法创建一个RefWatcher对象,这里面会有很多默认的设置
    RefWatcher refWatcher = build();
    if (refWatcher != DISABLED) {
      //如果允许显示内存泄漏Activity，则进行处理
      if (enableDisplayLeakActivity) {
        LeakCanaryInternals.setEnabledAsync(context, DisplayLeakActivity.class, true);
      }
      //如果设置了监听Activity，那么就为Activity注册生命周期监听
      if (watchActivities) {
        ActivityRefWatcher.install(context, refWatcher);
      }
      //如果设置了监听Fragment，那么就为Fragment注册生命周期监听
      if (watchFragments) {
        FragmentRefWatcher.Helper.install(context, refWatcher);
      }
    }
    LeakCanaryInternals.installedRefWatcher = refWatcher;
    return refWatcher;
  }
```

我们这里主要看一下如何进行Activity以及Fragment的监听的。

1. 对Activity的处理

```java
    //ActivityRefWatcher.java
    public static void install(@NonNull Context context, @NonNull RefWatcher refWatcher) {
        Application application = (Application) context.getApplicationContext();
        //创建一个对于Activity的弱引用监听类
        ActivityRefWatcher activityRefWatcher = new ActivityRefWatcher(application, refWatcher);
        //对传入的应用的Application注册一个对于Activity的生命周期监听函数
        application.registerActivityLifecycleCallbacks(activityRefWatcher.lifecycleCallbacks);
    }
```

这里创建了一个**ActivityRefWatcher**对象，然后将对于应用，通过**registerActivityLifecycleCallbacks**注册了一个监听的回调。

```java
    //ActivityRefWatcher.java
	private final Application.ActivityLifecycleCallbacks lifecycleCallbacks =
            new ActivityLifecycleCallbacksAdapter() {
                //只监听destory方法，将调用destory的activity添加到监听watcher中
                @Override
                public void onActivityDestroyed(Activity activity) {
                    refWatcher.watch(activity);
                }
            };
```

在这个监听方法中，只监听了Activity的**onDestroy**方法。当Activity销毁的时候，使用refWatcher来监控其实例。

2. 对Fragment的处理

```java
//FragmentRefWatcher.java
	public static void install(Context context, RefWatcher refWatcher) {
            List<FragmentRefWatcher> fragmentRefWatchers = new ArrayList<>();
            //将实现了FragmentRefWatcher接口的两个实现类加入到fragmentRefWatchers中
            //两个实现类，一个是实现对于V4包下的Fragment的监听，一个是对于当前包下Fragment的监听
            if (SDK_INT >= O) {
                //实现类AndroidOFragmentRefWatcher
                fragmentRefWatchers.add(new AndroidOFragmentRefWatcher(refWatcher));
            }

            try {
                //实现类SupportFragmentRefWatcher用于监听V4包下面的Fragment
                //这里使用反射，是因为SupportFragmentRefWatcher这个类在support-fragment这个module中。
                //所以，如果我们没有引入V4的话，其实这个类是可以不引入的。
                Class<?> fragmentRefWatcherClass = Class.forName(SUPPORT_FRAGMENT_REF_WATCHER_CLASS_NAME);
                Constructor<?> constructor = fragmentRefWatcherClass.getDeclaredConstructor(RefWatcher.class);
                FragmentRefWatcher supportFragmentRefWatcher = (FragmentRefWatcher) constructor.newInstance(refWatcher);
                fragmentRefWatchers.add(supportFragmentRefWatcher);
            } catch (Exception ignored) {
            }
            //如果没有Fragment的监控者，那么直接返回
            if (fragmentRefWatchers.size() == 0) {
                return;
            }
            //创建Helper实例
            Helper helper = new Helper(fragmentRefWatchers);
            Application application = (Application) context.getApplicationContext();
            //注册Activity的生命周期回调
            application.registerActivityLifecycleCallbacks(helper.activityLifecycleCallbacks);
        }
```

由于我们经常使用的Fragment包含两种，一种是support包中的Fragment，一种是标准的app包中的Fragment。这里对这两种都进行了处理。

我们看一下对于注册的生命周期函数

```java
        private final Application.ActivityLifecycleCallbacks activityLifecycleCallbacks =
                new ActivityLifecycleCallbacksAdapter() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                        for (FragmentRefWatcher watcher : fragmentRefWatchers) {
                            //这里会调用具体的实现类的watchFragments方法。这里关心的是绑定的Activity的onCreate方法。走到这里的时候已经创建了对应FragmentManager对象
                            //而通过FragmentManager对象可以来registerFragmentLifecycleCallbacks来创建对于其管理的Fragment的生命周期监听
                            watcher.watchFragments(activity);
                        }
                    }
                };
```

这里我们同样是注册了Activity的生命周期回调。但是这里监控的是**onActivityCreated**方法。我们这里看一下**watchFragments**的实现。

具体的实现有两个类，一个是**SupportFragmentRefWatcher**，一个是AndroidOFragmentRefWatcher。我们这里只分析第一个。剩下的另一个是类似的，只是因为使用的Fragment不同，而有所区别。

```java
    public void watchFragments(Activity activity) {
        //V4包中的Fragment，必须使用FragmentActivity来进行处理
        if (activity instanceof FragmentActivity) {
            FragmentManager supportFragmentManager = ((FragmentActivity) activity).getSupportFragmentManager();
            supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true);
        }
    }

    private final FragmentManager.FragmentLifecycleCallbacks fragmentLifecycleCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {

                @Override
                public void onFragmentViewDestroyed(FragmentManager fm, Fragment fragment) {
                    View view = fragment.getView();
                    if (view != null) {
                        //当fragment的view销毁的时候，开始监控
                        refWatcher.watch(view);
                    }
                }

                @Override
                public void onFragmentDestroyed(FragmentManager fm, Fragment fragment) {
                    //当fragment销毁的时候，开始监控
                    refWatcher.watch(fragment);
                }
            };
```

所以，这里通过获取**Activity**中的**FragmentManager**，通过**registerFragmentLifecycleCallbacks**来对于其管理的Fragment的生命周期进行监听。当Fragment执行销毁的时候，将其引用加入到监控队列。

到这里为止，就已经将我们的Activity和Fragment通过**refWatcher**的**watch**进行了监控。

那么我们下一步分析，watch方法中又是如何监控实例，并判断其存在内存泄漏的。

#### 监控

我们对于已经销毁的界面会通过**refWatcher**的**watch**方法来进行监控。

```java
//RefWatcher.java
	public void watch(Object watchedReference) {
        //重载方法
        watch(watchedReference, "");
    }
    public void watch(Object watchedReference, String referenceName) {
        if (this == DISABLED) {
            return;
        }
        //保证watch的对象不为空
        checkNotNull(watchedReference, "watchedReference");
        checkNotNull(referenceName, "referenceName");
        final long watchStartNanoTime = System.nanoTime();
        //创建一个UUID
        String key = UUID.randomUUID().toString();
        //将UUID保存到set中
        retainedKeys.add(key);
        //创建一个弱引用，指向要检测的对象。
        //如果这个弱引用被回收，那么会将reference加入到queue队列中
        
        final KeyedWeakReference reference = new KeyedWeakReference(watchedReference, key, referenceName, queue);
        //判断reference是否被回收
        ensureGoneAsync(watchStartNanoTime, reference);
    }
```

这个里面主要执行了3个操作

* 创建了UUID
* 将生成的UUID保存到**retainedKeys**队列中。
* 创建一个弱引用，指定了对应的引用队列**queue**。

这里的retainedKeys队列记录了我们执行了监控的引用对象。而**queue**中会保存回收的引用。所以通过二者的对比，我们就可以找到内存泄漏的引用了。

我们看一下**ensureGoneAsync**中是如何执行这个操作过程的。

```java
    //RefWatcher.java
	private void ensureGoneAsync(final long watchStartNanoTime, final KeyedWeakReference reference) {
        watchExecutor.execute(new Retryable() {
            @Override
            public Retryable.Result run() {
                return ensureGone(reference, watchStartNanoTime);
            }
        });
    }
```

这里的watcheExecute使用的是**AndroidWatchExecutor**

```java
//AndroidRefWatcherBuilder.java
  @Override protected @NonNull WatchExecutor defaultWatchExecutor() {
    return new AndroidWatchExecutor(DEFAULT_WATCH_DELAY_MILLIS);
  }

```

我们跟踪一下**execute**方法。

```java
//AndroidWatchExecutor.java
  @Override public void execute(@NonNull Retryable retryable) {
    //如果当前线程是主线程，则直接执行waitForIdl
    if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
      waitForIdle(retryable, 0);
    } else {
      //如果不是主线程，则通过Handler机制，将waitForIdle放入到主线程去执行
      postWaitForIdle(retryable, 0);
    }
  }

  private void postWaitForIdle(final Retryable retryable, final int failedAttempts) {
    //通过Handler机制，将waitForIdle发送到主线程执行
    mainHandler.post(new Runnable() {
      @Override public void run() {
        waitForIdle(retryable, failedAttempts);
      }
    });
  }

  private void waitForIdle(final Retryable retryable, final int failedAttempts) {
    //当messagequeue闲置时，增加一个处理。这种方法主要是为了提升性能，不会影响我们正常的应用流畅度
    //这个方法会在主线程执行，所以postToBackgroundWithDelay会在主线程执行
    Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
      @Override public boolean queueIdle() {
        postToBackgroundWithDelay(retryable, failedAttempts);
        return false;
      }
    });
  }
```

所以这里最终都会在主线程中执行**postToBackgroundWithDelay**方法。

```java
  private void postToBackgroundWithDelay(final Retryable retryable, final int failedAttempts) {
    //计算补偿因子。如果返回了重试的话，这个failedAttempts回增加，会使得方法的执行时间延迟时间增加。
    //比如说第一次，演示5秒执行，但是执行结果为RETRY，那么下一次就是延迟10秒来执行了
    long exponentialBackoffFactor = (long) Math.min(Math.pow(2, failedAttempts), maxBackoffFactor);
    //计算延迟时间
    long delayMillis = initialDelayMillis * exponentialBackoffFactor;
    //backgroundHandler会将run方法中的代码放在一个新的线程中去执行。
    backgroundHandler.postDelayed(new Runnable() {
      @Override public void run() {
        Retryable.Result result = retryable.run();
        if (result == RETRY) {
          postWaitForIdle(retryable, failedAttempts + 1);
        }
      }
    }, delayMillis);
  }
```

这个方法的执行，会根据执行的次数进行来延迟执行对应的run方法。

我们看一下retryable.run()方法的执行。也就回到了我们的RefWatcher中的**ensureGoneAsync**方法。

```java
private void ensureGoneAsync(final long watchStartNanoTime, final KeyedWeakReference reference) {
  watchExecutor.execute(new Retryable() {
    @Override public Retryable.Result run() {
      return ensureGone(reference, watchStartNanoTime);
    }
  });
}
```

这里的ensureGone方法属于我们最核心的代码了。

```java
    //判断reference是否被回收
    @SuppressWarnings("ReferenceEquality")
    // Explicitly checking for named null.
    Retryable.Result ensureGone(final KeyedWeakReference reference, final long watchStartNanoTime) {
        long gcStartNanoTime = System.nanoTime();
        long watchDurationMs = NANOSECONDS.toMillis(gcStartNanoTime - watchStartNanoTime);
        //移除已经回收的监控对象
        removeWeaklyReachableReferences();
        //如果当前是debug状态，则直接返回retry
        if (debuggerControl.isDebuggerAttached()) {
            // The debugger can create false leaks.
            return RETRY;
        }
        //监控对象已经回收了，直接返回Done
        if (gone(reference)) {
            return DONE;
        }
        //执行一次垃圾回收
        gcTrigger.runGc();
        //再次移除已经回收的监控对象
        removeWeaklyReachableReferences();
        if (!gone(reference)) {
            //如果仍然没有回收，证明发生了内存泄漏
            long startDumpHeap = System.nanoTime();
            //gc执行的时长
            long gcDurationMs = NANOSECONDS.toMillis(startDumpHeap - gcStartNanoTime);
            //dump出hprof文件
            File heapDumpFile = heapDumper.dumpHeap();
            if (heapDumpFile == RETRY_LATER) {
                // Could not dump the heap.
                //不能生成快照文件的话，进行重试
                return RETRY;
            }
            //生成hprof文件消耗的的时间
            long heapDumpDurationMs = NANOSECONDS.toMillis(System.nanoTime() - startDumpHeap);
            HeapDump heapDump = heapDumpBuilder.heapDumpFile(heapDumpFile).referenceKey(reference.key)
                    .referenceName(reference.name)
                    .watchDurationMs(watchDurationMs)
                    .gcDurationMs(gcDurationMs)
                    .heapDumpDurationMs(heapDumpDurationMs)
                    .build();
            //分析堆内存，heapdumpListener默认是ServiceHeapDumpListener
            heapdumpListener.analyze(heapDump);
        }
        return DONE;
    }
```

这段代码执行了几个过程

1. 移除已经回收的监控对象
2. 如果当前监控的对象已经回收了，直接返回DONE。
3. 如果没有回收，则强行执行一次GC操作。
4. 再次移除已经回收的监控对象。
5. 如果当前监控对象仍然没有回收，则dump出hprof文件，然后根据快照文件进行内存泄漏情况的分析。

这里我们对每个方法都一一的进行一次分析

##### 移除已回收的弱引用对象

```java
    private void removeWeaklyReachableReferences() {
        KeyedWeakReference ref;
        //循环queue
        while ((ref = (KeyedWeakReference) queue.poll()) != null) {
            //在queue中的ref，说明已经被回收了，所以直接将其对应的key从retainedKeys移除。
            retainedKeys.remove(ref.key);
        }
    }
```

这里的**queue**是我们提到的引用队列，而**retainedKeys**中则保存着我们要监控的对象。当对象被回收以后，就会将对应的弱引用信息保存到**queue**中，所以我们将**queue**中的相关弱引用信息从**retainedKeys**移除。剩下的就是我们在监听或者已经发生内存泄漏的对象了。

##### 判断监控对象是否回收

```java
    //判断监控的对象是否已经回收 true:已经回收
    private boolean gone(KeyedWeakReference reference) {
        return !retainedKeys.contains(reference.key);
    }
```

在上一步中，我们已经将回收的引用信息从retainedKeys中移除了，所以这里只要通过判断这个set中是否有我们监控的这个类即可。

##### 导出.hprof文件

```java
  public File dumpHeap() {
    //创建一个.hrof文件
    File heapDumpFile = leakDirectoryProvider.newHeapDumpFile();
    if (heapDumpFile == RETRY_LATER) {
      //创建失败了，等会再重试
      return RETRY_LATER;
    }
    FutureResult<Toast> waitingForToast = new FutureResult<>();
    //通过Handler机制在主线程显示Toast，使用了CountDownLatch机制。显示Toast的时候会将其数值修改为0，
    showToast(waitingForToast);
    //这里会等待主线程显示Toast，也就是CountDownLatch变为0。然后就可以继续后面的操作
    if (!waitingForToast.wait(5, SECONDS)) {
      CanaryLog.d("Did not dump heap, too much time waiting for Toast.");
      return RETRY_LATER;
    }
    //创建一个Notification通知
    Notification.Builder builder = new Notification.Builder(context)
        .setContentTitle(context.getString(R.string.leak_canary_notification_dumping));
    Notification notification = LeakCanaryInternals.buildNotification(context, builder);
    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    int notificationId = (int) SystemClock.uptimeMillis();
    notificationManager.notify(notificationId, notification);

    Toast toast = waitingForToast.get();
    try {
      //创建heap堆的快照信息，可以获知程序的哪些部分正在使用大部分的内存
      Debug.dumpHprofData(heapDumpFile.getAbsolutePath());
      //关闭Toask和Notification通知
      cancelToast(toast);
      notificationManager.cancel(notificationId);
      return heapDumpFile;
    } catch (Exception e) {
      CanaryLog.d(e, "Could not dump heap");
      // Abort heap dump
      return RETRY_LATER;
    }
  }
```

这里会创建一个.hprof文件，然后显示一个Toast和Notification通知，再将内存泄漏时候的堆的快照信息保存的.hprof文件中，最后将Toast和Notification通知关闭。所以执行完这个操作之后，我们生成的.hprof文件中就保存了对应的内存泄漏时的堆的相关信息了。

##### 快照文件分析

当生成了文件以后，会通过heapdumpListener来分析生成的快照文件。这里的listener默认的是ServiceHeapDumpListener类

```java
  //AndroidRefWatcherBuilder.java
  @Override protected @NonNull HeapDump.Listener defaultHeapDumpListener() {
    return new ServiceHeapDumpListener(context, DisplayLeakService.class);
  }
```

我们看一下它的**analyze**方法

```java
  //ServiceHeapDumpListener.java
   public void analyze(@NonNull HeapDump heapDump) {
    checkNotNull(heapDump, "heapDump");
    HeapAnalyzerService.runAnalysis(context, heapDump, listenerServiceClass);
  }
  //HeapAnalyzerService.java
  public static void runAnalysis(Context context, HeapDump heapDump,
      Class<? extends AbstractAnalysisResultService> listenerServiceClass) {
    setEnabledBlocking(context, HeapAnalyzerService.class, true);
    setEnabledBlocking(context, listenerServiceClass, true);
    Intent intent = new Intent(context, HeapAnalyzerService.class);
    //这里的listenerServiceClass是DisplayLeakService
    intent.putExtra(LISTENER_CLASS_EXTRA, listenerServiceClass.getName());
    intent.putExtra(HEAPDUMP_EXTRA, heapDump);
    //启动一个前台的服务，启动时，会调用onHandleIntent方法，该方法在父类中实现了。实现中会调用onHandleIntentInForeground()方法
    ContextCompat.startForegroundService(context, intent);
  }
```

这里启动了一个服务来进行对于文件的分析功能。当启动服务的时候会调用**onHandleIntent**方法。**HeapAnalyzerService**的**onHandleIntent**是在其父类中实现的。

```java
//ForegroundService.java
@Override protected void onHandleIntent(@Nullable Intent intent) {
  onHandleIntentInForeground(intent);
}
```

所以会调用**onHandleIntentInForeground**这个方法。

```java
    protected void onHandleIntentInForeground(@Nullable Intent intent) {
        String listenerClassName = intent.getStringExtra(LISTENER_CLASS_EXTRA);
        HeapDump heapDump = (HeapDump) intent.getSerializableExtra(HEAPDUMP_EXTRA);
        //创建一个堆分析器
        HeapAnalyzer heapAnalyzer = new HeapAnalyzer(heapDump.excludedRefs, this, heapDump.reachabilityInspectorClasses);
        //**重点分析方法***分析内存泄漏结果
        AnalysisResult result = heapAnalyzer.checkForLeak(heapDump.heapDumpFile, heapDump.referenceKey, heapDump.computeRetainedHeapSize);
        //调用接口，将结果回调给listenerClassName所对应的类（这里是DisplayLeakService类）来进行处理
        AbstractAnalysisResultService.sendResultToListener(this, listenerClassName, heapDump, result);
    }
```

这里会创建一个堆分析器，对于我们的快照文件进行分析，然后将结果通过AbstractAnalysisResultService的方法，将结果交给DisplayLeakService类来进行处理。

##### 检测泄漏结果

HeapAnalyzer类的作用主要就是通过对.hprof文件的分析，检测我们监控的对象是否发生了内存的泄漏

```java
//HeapAnalyzer.java
//将hprof文件解析，解析为对应的AnalysisResult对象
public @NonNull AnalysisResult checkForLeak(@NonNull File heapDumpFile, @NonNull String referenceKey, boolean computeRetainedSize) {
    long analysisStartNanoTime = System.nanoTime();

    if (!heapDumpFile.exists()) {
        Exception exception = new IllegalArgumentException("File does not exist: " + heapDumpFile);
        return failure(exception, since(analysisStartNanoTime));
    }

    try {
        //开始读取Dump文件
        listener.onProgressUpdate(READING_HEAP_DUMP_FILE);
        HprofBuffer buffer = new MemoryMappedFileBuffer(heapDumpFile);
        //.hprof的解析器,这个是haha库的类
        HprofParser parser = new HprofParser(buffer);
        listener.onProgressUpdate(PARSING_HEAP_DUMP);
        //解析生成快照，快照中会包含所有被引用的对象信息
        Snapshot snapshot = parser.parse();
        listener.onProgressUpdate(DEDUPLICATING_GC_ROOTS);
        deduplicateGcRoots(snapshot);
        listener.onProgressUpdate(FINDING_LEAKING_REF);
        //根据key值，查找快照中是否有所需要的对象
        Instance leakingRef = findLeakingReference(referenceKey, snapshot);
        if (leakingRef == null) {
            //表示对象不存在，在gc的时候，进行了回收。表示没有内存泄漏
            String className = leakingRef.getClassObj().getClassName();
            return noLeak(className, since(analysisStartNanoTime));
        }
        //检测泄漏的路径，并将检测的结果进行返回
        return findLeakTrace(analysisStartNanoTime, snapshot, leakingRef, computeRetainedSize);
    } catch (Throwable e) {
        return failure(e, since(analysisStartNanoTime));
    }
}
```

这个方法使用了haha三方类库来对.hprof文件解析以及处理。里面的主要流程如下：

1. 创建一个.hprof文件的buffer来进行文件的读取
2. 通过HprofParser解析器来解析hprof文件，生成Snapshot对象。在这一步中构建了一颗对象的引用关系树，我们可以在这颗树中查询各个Object的信息，包括Class信息、内存地址、持有的引用以及被持有引用的关系。
3. 根据传入的监控的对象key值，获取其在Snapshot中所对应的引用leakingRef。
4. 分析leakingRef，获取到内存泄漏的路径。这里会找到一条到泄漏对象的最短引用路径。这个过程由findLeakTrace来完成，实际上寻找最短引用路径的逻辑是封装在PathsFromGCRootsComputerImpl类的getNextShortestPath和processCurrentReferrefs方法中

##### 泄漏的通知

当找到我们的内存泄漏的路径后，会调用**AbstractAnalysisResultService.sendResultToListener**将结果交给**DisplayLeakService**类来进行处理。

```java
//AbstractAnalysisResultService.java
public static void sendResultToListener(@NonNull Context context,
    @NonNull String listenerServiceClassName,
    @NonNull HeapDump heapDump,
    @NonNull AnalysisResult result) {
  Class<?> listenerServiceClass;
  try {
    //通过反射获取到一个类信息
    listenerServiceClass = Class.forName(listenerServiceClassName);
  } catch (ClassNotFoundException e) {
    throw new RuntimeException(e);
  }
  Intent intent = new Intent(context, listenerServiceClass);
  //将结果保存到文件中，然后将文件路径传递给service
  File analyzedHeapFile = AnalyzedHeap.save(heapDump, result);
  if (analyzedHeapFile != null) {
    intent.putExtra(ANALYZED_HEAP_PATH_EXTRA, analyzedHeapFile.getAbsolutePath());
  }
  //启动服务，然后传递内存泄漏分析的结果文件所对应的位置
  ContextCompat.startForegroundService(context, intent);
}
```

这里会启动一个DisplayLeakService服务，传递了对应的内存泄漏分析结果的文件路径信息。

然后通过onHandleIntent()->onHandleIntentInForeground()->onHeapAnalyzed()。最终调用了**DisplayLeakService**的**onHeapAnalyzed**方法

```java
protected final void onHeapAnalyzed(@NonNull AnalyzedHeap analyzedHeap) {
    HeapDump heapDump = analyzedHeap.heapDump;
    AnalysisResult result = analyzedHeap.result;
    //根据泄漏的信息，生成提示的String字符串
    String leakInfo = leakInfo(this, heapDump, result, true);
    CanaryLog.d("%s", leakInfo);
    //重命名.hprof文件
    heapDump = renameHeapdump(heapDump);
    //保存分析的结果
    boolean resultSaved = saveResult(heapDump, result);
    //结果表头
    String contentTitle;
    if (resultSaved) {
        PendingIntent pendingIntent = DisplayLeakActivity.createPendingIntent(this, heapDump.referenceKey);
        if (result.failure != null) {
            //分析失败
            contentTitle = getString(R.string.leak_canary_analysis_failed);
        } else {
            String className = classSimpleName(result.className);
            if (result.leakFound) {//检测到内存泄漏
                if (result.retainedHeapSize == AnalysisResult.RETAINED_HEAP_SKIPPED) {
                    if (result.excludedLeak) {//被排除的检测结果
                        contentTitle = getString(R.string.leak_canary_leak_excluded, className);
                    } else {
                        contentTitle = getString(R.string.leak_canary_class_has_leaked, className);
                    }
                } else {
                    String size = formatShortFileSize(this, result.retainedHeapSize);
                    if (result.excludedLeak) {
                        contentTitle = getString(R.string.leak_canary_leak_excluded_retaining, className, size);
                    } else {
                        contentTitle = getString(R.string.leak_canary_class_has_leaked_retaining, className, size);
                    }
                }
            } else {
                //未检测到内存泄漏
                contentTitle = getString(R.string.leak_canary_class_no_leak, className);
            }
        }
        String contentText = getString(R.string.leak_canary_notification_message);
        //***重点方法***显示一个Notification通知
        showNotification(pendingIntent, contentTitle, contentText);
    } else {
        onAnalysisResultFailure(getString(R.string.leak_canary_could_not_save_text));
    }
    //钩子函数，可以重写此方法，将内存的泄露信息和对应的.hprof文件上传到服务器。
    // 需要注意，leakfind和excludedLeak的情况都会调用这个方法
    afterDefaultHandling(heapDump, result, leakInfo);
}
```

这个服务的作用就是将我们分析之后的泄漏路径的相关信息通过Notification的通知形式，告知用户具体的内存泄漏情况。

在程序的最后有一个afterDefaultHandling方法，这个方法是一个空实现，用户可以覆写这个方法来实现将内存泄漏的信息上传到服务器的功能

到这里为止LeakCanary的整个实现流程解析完成了。

### 学习到的新知识

整篇的学习，还是学到了一些之前没有认识到的东西的。

1. 主要是通过**registerActivityLifecycleCallbacks**来注册对于我们销毁的Activity的监听。
2. 使用了弱引用的**引用队列**方式对于我们已经销毁的Activity的引用信息进行监控，检测其是否被回收。
3. 对于执行垃圾回收需要使用**Runtime.getRuntime().gc()**。
4. 可以使用**CountDownLatch**来实现线程之间的同步处理。比如说这套源码里面对于showToast的处理。
5. 不同的Android版本本身可能就存在一些内存泄漏的情况。
6. LeakCanary可以通过覆写**afterDefaultHandling**方法来实现对于内存泄漏信息的自行处理

源码解析项目地址：[leakcanary-source](https://github.com/kailaisi/leakcanary-source.git)

> 本文由 [开了肯](http://www.kailaisii.com/) 发布！ 
>
> 同步公众号[开了肯]

![image-20200404120045271](http://cdn.qiniu.kailaisii.com/typora/20200404120045-194693.png)
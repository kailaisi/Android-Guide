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





因为当Activity执行该回调方法的时候，已经创建了对应的**FragmentManager**。我们可以对

​	注册监听的实现方案：

销毁后放入到弱引用WeakReference中

将WeakReference关联到ReferenceQueue

查看Reference中是否存在Activity的引用。

如果泄露，则Dump出heap信息，然后分析泄露路径



> 源码解析项目地址：[leakcanary-source](https://github.com/kailaisi/leakcanary-source.git)
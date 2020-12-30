## Android启动优化

应用的启动是给客户的第一体验，就像相亲的时候，第一印象是最重要的（😃，我没相过亲，反正他们都那么说）。如果我们的应用启动比较慢的话，哪怕应用内容很丰富，也很难再建立一个完美的形象了。





### 基础知识![Android启动优化](http://cdn.qiniu.kailaisii.com/typora/20200705171316-7442.png)

#### 启动流程

之前发布过一些列和应用启动相关的文章。

[Android启动流程源码解析（一）](https://mp.weixin.qq.com/s/ASjx3aR69rfVKrjeUM94iw)

[Android启动流程源码解析（二）](https://mp.weixin.qq.com/s/EYO3WZIt8IR6e48LuEMmFA)

[Android的inflate源码详解](https://mp.weixin.qq.com/s/46PBiGZSfTaI-UZghCWNtg)

[Android应用进程的创建姿势](https://mp.weixin.qq.com/s/IVU0MYgrh8xDY5vuNhsuuQ)

[Android之窗口布局绘制分析](https://mp.weixin.qq.com/s/XRgd-OEDMSjIjfzoaJ0pKQ)

这一系列文章，将从点击应用图标开始之后，**应用进程的创建**，到**生命周期的调用**，再到**布局的加载绘制**，直到页面展示到我们面前的所有过程都进行了一系列的源码剖析。具体的流程就不再进行详细说明了。我们只放一个应用启动流程图来总结一下。![image-20200630220520419](http://cdn.qiniu.kailaisii.com/typora/20200630220524-215293.png)

从图上可以看到整个启动流程可以划分为：

**IPC->Process.start->ActivityThread->bindApplication->LifeCycle->ViewRootImpl**

其中前半部分都是系统内部的函数调用。**我们所能够操作的地方，是从Application的attachBaseContext()的位置开始。**从感官来说，**整个启动结束，则是页面展示在用户面前为止**。

#### 启动分类

对于应用，[官方](https://developer.android.google.cn/topic/performance/vitals/launch-time)根据不同的启动状态，将应用分为了三类：**冷启动、温启动、热启动**。其中冷启动中，引用从头开始启动。而另外两种状态，系统只需要将后台运行的应用带入前台即可。

##### 冷启动

冷启动是指应用从头开始启动：系统进程在冷启动后创建进程。也就是包含了我们上面所说的整个的启动流程。

**特点**

耗时多，是应用启动快慢的衡量标准。

**冷启动需要执行的任务：**

系统级别任务：

> 1. 加载并启动应用。
> 2. 在启动后立即显示应用的空白启动窗口。
> 3. 创建应用进程

应用进程级别任务：

> 1. 创建应用对象。
> 2. 启动主线程。
> 3. 创建主Activity
> 4. 扩充视图。
> 5. 布局屏幕。
> 6. 执行初始化绘制。

**在进行创建应用和创建Activity的过程中最可能出现性能优化问题。这也是我们性能优化需要重点关注的地方。**

应用创建

应用启动时，空白的启动窗口将一直保留在屏幕上，直到首次完成应用绘制。如果覆写*Application.onCreate()*方法，那么就会调用对象的*onCreate()*方法。之后，应用生成主线程，并用其执行创建主Activity的任务。

Activity创建

在应用创建完成以后，Activity将执行以下操作：

1. 初始化值
2. 调用构造函数
3. 根据Activity的当前生命周期状态，调用相应的回调方法，例如*Activity.onCreate()*。

通常*onCreate()*方法对于加载时间影响最大。因为它会执行布局文件的*加载(文件IO)*和*绘制(反射)*以及对象的初始化工作。

##### 热启动

热启动比冷启动简单，开销更低。是指APP并没有被后台杀死，**系统所有的工作就是将Activity带到前台**。

**特点**

如果所有的Activity仍然主流在内存中，应用就不需要执行对象的初始化、布局文件的加载以及绘制等过程。

但是，如果内存为了响应内存整理事件（如*onTrimMemory()*）而被完全清楚，则需要重新创建对应的对象。

热启动从启动到显示到屏幕上的过程和冷启动相似：在应用完成Activity呈现之前，都会显示空白屏幕。

##### 温启动

温启动时App的进程仍然存在，只需要进行应用进程级别的任务即可。他的开销比热启动要高。

> 1. 创建应用对象。
> 2. 启动主线程。
> 3. 创建主Activity
> 4. 扩充视图。
> 5. 布局屏幕。
> 6. 执行初始化绘制。

一些常见的温启动场景：

> 1. 用户双击返回退出应用。
> 2. APP由于内存不足被回收。

#### 启动过长

Google对于启动耗时有一套判断规则：

> * 冷启动超过5秒
> * 温启动超过2秒
> * 热启动超过1.5秒

对于启动耗时，最明显的现象就是当我们点击应用图标到所需要的数据展示在我们面前所使用的时间。从实际情况说，冷启动超过5秒应该算已经不能容忍的情况了。最好的启动当然是秒启动。

#### CPU密集 VS IO密集

##### CPU密集

CPU密集型也叫计算密集型，指的是系统的硬盘、内存性能相对CPU要好很多。此时，系统大部分状态都在进行计算。而I/O操作比较少。

##### IO密集型

IO密集型指的是系统的CPU性能相对硬盘、内存要好很多。此时，系统大部分时间CPU都在等待I/O等读写操作。CPU的利用率并不是很高。

### 启动时间测量

既然要做启动性能的优化，那么首先要跟踪应用启动所需要的时间指标。对于启动时间可以分为初步显示所用的时间以及完全显示所用时间。

#### 初步显示所用时间

初步显示包括了以下事件序列：

> 1. 启动进程。
> 2. 初始化对象。
> 3. 创建并初始化 Activity。
> 4. 扩充布局。
> 5. 首次绘制应用。

初步显示所用的时间的测量更加方便一些。

##### **系统日志**

在Android4.4（API级别19）及更高版本中，logcat包含了一个输出行，其中包含名为 `Displayed` 的值。此值代表从启动进程到屏幕完成对应Activity的绘制所用的时间。

由于该log属于系统服务器，而不是应用本身，所以需要在logcat中停用过滤器。

![GIF 2020-7-5 17-23-17](http://cdn.qiniu.kailaisii.com/typora/20200705172520-331048.gif)

这里我们在手机上启动多个应用，可以通过日志看到对应的不同应用的启动时间。

##### adb指令方案

可以通过ADB命令运行应用来测量初步显示所用的时间。示例如下：

```
adb [-d|-e|-s <serialNumber>] shell am start -S -W
    com.kailaisi.app/.MainActivity
    -c android.intent.category.LAUNCHER
    -a android.intent.action.MAIN
```

`Displayed` 指标和以前一样出现在 logcat 输出中。您的终端窗口还应显示以下内容：

```
Starting: Intent
    Activity: com.kailaisi.app/.MainActivity
    ThisTime: 2044
    TotalTime: 2044
    WaitTime: 2054
    Complete
```

在显示的窗口中，显示的几个方法：

- ThisTime：最后一个Acitivty启动耗时

- TotalTime：所有Activity启动耗时

- WaitTime：AMS启动Activity的耗时

无论是系统日志方案，还是adb指令方案，都**只适合在线下使用**。而且其所统计的时间并不严谨，也不是最精确的时间。所以只能为我们指明大体的优化方向。

#### 完全显示所用时间

完全显示所用时间是指：**从应用启动到完全显示所有资源和视图层次结构所用的时间。**

在应用执行延迟加载时，此数据很有用。延迟加载不会阻止窗口的初步绘制，但是会异步加载资源并更新视图层次结构。

如果延迟加载，应用初步显示不包括所有资源，比如说只绘制了一些文本，但是尚未显示应用从网络中获取的图片等。

##### 系统方法

在延迟加载完成以后，手动调用 `reportFullyDrawn()` 方法，让系统知道Activity已经完成延迟加载。使用此方法，logcat显示的值为从创建应用对象到调用 `reportFullyDrawn()` 方法所用的时间。logcat输出的实例如下：

```verilog
system_process I/ActivityManager: Fully drawn {package}/.MainActivity: +1s54ms
```

##### 手动打点

对于整个冷启动过程，我们所能够操作的地方，是从Application的`attachBaseContext()`的位置开始，然后到整个启动结束，则是页面展示在用户面前为止。

通过在`attachBaseContext()`的位置记录启动时间，然后在页面显示出来以后，记录结束时间，通过两个时间的对比来统计启动完全显示所用的时间。

手动打点的启动时间记录相对来说更加精确，**可以线上使用**，而且也是我们**推荐使用**的方案。

### 优化方向

如果发现显示时间比希望的时间长。那么我们的应用启动存在着问题。需要识别启动过程中的瓶颈并解决。

在冷启动过程中，对于系统级别任务，我们基本无法插手，而在**应用级别的创建应用和创建 Activity 的过程最可能出现性能优化问题。这也是我们性能优化需要重点关注的地方**。

### 工具

#### traceview+systrace

关于traceview和systrace的使用方法，在[深入理解内存优化]()一文中我们已经详细讲解过，这里就不再进行赘述了。

#### Perfetto 

Perfetto 是 Android 10 中引入的全新平台级跟踪工具。适用于Android中更加通用和复杂的开源跟踪项目。与Systrace不同，它提供数据源超级，可让你以protobuf编码的二进制流形式记录任意长度的跟踪记录。可以抓取平台和app的 `trace` 信息，是用来取代 `systrace` 的。

### 方法耗时统计

对于一些常用的三方类库，可能需要我们在引用启动的时候进行初始化工作，比如bugly、umeng、推送、Weex等等。官方推荐的方法都是在我们的Application中进行初始化。

在进行启动优化的开始阶段，我们要确定这些初始化方法的耗时。

#### 常规方式

通过手动埋点。

```java
        long start = System.currentTimeMillis();
        //TX Bugly
        initBugly();
        long cost = System.currentTimeMillis() - start;
        Log.d(TAG, "initBugly耗时:" + cost);
        // 初始化百度语音
        start = System.currentTimeMillis();
        initVoice();
        cost = System.currentTimeMillis() - start;
        Log.d(TAG, "initBugly耗时:" + cost);
        start = System.currentTimeMillis();
        //初始化友盟统计
        initUmeng();
        cost = System.currentTimeMillis() - start;
        Log.d(TAG, "initBugly耗时:" + cost);
        start = System.currentTimeMillis();
        //初始化极光推送
        initJpush();
        cost = System.currentTimeMillis() - start;
        Log.d(TAG, "initBugly耗时:" + cost);
```

可以看到这种方式对代码侵入性强，而且工作量大，很容易出现失误。特别不推荐这种方式。

#### AOP方式

AOP是一种切面编程的方式，通过切面来将所需要的代码织入方法中。我们这里使用hujiang的三方库。具体的大家可以去看一下使用方法。我们这里只贴出使用的代码

```java
@Aspect
public class TimeCostAop {

    @Pointcut("call(* com.kailaisii.wan.MyApp.**(..))")
    public void methodInfo() {
    }
    @Around("methodInfo()")
    public void time(ProceedingJoinPoint joinPoint) {
        Signature signature=joinPoint.getSignature();
        String name=signature.toShortString();
        try {
            long start = System.currentTimeMillis();
            joinPoint.proceed();
            long end = System.currentTimeMillis();
            Log.e(TAG, name +" cost: "+ (end - start));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
}
```

这种方法能够对所有的方法进行自动的耗时统计。对原来的代码没有任何的侵入性，而且修改方便。

### 优化

对于启动优化，对于不同的问题，采用不同的实现方案。对于冷启动，有两个问题需要我们去解决。

1. 空白启动窗口问题
2. 应用启动时间比较长的问题

#### 投机取巧

白屏问题，其原因是因为还没有加载到布局，从而一直显示窗口背景。我们可以投机取巧，通过扔一个图片“假装”布局已经加载完成了。从而“欺骗”用户，给用户以感官上的错觉。

1. 让窗口透明
2. 给窗口增加背景。

##### 透明窗口法

将透明的主题设置到启动activity上。

```java
<style name="TransluteTheme" parent="AppTheme">
    <item name="android:windowNoTitle">true</item>
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:screenOrientation">portrait</item>
  </style>
```

使用<item name="android:windowBackground">@android:color/transparent</item>设置背景为透明。

![img](https://upload-images.jianshu.io/upload_images/8669504-9acfb3904218f95e.gif?imageMogr2/auto-orient/strip|imageView2/2/w/270/format/webp)

这种方案给用户一种点击了桌面图标，但是应用过一段时间才显示出来，并没有达到秒速启动的效果。有“甩锅”的嫌疑。所以这种方案一般用的比较少。

##### 伪布局做法（主流做法）

将图片设置到启动Activity中。

```java
    <style name="SplashTheme" parent="AppTheme">
        <item name="android:windowBackground">@mipmap/splashbg</item>
        <item name="android:windowFullscreen">true</item>
        <item name="android:windowContentOverlay">@null</item>
    </style>
```

![img](https://upload-images.jianshu.io/upload_images/8669504-1f871d593df4ea5b.gif)

可以看到，点击图标就立即加载了窗口，显示出了背景图。但是其实这种也属于欺骗性方法，对于实际的启动时间没有任何的优化效果。

#### 异步优化

既然Application的*onCreate()*中执行各种初始化方法比较耗时，那么我们可以通过创建子线程，由**子线程分担主线程任务，并行减少时间**。

##### 常规方案

 常规方案肯定就是通过创建线程来执行我们的任务了。这里我们一般会**采用线程池**的方法来进行处理，防止创建多个线程导致抢占CPU资源。

```java
 //MyApp.java
 	private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    @Override
    public void onCreate() {
        super.onCreate();
        ExecutorService pool = Executors.newFixedThreadPool(CORE_POOL_SIZE);
        pool.submit(new Runnable() {
            @Override
            public void run() {
                //Logger信息初始化
                initLogger();
            }
        });
       pool.submit(new Runnable() {
           @Override
           public void run() {
               //TX Bugly
               Bugly.init(getApplicationContext(),"12dskkdlsdf",false);
           }
       });
       ...
```

这里我们的线程池的核心数的设置采用了API版本28中AsynTask中的方案(29中的已经将核心线程池数固定为了1)。这种方法效果很棒。但是其实是存在问题的。

**存在的缺陷：**

> 1. 有的库中使用了Handler，如果作者比较low，没有使用*Looper.getMainLooper*来获取，那么会导致崩溃。
> 2. 某些基础库的初始化或者业务之间存在依赖关系，B需要等待A初始化完成之后才能初始化。
> 3. 代码不优化。各种任务都需要放到线程池去执行。
> 4. 维护成本比较高。
> 5. 有些基础库必须保证在进入首屏页前初始化完成。

对于以上缺陷，可能会有解决方案来解决。比如说第5种，可以通过CountDownLatch这种同步方案来进行解决。

但是总体来说其他问题的缺陷还是比较难处理的。所以并不推荐使用这种异步方案。

##### 启动器方案

启动器方案的核心思想是：**充分利用多核CPU，自动梳理任务顺序**。

其具体的步骤：

1. 将启动的任务，通过封装，封装为一个Task任务。
2. 根据所有的任务依赖关系生成有向无环图。
3. 多线程依照排序后的优先级依次进行执行。

这部分的代码实现可以查看：[https://github.com/NoEndToLF/AppStartFaster](https://github.com/NoEndToLF/AppStartFaster)

#### 延迟初始化

有一些任务可能不是App启动后就马上使用的。对于这些任务我们就可以通过延迟加载来进行处理。从而减少我们的应用启动时间。

##### 常规方案

在Application或者在首屏页打开之后简单粗暴的通过handler.postDelay()方法，在一段时间之后去执行。

这种常规方案简单易行，但是存在着明显的缺陷

缺陷：

> 1. post之后，用户可能正在执行操作，导致卡顿了。
> 2. 时机不可控。

##### 更优方案

核心思想：**将延迟任务进行分批处理，在应用空闲的时候去进行初始化。**

这种方式主要通过idlehandler的特性来进行初始化工作。

```java
import android.os.Looper;
import android.os.MessageQueue;

import com.optimize.performance.launchstarter.task.DispatchRunnable;
import com.optimize.performance.launchstarter.task.Task;

import java.util.LinkedList;
import java.util.Queue;

public class DelayInitDispatcher {

    private Queue<Task> mDelayTasks = new LinkedList<>();

    private MessageQueue.IdleHandler mIdleHandler = new MessageQueue.IdleHandler() {
        @Override
        public boolean queueIdle() {
            if(mDelayTasks.size()>0){
                Task task = mDelayTasks.poll();
                new DispatchRunnable(task).run();
            }
            return !mDelayTasks.isEmpty();
        }
    };

    public DelayInitDispatcher addTask(Task task){
        mDelayTasks.add(task);
        return this;
    }

    public void start(){
        Looper.myQueue().addIdleHandler(mIdleHandler);
    }

}
```

这里将上面所讲的启动器方案和IdelHandler进行结合。大幅度的提升App初始化的性能以及初始化代码的优雅度。

#### 懒加载

对于一些功能，在只有在特定的页面中才会使用。对于这种功能，其实我们在使用的时候再去加载。比如高德地图、百度语音等功能。

#### 其他

##### 调整IO任务数

在基础知识里面我们将了CPU密集型和IO密集型任务的区别。如果在通过systrace进行分析的过程中，发现任务的cpu time相对来说比较短的话，说明这时候主要在进行IO操作。我们可以**增加多种IO操作的任务，从而让CPU不被浪费**。

##### 提前加载SharedPreferences

SharedPreferences是线程安全的。在第一次 getSharedPreferences 会读取磁盘文件**【开辟单线程池，异步读取，get、set操作在未把磁盘数据加载完毕之前的，都会卡住等待】**。

所以对于SharedPreferences我们最好做一种提前初始化的工作。

在MultiDex之前的阶段，CPU是利用不满的，而**SharedPreferences**的加载工作属于IO型操作，所以可以将对SharedPreferences的提前加载工作放到这里来执行。

具体方法：

> 1. 覆写attachBaseContext()，执行getSharedPreferences 方法
> 2. 对sp的操作需要使用context，需要先调用super.attachBaseContext方法。因为这个方法才会对context进行赋值。

也就是覆写attachBaseContext()

如果项目对于存储性能要求非常高的情况，可以考虑放弃系统的SharedPreference存储，推荐你使用腾讯的高性能组件[MMKV](https://github.com/Tencent/MMKV)。

##### 不启动子进程

子进程会共享CPU资源，如果在启动过程中启动子进程，会导致主进程的CPU紧张。

另一个，App的启动周期：onAttachBase->ContentProvider->onCreate。所以不要在Application中启动Service、广播、ContentProvider等操作。

##### 黑科技：抑制GC

这种黑科技，是支付宝团队所采用的一种方案。

Java的GC机制会阻塞 Java 程序的执行，占用 CPU 资源，占用额外内存。

而抑制GC方案，就是在App启动的过程中，通过修改内存中的Dalvik库文件libdvm.so影响Dalvik的行为，从而阻止Davlik在此过程中进行垃圾回收。

##### 私藏：CPU锁频

这是个秘密~~

### 总结

本篇文章从启动流程、启动分类、启动时间测量、启动检测工具、启动优化等几个方面，详细的阐述了如何进行启动的优化。

### 参考

[App立即启动方案,怎样解决启动白屏](https://www.jianshu.com/p/7469704e3ba0)

[应用启动时间](https://developer.android.google.cn/topic/performance/vitals/launch-time)

[彻底搞懂 SharedPreferences](https://www.jianshu.com/p/ab0160989514)

[android性能优化(四)之启动优化](https://www.baidu.com/link?url=G1v-j7Darxtew6rVrOCNOP3VRMndPmdgVsiFPun_HKaH0E_QTUZNzQAMgy5Vn4O9FANdWDYi7RDYd4gM73KhpazwUOHzTcp9Svznh83zD6m&wd=&eqid=f32a74ae00045ba2000000035f019377)

[谷歌](https://developer.android.google.cn/topic/performance/tracing)

[启动器源码库](https://github.com/NoEndToLF/AppStartFaster)

[Android性能优化之启动优化](https://zhuanlan.zhihu.com/p/63188694)

[对Android设备CPU进行锁频](https://blog.csdn.net/weixin_33922672/article/details/90395695)

> 本文由 [开了肯](http://www.kailaisii.com/) 发布！ 
>
> 同步公众号[开了肯]

![image-20200404120045271](http://cdn.qiniu.kailaisii.com/typora/20200404120045-194693.png)
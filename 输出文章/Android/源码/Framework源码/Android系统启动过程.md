## Android系统启动过程

### 计算机是如何启动的

计算机的硬件包括：CPU，内存，硬盘，显卡，显示器，键盘鼠标等输入输出设备。所有的软件都是存放在硬盘中，程序执行时，需要将程序从硬盘上读取到内存中，然后加载到CPU中来运行。当按下开机键时，内存中什么都没有，因此需要借助某种方式，将操作系统加载到内存中，而完成这项任务的就是BIOS。

- 引导阶段

BIOS：BIOS是主板芯片上的一个程序，计算机通电后，第一件事情就是读取BIOS。

BIOS首先进行硬件检测，检查计算机硬件能否满足运行的基本条件。如果硬件出现问题，主板发出不同的蜂鸣声，启动停止。如果没有问题，屏幕会显示CPU，内存，硬盘等信息。

硬件自检完成后，BIOS将控制权交给下一个阶段的启动程序。这时候BIOS需要知道下一个启动程序存放在哪个设备中。也就是BIOS需要一个外部存储设备的排序。优先交给排在前面的设备。这就是我们在BIOS中设置的启动排序。

当第一个存储设备被激活后，设备读取设备的第一个扇区，也就是前512字节。如果这512个字节的最后两个字节是0x55和0xAA，表明设备是可以用作系统启动的。如果不是，那么就会顺序启动下一个设备。

这前512个字节，就叫做“主引导记录”（缩写MBR）。它负责磁盘操作系统对硬盘进行读写时分区合法型判断、分区引导信息定位。MBR不属于任何一个CIA做系统，它先于操作系统而被调入内存，并发挥作用。然后才将控制权交给主分区内的操作系统，并用主分区信息来管理硬盘。

MBR主要作用是告诉计算机到硬盘的哪个位置去找操作系统。计算机从MBR中读取前446字节的机器码后，不再转交控制权，而是运行实现安装的“启动管理器”（boot loader），由用户选择启动哪个操作系统。

- 加载内核阶段

选择完操作系统以后，控制权交给操作系统，操作系统内核被载入内存。

以Linux为例，先载入/boot下面的kernel。内核加载完成后，运行第一个程序 /sbin/init。它根据配置文件产生init进程。它是Linux启动后的第一个进程，pid为1.其他进程都是它的后代。

然后init线程加载系统的各个模块。比如：窗口程序和网络程序，直至执行/bin/login程序执行，跳出登录页面，等待用户输入用户名密码。

至此，系统启动完成。

### Android的启动过程

Android是基于Linux系统的。但是 它没有BIOS程序，取而代之的是BootLoader（系统启动加载器）。类似于BIOS，在系统加载前，用于初始化硬件设备，最终调用系统内核准备好环境。在Android中没有硬盘，而是ROM，类似于硬盘存放操作系统，用户程序等。ROM跟硬盘一样也会划分为不同的区域，用于放置不同的程序，在Android中主要划分为以下几个区域：

- /boot :存放引导程序，包括内核和内存操作程序
- /system：相当于电脑C盘，存放Android系统和系统应用
- /recover:回复分区。可以进入该分区进行系统回复
- /data:用户数据区，包含了用户的数据：联系人、短信、设置、用户安装的程序
- /cache：安卓系统缓存区，保存系统经常访问的数据和应用程序
- /misc：杂项内容
- /sdcard:用户自己的存储区域。存放照片视频等

Android系统启动跟PC相似。当开机时，首先加载BootLoader，BootLoader会读取ROM找到系统并将内核加载进RAM中。

当内核启动后会初始化各种软硬件环境，加载驱动程序，挂载跟文件系统。最后阶段会启动执行第一个用户空间进程init进程。

### init进程

init是用户的第一个进程，pid=1。kernal启动后会调用/system/core/init/init.cpp的main()方法。

```cpp
int main(int argc,char ** argv){
    ...
    if(is_first_stage){
        //创建和挂在启动所需要的文件目录
        mount("tmpfs","/dev","tmpfs",MS_NOSUID,"mode=0755");
        mkdir("/dev/pts",0755);
        //创建和挂在很多...
        ...
    } 
    ...
    //对属性服务进行初始化
    property_init();
    ...
    //用于设置子进程信号处理函数（如Zygote），如果子进程异常退出，init进程会调用该函数中设定的信号处理函数来处理
    signal_handler_init();
    ...
    //启动属性服务
    start_property_service();
    ...
    //解析init.rc配置文件
    parser.ParseConfig("/init.rc");
}
```

首先初始化 Kernel log，创建一块共享的内存空间，加载 /default.prop 文件，解析 init.rc 文件。

### init.rc 文件
init.rc 文件是 Android 系统的重要配置文件，位于 /system/core/rootdir/ 目录中。 主要功能是定义了系统启动时需要执行的一系列 action 及执行特定动作、设置环境变量和属性和执行特定的 service。

init.rc 脚本文件配置了一些重要的服务，init 进程通过创建子进程启动这些服务，这里创建的 service 都属于 native 服务，运行在 Linux 空间，通过 socket 向上层提供特定的服务，并以守护进程的方式运行在后台。

通过 init.rc 脚本系统启动了以下几个重要的服务：

- service_manager：启动 binder IPC，管理所有的 Android 系统服务
- mountd：设备安装 Daemon，负责设备安装及状态通知
- debuggerd：启动 debug system，处理调试进程的请求
- rild：启动 radio interface layer daemon 服务，处理电话相关的事件和请求
- media_server：启动 AudioFlinger，MediaPlayerService 和 CameraService，负责多媒体播放相关的功能，包括音视频解码
- surface_flinger：启动 SurfaceFlinger 负责显示输出
- zygote：进程孵化器，启动 Android Java VMRuntime 和启动 systemserver，负责 Android 应用进程的孵化工作
在这个阶段你可以在设备的屏幕上看到 “Android” logo 了。

以上工作执行完，init 进程就会进入 loop 状态。

###  service_manager 进程

ServiceManager 是 Binder IPC 通信过程中的守护进程，本身也是一个 Binder 服务。ServiceManager 进程主要是启动 Binder，提供服务的查询和注册。

### surface_flinger 进程
SurfaceFlinger 负责图像绘制，是应用 UI 的和兴，其功能是合成所有 Surface 并渲染到显示设备。SurfaceFlinger 进程主要是启动 FrameBuffer，初始化显示系统。

### media_server 进程
MediaServer 进程主要是启动 AudioFlinger 音频服务，CameraService 相机服务。负责处理音频解析播放，相机相关的处理。

### Zygote 进程

zygote有两个作用：启动systemService和孵化应用进程。

Zygote 进程孵化了所有的 Android 应用进程，是 Android Framework 的基础，该进程的启动也标志着 Framework 框架初始化启动的开始。

Zygote启动主要调用app_main.cpp的main()中的AppRuntime的start方法来启动Zygote进程

```cpp
int main(int argc,char* const argv[]){
    while( i < argc ){
        const char* arg=argv[i++];
        if(strcmp(arg,"--zygote")==0){
            //如果当前进程在Zygote中，则设置zygote=true
            zygote=true;
            niceName=ZYGOTE_NICE_NAME;
        }else if(strcmp(arg,"--start-system-server")==0){
            //如果当前进程在SystemServer中，将startSystemServer=true
            startSystemServer=true;
        }
    }
    //承接上面Init进程中的代码
    if(zygote){
        //启动Zygote进程
        runtime.start("com.android.internal.os.ZygoteInit",args,zygote);
    }
}
```

Zygote 服务进程的主要功能：

- 注册底层功能的 JNI 函数到虚拟机
- 预加载 Java 类和资源
- fork 并启动 system_server 核心进程
- 作为守护进程监听处理“孵化新进程”的请求

当 Zygote 进程启动后, 便会执行到 frameworks/base/cmds/app_process/App_main.cpp 文件的 main() 方法。

### system_server 进程

system_server 进程 由 Zygote 进程 fork 而来。

```java
//首先会调用 ZygoteInit.startSystemServer() 方法
ZygoteInit.startSystemServer()  
//fork 子进程 system_server，进入 system_server 进程。
ZygoteInit.handleSystemServerProcess()  
//设置当前进程名为“system_server”，创建 PathClassLoader 类加载器。
RuntimeInit.zygoteInit()    
//重定向 log 输出，通用的初始化（设置默认异常捕捉方法，时区等），初始化 Zygote -> nativeZygoteInit()。
nativeZygoteInit()  
//方法经过层层调用，会进入 app_main.cpp 中的 onZygoteInit() 方法。
app_main::onZygoteInit()// 启动新 Binder 线程。
applicationInit()   

//方法经过层层调用，会抛出异常 ZygoteInit.MethodAndArgsCaller(m, argv), ZygoteInit.main() 会捕捉该异常。
ZygoteInit.main()  

//开启 DDMS 功能，preload() 加载资源，预加载 OpenGL，调用 SystemServer.main() 方法。

SystemServer.main() 
//先初始化 SystemServer 对象，再调用对象的 run() 方法。

SystemServer.run() 
//准备主线程 looper，加载 android_servers.so 库，该库包含的源码在 frameworks/base/services/ 目录下。
```

system_server 进程启动后将初始化系统上下文（设置主题），创建系统服务管理 SystemServiceManager，然后启动各种系统服务

```java
startBootstrapServices(); // 启动引导服务
//该方法主要启动服务 ActivityManagerService，PowerManagerService，LightsService，DisplayManagerService，PackageManagerService，UserManagerService。
//设置 ActivityManagerService，启动传感器服务。

startCoreServices();      // 启动核心服务
//该方法主要
//启动服务 BatteryService 用于统计电池电量，需要 LightService。
//启动服务 UsageStatsService，用于统计应用使用情况。
//启动服务 WebViewUpdateService。

startOtherServices();     // 启动其他服务
//该方法主要启动服务 InputManagerService，WindowManagerService。
//等待 ServiceManager，SurfaceFlinger启动完成，然后显示启动界面。
//启动服务 StatusBarManagerService，
//准备好 window, power, package, display 服务：
//	- WindowManagerService.systemReady()
//	- PowerManagerService.systemReady()
//	- PackageManagerService.systemReady()
//	- DisplayManagerService.systemReady()

```

所有的服务启动后会注册到ServiceManager。

ActivityManagerService 服务启动完成后，会进入 ActivityManagerService.systemReady()，然后启动 SystemUI，WebViewFactory，Watchdog，最后启动桌面 Launcher App。

最后会进入循环 Looper.loop()。

### ActivityManagerService 启动

启动桌面 Launcher App 需要等待 ActivityManagerService 启动完成。我们来看下 ActivityManagerService 启动过程。

```c++
ActivityManagerService(Context) 
//创建名为“ActivityManager”的前台线程，并获取mHandler。
//通过 UiThread 类，创建名为“android.ui”的线程。
//创建前台广播和后台广播接收器。
//创建目录 /data/system。
//创建服务 BatteryStatsService。

ActivityManagerService.start()  //启动电池统计服务，创建 LocalService，并添加到 LocalServices。

ActivityManagerService.startOtherServices() -> installSystemProviders()
//安装所有的系统 Provider。

ActivityManagerService.systemReady()
//恢复最近任务栏的 task。
//启动 WebView，SystemUI，开启 Watchdog，启动桌面 Launcher App。
//发送系统广播。

```

启动桌面 Launcher App，首先会通过 Zygote 进程 fork 一个新进程作为 App 进程，然后创建 Application，创建启动 Activity，最后用户才会看到桌面。

### 完整的启动流程图

![img](https://img-blog.csdn.net/20180211170703861?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvZnJlZWtpdGV5dQ==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70)

源码解析项目地址：https://github.com/kailaisi/android-29-framwork



> 同步公众号[开了肯]


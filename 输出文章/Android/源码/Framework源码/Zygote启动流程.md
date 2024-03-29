### 对zygote的理解

在Android系统中，zygote是一个native进程，是所有应用进程的父进程。而zygote则是Linux系统用户空间的第一个进程——init进程，通过fork的方式创建并启动的。

### 作用

zygote进程在启动时，会创建一个Dalvik虚拟机实例，每次孵化新的应用进程时，都会将这个Dalvik虚拟机实例复制到新的应用程序进程里面，从而使得每个应用程序进程都有一个独立的Dalvik虚拟机实例。

zygote进程的主要作用有两个：

* 启动SystemServer。
* 孵化应用进程。

### 启动流程

##### 启动入口

Zygote进程在init进程中，通过解析init.zygote.rc配置文件，以service(服务)的方式启动并创建的。

以init.zygote32.rc为例来看下：

##### 脚本讲解

```c
//    system\core\rootdir\init.zygote32.rc
service zygote /system/bin/app_process -Xzygote /system/bin --zygote --start-system-server
    class main
    priority -20
    user root
    group root readproc reserved_disk
    socket zygote stream 660 root system
    socket usap_pool_primary stream 660 root system
    onrestart write /sys/power/state on
    onrestart restart audioserver
    onrestart restart cameraserver
    onrestart restart media
    onrestart restart netd
    onrestart restart wificond
    writepid /dev/cpuset/foreground/tasks
```

<!--服务名称为：zygote
启动该服务执行的命令： /system/bin/app_process
命令的参数： -Xzygote /system/bin --zygote --start-system-server
socket zygote stream 660创建一个名为：/dev/socket/zygote 的 socket，
类型为：stream，权限为：660
onrestart：当服务重启时，执行该关键字后面指定的command-->



**这段脚本要求 init 进程创建一个名为 zygote 的进程，该进程要执行的程序是“/system/bin/app_process”。并且为 zygote 进程创建一个 socket 资源 (用于进程间通信，ActivityManagerService 就是通过该 socket 请求 zygote 进程 fork 一个应用程序进程)。**

后面的**--zygote**是参数，表示启动的是zygote进程。在app_process的main函数中会依据该参数决定执行ZygoteInit还是Java类。

##### 启动过程

zygote要执行的程序便是system/bin/app_process，它的源代码在frameworks/base/cmds/app_process/app_main.cpp

###### App_main::main

```c
int main(int argc, char* const argv[])
{
    ...
    while (i < argc) {
        const char* arg = argv[i++];
        if (strcmp(arg, "--zygote") == 0) {//是否有--zygote参数。这个是启动zygote进程的时候的参数
            zygote = true;
			//进程名称，设置为zygote
            niceName = ZYGOTE_NICE_NAME;
        } else if (strcmp(arg, "--start-system-server") == 0) {//是否有--start-system-server
            startSystemServer = true;
	....
    if (zygote) {
		//最最重要方法。。。如果是zygote进程，则启动ZygoteInit。
        runtime.start("com.android.internal.os.ZygoteInit", args, zygote);
    } else if (className) {
        runtime.start("com.android.internal.os.RuntimeInit", args, zygote);
    } else {
        fprintf(stderr, "Error: no class name or --zygote supplied.\n");
        app_usage();
        LOG_ALWAYS_FATAL("app_process: no class name or --zygote supplied.");
    }
}
```

###### AndroidRuntime::start

```c
void AndroidRuntime::start(const char* className, const Vector<String8>& options, bool zygote)
{
    ...
    JNIEnv* env;
	//重点方法     创建VM虚拟机，参数是指针，可以用于获取返回的值，可以使用env来和Java层来做交互
    if (startVm(&mJavaVM, &env, zygote) != 0) {
        return;
    }
    onVmCreated(env);
    //重点方法      给虚拟机注册一些JNI函数，（系统so库、用户自定义so库 、加载函数等。）
    if (startReg(env) < 0) {
        ALOGE("Unable to register all android natives\n");
        return;
    }

    	//找到类的main方法，并调用。如果是zygote的话，这里就会启动ZygoteInit类的main方法
        jmethodID startMeth = env->GetStaticMethodID(startClass, "main",
            "([Ljava/lang/String;)V");
        if (startMeth == NULL) {
            ALOGE("JavaVM unable to find main() in '%s'\n", className);
            /* keep going */
        } else {
        	//调用main方法。这里通过JNI调用Java方法之后，Zygote(Native层)就进入了Java的世界，从而开启了Android中Java的世界。
            env->CallStaticVoidMethod(startClass, startMeth, strArray);
}
```



```C++
App_main.main
  AndroidRuntime.start
    startVm//创建虚拟机
    startReg//注册JNI函数
    ZygoteInit.main//这里就进入到了Java层了
        registerZygoteSocket//建立IPC的通讯机制
        preload//预加载类和资源
        startSystemServer//启动system_server
        runSelectLoop//等待进程创建的请求
```

> 对应的源码地址：
> /frameworks/base/cmds/app_process/App_main.cpp （内含AppRuntime类）
> /frameworks/base/core/jni/AndroidRuntime.cpp
> /frameworks/base/core/java/com/android/internal/os/ZygoteInit.java
> /frameworks/base/core/java/com/android/internal/os/Zygote.java
> /frameworks/base/core/java/android/net/LocalServerSocket.java

Zygote进程的启动过程中，除了会**创建一个Dalvik虚拟机实例**之外，还会**将Java运行时库加载到进程**中，以及**注册一些Android核心类的JNI方法**到创建的Dalvik虚拟机实例中。

zygote进程初始化时启动虚拟，并加载一些系统资源。这样zygote fork出子进程之后，子进程也会继承能正常工作的虚拟机和各种系统资源，剩下的只需要装载APK文件的字节码就可以运行程序，。

Java应用程序不能以本地进程的形态运行，必须在一个独立的虚拟机中运行。如果每次都重新启动虚拟机，肯定就会拖慢应用程序的启动速度。

注意：APK应用程序进程被zygote进程孵化出来以后，不仅会获得Dalvik虚拟机实例拷贝，还会与Zygote一起共享Java运行时库。



### 参考文献

https://blog.csdn.net/qq_24451593/article/details/80103450

https://blog.csdn.net/tfygg/article/details/52086621

https://blog.csdn.net/chz429/article/details/87514718



源码解析项目地址：https://github.com/kailaisi/android-29-framwork

> 同步公众号[开了肯]

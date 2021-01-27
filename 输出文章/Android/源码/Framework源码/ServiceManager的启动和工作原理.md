### ServiceManager的启动和工作原理

启动进程

所有的系统服务都是需要在ServiceManager中进行注册的，所以ServiceManager作为一个起始的服务，是通过init.rc来启动的。

```c++
    //system\core\rootdir\init.rc	
	//启动的服务，这里是用的服务名称。服务名称是在对应的rc文件中注册并启动的
    start servicemanager
```

具体的服务信息是在servicemanger.rc命名并定义的

```c++
//\frameworks\native\cmds\servicemanager\servicemanager.rc
service servicemanager /system/bin/servicemanager
    class core animation
    user system  //说明以用户system身份运行
    group system readproc
    //说明servicemanager是系统中的关键服务，
    //关键服务是不会退出的，如果退出了，系统就会重启，当系统重启时就会启动用onrestart关键字修饰的进程，
    //比如zygote、media、surfaceflinger等等。
   critical
    onrestart restart healthd
    onrestart restart zygote
    onrestart restart audioserver
    onrestart restart media
    onrestart restart surfaceflinger
    onrestart restart inputflinger
    onrestart restart drm
    onrestart restart cameraserver
    onrestart restart keystore
    onrestart restart gatekeeperd
    onrestart restart thermalservice
    ..
```

servicemanager的入口函数在service_manager.c中

```c++
//frameworks\native\libs\binder\ndk\service_manager.cpp
int main(int argc, char** argv)
{
	//binder_state结构体，用来存储binder的三个信息
    struct binder_state *bs;
	//打开binder驱动，并申请125k字节的内存空间
    bs = binder_open(driver, 128*1024);
    ...
	//将自己注册为Binder机制的管理者
    if (binder_become_context_manager(bs)) {
        ALOGE("cannot become context manager (%s)\n", strerror(errno));
        return -1;
    }
    ...
	//启动循环，等待并处理client端发来的请求
    binder_loop(bs, svcmgr_handler);

    return 0;
}

```

在main函数中主要做了3件事情。

* 打开驱动，并申请了128k字节大小的内存空间
* 将自己注册为Binder机制的管理者
* 启动循环，等待并处理Client端发来的请求



#### 启动Binder机制

#### 发布自己的服务

#### 等待并响应请求





https://www.jianshu.com/p/a90c697d6086
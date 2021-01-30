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

##### binder_open

```c++
//frameworks\native\cmds\servicemanager\binder.c
struct binder_state *binder_open(const char* driver, size_t mapsize)
{
    struct binder_state *bs;
    struct binder_version vers;
    //申请对应的内存空间 
    bs = malloc(sizeof(*bs));
	//打开binder设备文件，这种属于设备驱动的操作方法
    bs->fd = open(driver, O_RDWR | O_CLOEXEC);
	//通过ioctl获取binder的版本号
    if ((ioctl(bs->fd, BINDER_VERSION, &vers) == -1) ||
        (vers.protocol_version != BINDER_CURRENT_PROTOCOL_VERSION)) {
        fprintf(stderr,
                "binder: kernel driver version (%d) differs from user space version (%d)\n",
                vers.protocol_version, BINDER_CURRENT_PROTOCOL_VERSION);
        goto fail_open;
    }
    bs->mapsize = mapsize;
	//mmap进行内存映射，将Binder设备文件映射到进程的对应地址空间，地址空间大小为128k
	//映射之后，会将地址空间的起始地址和大小保存到结构体中，
    bs->mapped = mmap(NULL, mapsize, PROT_READ, MAP_PRIVATE, bs->fd, 0);
    return bs;
}
```

binder_opende的主要功能是打开了Binder的驱动文件，并将文件进行了mmap映射，并将对应的地址空间保存到了结构体中。

##### binder_become_context_manager

```c++
//frameworks\native\cmds\servicemanager\binder.c
int binder_become_context_manager(struct binder_state *bs)
{
    return ioctl(bs->fd, BINDER_SET_CONTEXT_MGR, 0);
}
```

ioctl会调用Binder驱动的**binder_ioctl**函数，去注册成为管理者。

##### binder_loop

将servicemanger注册为Binder的上下文管理者后，它就是Binder机制的“大总管”了，它会在系统运行期间处理Client端的请求，因为请求的时间不确定性，这里采用了无限循环来实现。也就是**binder_loop**

```c++
//frameworks\native\cmds\servicemanager\binder.c
void binder_loop(struct binder_state *bs, binder_handler func)
{
    int res;
    struct binder_write_read bwr;
    uint32_t readbuf[32];

    bwr.write_size = 0;
    bwr.write_consumed = 0;
    bwr.write_buffer = 0;
	//当前线程注册为Binder的指令
    readbuf[0] = BC_ENTER_LOOPER;
	//将BC_ENTER_LOOPER指令写入到Binder驱动，
	//将当前的ServiceManager线程注册为了一个Binder线程(注意ServiceManager本身也是一个Binder线程)。
	//注册为Binder线程之后，就可以处理进程间的请求了
    binder_write(bs, readbuf, sizeof(uint32_t));
	//不断的循环遍历
    for (;;) {
        bwr.read_size = sizeof(readbuf);
        bwr.read_consumed = 0;
        bwr.read_buffer = (uintptr_t) readbuf;
		//使用BINDER_WRITE_READ指令查询Binder驱动中是否有请求。
		//如果有请求，就走到下面的binder_parse部分处理，如果没有，当前的ServiceManager线程就会在Binder驱动中水命，等待新的进程间请求
        res = ioctl(bs->fd, BINDER_WRITE_READ, &bwr);
		//走到这里说明有请求信息。将请求的信息用binder_parse来处理,处理方法是func
        res = binder_parse(bs, 0, (uintptr_t) readbuf, bwr.read_consumed, func);
        
    }
}

```

* servicemanager会先将自己注册为一个Binder线程。因为只有注册成为Binder服务之后才能接收进程间的请求。而注册为Binder服务的指令是**BC_ENTER_LOOPER**。然后通过**binder_write()**方法写入到binder驱动。

```C++
//frameworks\native\cmds\servicemanager\binder.c
int binder_write(struct binder_state *bs, void *data, size_t len)
{
    struct binder_write_read bwr;
    int res;

    bwr.write_size = len;
    bwr.write_consumed = 0;
    bwr.write_buffer = (uintptr_t) data;
    bwr.read_size = 0;
    bwr.read_consumed = 0;
    bwr.read_buffer = 0;
	//BINDER_WRITE_READ既可以读也可以写。关键在于read_size和write_size。
	//如果write_size>0。则是写。如果read_size>0则是读。
	//如果都大于0，则先写，再读
    res = ioctl(bs->fd, BINDER_WRITE_READ, &bwr);
    if (res < 0) {
        fprintf(stderr,"binder_write: ioctl failed (%s)\n",
                strerror(errno));
    }
    return res;
}

```

#### 启动Binder机制

#### 发布自己的服务

#### 等待并响应请求



### ServiceManager服务的获取

在Android中，每个进程获取系统提供的各种系统服务（AMS，PMS，WMS等）都是需要通过ServiceManager才可以。而这些系统服务进行Binder注册，也需要获取ServiceManager服务才可以。在刚才我们讲过，ServiceManager会将自己也注册成为一个Binder服务。

这里我们以SurfaceFling获取ServiceManager服务为例来看一下是如何获取的。

```c++
//frameworks\native\services\surfaceflinger\main_surfaceflinger.cpp
#include <binder/IServiceManager.h>
int main(int, char**) {
    ....
    //获取一个SM对象，相当于是new BpServiceManager(new BpBinder(0))
    sp<IServiceManager> sm(defaultServiceManager());
	//向ServiceManager注册SurfaceFling服务
    sm->addService(String16(SurfaceFlinger::getServiceName()), flinger, false,
                   IServiceManager::DUMP_FLAG_PRIORITY_CRITICAL | IServiceManager::DUMP_FLAG_PROTO);
	//在SurfaceFlinger调用init方法的时候，会初始化Display的相关信息
    startDisplayService(); // dependency on SF getting registered above
    ...
    return 0;
}

```

这里使用的是**defaultServiceManager()**来获取了ServiceManager服务的Binder对象。

#### defaultServiceManager

```c++
//frameworks\native\libs\binder\IServiceManager.cpp
sp<IServiceManager> defaultServiceManager()
{
    std::call_once(gSmOnce, []() {//只调用一次
        sp<IServiceManager> sm = nullptr;
		/*
		 * 1. ProcessState::self()->getContextObject(NULL): 返回的是一个 BpBinder. ServiceManager 的 desc 默认为0.
		 * 2. interface_cast 就是将 BpBinder 封装为 IServiceManager,这样可以直接调用 IServiceManager 的接口.
		*/
        while (sm == nullptr) {//如果不为空，表示设置过了，直接返回
        	//尝试不断的获取ServiceManager对象，如果获取不到，就sleep（1）,
        	//这里之所以会获取不到，是因为ServiceManager和一些通过init.rc启动的服务是同时启动的，不能保证ServiceManager能够优先启动完成。
        	//所以会存在获取ServiceManager的时候获取不到。
            //这里的IServiceManager其实就是IServiceManager
            sm = interface_cast<IServiceManager>(ProcessState::self()->getContextObject(nullptr));
            if (sm == nullptr) {
                sleep(1);
            }
        }
        //进行了一层包装
        gDefaultServiceManager = new ServiceManagerShim(sm);
    });
    return gDefaultServiceManager;
}
```

这里会直接调用**ProcessState::self()->getContextObject(nullptr)**来获取对应的服务。

* ProcessState::self()->getContextObject(NULL): 返回的是一个 BpHwBinder。ServiceManager 的 desc 默认为0。
* interface_cast 就是将 BpBinder 封装为 IServiceManager，
* 创建ServiceManagerShim对象，将IServiceManager进行了一层包装

##### ProcessState::self()

```c++
//system\libhwbinder\ProcessState.cpp
//返回一个ProcessState
sp<ProcessState> ProcessState::self()
{
    Mutex::Autolock _l(gProcessMutex);
    if (gProcess != nullptr) {
        return gProcess;
    }
    gProcess = new ProcessState(kDefaultDriver);
    return gProcess;
}

```

这里会返回一个ProcessState对象。

##### getContextObject

```c++
//system\libhwbinder\ProcessState.cpp
sp<IBinder> ProcessState::getContextObject(const sp<IBinder>& /*caller*/)
{
    //传入的参数是handle。0，
    return getStrongProxyForHandle(0);
}


sp<IBinder> ProcessState::getStrongProxyForHandle(int32_t handle)
{
    handle_entry* e = lookupHandleLocked(handle);
    if (e != nullptr) {
        IBinder* b = e->binder;
        if (b == nullptr || !e->refs->attemptIncWeak(this)) {
			//如果b为空，那么创建一个BpHwBinder
            b = new BpHwBinder(handle);
            e->binder = b;
            if (b) e->refs = b->getWeakRefs();
            result = b;
        } else {
            result.force_set(b);
            e->refs->decWeak(this);
        }
    }

    return result;
}
```

当不存在的时候，这里会创建一个BpHwBinder对象。所以可以理解为最后我们返回的是一个**BpBinder对象**

 这里,有一个设计思想：

1. defaultServiceManager 首先实例化 BpBinder。

2. interface_cast 就是 实例化 BpXXX,并将 BpBinder交给其管理。

Proxy 端的用户无法直接看到 BpBinder, BpBinder 由 BpXXX 持有.用户本身不关心 BpBinder的能力,只关心 IXXX 定义的 接口。所以这里很好的进行了封装。

回到前文的defaultServiceManger方法中，将返回值带入，可以得到

```c++
//注意，方法中传入的handle为0，所以BpBinder参数为0
gDefaultServiceManager = interface_cast<IServiceManager>(new BpBinder(0));
```

##### interface_cast

```c++
//frameworks\native\include\binder\IInterface.h
inline sp<INTERFACE> interface_cast(const sp<IBinder>& obj)
{
    return INTERFACE::asInterface(obj);
}
//INTERFACE带入为IServiceManager之后，得到的代码为
inline sp<IServiceManager> interface_cast(const sp<IBinder>& obj)
{
    return IServiceManager::asInterface(obj);//静态方法所以直接调用
}
```
调用IServiceManager接口的成员函数asInterface，将一个句柄值为0的Binder代理对象封装为一个ServiceManger代理对象。将一个句柄值为0的Binder代理对象封装为一个ServiceManger代理对象。

这里 IServiceManager接口的成员函数asInterface是通过宏IMPLEMENT_META_INTERFACE实现，如下所示：

```c
#define IMPLEMENT_META_INTERFACE(INTERFACE, NAME)                       
    DO_NOT_DIRECTLY_USE_ME_IMPLEMENT_META_INTERFACE(INTERFACE, NAME)    
#endif

#define DO_NOT_DIRECTLY_USE_ME_IMPLEMENT_META_INTERFACE(INTERFACE, NAME)\
    ::android::sp<I##INTERFACE> I##INTERFACE::asInterface(              \
            const ::android::sp<::android::IBinder>& obj)               \
    {                                                                   \
        android::sp<I##INTERFACE> intr;                               \
        if (obj != nullptr) {                                           \
            intr = static_cast<I##INTERFACE*>(                          \
                obj->queryLocalInterface(                               \
                        I##INTERFACE::descriptor).get());               \
            if (intr == nullptr) {                                      \
                intr = new Bp##INTERFACE(obj);                          \
            }                                                           \
        }                                                               \
        return intr;                                                    \
    }                                                                   \
```

带入IServiceManager之后的代码为：

```c++
android::sp<IServiceManager> IIServiceManager::asInterface(const android::sp<android::IBinder>& obj)                                 
{                                                                                     
	android::sp<IServiceManager> intr;                                                    
	if (obj != NULL) {                                                                     
		intr = static_cast<IIServiceManager*>(                                                  
                    obj->queryLocalInterface(IServiceManager::descriptor).get());//返回NULL
		if (intr == NULL) {                
			intr = new BpServiceManager(obj);  //创建了ServiceManager代理对象                     
		}                                          
	｝
	return intr;                                  
}  
```

到这里为止，我们创建了一个BpIServiceManager对象，并将他的接口IServiceManager返回给了调用者。

整体的逻辑可以理解为：**new BpServiceManager(new BpBinder())**。当然了，这只是简化之后的代码，其内部复杂的逻辑现在可以暂不考虑。

### 添加Service

当获取到ServiceManager服务之后，就可以使用addService方法来进行服务的注册了。在获取服务的时候，最终返回的是BpServiceManager对象，所以这里我们可以直接找到对应的添加服务方法

```c++
    virtual status_t addService(const String16& name, const sp<IBinder>& service,
                                bool allowIsolated, int dumpsysPriority) {
        Parcel data, reply;
        data.writeInterfaceToken(IServiceManager::getInterfaceDescriptor());
        data.writeString16(name);
        data.writeStrongBinder(service);
        data.writeInt32(allowIsolated ? 1 : 0);
        data.writeInt32(dumpsysPriority);
        status_t err = remote()->transact(ADD_SERVICE_TRANSACTION, data, &reply);
        return err == NO_ERROR ? reply.readExceptionCode() : err;
    }
```

BpServiceManager的构造函数传入的了BpBinder对象，这里的remote()方法其实就是BpBinder对象。









https://www.jianshu.com/p/a90c697d6086

https://www.jianshu.com/p/c5110f71af58?utm_campaign=maleskine&utm_content=note&utm_medium=seo_notes&utm_source=recommendation

https://blog.csdn.net/jltxgcy/article/details/25953361

https://www.jianshu.com/p/dafbc4751df7
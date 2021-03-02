## ServiceManager的启动和工作原理

### ServiceManager启动

所有的系统服务都是需要在ServiceManager中进行注册的，而ServiceManager作为一个起始的服务，是通过init.rc来启动的。

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

### 系统服务注册

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

系统服务的注册过程主要有2点

* 获取ServiceManager所对应的Binder对象。
* 通过addService注册为系统服务。

#### ServiceManager的Binder对象获取

**defaultServiceManager()**方法就是用来获取ServiceManager服务的Binder对象。

##### defaultServiceManager

```c++
//frameworks\native\libs\binder\IServiceManager.cpp
sp<IServiceManager> defaultServiceManager()
{
    if (gDefaultServiceManager != nullptr) return gDefaultServiceManager;

    {
        AutoMutex _l(gDefaultServiceManagerLock);
		/*
		 * 1. ProcessState::self()->getContextObject(NULL): 返回的是一个 BpBinder. ServiceManager 的 desc 默认为0.
		 * 2. interface_cast 就是将 BpBinder 封装为 IServiceManager,这样可以直接调用 IServiceManager 的接口.
		*/
		
		/*
		 * 这里,有一个设计思想.
		 * 1. defaultServiceManager 首先实例化 BpBinder.
		 * 2. interface_cast 就是 实例化 BpXXX,并将 BpBinder 交给其管理.
		 * 
		 * Proxy 端的用户无法直接看到 BpBinder , BpBinder 由 BpXXX 持有.用户本身不关心 BpBinder 的能力,只关心 IXXX 定义的 接口.
		 * 所以这里很好的进行了封装.
		*/

        while (gDefaultServiceManager == nullptr) {//如果不为空，表示设置过了，直接返回
        	//尝试不断的获取ServiceManager对象，如果获取不到，就sleep（1）,
        	//这里之所以会获取不到，是因为ServiceManager和一些通过init.rc启动的服务是同时启动的，不能保证ServiceManager能够优先启动完成。
        	//所以会存在获取ServiceManager的时候获取不到。
            gDefaultServiceManager = interface_cast<IServiceManager>(
                ProcessState::self()->getContextObject(nullptr));
            if (gDefaultServiceManager == nullptr)
                sleep(1);
        }
    }

    return gDefaultServiceManager;
}
```

这里会直接调用**ProcessState::self()->getContextObject(nullptr)**来获取对应的服务。

* ProcessState::self()->getContextObject(NULL): 返回的是一个 BpHwBinder。ServiceManager 的 desc 默认为0。
* interface_cast 就是将 BpBinder 封装为 IServiceManager

###### ProcessState::self()

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

###### getContextObject

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

###### interface_cast

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

整体的逻辑可以理解为：**new BpServiceManager(new BpBinder())**。当然了，这只是简化之后的代码，其内部复杂的逻辑现在可以暂不考虑。整体流程如下：

![image-20210131171812882](http://cdn.qiniu.kailaisii.com/typora/20210131171815-726894.png)

#### 添加Service

##### 客户端请求

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

```c++
status_t BpBinder::transact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    //如果binder已经died，则不会返回数据
    if (mAlive) {
        ...
		//调用IPCThreadState的transact方法。
        status_t status = IPCThreadState::self()->transact(
            mHandle, code, data, reply, flags);
        if (status == DEAD_OBJECT) mAlive = 0;
        return status;
    }
    return DEAD_OBJECT;
}
```

这里调用了IPCThreadState的transact方法将对应的数据写入到了Binder驱动了。当Binder驱动接收到注册服务的信息的时候，就会将对应的服务注册到ServiceManager中。我们可以看下传递的参数：

* mHandle：0，表示要处理该请求的进程号，ServiceManager在注册的时候，其对应的进程号是0。所以处理请求的也就是ServiceManager进程。
* code：参数是ADD_SERVICE_TRANSACTION。
* data：包含了要添加的进程相关信息：包括名称、是否单独运行等等相关信息

##### ServiceManager处理请求

当客户端发送请求之后，我们的ServiceManger就可以接收到消息，并且进行消息的处理了。在**ServiceManager的启动**中我们了解到，当ServiceManger启动之后，会调用binder_looper来不断的循环，检测是否接收到对应的数据信息。

这个功能是在**binder_loop()**方法的入参中的**svcmgr_handler**来实现的。

```c++
//frameworks\native\libs\binder\ndk\service_manager.cpp
int main(int argc, char** argv){
	...
	//启动循环，等待并处理client端发来的请求
    binder_loop(bs, svcmgr_handler);
    ...
}
```

svcmgr_handler就是我们具体的请求处理方法。

```c++
//frameworks\native\cmds\servicemanager\service_manager.c
int svcmgr_handler(struct binder_state *bs,
                   struct binder_transaction_data_secctx *txn_secctx,
                   struct binder_io *msg,
                   struct binder_io *reply)
{
    ...
    struct binder_transaction_data *txn = &txn_secctx->transaction_data;
    ...	
	//根据传输的不同类型来进行处理。
    switch(txn->code) {
        case SVC_MGR_ADD_SERVICE://添加服务
            //进行服务的添加
            do_add_service(bs, s, len, handle, txn->sender_euid, allow_isolated, dumpsys_priority,
                               txn->sender_pid, (const char*) txn_secctx->secctx)
            ... 
}

    
int do_add_service(struct binder_state *bs, const uint16_t *s, size_t len, uint32_t handle,
                   uid_t uid, int allow_isolated, uint32_t dumpsys_priority, pid_t spid, const char* sid) {
    struct svcinfo *si;

    //ALOGI("add_service('%s',%x,%s) uid=%d\n", str8(s, len), handle,
    //        allow_isolated ? "allow_isolated" : "!allow_isolated", uid);
    //服务的名称长度不能超过127字节
    if (!handle || (len == 0) || (len > 127))
        return -1;
	//最终调用selinux_check_access方法，会进行权限的检测，检查服务是否有进行服务注册
    if (!svc_can_register(s, len, spid, sid, uid)) {
        ALOGE("add_service('%s',%x) uid=%d - PERMISSION DENIED\n",
             str8(s, len), handle, uid);
        return -1;
    }
	//查询是否已经有包含了name的svcinfo
    si = find_svc(s, len);
    if (si) {
        if (si->handle) {
            ALOGE("add_service('%s',%x) uid=%d - ALREADY REGISTERED, OVERRIDE\n",
                 str8(s, len), handle, uid);
			//已经注册了，释放相应的服务
            svcinfo_death(bs, si);
        }
		//更新服务的handle
        si->handle = handle;
    } else {
		//申请内存
        si = malloc(sizeof(*si) + (len + 1) * sizeof(uint16_t));
        si->handle = handle;
        si->len = len;
        memcpy(si->name, s, (len + 1) * sizeof(uint16_t));
        si->name[len] = '\0';
        si->death.func = (void*) svcinfo_death;
        si->death.ptr = si;
        si->allow_isolated = allow_isolated;
        si->dumpsys_priority = dumpsys_priority;
		//将其注册到服务列表svclist中，这里使用的链表来保存数据
        si->next = svclist;
        svclist = si;
    }
	//以handle为目标，发送BC_ACQUIRE指令。
    binder_acquire(bs, handle);
	//以handle为目标，发送BC_REQUEST_DEATH_NOTIFICATION指令。
    binder_link_to_death(bs, handle, &si->death);
    return 0;
}
```

当拿到请求信息之后，ServiceManager会生成对应的**svcinfo**对象，将其保存到服务列表**svclist**中。

整体流程如下：

![image-20210131171938211](http://cdn.qiniu.kailaisii.com/typora/20210131171938-754270.png)

我们也可以从另一个维度去看看Binder的具体



### 系统服务获取

对于Servie服务的获取，其实也是答题思路也是相同的。显示获取ServiceManager的Binder对象，然后服务端发送获取某项服务的请求，ServiceManager来进行处理。

这里我们只看一下ServiceManager接收到服务获取的处理机制。也是在**svcmgr_handler()**中。

```c++
//frameworks\native\cmds\servicemanager\service_manager.c
int svcmgr_handler(struct binder_state *bs,
                   struct binder_transaction_data_secctx *txn_secctx,
                   struct binder_io *msg,
                   struct binder_io *reply)
{
    ...
    struct binder_transaction_data *txn = &txn_secctx->transaction_data;
    ...	
	//根据传输的不同类型来进行处理。
    switch(txn->code) {
        case SVC_MGR_GET_SERVICE://获取服务
        case SVC_MGR_CHECK_SERVICE:
            s = bio_get_string16(msg, &len);
            if (s == NULL) {
                return -1;
            }
            //根据pid，uid来获取服务对应的handle值
            handle = do_find_service(s, len, txn->sender_euid, txn->sender_pid,
                                     (const char*) txn_secctx->secctx);
            if (!handle)
                break;
            //返回服务对应的handle
            bio_put_ref(reply, handle);
            return 0;
}

```

这里主要做了2个操作：

1. 从服务列表中获取到对应的服务的handle
2. 将handle写入到要返回的reply数据中。

##### do_find_service

```c++
//frameworks\native\cmds\servicemanager\service_manager.c  
//获取对应的服务
uint32_t do_find_service(const uint16_t *s, size_t len, uid_t uid, pid_t spid, const char* sid)
{
	//获取对应的服务
    struct svcinfo *si = find_svc(s, len);

    if (!si || !si->handle) {
        return 0;
    }

    if (!si->allow_isolated) {
        // If this service doesn't allow access from isolated processes,
        // then check the uid to see if it is isolated.
        uid_t appid = uid % AID_USER;
		//检查服务是否是允许孤立于进程而单独存在的
        if (appid >= AID_ISOLATED_START && appid <= AID_ISOLATED_END) {
            return 0;
        }
    }
	//检测是否有selinx权限。
    if (!svc_can_find(s, len, spid, sid, uid)) {
        return 0;
    }
	//返回服务的handle
    return si->handle;
}

```
##### bio_put_ref

当获取到服务之后handle之后，会调用**bio_put_ref()**方法将服务对应的handle写入到返回的数据中。

```c++
//frameworks\native\cmds\servicemanager\service_manager.c
void bio_put_ref(struct binder_io *bio, uint32_t handle)
{
    struct flat_binder_object *obj;
	//申请对应的地址空间
    if (handle)
        obj = bio_alloc_obj(bio);
    else
        obj = bio_alloc(bio, sizeof(*obj));

    if (!obj)
        return;

    obj->flags = 0x7f | FLAT_BINDER_FLAG_ACCEPTS_FDS;
	//类型是BINDER_TYPE_HANDLE
    obj->hdr.type = BINDER_TYPE_HANDLE;
	//记录handle
    obj->handle = handle;
    obj->cookie = 0;
}
```

对于服务的获取，肯定是需要将reply的数据写回到请求服务的进程的。这时候就需要回到我们在**binder_loop()**函数了。在该函数中，存在一个**binder_parse()**，在这个方法里面会处理请求信息，并将reply信息通过binder驱动发送给客户端。

##### binder_parse

```c++
//frameworks\native\cmds\servicemanager\binder.c
int binder_parse(struct binder_state *bs, struct binder_io *bio,
                 uintptr_t ptr, size_t size, binder_handler func)
{
    ...
        case BR_TRANSACTION: {
           ...
				//调用func函数
                res = func(bs, txn, &msg, &reply);
                if (txn->flags & TF_ONE_WAY) {
                    binder_free_buffer(bs, txn->data.ptr.buffer);
                } else {
                	//发送协议指令给Binder驱动，向Client端发送reply
                    binder_send_reply(bs, &reply, txn->data.ptr.buffer, res);
                }
           ...
    return r;
}
```

当调用了func函数，有对应返回信息之后，会通过**binder_send_reply()**方法，将**reply**数据信息发送给client端。

```c++
void binder_send_reply(struct binder_state *bs,
                       struct binder_io *reply,
                       binder_uintptr_t buffer_to_free,
                       int status)
{
    struct {
        uint32_t cmd_free;
        binder_uintptr_t buffer;
        uint32_t cmd_reply;
        struct binder_transaction_data txn;
    } __attribute__((packed)) data;

    data.cmd_free = BC_FREE_BUFFER;
    data.buffer = buffer_to_free;
	//返回指令
    data.cmd_reply = BC_REPLY;
    data.txn.target.ptr = 0;
    data.txn.cookie = 0;
    data.txn.code = 0;
    if (status) {
        data.txn.flags = TF_STATUS_CODE;
        data.txn.data_size = sizeof(int);
        data.txn.offsets_size = 0;
        data.txn.data.ptr.buffer = (uintptr_t)&status;
        data.txn.data.ptr.offsets = 0;
    } else {//svcmgr_handler执行成功，将reply数据组装到txn中
        data.txn.flags = 0;
        data.txn.data_size = reply->data - reply->data0;
        data.txn.offsets_size = ((char*) reply->offs) - ((char*) reply->offs0);
        data.txn.data.ptr.buffer = (uintptr_t)reply->data0;
        data.txn.data.ptr.offsets = (uintptr_t)reply->offs0;
    }
	//发送数据
    binder_write(bs, &data, sizeof(data));
}
```

### 总结

ServiceManager是一个守护进程，负责管理系统中的所有服务信息。通过一个链表来保存了所有注册过的信息。而且其本身也是一个服务，在通过Binder驱动将其注册为守护进程之后，会将自己也注册为一个服务，供其他服务调用。

![image-20210302135139774](/Users/jj/Library/Application Support/typora-user-images/image-20210302135139774.png)

### 参考文献

https://www.jianshu.com/p/a90c697d6086

https://www.jianshu.com/p/c5110f71af58?utm_campaign=maleskine&utm_content=note&utm_medium=seo_notes&utm_source=recommendation

https://blog.csdn.net/jltxgcy/article/details/25953361

https://www.jianshu.com/p/dafbc4751df7

源码解析项目地址：[android-29-framwork](https://github.com/kailaisi/android-29-framwork.git)

> 同步公众号[开了肯]

![image-20200404120045271](http://cdn.qiniu.kailaisii.com/typora/20200404120045-194693.png)
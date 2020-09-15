## Android屏幕刷新机制

之前我们讲过布局优化中提到Android系统每16ms发出一个VSYNC信号，然后执行一次UI的渲染工作。如果渲染成功，那么界面基本就是流畅的。

我们看看Android系统是如何做屏幕刷新机制，如果做到16ms执行一次绘制工作，又如何保证我们每次点击或者触摸屏幕的时候，快速的处理对应的事件。

VSync来源自底层硬件驱动程序的上报，对于Android能看到的接口来说，它是来自HAL层的hwc_composer_device的抽象硬件设备

### 基础知识

#### View绘制

这部分在之前的文章有过专门的说明

![](http://cdn.qiniu.kailaisii.com/typora/20200913090942-343699.png)

#### 同步屏障

#### Vsync

　**1.**硬件或者软件创建vsyncThread产生vsync。
　**2.**DispSyncThread处理vsync，把vsync虚拟化成vsync-app和vsync-sf。
　**3.**vsync-app/sf按需产生（如果App和SurfaceFlinger都没有更新请求，则休眠省电）：
　　APP端：APP需要更新界面时发出vsync请求给EventThread（设置connection.count>=0），DispSyncThread收到vsync信号后休眠offset，然后唤醒EventThread通知APP开始渲染。
　　SF端：sf请求EventThread-sf，EventThread-sf收到vsync后通知SF可以开始合成。

#### EventThread

EventThread被设计用来接收VSync事件通知，并分发VSync通知给系统中的每一个感兴趣的注册者。

### 源码

#### SurfaceFling

SurfaceFling的启动：frameworks\native\services\surfaceflinger\main_surfaceflinger.cpp

```c++

int main(int, char**) {
    signal(SIGPIPE, SIG_IGN);
    hardware::configureRpcThreadpool(1 /* maxThreads */,false /* callerWillJoin */);
    startGraphicsAllocatorService();
    //设置线程池最多只能有4个Binder线程
    ProcessState::self()->setThreadPoolMaxThreadCount(4);
    //创建进行的ProcessState对象，打开Binder设备，同时创建并映射一部分Binder共享内存
    sp<ProcessState> ps(ProcessState::self());
	//开启Binder线程，里面会循环不断的talkWithDriver
    ps->startThreadPool();
    //重点方法1   通过工厂方法，创建Surfaceflinger。这里会初始化很多线程信息。
    //这里使用了sp强指针，而且SurfaceFlinger->DeathRecipient->RefBase的继承关系，所以在赋值给sp指针后，会立即调用其onFirstRef方法
    sp<SurfaceFlinger> flinger = surfaceflinger::createSurfaceFlinger();
	//设置Surfaceflinger的优先级
    setpriority(PRIO_PROCESS, 0, PRIORITY_URGENT_DISPLAY);
    set_sched_policy(0, SP_FOREGROUND);
    //初始化Surfaceflinger对象信息。位置：SurfaceFlinger.cpp
    flinger->init();

    // publish surface flinger
    //获取一个SM对象，相当于是new BpServiceManager(new BpBinder(0))
    sp<IServiceManager> sm(defaultServiceManager());
	//向ServiceManager守护进行注册SurfaceFling服务
    sm->addService(String16(SurfaceFlinger::getServiceName()), flinger, false,
                   IServiceManager::DUMP_FLAG_PRIORITY_CRITICAL | IServiceManager::DUMP_FLAG_PROTO);
	//在SurfaceFlinger调用init方法的时候，会初始化Display的相关信息
    startDisplayService(); // dependency on SF getting registered above
    struct sched_param param = {0};
    param.sched_priority = 2;
    //运行SurfaceFling
    flinger->run();

    return 0;
}

```

我们这里按照标注的重点方法进行跟踪

* **SurfaceFlinger的onFirstRef方法**

```c++
void SurfaceFlinger::onFirstRef()
{
	//初始化消息队列，创建对应的loop和handler
    mEventQueue->init(this);
}

MessageQueue.cpp	frameworks\native\services\surfaceflinger\Scheduler\
void MessageQueue::init(const sp<SurfaceFlinger>& flinger) {
    mFlinger = flinger;
    mLooper = new Looper(true);
    mHandler = new Handler(*this);
}

```

该方法中会创建对应的Handler和Looper信息

* SurfaceFlinger::init()





#### 入口

```java
	mChoreographer = Choreographer.getInstance();
	
	Choreographer.java	frameworks\base\core\java\android\view
    public static Choreographer getInstance() {
        return sThreadInstance.get();
    }	

    private static final ThreadLocal<Choreographer> sThreadInstance = new ThreadLocal<Choreographer>() {
        @Override
        protected Choreographer initialValue() {
            //获取对应的looper
            Looper looper = Looper.myLooper();
            if (looper == null) {
                throw new IllegalStateException("The current thread must have a looper!");
            }
			//注意这里使用的VSYNC_SOURCE_APP
            Choreographer choreographer = new Choreographer(looper, VSYNC_SOURCE_APP);
            if (looper == Looper.getMainLooper()) {
                mMainInstance = choreographer;
            }
            return choreographer;
        }
    };

    private Choreographer(Looper looper, int vsyncSource) {
		//FrameDisplayEventReceiver创建的信号是VSYNC_SOURCE_APP，APP层请求的VSYNC
        mDisplayEventReceiver = USE_VSYNC? new FrameDisplayEventReceiver(looper, vsyncSource): null;
        ...
    }

	//DisplayEventReceiver.java	frameworks\base\core\java\android\view	
    public DisplayEventReceiver(Looper looper, int vsyncSource) {
        if (looper == null) {
            throw new IllegalArgumentException("looper must not be null");
        }

        mMessageQueue = looper.getQueue();
		//调用底层初始化，并将本身以及对应的mMessageQueue传入进去
        mReceiverPtr = nativeInit(new WeakReference<DisplayEventReceiver>(this), mMessageQueue,vsyncSource);

        mCloseGuard.open("dispose");
    }
```

这里初始化的**FrameDisplayEventReceiver**类继承自**DisplayEventReceiver**类

```java
    public DisplayEventReceiver(Looper looper, int vsyncSource) {
        if (looper == null) {
            throw new IllegalArgumentException("looper must not be null");
        }

        mMessageQueue = looper.getQueue();
		//调用底层初始化，并将本身以及对应的mMessageQueue传入进去
        //对应frameworks\base\core\jni\android_view_DisplayEventReceiver.cpp
        mReceiverPtr = nativeInit(new WeakReference<DisplayEventReceiver>(this), mMessageQueue,vsyncSource);

        mCloseGuard.open("dispose");
    }
```

这里会调用Native层的方法，并将当前的**DisplayEventReceiver**以及队列**mMessageQueue**和**vsyncSource(VSYNC_SOURCE_APP)**传递给底层

### nativeInit

```c
//frameworks\base\core\jni\android_view_DisplayEventReceiver.cpp
static jlong nativeInit(JNIEnv* env, jclass clazz, jobject receiverWeak,
        jobject messageQueueObj, jint vsyncSource) {
    //申请对应的MessageQueue
    sp<MessageQueue> messageQueue = android_os_MessageQueue_getMessageQueue(env, messageQueueObj);
    ...
	//重点方法1       创建NativeDisplayEventReceiver
    sp<NativeDisplayEventReceiver> receiver = new NativeDisplayEventReceiver(env,
            receiverWeak, messageQueue, vsyncSource);
	//重点方法2        进行初始化NativeDisplayEventReceiver,并返回对应的初始化结果
    status_t status = receiver->initialize();
    if (status) {//初始化出现异常
        String8 message;led to initialize display event receiver.  status
        message.appendFormat("Fai=%d", status);
        jniThrowRuntimeException(env, message.string());
        return 0;
    }
    receiver->incStrong(gDisplayEventReceiverClassInfo.clazz); // retain a reference for the object
    return reinterpret_cast<jlong>(receiver.get());
}

```

我们这里先看一下**NativeDisplayEventReceiver**的创建过程。

#### [NativeDisplayEventReceiver的创建]

```c

NativeDisplayEventReceiver::NativeDisplayEventReceiver(JNIEnv* env,
        jobject receiverWeak, const sp<MessageQueue>& messageQueue, jint vsyncSource) :
        //继承了DisplayEventDispatcher，并传入了对应的messagequeue，将vsyncSource转化为了底层使用的变量
        DisplayEventDispatcher(messageQueue->getLooper(),
                static_cast<ISurfaceComposer::VsyncSource>(vsyncSource)),
        mReceiverWeakGlobal(env->NewGlobalRef(receiverWeak)),
        mMessageQueue(messageQueue) {
    ALOGV("receiver %p ~ Initializing display event receiver.", this);
}

//DisplayEventDispatcher构造函数
DisplayEventDispatcher::DisplayEventDispatcher(const sp<Looper>& looper,ISurfaceComposer::VsyncSource vsyncSource) :
        //Vsync的来源传递给了mReceiver。这里相当于调用了mReceiver(DisplayEventReceiver)的构造函数
        mLooper(looper), mReceiver(vsyncSource), mWaitingForVsync(false) {
    ALOGV("dispatcher %p ~ Initializing display event dispatcher.", this);
}


```

这里会创建**DisplayEventReceiver**

```c++

//DisplayEventReceiver构造函数	frameworks\native\libs\gui\DisplayEventReceiver.cpp
DisplayEventReceiver::DisplayEventReceiver(ISurfaceComposer::VsyncSource vsyncSource,
                                           ISurfaceComposer::ConfigChanged configChanged) {
    //方法1   	获取SurfaceFling服务,并保存在ComposerService中                             
    sp<ISurfaceComposer> sf(ComposerService::getComposerService());
    if (sf != nullptr) {
		//方法2   通过binder，最后跨进程调用surfaceFling的createDisplayEventConnection方法
		//方法位置 ISurfaceComposer.cpp	frameworks\native\libs\gui	66331	2020/3/22	1379
        mEventConnection = sf->createDisplayEventConnection(vsyncSource, configChanged);
        if (mEventConnection != nullptr) {
			//方法3
            mDataChannel = std::make_unique<gui::BitTube>();
			//方法4
            mEventConnection->stealReceiveChannel(mDataChannel.get());
        }
    }
}
```

DisplayEventReceiver**结构体是一个比较重要的类，其主要作用是建立与**SurfaceFlinger**的连接。我们这里将对其每一个调用的方法都来进行一个自习的分析

* 方法1：获取SurfaceFlinger服务

 sp<ISurfaceComposer> sf(ComposerService::getComposerService());

##### ComposerService::getComposerService()

```c
// 	frameworks\native\libs\gui\SurfaceComposerClient.cpp
/*static*/ sp<ISurfaceComposer> ComposerService::getComposerService() {
    ComposerService& instance = ComposerService::getInstance();
    Mutex::Autolock _l(instance.mLock);//加锁
    if (instance.mComposerService == nullptr) {
		//获取SurfaceFling服务，并保存在ComposerService中
        ComposerService::getInstance().connectLocked();
        assert(instance.mComposerService != nullptr);
        ALOGD("ComposerService reconnected");
    }
    return instance.mComposerService;
}

void ComposerService::connectLocked() {
    const String16 name("SurfaceFlinger");
	//通过getService方法获取SurfaceFlinger服务，并将获取到的服务保存到mComposerService变量中
    while (getService(name, &mComposerService) != NO_ERROR) {
        usleep(250000);
    }
    //创建死亡回调
    ...
    mDeathObserver = new DeathObserver(*const_cast<ComposerService*>(this));
    IInterface::asBinder(mComposerService)->linkToDeath(mDeathObserver);
}
```

通过**getService**方法来获取对应的**SurfaceFlinger**服务。这里会将获取到的服务保存到mComposerService变量中.

* 创建事件连接

##### sf->createDisplayEventConnection

```c
    virtual sp<IDisplayEventConnection> createDisplayEventConnection(VsyncSource vsyncSource,ConfigChanged configChanged) {
        Parcel data, reply;
        sp<IDisplayEventConnection> result;
		//binder机制调用SurfaceFling的createDisplayEventConnection方法
		//SurfaceFlinger.cpp	frameworks\native\services\surfaceflinger
        int err = data.writeInterfaceToken(ISurfaceComposer::getInterfaceDescriptor());
        data.writeInt32(static_cast<int32_t>(vsyncSource));
        data.writeInt32(static_cast<int32_t>(configChanged));
        err = remote()->transact(
                BnSurfaceComposer::CREATE_DISPLAY_EVENT_CONNECTION,
                data, &reply);
        ...
        result = interface_cast<IDisplayEventConnection>(reply.readStrongBinder());
        return result;
    }
```

可以看到，该方法使用的是**Binder机制**，而服务的提供方则是**SurfaceFlinger**。

```c
//创建显示事件连接
sp<IDisplayEventConnection> SurfaceFlinger::createDisplayEventConnection(
        ISurfaceComposer::VsyncSource vsyncSource, ISurfaceComposer::ConfigChanged configChanged) {
    //makeResyncCallback是一个方法，定义在EventThread.h中。using ResyncCallback = std::function<void()>;
    //创建一个resyncCallback
    auto resyncCallback = mScheduler->makeResyncCallback([this] {
        Mutex::Autolock lock(mStateLock);
        return getVsyncPeriod();
    });
	//根据传入的Vsync类型，返回不同的Handler。如果是应用中注册的，则返回mAppConnectionHandle
    const auto& handle = vsyncSource == eVsyncSourceSurfaceFlinger ? mSfConnectionHandle : mAppConnectionHandle;
	//调用createDisplayEventConnection，传入了对应的handle,mScheduler是Scheduler.cpp结构体
    return mScheduler->createDisplayEventConnection(handle, std::move(resyncCallback),
                                                    configChanged);
}

```

根据传入的**vsyncSource**类型来返回具体的Handler。因为我们这里使用过的应用类型，所以这里的handle是**mAppConnectionHandle**。

然后通过mScheduler创建对应的连接。

这里我们需要对handle进行一个**补充说明**

补充说明：

对于Handler的创建是在SurfaceFlinger的初始化方法init()中进行创建的

```c++
void SurfaceFlinger::init() {
    ...
	mAppConnectionHandle =
       	mScheduler->createConnection("app", mVsyncModulator.getOffsets().app,
                                     mPhaseOffsets->getOffsetThresholdForNextVsync(),
                                     resyncCallback,
                                     impl::EventThread::InterceptVSyncsCallback());
    ...
}


sp<Scheduler::ConnectionHandle> Scheduler::createConnection(
        const char* connectionName, nsecs_t phaseOffsetNs, nsecs_t offsetThresholdForNextVsync,
        ResyncCallback resyncCallback,
        impl::EventThread::InterceptVSyncsCallback interceptCallback) {
    //对应的id，累加的
    const int64_t id = sNextId++;
	//创建一个EventThread,名称为传入的connectionName
    std::unique_ptr<EventThread> eventThread =
            makeEventThread(connectionName, mPrimaryDispSync.get(), phaseOffsetNs,
                            offsetThresholdForNextVsync, std::move(interceptCallback));
	//创建EventThreadConnection
    auto eventThreadConnection = createConnectionInternal(eventThread.get(), std::move(resyncCallback),
                                     ISurfaceComposer::eConfigChangedSuppress);
	//创建ConnectionHandle,入参是id，
	//然后将创建的connection并存入到map中。key是id。
    mConnections.emplace(id,
                         std::make_unique<Connection>(new ConnectionHandle(id),
                                                      eventThreadConnection,
                                                      std::move(eventThread)));
    return mConnections[id]->handle;
}
```
这里创建的Handler，持有了对应的**EventThread**，而**eventThreadConnection**是通过**EventThread**来进行创建。创建**eventThreadConnection**以后，会将其保存到map中，对应的key则是id信息。

而连接处理器：**ConnectionHandle**则是一个持有id的对象。

我们回到主线。。。。

##### mScheduler->createDisplayEventConnection

```c++
//	frameworks\native\services\surfaceflinger\Scheduler\Scheduler.cpp

sp<IDisplayEventConnection> Scheduler::createDisplayEventConnection(
        const sp<Scheduler::ConnectionHandle>& handle, ResyncCallback resyncCallback,
        ISurfaceComposer::ConfigChanged configChanged) {
    RETURN_VALUE_IF_INVALID(nullptr);
	//传入了handle.id。能够表明连接是app还是surfaceFlinger
    return createConnectionInternal(mConnections[handle->id]->thread.get(),
                                    std::move(resyncCallback), configChanged);
}

sp<EventThreadConnection> Scheduler::createConnectionInternal(
        EventThread* eventThread, ResyncCallback&& resyncCallback,
        ISurfaceComposer::ConfigChanged configChanged) {
    //调用EventThread的方法,创建事件连接器
    return eventThread->createEventConnection(std::move(resyncCallback), configChanged);
}
```

我们看看事件连接器**EventThreadConnection**的创建过程

```c++
sp<EventThreadConnection> EventThread::createEventConnection(
        ResyncCallback resyncCallback, ISurfaceComposer::ConfigChanged configChanged) const {
    return new EventThreadConnection(const_cast<EventThread*>(this), std::move(resyncCallback),
                                     configChanged);
}


EventThreadConnection::EventThreadConnection(EventThread* eventThread,
                                             ResyncCallback resyncCallback,
                                             ISurfaceComposer::ConfigChanged configChanged)
      : resyncCallback(std::move(resyncCallback)),
        configChanged(configChanged),
        mEventThread(eventThread),
        mChannel(gui::BitTube::DefaultSize) {}
```

**EventThreadConnection**的构造方法中最重要的是创建了**mChannel**，而它是gui::BitTube类型的

```c++
//	frameworks\native\libs\gui\BitTube.cpp
BitTube::BitTube(size_t bufsize) {
    init(bufsize, bufsize);
}


void BitTube::init(size_t rcvbuf, size_t sndbuf) {
    int sockets[2];
    if (socketpair(AF_UNIX, SOCK_SEQPACKET, 0, sockets) == 0) {
        size_t size = DEFAULT_SOCKET_BUFFER_SIZE;
		//创建对应一对socket：0和1，一个用来读，一个用来写。
        setsockopt(sockets[0], SOL_SOCKET, SO_RCVBUF, &rcvbuf, sizeof(rcvbuf));
        setsockopt(sockets[1], SOL_SOCKET, SO_SNDBUF, &sndbuf, sizeof(sndbuf));
        // since we don't use the "return channel", we keep it small...
        setsockopt(sockets[0], SOL_SOCKET, SO_SNDBUF, &size, sizeof(size));
        setsockopt(sockets[1], SOL_SOCKET, SO_RCVBUF, &size, sizeof(size));
        fcntl(sockets[0], F_SETFL, O_NONBLOCK);
        fcntl(sockets[1], F_SETFL, O_NONBLOCK);
		//将mReceiveFd文件和socket进行绑定。当Vsync到来的时候，会通过mSendFd文件来写入消息，通过对文件的消息写入监听，完成了对Vsync信号的监听
        mReceiveFd.reset(sockets[0]);
        mSendFd.reset(sockets[1]);
    } else {
        mReceiveFd.reset();
    }
}
```

在初始化方法中，创建了一对socket，然后将**mReceiveFd**和**mSendFd**进行了绑定。当Vsync到来的时候通过mSendFd写入消息，然后APP就可以监听文件的变化。

在创建**EventThreadConnection**对象的时候，会自动调用**onFirstRef**方法。

```c++
void EventThreadConnection::onFirstRef() {
    mEventThread->registerDisplayEventConnection(this);
}

status_t EventThread::registerDisplayEventConnection(const sp<EventThreadConnection>& connection) {
    std::lock_guard<std::mutex> lock(mMutex);

    // this should never happen
    auto it = std::find(mDisplayEventConnections.cbegin(),
            mDisplayEventConnections.cend(), connection);
    if (it != mDisplayEventConnections.cend()) {
        ALOGW("DisplayEventConnection %p already exists", connection.get());
        mCondition.notify_all();
        return ALREADY_EXISTS;
    }
	//将连接放入到需要通知的列表中。
    mDisplayEventConnections.push_back(connection);
	//有新的连接了，就需要唤醒AppEventThread线程使能Vsync信号了。
    mCondition.notify_all();
    return NO_ERROR;
}
```

会将我们创建的连接放入到**EventThread**管理的**mDisplayEventConnections**中，然后唤醒**AppEventThread**线程使能Vsync信号

整个步骤二，其实是根据传入的vsyncSource，指导对应的监听者是来自APP，然后创建一对socket连接，来进行进程间的通信。

我们继续回到主线进行跟踪处理

```c++
DisplayEventReceiver::DisplayEventReceiver(ISurfaceComposer::VsyncSource vsyncSource,
                                           ISurfaceComposer::ConfigChanged configChanged) {
    //方法1   	获取SurfaceFling服务,并保存在ComposerService中                             
    sp<ISurfaceComposer> sf(ComposerService::getComposerService());
    if (sf != nullptr) {
		//方法2   通过binder，最后跨进程调用surfaceFling的createDisplayEventConnection方法
		//方法位置 ISurfaceComposer.cpp	frameworks\native\libs\gui
        mEventConnection = sf->createDisplayEventConnection(vsyncSource, configChanged);
        if (mEventConnection != nullptr) {
			//方法3 获取方法二中创建的gui::BitTube对象
            mDataChannel = std::make_unique<gui::BitTube>();
			//方法4
            mEventConnection->stealReceiveChannel(mDataChannel.get());
        }
    }
}
```

方法3是获取了对应的gui::BitTube对象。我们主要来分析一下方法四。

方法四调用了**EventThreadConnect**的**stealReceiveChannel**

```c++
status_t EventThreadConnection::stealReceiveChannel(gui::BitTube* outChannel) {
    outChannel->setReceiveFd(mChannel.moveReceiveFd());
    return NO_ERROR;
}
```

这的mChannel是gui::BitTube。这里将事件连接器**EventThreadConnection**中创建的Fd复制给了outChannel。也就是DisplayEventReceiver的mDataChannel。

所以**这时候app进程就有了mReceivedFd，surfaceFlinger进程有了mSendFd。这时候通过socket就能够进行通信了**。

> 整个DisplayEventReceiver的作用是创建一个socket以及对应的文件，然后实现和SurfaceFlinger的双向通讯。



这里我们为止，我们只是创建NativeDisplayEventReceiver。

那么后续还有

#### receiver->initialize()

```c++
status_t DisplayEventDispatcher::initialize() {
	//异常检测
    status_t result = mReceiver.initCheck();
    if (result) {
        ALOGW("Failed to initialize display event receiver, status=%d", result);
        return result;
    }
	//这里的Looper就是应用app进程的主线程Looper，这一步就是将创建的BitTube信道的
	//fd添加到Looper的监听。
    int rc = mLooper->addFd(mReceiver.getFd(), 0, Looper::EVENT_INPUT,
            this, NULL);
    if (rc < 0) {
        return UNKNOWN_ERROR;
    }
    return OK;
}
```

这里之所以能够加入到监听，是因为我们的

这里整个方法比较简单，就是进行异常的检测，让后将在步骤一中创建的fd文件加入到Looper的监听中。

到这里为止，整个流程算是打通了。

**java层通过DisplayEventReceive的nativeInit函数，创建了应用层和SurfaceFlinger的连接，通过一对socket，对应mReceiveFd和mSendFd，应用层通过native层Looper将mReceiveFd加入监听，等待mSendFd的写入。**

那么mSendFd什么时候写入，又是如何传递到应用层的呢？









当我们进行页面刷新绘制的时候，看一下如何注册对于Vsync的监听给的

```java

    @UnsupportedAppUsage
    void scheduleTraversals() {
       	...
            mChoreographer.postCallback(Choreographer.CALLBACK_TRAVERSAL, mTraversalRunnable, null);
		...	
    }

    public void postCallback(int callbackType, Runnable action, Object token) {
        postCallbackDelayed(callbackType, action, token, 0);
    }

    public void postCallbackDelayed(int callbackType,Runnable action, Object token, long delayMillis) {
        postCallbackDelayedInternal(callbackType, action, token, delayMillis);
    }

    private void postCallbackDelayedInternal(int callbackType,Object action, Object token, long delayMillis) {
        	...
				//需要立即进行绘制
                scheduleFrameLocked(now);
            ...
    }

    private void scheduleFrameLocked(long now) {
    	...
                    scheduleVsyncLocked();
        ...
    }

    private void scheduleVsyncLocked() {
        //执行同步功能，进行一次绘制。这里会进行一个VSYNC事件的监听注册，如果有有
        mDisplayEventReceiver.scheduleVsync();
    }

    public void scheduleVsync() {
        ..
            nativeScheduleVsync(mReceiverPtr);
        ...
    }
```

这里的**nativeScheduleVsync()**就是应用层向native层注册监听下一次Vsync信号的方法。

### nativeScheduleVsync

```c++
//base\core\jni\android_view_DisplayEventReceiver.cpp		8492	2020/9/14	96
static void nativeScheduleVsync(JNIEnv* env, jclass clazz, jlong receiverPtr) {
    sp<NativeDisplayEventReceiver> receiver =
            reinterpret_cast<NativeDisplayEventReceiver*>(receiverPtr);
    //调用Recivier的调度方法
    status_t status = receiver->scheduleVsync();
}

```

这里的receiver，是**NativeDisplayEventReceiver**。而**NativeDisplayEventReceiver**是继承自**DisplayEventDispatcher**

#### DisplayEventDispatcher->scheduleVsync();

```c++
//调度Vsync
status_t DisplayEventDispatcher::scheduleVsync() {
	//如果当前正在等待Vsync信号，那么直接返回
    if (!mWaitingForVsync) {
        nsecs_t vsyncTimestamp;
        PhysicalDisplayId vsyncDisplayId;
        uint32_t vsyncCount;
		//重点方法1   处理对应的准备事件，如果获取到了Vsync信号的话，这里会返回true
        if (processPendingEvents(&vsyncTimestamp, &vsyncDisplayId, &vsyncCount)) {
            ALOGE("dispatcher %p ~ last event processed while scheduling was for %" PRId64 "",
                    this, ns2ms(static_cast<nsecs_t>(vsyncTimestamp)));
        }
		//重点方法2   请求下一个Vsync信号
        status_t status = mReceiver.requestNextVsync();
        ...
		//设置正在等待Vsync信号
        mWaitingForVsync = true;
    }
    return OK;
}
```

这里我们跟踪一下方法1

##### DisplayEventDispatcher::processPendingEvents

```c++
bool DisplayEventDispatcher::processPendingEvents(
        nsecs_t* outTimestamp, PhysicalDisplayId* outDisplayId, uint32_t* outCount) {
    bool gotVsync = false;
    DisplayEventReceiver::Event buf[EVENT_BUFFER_SIZE];
    ssize_t n;
    //获取对应的事件
    while ((n = mReceiver.getEvents(buf, EVENT_BUFFER_SIZE)) > 0) {
        ALOGV("dispatcher %p ~ Read %d events.", this, int(n));
        for (ssize_t i = 0; i < n; i++) {
            const DisplayEventReceiver::Event& ev = buf[i];
            switch (ev.header.type) {
            case DisplayEventReceiver::DISPLAY_EVENT_VSYNC://Vsync类型
                //获取到最新的Vsync信号，然后将时间戳等信息保存下来
                gotVsync = true;
                *outTimestamp = ev.header.timestamp;
                *outDisplayId = ev.header.displayId;
                *outCount = ev.vsync.count;
                break;
           ...
    return gotVsync;
}
```

会通过**getEvents**方法获取到对应的事件类型，然后返回是否为Vsync信号。

##### DisplayEventReceiver::getEvents

```c++
//	native\libs\gui\DisplayEventReceiver.cpp

ssize_t DisplayEventReceiver::getEvents(DisplayEventReceiver::Event* events,size_t count) {
	//这里的mDataChannel是在init中创建的，用来接收Vsync信号
    return DisplayEventReceiver::getEvents(mDataChannel.get(), events, count);
}
ssize_t DisplayEventReceiver::getEvents(gui::BitTube* dataChannel,
        Event* events, size_t count)
{
    return gui::BitTube::recvObjects(dataChannel, events, count);
}

//native\libs\gui\BitTube.cpp
    static ssize_t recvObjects(BitTube* tube, T* events, size_t count) {
        return recvObjects(tube, events, count, sizeof(T));
    }

ssize_t BitTube::recvObjects(BitTube* tube, void* events, size_t count, size_t objSize) {
    char* vaddr = reinterpret_cast<char*>(events);
	//通过socket读取数据
    ssize_t size = tube->read(vaddr, count * objSize);
    return size < 0 ? size : size / static_cast<ssize_t>(objSize);
}
//读取数据
ssize_t BitTube::read(void* vaddr, size_t size) {
    ssize_t err, len;
    do {
		//将mReceiveFd接收到的数据，放入到size大小的vaddr缓冲区。并返回实际接收到的数据大小len
        len = ::recv(mReceiveFd, vaddr, size, MSG_DONTWAIT);
        err = len < 0 ? errno : 0;
    } while (err == EINTR);
    if (err == EAGAIN || err == EWOULDBLOCK) {
        //如果接收出现异常，返回0
        return 0;
    }
    return err == 0 ? len : -err;
}
```

这里将接收到的数据放入到对应的缓冲区，并返回数据之后，会校验返回的具体的数据类型。



```c++
status_t DisplayEventReceiver::requestNextVsync() {
	//校验当前连接存在
    if (mEventConnection != nullptr) {
		//通过连接请求下一个Vsync信号。这个mEventConnection。是在DisplayEventReceiver初始化的时候创建的
		//具体的是EventThreadConnection（位于EventThread中）
        mEventConnection->requestNextVsync();
        return NO_ERROR;
    }
    return NO_INIT;
}

void EventThreadConnection::requestNextVsync() {
    ATRACE_NAME("requestNextVsync");
    mEventThread->requestNextVsync(this);
}

void EventThread::requestNextVsync(const sp<EventThreadConnection>& connection) {
    if (connection->resyncCallback) {
        connection->resyncCallback();
    }
	//线程锁机制
    std::lock_guard<std::mutex> lock(mMutex);
	//vsyncRequest默认值是None.定义在EventThread.h文件中
    if (connection->vsyncRequest == VSyncRequest::None) {
		//之所以Vsync是一次性的，是因为，当我们当前是None之后，会将这个字段设置为Single。
		//后续硬件再有Vsync信号过来的时候，不会再执行这个方法
        connection->vsyncRequest = VSyncRequest::Single;
        mCondition.notify_all();
    }
}

```





[回到主线](#main)



http://dandanlove.com/2018/04/25/android-source-choreographer/

https://blog.csdn.net/stven_king/article/details/80098798

[VSYNC调用流程]https://blog.csdn.net/litefish/article/details/53939882

[Android垂直同步信号VSync的产生及传播结构详解](https://blog.csdn.net/houliang120/article/details/50908098)

EventThread

https://blog.csdn.net/qq_34211365/article/details/105123790

https://blog.csdn.net/qq_34211365/article/details/105155801
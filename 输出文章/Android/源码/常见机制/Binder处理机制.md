### 基础



#### 进程间通讯（IPC）

Android是基于Linux开发的，而且Linux已经有了很多线程的进程间的通讯机制。

* **管道**：在创建时分配一个page大小的内存，缓存区大小比较有限
* **消息队列**：信息复制两次，消耗额外的CPU；不适合频繁或者信息量大的通讯
* **共享内存**：无需复制，速度快。但是进程间的同步问题需要操作系统来处理。比较繁琐
* **套接字**：更通用的通讯方式，但是传输效率比较低，适用于不同机器或者跨网络的通讯
* **信号量**：能够处理并发问题，常作为一种锁机制，防止某个进程访问共享资源时，其他进程也访问该资源。
* **信号**：不适用于信息交换，更加适合用于进程中断控制等等。

#### Binder

Binder是Android所独有的采用的两个进程间进行通讯交流的一种机制。

#### 对比

谷歌放弃Linux已有的进程间通讯的机制，而采用Binder机制，那么肯定是由于一些无法满足的需求，而不得不创造Android独有的进程间通讯方式。


|          |                            Binder                            |                           共享内存                           |                     套接字                     |
| :------: | :----------------------------------------------------------: | :----------------------------------------------------------: | :--------------------------------------------: |
| 拷贝次数 |                             1次                              |                             0次                              |                      2次                       |
|   特点   | 基于C/S架构，架构清晰明朗，Server端和Cliend端相互独立，稳定性好 | 实现方式复杂，没有客户端、服务端区别，需要考虑资源同步并发问题，容易出现死锁 |        C/S架构，但是传输效率低，开销大         |
|  安全性  |   **为每个进程分配对应的UID/PID。从而能够鉴别对方身份。**    |        依赖上层协议。访问接入点是完全开放的，不安全。        | 依赖上层协议。访问接入点是完全开放的，不安全。 |

IBinder**决胜点主要在于安全性**上。Android作为一个开放性的平台，用户可以自由的安装各种应用，很可能就有各种非法盗取用户信息的程序，所以在安全性对于Android平台是及其重要的。肯定你不想有一天你拍的美美的照片被pdd给删了，也不希望你的聊天记录某天出现在网上被人围观，更不希望自己的联系人被各种"广告平台"悄悄拿走~。

为了解决这个问题，采用了Binder这种方式来作为跨进程通讯方式。

* 为每一个安装好的APP都分配一个UID，将身份标识交给IPC机制在内核中完成，而不是用户自行填入。

* 在进行跨进程通讯的时候，都需要验证对方的UID/PID，从而能够有效的辨别恶意程序。

Android一直在安全性上做着努力，不管是Android6.0上的动态权限申请，还是Android7上的私有目录访问，Android8上的后台执行限制、后台位置限制；Android9上的 Android Protected Confirmation 的能力；Android10上的限制外部存储访问等等。这些功能的实现，大体上都是通过Binder的身份识别来进行保证的。

具体的实现方式，大家可以参考：[Android源码的Binder权限是如何控制？](https://www.zhihu.com/question/41003297/answer/89328987?from=profile_answer_card)

### Binder整体架构

本文不会从代码层次去说Binder的整个流程。可能会更加注重理念层次的东西。

#### 设定通信

在软件开发过程中，对于Activity或者Service的进程配置，是通过AndroidManifest.xml来进行设定的。如果我们想让Service在单独的进程中去运行，那么只需要如下设置即可：

```xml
<service 
    ...
    android:process="****" >
</service>
```

如果没有这种设定，默认是所有的Activity和Service都是在同一个进程中执行的。

所以可以知道一个进程只能有一个APP，但是一个APP其实是可以占用多个进程的。

#### 接口约定

当两个类都在同一个进程的时候，只需要函数调用就可以了。一旦两个类在不同的进程时，就不能使用函数调用了，只能采取IPC沟通机制。而IPC依赖**IBinder**接口。

Ibinder接口定义了一些函数，可以让Client程序可以跨进程去掉用。最主要的函数就是**transact()**函数。

```java
    public boolean transact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags)
```

Client端调用接口的`trancact()`函数，透过IPC机制调用远程的`trancact()`函数。 

#### Binder通讯模型

##### 四大角色

在Android中进行跨进程的通讯，是由4部分协作来完成的：**Client、Server、Binder驱动和ServiceManager。**四个角色和我们平时打电话的流程是相似的。

|            Binder角色            |  通讯角色  |
| :------------------------------: | :--------: |
|              客户端              | 打电话的人 |
|     服务端，能够提供各种服务     | 接电话的人 |
|   ServiceManager，管理各种服务   |   电话簿   |
| Binder驱动，提供进程间通讯的能力 |  通信网络  |

##### **通信流程**

跨进程的流程是通过四个角色的相互配合来实现的。

整个具体流程模型如下（图片来源于网络）：

![img](http://cdn.qiniu.kailaisii.com/typora/202012/02/160410-784686.png)

1.首先服务端需要向ServiceManager进行服务注册，ServiceManager有一个全局的service列表svcinfo，用来缓存所有服务的handler和name。

2.客户端与服务端通信，需要拿到服务端的对象，由于进程隔离，客户端拿到的其实是服务端的代理，也可以理解为引用。客户端通过ServiceManager从svcinfo中查找服务，ServiceManager返回服务的代理。

3.拿到服务对象后，我们需要向服务发送请求，实现我们需要的功能。通过 BinderProxy 将我们的请求参数发送给 内核，通过共享内存的方式使用内核方法 copy_from_user() 将我们的参数先拷贝到内核空间，这时我们的客户端进入等待状态。然后 Binder 驱动向服务端的 todo 队列里面插入一条事务，执行完之后把执行结果通过 copy_to_user() 将内核的结果拷贝到用户空间（这里只是执行了拷贝命令，并没有拷贝数据，binder只进行一次拷贝），唤醒等待的客户端并把结果响应回来，这样就完成了一次通讯。

##### 难点

在这里其实会存在一个问题，Client和Server之间通信是称为进程间通信，使用了Binder机制，那么Server和ServiceManager之间通信也叫进程间通信，Client和Server之间还会用到ServiceManager，也就是说Binder进程间通信通过Binder进程间通信来完成，这就好比是 孵出鸡前提却是要找只鸡来孵蛋，这是怎么实现的呢？

Binder的实现比较巧妙：预先创造一只鸡来孵蛋：ServiceManager和其它进程同样采用Binder通信，ServiceManager是Server端，有自己的Binder对象（实体），其它进程都是Client，需要通过这个Binder的引用来实现Binder的注册，查询和获取。

ServiceManager提供的Binder比较特殊，它没有名字也不需要注册，当一个进程使用BINDER_SET_CONTEXT_MGR_EXT命令将自己注册成ServiceManager时Binder驱动会自动为它创建Binder实体（这就是那只预先造好的鸡）。

其次这个Binder的引用在所有Client中都固定为0(handle=0)而无须通过其它手段获得。也就是说，一个Server若要向ServiceManager注册自己Binder就必须通过0这个引用号和ServiceManager的Binder通信。

类比网络通信，0号引用就好比域名服务器的地址，你必须预先手工或动态配置好。要注意这里说的Client是相对ServiceManager而言的，一个应用程序可能是个提供服务的Server，但对ServiceManager来说它仍然是个Client。

#### 数据传输架构演化

当整个通讯模型流程建立完成之后，进行的就是数据传输的工作。

##### 极简版本

在接口约定中，我们提过，对于跨进程的调用，需要实现IBinder接口来进行接口调用以及数据的传输。

```java
   public boolean transact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags)
        throws RemoteException;
```

其中code其实代表的是不同的方法所对应的编号，如果用户本身作为程序的编写者，那么Server端和Client端必须要知道编号和函数的对应关系。

![image-20210110214132920](http://cdn.qiniu.kailaisii.com/typora/20210110214135-2869.png)

这种基本无任何框架可言，对于Server提供端还是Client调用端来说都是可怕的。

![img](http://cdn.qiniu.kailaisii.com/typora/20210110214302-269897.gif)

##### 进化版

作为开发人员最希望使用的肯定是直接调用函数，而无需关心编号和函数对应关系，更不用关心跨进程的过程处理，希望由系统层级的方式来帮我们解决编码解码工作：

![image-20210110214617708](http://cdn.qiniu.kailaisii.com/typora/20210110214620-186881.png)

也就是通过中间一层的**Proxy/Stub**的处理，能够屏蔽实现对于用户的感知。Proxy和Stub类这一层的封装，将transact()和对应函数的转换进行了封装，对外只提供对应的函数。

##### 设计难点 ：

对于**Proxy-Stub**的这一层的设计难点在于，**接口是由用户来定义的，所以无法提前将code和函数的对应关系进行处理**。

对于这种问题，有两种解决方案：

###### 模板模式

由Google来编写模板，而用户来编写具体的接口。

具体实现：

```java
public class BinderProxy<T>{
    //定义的模板类
}

//用户使用模板生成类
BinderProxy<MyInterface> proxy;
```

该方案是在JNI层使用的方案（后续会进行源码级分析）。

###### AIDL.exe

AIDL.exe属于程序生成器，位于**SDK包下的builde-tools下各个版本之中**。通过这种方式，可以工具来协助生成Porxy和Stub类别。 该方案是Java层使用的方案。

### AIDL分析

#### 本质

AIDL的目的是为了减少用户每次都进行复杂的跨进程程序编写。通过定义Proxy/Stub来封装IBinder接口，来生成更加贴心的新接口。

#### 类

**IBinder**

跨进程的Base接口，声明跨进程通信需要实现的一系列抽象方法，实现了这个接口就可以进行跨进程的通信。Client和Server端都要实现该功能。

**IInterface**

也是Base接口，表明Server提供了那些能力，是一个通讯协议。

**Binder**

提Binder服务的本地对象基类，实现IBinder接口，所有本地对象都要继承这个类

**BinderProxy**

Binder代理对象，也实现了Ibinder接口，具体的实现交给Native层（底层通过Binder驱动）来处理。Client端拿到的就是这个代理对象。

**Stub**

自动生成的类，继承Binder，表示是Binder本地对象；是一个抽象类，实现了IInterface接口，表明它的子类需要实现Server端要提供的具体能力。也就是具体的接口实现方法。

**Proxy**

实现了IInterface接口；实现了aidl生命的方法，但是最后是交给mRemote成员来处理，所以是代理类，mRemote变量实际上就是BinderProxy。

#### 类详解

这里我们看下我们写了AIDL文件之后，工具帮我们生成的类：

* IMyAidlInterface ：定义的可以跨进程通讯的接口：

```java
public interface IMyAidlInterface extends android.os.IInterface {
    //定义的接口
    public int add(int anInt, long aLong) throws android.os.RemoteException;
}

```

* Default：接口默认实现：

```java
//接口默认实现
public static class Default implements IMyAidlInterface {
    @Override
    public int add(int anInt, long aLong) throws android.os.RemoteException {
        return 0;
    }

    @Override
    public android.os.IBinder asBinder() {
        return null;
    }
}

```

* Stub类：属于服务端

```java
//表示是Binder本地对象,实现onTransact。来接收对应的参数信息，然后进行处理，属于Server端
public static abstract class Stub extends android.os.Binder implements IMyAidlInterface {
    private static final java.lang.String DESCRIPTOR = "IMyAidlInterface";

    public Stub() {
        this.attachInterface(this, DESCRIPTOR);
    }

    //当Client端调用以后，会调用asInterface()方法将Binder驱动返回的IBinder对象转化为具体的IInterface接口，转化完以后就可以通过接口来进行通讯了。
    public static IMyAidlInterface asInterface(android.os.IBinder obj) {
        if ((obj == null)) {
            return null;
        }
        android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
        if (((iin != null) && (iin instanceof IMyAidlInterface))) {
            //如果发现调用方是当前进程，那么直接返回本地对象即可，没必要再通过binder机制进行通信了
            return ((IMyAidlInterface) iin);
        }
        //否则返回代理对象
        return new IMyAidlInterface.Stub.Proxy(obj);
    }

    @Override
    public android.os.IBinder asBinder() {
        return this;
    }

    @Override
    public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
        java.lang.String descriptor = DESCRIPTOR;
        switch (code) {
            case INTERFACE_TRANSACTION: {
                reply.writeString(descriptor);
                return true;
            }
            case TRANSACTION_add: {
                data.enforceInterface(descriptor);
                int _arg0;
                _arg0 = data.readInt();
                long _arg1;
                _arg1 = data.readLong();
                int _result = this.add(_arg0, _arg1);
                reply.writeNoException();
                reply.writeInt(_result);
                return true;
            }
            default: {
                return super.onTransact(code, data, reply, flags);
            }
        }
    }
}
```

* Proxy类：属于Client端，用来给Client端来使用

```java
//代理对象，Client端，内部的mRemote对象是具体的ProxyBinder对象
private static class Proxy implements IMyAidlInterface {
    private android.os.IBinder mRemote;

    Proxy(android.os.IBinder remote) {
        mRemote = remote;
    }

    @Override
    public android.os.IBinder asBinder() {
        return mRemote;
    }

    public java.lang.String getInterfaceDescriptor() {
        return DESCRIPTOR;
    }


    @Override
    public int add(int anInt, long aLong) throws android.os.RemoteException {
        //这里属于Binder通讯的机制，但是通过Proxy类的包装，节省了用户开发的时间，用户直接调用add方法即可。
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeInt(anInt);
            _data.writeLong(aLong);
            boolean _status = mRemote.transact(Stub.TRANSACTION_add, _data, _reply, 0);
            if (!_status && getDefaultImpl() != null) {
                return getDefaultImpl().add(anInt, aLong);
            }
            _reply.readException();
            _result = _reply.readInt();
        } finally {
            _reply.recycle();
            _data.recycle();
        }
        return _result;
    }

    public static IMyAidlInterface sDefaultImpl;
}

```

这里会自动帮我们生成**Stub**和**Proxy**类，也就是在架构角度中的Binder封装类。

**Client和Server在同一个进程**

Stub.asInterface方法返回的是Stub对象，即Binder本地对象。也就是说和Binder跨进程通信无关，直接调用即可，此时Client调用方和Server响应方在同一个进程中。

**Client和Server在不同的进程**

Stub.asInterface方法返回的是Binder代理对象，需要通过Binder驱动完成跨进程通信。这种场景下，Client调用方线程会被挂起（Binder也提供了异步的方式，这里不讨论），等待Server响应后返回数据。这里要注意的是，**Server的响应是在Server进程的Binder线程池中处理的，并不是主线程**。

### 请求流程汇总

**Client调用Binder代理对象，Client线程挂起**

Client中拿到的IRemoteService引用实际上是Proxy，调用getPid方法实际上是调用Proxy的add方法，这个方法只是将参数序列化后，调用了mRemote成员的transact方法。Stub类中为IRemoteService中的每个方法定义了方法编号，transact方法中传入add方法的编号。此时Client调用方线程挂起，等待Server响应数据。

**Binder代理对象将请求派发给Binder驱动**

Proxy中的mRemote成员实际上是BinderProxy，而BinderProxy中的transact方法最终调用于transactNative方法，也就是说Client的请求派发给了Binder驱动来处理。

**Binder驱动将请求派发给Server**

Binder驱动经过一系列的处理后，将请求派发给了Server，即调用Server本地Binder对象(Stub)的onTransact方法最终在此方法中完成getPid方法的具体调用。在onTransact方法中，根据Proxy中调用transact时传入的方法编号来区别具体要处理的方法。

**唤醒Client线程，返回结果**

onTransact处理结束后，将结果写入reply并返回至Binder驱动，驱动唤醒挂起的Client线程，并将结果返回。至此，一次跨进程通信完成。

### 总结

Binder这种进程间通讯方式可以说是Android的一种核心机制，在我们做源码解析工作的时候，随处都可以看到它的影子。理解它能够使我们在做源码解析工作的时候更加的得心应手。后期我也会从源码角度去将这里面的各个流程进行梳理，敬请期待~

### 参考：

[关于Binder，作为应用开发者你需要知道的全部](https://mp.weixin.qq.com/s?__biz=MzAxMTI4MTkwNQ==&mid=2650826010&idx=1&sn=491e295e6a6c0fe450ad7aa91b6e97cc&chksm=80b7b184b7c03892392015840e4ebc7f2c3533ce8c1a98a5dc6d6d3dd19d53562805d76f6dcb&scene=38#wechat_redirect)

[Android Binder设计与实现](https://blog.csdn.net/freekiteyu/article/details/70082302?depth_1-utm_source=distribute.pc_relevant_right.none-task&utm_source=distribute.pc_relevant_right.none-task)

[Android源码的Binder权限是如何控制](https://www.zhihu.com/question/41003297/answer/89328987?from=profile_answer_card)

[Binder系列](http://gityuan.com/2015/10/31/binder-prepare/)

[Android从程序员到架构师之路]()



> 同步公众号[开了肯]

![image-20200404120045271](http://cdn.qiniu.kailaisii.com/typora/20200404120045-194693.png)
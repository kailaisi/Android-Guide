### IPC通讯

* IPC通信是跨越不同进程之间的通信。
* 一般一个Android应用程序里的各个组件是在同一个进程里执行。

### IPC设定通信

使用AndroidManifest.xml

* 一个应用，，通常含有多个Java类，这些类可以在一个进程里执行，也可以在不同的进程执行
* 一个进程只能有一个APP，但是一个APP可以占用多个进程。

### IBinder接口

* 当两个类都在同一个进城的时候，只需要函数调用就可以了。一旦两个类在不同的进程时，就不能使用函数调用了，只能采取IPC沟通机制。
* IPC依赖IBinder接口。Client端调用接口的`trancact()`函数，透过IPC机制调用远程的`onTransact()`函数

![img](http://cdn.qiniu.kailaisii.com/typora/202012/02/160410-784686.png)



**Binder通信流程如下：**

1.首先服务端需要向ServiceManager进行服务注册，ServiceManager有一个全局的service列表svcinfo，用来缓存所有服务的handler和name。

2.客户端与服务端通信，需要拿到服务端的对象，由于进程隔离，客户端拿到的其实是服务端的代理，也可以理解为引用。客户端通过ServiceManager从svcinfo中查找服务，ServiceManager返回服务的代理。

3.拿到服务对象后，我们需要向服务发送请求，实现我们需要的功能。通过 BinderProxy 将我们的请求参数发送给 内核，通过共享内存的方式使用内核方法 copy_from_user() 将我们的参数先拷贝到内核空间，这时我们的客户端进入等待状态。然后 Binder 驱动向服务端的 todo 队列里面插入一条事务，执行完之后把执行结果通过 copy_to_user() 将内核的结果拷贝到用户空间（这里只是执行了拷贝命令，并没有拷贝数据，binder只进行一次拷贝），唤醒等待的客户端并把结果响应回来，这样就完成了一次通讯。

在这里其实会存在一个问题，Client和Server之间通信是称为进程间通信，使用了Binder机制，那么Server和ServiceManager之间通信也叫进程间通信，Client和Server之间还会用到ServiceManager，也就是说Binder进程间通信通过Binder进程间通信来完成，这就好比是 孵出鸡前提却是要找只鸡来孵蛋，这是怎么实现的呢？

Binder的实现比较巧妙：预先创造一只鸡来孵蛋：ServiceManager和其它进程同样采用Binder通信，ServiceManager是Server端，有自己的Binder对象（实体），其它进程都是Client，需要通过这个Binder的引用来实现Binder的注册，查询和获取。

ServiceManager提供的Binder比较特殊，它没有名字也不需要注册，当一个进程使用BINDER_SET_CONTEXT_MGR_EXT命令将自己注册成ServiceManager时Binder驱动会自动为它创建Binder实体（这就是那只预先造好的鸡）。

其次这个Binder的引用在所有Client中都固定为0(handle=0)而无须通过其它手段获得。也就是说，一个Server若要向ServiceManager注册自己Binder就必须通过0这个引用号和ServiceManager的Binder通信。

类比网络通信，0号引用就好比域名服务器的地址，你必须预先手工或动态配置好。要注意这里说的Client是相对ServiceManager而言的，一个应用程序可能是个提供服务的Server，但对ServiceManager来说它仍然是个Client。

![image-20201209085011538](http://cdn.qiniu.kailaisii.com/typora/202012/09/085013-414777.png)

![image-20201203155331528](http://cdn.qiniu.kailaisii.com/typora/202012/03/155332-722377.png)

![image-20201203155528773](http://cdn.qiniu.kailaisii.com/typora/202012/03/155623-33186.png)



### AIDL分析

#### 类

**IBinder**

跨进程的Base接口，声明跨进程通信需要实现的一系列抽象方法，实现了这个接口就可以进行跨进程的通信。Client和Server端都要实现该功能

**IInterface**

也是Base接口，表明Server提供了那些能力，是一个通讯协议。

**Binder**

提Binder服务的本地对象基类，实现IBinder接口，所有本地对象都要继承这个类

**BinderProxy**

Binder代理对象，也实现了Ibinder接口，具体的实现交给Native层（底层通过Binder驱动）来处理。Client端拿到的就是这个代理对象。

**Stub**

自动生成的类，继承Binder，表示是Binder本地对象；是一个抽象类，实现了IInterface接口，表明它的子类需要实现Server端要提供的具体能力。也就是具体的接口实现方法。

**Proxy**

实现了IInterface接口；实现了aidl生命的呃方法，但是最后是交给mRemote成员来处理，所以是代理类，mRemote变量实际上就是BinderProxy。

```java
public interface IMyAidlInterface extends android.os.IInterface {
    //定义的接口
    public int add(int anInt, long aLong) throws android.os.RemoteException;
}

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

这里会自动帮我们生成**Stub**和**Proxy**类。

**Client和Server在同一个进程**

Stub.asInterface方法返回的是Stub对象，即Binder本地对象。也就是说和Binder跨进程通信无关，直接调用即可，此时Client调用方和Server响应方在同一个线程中。

**Client和Server在不同的进程**

Stub.asInterface方法返回的是Binder代理对象，需要通过Binder驱动完成跨进程通信。这种场景下，Client调用方线程会被挂起（Binder也提供了异步的方式，这里不讨论），等待Server响应后返回数据。这里要注意的是，**Server的响应是在Server进程的Binder线程池中处理的，并不是主线程**。



### 请求流程

**Client调用Binder代理对象，Client线程挂起**

Client中拿到的IRemoteService引用实际上是Proxy，调用getPid方法实际上是调用Proxy的add方法，这个方法只是将参数序列化后，调用了mRemote成员的transact方法。Stub类中为IRemoteService中的每个方法定义了方法编号，transact方法中传入add方法的编号。此时Client调用方线程挂起，等待Server响应数据。

**Binder代理对象将请求派发给Binder驱动**

Proxy中的mRemote成员实际上是BinderProxy，而BinderProxy中的transact方法最终调用于transactNative方法，也就是说Client的请求派发给了Binder驱动来处理。

**Binder驱动将请求派发给Server**

Binder驱动经过一系列的处理后，将请求派发给了Server，即调用Server本地Binder对象(Stub)的onTransact方法最终在此方法中完成getPid方法的具体调用。在onTransact方法中，根据Proxy中调用transact时传入的方法编号来区别具体要处理的方法。

**唤醒Client线程，返回结果**

onTransact处理结束后，将结果写入reply并返回至Binder驱动，驱动唤醒挂起的Client线程，并将结果返回。至此，一次跨进程通信完成。

### 参考：

https://mp.weixin.qq.com/s?__biz=MzAxMTI4MTkwNQ==&mid=2650826010&idx=1&sn=491e295e6a6c0fe450ad7aa91b6e97cc&chksm=80b7b184b7c03892392015840e4ebc7f2c3533ce8c1a98a5dc6d6d3dd19d53562805d76f6dcb&scene=38#wechat_redirect

https://blog.csdn.net/universus/article/details/6211589


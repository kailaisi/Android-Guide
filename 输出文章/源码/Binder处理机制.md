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



![image-20201203155331528](http://cdn.qiniu.kailaisii.com/typora/202012/03/155332-722377.png)

![image-20201203155528773](http://cdn.qiniu.kailaisii.com/typora/202012/03/155623-33186.png)
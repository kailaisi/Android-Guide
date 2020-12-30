## Handler 源码机制

在安卓中，如果想要在多线程间进行通讯，那么最常用到的方法就是 **Handler** 了，用法也很简单，创建 **Handler** ，然后进行 **Message** 的发送。那么其具体是如何实现的呢？我们一点点来进行一次大揭秘

### 四大组件

**Handler** 的消息机制主要是靠4个组件来进行完成的：**Handler **、**Message** 、**MessageQueue** 、**Looper**

很早之前看过一篇文章，对于Handler的消息机制有一个特别形象的比喻，就是送信的机制（一不小心暴漏了年龄）。

|    组件     | 邮件的发送 | 作用 |
| :----------: | :--------: | :----------------: |
|   Handler    | 收件人 | 发送消息，处理消息 |
|   Message    |     信件     |     传递的消息     |
| MessageQueue |    信箱    |      消息队列      |
|    Looper    |    邮差    |      消息循环      |

感觉很恰当的一个比喻。邮差(Looper)会不断的去查看信箱（MessageQueue）是否有邮件（Message），如果有的话，邮差就会送信，交给具体的收件人(Handler)处理。

### 使用方式

```java
    val handler=object: Handler(){
        override fun handleMessage(msg: Message?) {
            super.handleMessage(msg)
        }
    }
    //发送消息，可以在子线程
    handler.sendEmptyMessage(1)
```

可能有人说可能会导致内存泄漏，这里暂时不考虑，小伙伴既然知道有内存泄漏，那就肯定知道标准代码应该怎么撸。

### 揭秘

那么是如何通过简单的方式就实现了跨线程的数据通信了呢？

#### Handler大揭秘

```java
    public Handler() {
        this(null, false);
    }
    
    public Handler(Callback callback, boolean async) {
        if (FIND_POTENTIAL_LEAKS) {
            final Class<? extends Handler> klass = getClass();
            if ((klass.isAnonymousClass() || klass.isMemberClass() || klass.isLocalClass()) &&
                    (klass.getModifiers() & Modifier.STATIC) == 0) {
                Log.w(TAG, "The following Handler class should be static or leaks might occur: " +
                    klass.getCanonicalName());
            }
        }
        //获取线程对应的Looper，是线程安全的，通过ThreadLocal来实现。
        mLooper = Looper.myLooper();
        if (mLooper == null) {
            throw new RuntimeException(
                "Can't create handler inside thread that has not called Looper.prepare()");
        }
        //设置Handler对应的消息队列
        mQueue = mLooper.mQueue;
        //设置回调
        mCallback = callback;
        mAsynchronous = async;
    }
```

在获取 **Looper** 时，使用的是 **myLooper()** 来获取的对象，

```java
    public static @Nullable Looper myLooper() {
        return sThreadLocal.get();
    }
```

所以每个线程所对应的Looper对象都是不同的，那么通过什么方式设置的呢？答案是 **prepare()** 方法。

```java
public static void prepare() {
    prepare(true);
}

private static void prepare(boolean quitAllowed) {
	//如果已经设置过，那么就报错，如果没有，就设置
    if (sThreadLocal.get() != null) {
        throw new RuntimeException("Only one Looper may be created per thread");
    }
    sThreadLocal.set(new Looper(quitAllowed));
}
```

以前经常遇到的一个问题就是在子线程创建了Handler对象，直接就报错了，百度答案告诉你要先调用 **Looper.prepare()** 方法，原因就在这儿。那么很多人问了，为什么主线程创建Handler对象不用管啊？因为系统已经帮你做了啊~~~我可是有证据的

```java
    public static void prepareMainLooper() {
        prepare(false);
        synchronized (Looper.class) {
            if (sMainLooper != null) {
                throw new IllegalStateException("The main Looper has already been prepared.");
            }
            sMainLooper = myLooper();
        }
    }
```

**prepareMainLooper()** 这个方法是在ActivityThread的main函数中调用的。所以在主线程中使用Handler的时候不需要我们做什么特殊的处理。

现在 **Handler** 对象创建完了，那么下一步就是进行消息的创建和发送了

### 消息的发送

进行消息的发送，有很多种方式。

![image-20200312114058456](http://cdn.qiniu.kailaisii.com/typora/20200312114059-875648.png)

我们按照我们最开始的测试代码来进行跟踪

```java
    public final boolean sendEmptyMessage(int what)
    {
        return sendEmptyMessageDelayed(what, 0);
    }
    public final boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        Message msg = Message.obtain();
        msg.what = what;
        return sendMessageDelayed(msg, delayMillis);
    }
    
    public final boolean sendMessageDelayed(Message msg, long delayMillis)
    {
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        return sendMessageAtTime(msg, SystemClock.uptimeMillis() + delayMillis);
    }
    
    public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
        MessageQueue queue = mQueue;
        if (queue == null) {
        	//检测队列不能为空
            RuntimeException e = new RuntimeException(this + " sendMessageAtTime() called with no mQueue");
            Log.w("Looper", e.getMessage(), e);
            return false;
        }
        return enqueueMessage(queue, msg, uptimeMillis);
    }
```

发现了么，其实不管调用那个方法，其实最后都会进入到 **sendMessageAtTime** 这个函数里面。并且通过 **enqueueMessage** 将消息放到对应的消息队列中。

```java
    private boolean enqueueMessage(MessageQueue queue, Message msg, long uptimeMillis) {
        msg.target = this;
        if (mAsynchronous) {
            msg.setAsynchronous(true);
        }
        return queue.enqueueMessage(msg, uptimeMillis);
    }
```

也是个入队的操作，只是对消息进行了一些相关的设置，设置了对应的target，和同步参数。最重要的是 **enqueueMessage** 操作

```java
    boolean enqueueMessage(Message msg, long when) {
        if (msg.target == null) {//这个target是对应的Handler，总得需要知道消息发给谁吧？
            throw new IllegalArgumentException("Message must have a target.");
        }
        if (msg.isInUse()) {//如果消息已经被用过了，不能再重复消费了
            throw new IllegalStateException(msg + " This message is already in use.");
        }
        //线程安全，同步
        synchronized (this) {
            if (mQuitting) {//如果队列已经关闭，则直接将消息回收处理掉
                IllegalStateException e = new IllegalStateException(
                        msg.target + " sending message to a Handler on a dead thread");
                Log.w(TAG, e.getMessage(), e);
                msg.recycle();
                return false;
            }
            //标记消息已经被使用了
            msg.markInUse();
            msg.when = when;
            //标记当前Handler要发送的消息
            Message p = mMessages;
            boolean needWake;
            if (p == null || when == 0 || when < p.when) {
                //如果当前队列没有要处理的消息，或者新入队的消息需要立即处理或者如对消息的发送时间比当前要处理的小时发送时间早
                //那么将消息放入到队列头，并唤醒消息
                msg.next = p;
                mMessages = msg;
                needWake = mBlocked;
            } else {
                //判断消息队列里有消息，则根据 消息（Message）创建的时间 插入到队列中
                needWake = mBlocked && p.target == null && msg.isAsynchronous();
                Message prev;
                for (;;) {
                    prev = p;
                    p = p.next;
                    if (p == null || when < p.when) {
                        break;
                    }
                    if (needWake && p.isAsynchronous()) {
                        needWake = false;
                    }
                }
                msg.next = p; 
                prev.next = msg;
            }
            if (needWake) {
            	//如果需要唤醒队列对消息的处理，通过nativeWake可以唤醒 nativePollOnce （这个会在queue.next()中调用，使对于消息的处理进行休眠操作）的沉睡
                nativeWake(mPtr);
            }
        }
        return true;
    }
```

到现在为止，我们**Message**（邮件）已经进入到了消息队列**MessageQueue**（邮箱）中了，那么程序是什么时候从 **MessageQueue** 中读取数据的呢？  主要就是靠我们的**Looper**（邮差）。Looper会通过 **loop()**函数一直遍历循环。

```java
    //消息循环，即从消息队列中获取消息、分发消息到Handler
    public static void loop() {
        final Looper me = myLooper();
        if (me == null) {
            throw new RuntimeException("No Looper; Looper.prepare() wasn't called on this thread.");
        }
        //获取当前Looper的消息队列
        final MessageQueue queue = me.mQueue;
        //循环遍历消息
        for (;;) {
            //获取到下一个消息
            Message msg = queue.next();
            if (msg == null) {
                // No message indicates that the message queue is quitting.
                return;
            }
            //派发消息到对应的Handler
            msg.target.dispatchMessage(msg);
            ...
            //消息的回收
            msg.recycleUnchecked();
        }
    }

```

可以看到，**loop()** 函数会一直循环从队列中拿出消息来进行处理。

我们知道，在将消息保存到消息队列的时候，是有一个消息的投递时间参数的，也就是如果消息还没有到处理的时间，那么是不会进行**dispatchMessage**的分发的

那么**queue.next()** 里面是如何来进行消息什么时候执行操作的呢？

```java
   Message next() {
        //用于确定下一个消息的执行时间
        int nextPollTimeoutMillis = 0;
        for (;;) {
            //如果遍历有一个消息的下一个执行时间不是当前时间的话，会进入等待，然后等待一段时间后唤醒，再继续执行
            //或者有地方调用了nativeWake方法来唤醒（这个方法会在消息入队的时候调用）
            nativePollOnce(ptr, nextPollTimeoutMillis);
            synchronized (this) {
                final long now = SystemClock.uptimeMillis();
                //用于记录
                Message prevMsg = null;
                Message msg = mMessages;
                //消息出队
                if (msg != null && msg.target == null) {
                    //遍历获取到不为空的message消息
                    do {
                        prevMsg = msg;
                        msg = msg.next;
                    } while (msg != null && !msg.isAsynchronous());
                }
                if (msg != null) {
                    if (now < msg.when) {
                        //还没有到下一条消息的处理时间，那么计算器下一次执行的时间，即下一次唤醒的时间
                        nextPollTimeoutMillis = (int) Math.min(msg.when - now, Integer.MAX_VALUE);
                    } else {
                        //已经到了消息要处理的时候了，那么将当前消息返回
                        mBlocked = false;
                        if (prevMsg != null) {
                            prevMsg.next = msg.next;
                        } else {
                            mMessages = msg.next;
                        }
                        msg.next = null;
                        if (DEBUG) Log.v(TAG, "Returning message: " + msg);
                        msg.markInUse();
                        return msg;
                    }
                } else {
                    //消息队列中已经没有消息了
                    nextPollTimeoutMillis = -1;
                }
                if (mQuitting) {
                    dispose();
                    return null;
                }
            }
            nextPollTimeoutMillis = 0;
        }
    }

```

这里有一个机制就是唤醒和等待，**nextPollTimeoutMillis**会使当前程序进入“等待”，如果参数是-1，就只能等**nativeWake**来进行“唤醒”，否则的话，会在等待对应的**nextPollTimeoutMillis**时间后恢复，然后执行相关的**message**消息。

这就是我们Handler的整个执行机制。如果感觉难以理解的话，我觉得开头的那个比喻就比较形象了。

好了，到此为止~~



### 总结

1. 对于message的获取，最好使用obtainMessage方法，这种方式会从池中获取可以使用的消息，而不需要每次都new对象出来。
2. Handler的消息，是放在其发送的线程的。只有需要执行的时候，通过target。调用到handler所在的线程。
3. Handler，是进行线程间通讯。主要有4个列，Message消息，内部含有消息类的具体的执行的对象，也就是target:Handler,下一个消息。MessageQueue：消息队列，是以一个以链表形式存在的，每一个消息都指向了下一个要执行的消息。Looper：循环，能够不断地从queue中获取对应的消息来执行。需要通过prepare()来启动执行，而主线程是在ActivityThread中启动了。循环并不会阻塞，当没有到时间的时候，会休眠，时间到了以后通过epoll机制重新启动。消息队列有一个Idle队列，可以存放并不是特别紧急的消息，当CPU空闲以后再执行的操作。对于停止则可以使用quit和quitSafe两种方式。对于Looper是通过ThreadLocal来保证线程安全的.
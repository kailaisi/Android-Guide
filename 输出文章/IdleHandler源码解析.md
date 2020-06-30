## IdleHandler源码解析

在之前看过一遍[Handler源码解析]()，但是最近看各种性能优化的时候，总是能够提到IdleHandler。所以今天我们一起看看，IdleHandler是如何实现空闲时候再去处理消息的。

### 用法

对于IdleHandler的使用，比较简单。

```java
Looper.myQueue().addIdleHandler(new IdleHandler() {  
    @Override  
    public boolean queueIdle() {  
        //处理自己的业务
        return false;    
    }  
});
```

### 源码

我们直接看一下IdleHandler类的说明

```java
    //MessageQueue.java
	//当消息队列阻塞，等待新的消息到来时（当前为空闲状态），会回调方法。
    public static interface IdleHandler {
        // 如果返回true，那么将IdleHandler维持在消息队列中。返回false，则将IdleHandler从消息队列移除
        //注意，如果返回true的话，需要自己手动的将IdleHandler从消息队列中移除。
        boolean queueIdle();
    }
```

跟踪看一下**addIdleHandler()**方法。

```java
    //MessageQueue.java
    //空闲队列
    private final ArrayList<IdleHandler> mIdleHandlers = new ArrayList<IdleHandler>();
	//添加一个当消息队列空闲的时候，执行的操作。
    //如果IdelHandler的queueIdle返回false，那么执行完会自动从list中移除，否则就需要自己手动移除了
    public void addIdleHandler(@NonNull IdleHandler handler) {
        if (handler == null) {
            throw new NullPointerException("Can't add a null IdleHandler");
        }
        synchronized (this) {
            //放入到空闲执行队列
            mIdleHandlers.add(handler);
        }
    }
```

这里会将对应的handler放入到空闲队列中。那么这个方法什么时候执行呢？

在[Handler源码解析]()中我们说过，Looper会不断的从**MessageQueue**中获取Message消息，然后去执行。这个获取Message消息的方法，就是**next()**方法

```java
    //MessageQueue.java
	Message next() {
        ...
        int pendingIdleHandlerCount = -1; // -1 only during first iteration
        //用于确定下一个消息的执行时间
        int nextPollTimeoutMillis = 0;
        for (;;) {
                ...
                //如果执行了Looper.quit()  或者Looper.quitSafe()。那么就不会执行了
                if (mQuitting) {
                    dispose();
                    return null;
                }
                if (pendingIdleHandlerCount < 0&& (mMessages == null || now < mMessages.when)) {
                    pendingIdleHandlerCount = mIdleHandlers.size();
                }
                if (pendingIdleHandlerCount <= 0) {
                    // No idle handlers to run.  Loop and wait some more.
                    mBlocked = true;
                    continue;
                }

                if (mPendingIdleHandlers == null) {
                    //这里最多处理4个IdleHandler
                    mPendingIdleHandlers = new IdleHandler[Math.max(pendingIdleHandlerCount, 4)];
                }
                mPendingIdleHandlers = mIdleHandlers.toArray(mPendingIdleHandlers);
            }

            for (int i = 0; i < pendingIdleHandlerCount; i++) {
                final IdleHandler idler = mPendingIdleHandlers[i];
                //释放对应的IdleHandler
                mPendingIdleHandlers[i] = null; 
                boolean keep = false;
                keep = idler.queueIdle();
                if (!keep) {
                    synchronized (this) {
                        //将IdleHandler移除。如果返回的是true，那么就需要自己手动去移除了
                        mIdleHandlers.remove(idler);
                    }
                }
            }
        	//将count置为0，保证在本次遍历循环获取msg消息过程不再执行空闲的队列了。
            pendingIdleHandlerCount = 0;
             //当执行完空闲队列以后，可能有信息的消息到来，所以将nextPollTimeoutMillis置为0，再次检测是否有消息的
            nextPollTimeoutMillis = 0;
        }
    }

```

> 每次消费掉一个有效message，在获取下一个message时，如果当前时刻没有需要消费的有效(需要立刻执行)的message，那么会执行IdleHandler一次，执行完成之后线程进入休眠状态，直到被唤醒。

这个整体的逻辑可能复杂一些，所以我们梳理一下。

假设现在Looper调用了next()方法。

**队列中有消息A**

1. 第一次for循环，会获取到A消息，然后检测，发现A还没有到执行时间。那么将nextPollTimeoutMillis置为执行的时间间隔。这时候相当于是空闲状态。pendingIdleHandlerCount=-1，所以会执行一次空闲队列。空闲队列执行完之后pendingIdleHandlerCount=0。保证以后的循环都不会再执行空闲队列了。
2. 第二次执行for循环，会睡眠nextPollTimeoutMillis，然后唤醒。

**队列为空**

1. 第一次for循环，获取到的msg为空。将nextPollTimeoutMillis=-1。这时候相当于是空闲状态。pendingIdleHandlerCount=-1，所以会执行一次空闲队列。空闲队列执行完之后pendingIdleHandlerCount=0。保证以后的循环都不会再执行空闲队列了。
2. 第二次执行for循环，nextPollTimeoutMillis为-1，就会一直睡眠，知道有新消息的唤醒，然后获取到消息，并返回。

所以哪怕我们这个IdleHandler不移除，也并不会说每次循环都执行的。只有处理完一次Message消息以后，才有可能会再次执行。

IdleHandler作为一种空闲时执行的操作，我们可以利用它的这种优势做什么呢？

* Activity启动优化：onCreate，onStart，onResume中耗时较短但非必要的代码可以放到IdleHandler中执行，减少启动时间。
* 发送一个返回true的IdleHandler，在里面让某个View不停闪烁，这样当用户发呆时就可以诱导用户点击这个View，这也是种很酷的操作。
* 在一个View绘制完成之后添加其他依赖于这个View的View，当然这个用View#post()也能实现，区别就是前者会在消息队列空闲时执行。

更多的高级玩法，就等待大家一起去挖掘了~~~

### 总结

* **mIdleHanders** 不会空时，并不会进入死循环，就是因为将pendingIdlehanderCount 置为了0。只有为-1的时候，才会执行。所以就不会再次执行从而造成死循环了。


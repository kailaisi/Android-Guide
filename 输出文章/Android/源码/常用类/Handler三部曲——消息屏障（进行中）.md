Handler三部曲——消息屏障（待整理）

为了能够更从前入深的理解消息屏障，我们从屏障消息的插入和移除开始入手，然后再通过next()方法来深入理解消息屏障的具体实现原理。

### 屏障消息的插入

对于消息屏障的插入，使用的是**postSyncBarrier**方法。

```java
//frameworks\base\core\java\android\os\MessageQueue.java
	//设置一个同步屏障。这个方法调用的次数必须和removeSyncBarrier同步。
    private int postSyncBarrier(long when) {
        // Enqueue a new sync barrier token.
        // We don't need to wake the queue because the purpose of a barrier is to stall it.
        synchronized (this) {
            final int token = mNextBarrierToken++;
            final Message msg = Message.obtain();
			//插入的msg消息的target并没有进行设置，所以会是null。也就是说向任务队列中插入一个target为null的Message消息，来表示一个同步屏障的消息
			//因为target为空，所以是没有对应的handler去执行这个msg消息的
			msg.markInUse();
			//消息屏障也是有对应的执行时间的
            msg.when = when;
            msg.arg1 = token;
			//将消息屏障插入到消息队列对应的位置。
            Message prev = null;
            Message p = mMessages;
            if (when != 0) {
                while (p != null && p.when <= when) {
                    prev = p;
                    p = p.next;
                }
            }
            if (prev != null) {
                msg.next = p;
                prev.next = msg;
            } else {
                msg.next = p;
                mMessages = msg;
            }
			//返回token，token是对应的屏障的序列号。这个token主要是用来移除消息屏障的
            return token;
        }
    }
```

屏障消息，**其实是一个target为null的Message消息**。而且该消息会正常的添加到消息队列中。

### 消息屏障移除

```java
//frameworks\base\core\java\android\os\MessageQueue.java
	//将消息屏障从消息队列移除
    public void removeSyncBarrier(int token) {
        // Remove a sync barrier token from the queue.
        // If the queue is no longer stalled by a barrier then wake it.
        synchronized (this) {
            Message prev = null;
            Message p = mMessages;
			//找到target为空的message消息
            while (p != null && (p.target != null || p.arg1 != token)) {
                prev = p;
                p = p.next;
            }
			//记录是否需要唤醒线程
            final boolean needWake;
			//将消barrier从消息队列移除
            if (prev != null) {
                prev.next = p.next;
				//barrier消息并为位于消息队列头，证明还没有执行到
                needWake = false;
            } else {
				//barrier消息位于消息队列头部，那么当前的barrier消息已经执行到了，那么后面的正常的Message就不会执行
				//这时候，移除了barrier消息，那么为了能够执行后面的Message消息，需要进行唤醒
                mMessages = p.next;
                needWake = mMessages == null || mMessages.target != null;
            }
            if (needWake && !mQuitting) {
				//唤醒looper循环
                nativeWake(mPtr);
            }
```

对于消息屏障的移除，只是从消息队列中进行了移除工作，而另一个重点，则根据实际的情况，进行消息队列的唤醒工作。

### 屏障原理

屏障消息作为一种Message消息，肯定是在next()方法中去获取的，那么我们跟踪一下，当遇到一个屏障消息的时候，程序是如何处理的。

```java
//frameworks\base\core\java\android\os\MessageQueue.java
	Message next() {
        //用于确定下一个消息的执行时间。先设置为0，是为了直接检查当前有没有需要执行的msg，如果没有，那么这个nextPoll就会置为-1或者一个正数值
        int nextPollTimeoutMillis = 0;
        for (;;) {
            //找到要执行的msg消息
            nativePollOnce(ptr, nextPollTimeoutMillis);
            synchronized (this) {
                if (msg != null && msg.target == null) {
                    //这里msg的target为null，表明这是一个同步屏障。当我们设置了同步屏障以后，会优先执行异步消息。
                    //如果没有找到异步消息的话，下面会设置nextPollTimeoutMillis = -1，从而导致睡眠，直到有一个异步消息为止
                    do {
                        prevMsg = msg;
                        msg = msg.next;
						//通过循环找到第一个异步消息Message
                    } while (msg != null && !msg.isAsynchronous());
                }
                if (msg != null) {//找到了异步消息
                    if (now < msg.when) {
                        //还未到消息要执行的时间，那么这时候设置等待时间。再次循环的时候直接就等待了。
                        nextPollTimeoutMillis = (int) Math.min(msg.when - now, Integer.MAX_VALUE);
                    } else {
                        //找到了消息，那么就返回消息，并执行
                        ...
                        return msg;
                    }
                } else {
                    //没有找到异步消息，那么这时候设置为-1，会无限等待，等到有消息的到来
                    nextPollTimeoutMillis = -1;
                }
        }
    }
```

* 如果是屏障消息，那么会查找后面的异步消息
* 如果没有异步消息，则等待，等到消息唤醒
* 如果有异步消息，但是还未到执行时间，则等待一定的时间之后执行。

所以屏障消息的作用，其实就是为了执行异步消息，屏障消息本身是不会执行的，所以没变要设置target。但是其实这种以某个字段是否为空来判断来区分类型的方式个人感觉如果后期扩展可能会有问题。

那么既然是需要处理的是异步消息，那么我们当插入异步消息的时候是如何处理，并唤醒的。

```java
//frameworks\base\core\java\android\os\MessageQueue.java
	boolean enqueueMessage(Message msg, long when) {
        synchronized (this) {
            msg.markInUse();
            msg.when = when;
            Message p = mMessages;
            boolean needWake;
            if (p == null || when == 0 || when < p.when) {
                //当前消息插入到队列头。
                //如果当前队列没有要处理的消息，或者新入队的消息需要立即处理或者如对消息的发送时间比当前要处理的消息发送时间早
                //那么将消息放入到队列头，并唤醒消息
                msg.next = p;
                mMessages = msg;
                needWake = mBlocked;
            } else {
                //消息没有插入到队列头。
                //当前，而且当前线程是休眠的，而且队列头是消息屏障，而且当前消息是异步消息，那么就先按照需要唤醒处理
                needWake = mBlocked && p.target == null && msg.isAsynchronous();
                Message prev;
                for (;;) {
                    prev = p;
                    p = p.next;
                    if (p == null || when < p.when) {
                        break;
                    }
                    if (needWake && p.isAsynchronous()) {
						//如果插入的消息前面还有异步消息，说明当前消息肯定是没必要直接唤醒的，所以设置为不需要唤醒
                        needWake = false;
                    }
                }
                msg.next = p; // invariant: p == prev.next
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

在消息进行入队操作时，会根据实际情况进行线程的唤醒。而在两种情况下会进行唤醒：

* 如果消息插入到队列头，如果当前线程是休眠的，则唤醒
* 如果没有插入到队列头，如果当前线程是休眠的，并且队列头是屏障消息，而且当前消息是最早的一条异步消息，则唤醒线程

其他的情况，只需要正常插入到队列中即可。

### 屏障消息的使用

Framework层对于消息屏障的使用并不多。其中对于View的绘制工作就是其中一处使用到消息屏障的地方。

```java
//frameworks\base\core\java\android\view\ViewRootImpl.java
    void scheduleTraversals() {
        if (!mTraversalScheduled) {
			///表示在排好这次绘制请求前，不再排其它的绘制请求
            mTraversalScheduled = true;
			//Handler 的同步屏障,拦截 Looper 对同步消息的获取和分发,只能处理异步消息
			//也就是说，对View的绘制渲染操作优先处理
            mTraversalBarrier = mHandler.getLooper().getQueue().postSyncBarrier();
			//mChoreographer能够接收系统的时间脉冲，统一动画、输入和绘制时机,实现了按帧进行绘制的机制
			//这里增加了一个事件回调的类型。在绘制时，会调用mTraversalRunnable方法
            //postCallback的时候，顺便请求vsync垂直同步信号scheduleVsyncLocked
            //mTraversalRunnable这个线程会进行onMeasure，onLayout,onDraw的处理操作
            mChoreographer.postCallback(Choreographer.CALLBACK_TRAVERSAL, mTraversalRunnable, null);
            ....
        }
    }
```

这里插入了一个消息屏障，然后执行了**postCallback**方法。

```java
//frameworks\base\core\java\android\view\Choreographer.java
	public void postCallback(int callbackType, Runnable action, Object token) {
        postCallbackDelayed(callbackType, action, token, 0);
    }

    public void postCallbackDelayed(int callbackType,Runnable action,..) {
        postCallbackDelayedInternal(callbackType, action, token, delayMillis);
    }

	private void postCallbackDelayedInternal(int callbackType,Object action,..) {
        synchronized (mLock) {
            final long now = SystemClock.uptimeMillis();
            final long dueTime = now + delayMillis;
            //将callback放入到对应的队列中
            mCallbackQueues[callbackType].addCallbackLocked(dueTime, action, token);
            if (dueTime <= now) {
				//需要立即进行绘制
                scheduleFrameLocked(now);
            } else {
            	//延期绘制的消息，通过handler来进行处理
                Message msg = mHandler.obtainMessage(MSG_DO_SCHEDULE_CALLBACK, action);
                msg.arg1 = callbackType;
                //设置为异步消息，如果这里设置了同步屏障，则会优先于其他的消息执行。
                msg.setAsynchronous(true);
                mHandler.sendMessageAtTime(msg, dueTime);
            }
        }
    }
```

这个方法其实就是插入了一个异步消息，该异步消息会请求Vsync信号，当收到Vsync信号之后，会执行mTraversalRunnable。

```java
//frameworks\base\core\java\android\view\ViewRootImpl.java 
	final TraversalRunnable mTraversalRunnable = new TraversalRunnable();
	final class TraversalRunnable implements Runnable {
        @Override
        public void run() {
            doTraversal();
        }
    }

    void doTraversal() {
        if (mTraversalScheduled) {
            mTraversalScheduled = false;
			//移除同步屏障
            mHandler.getLooper().getQueue().removeSyncBarrier(mTraversalBarrier);
			//重点方法   执行绘制工作
            performTraversals();
        }
    }
```

### 总结

* 消息屏障其实是给异步消息开绿色通道，优先执行。
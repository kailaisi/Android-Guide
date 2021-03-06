Handler三部曲——消息屏障（进行中）

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

为了能够实现

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
* 如果没有异步消息，则等待，等到下一个消息的唤醒
* 如果有异步消息，但是还未到执行时间，则等待一定的时间之后执行。

所以屏障消息的作用，其实就是为了执行异步消息，本身是不会执行的，所以没变要设置target。但是其实这种以某个字段是否为空来判断来区分类型的方式个人感觉如果后期扩展可能会有问题。

https://www.cnblogs.com/renhui/p/12875589.html
### EventBus源码解析

#### 摘要

EventBus是一种用于Android的事件发布-订阅总线，由GreenRobot开发。它简化了应用程序内各个组件之间进行通信的复杂度，尤其是碎片之间进行通信的问题，可以避免由于使用广播通信而带来的诸多不便。

### 示例

Event的使用非常简单

```java

//注册和取消消息的注册
 @Override
 public void onStart() {
     super.onStart();
     EventBus.getDefault().register(this);
 }
//定义消息的处理
@Subscribe(threadMode = ThreadMode.MAIN)  
public void onMessageEvent(MessageEvent event) {/* Do something */};

 @Override
 public void onStop() {
     super.onStop();
     EventBus.getDefault().unregister(this);
 }
 //发送消息
 EventBus.getDefault().post(new MessageEvent());
```

只需要通过在通过**register()**，并且在类中的某个方法使用**Subscribe**注解。就可以实现消息的注册。

#### 解析

我们从消息的注册开始分析

```java
    //单例获取方法双重检测
    public static EventBus getDefault() {
        EventBus instance = defaultInstance;
        if (instance == null) {
            synchronized (EventBus.class) {
                instance = EventBus.defaultInstance;
                if (instance == null) {
                    instance = EventBus.defaultInstance = new EventBus();
                }
            }
        }
        return instance;
    }
    //注册订阅者
    public void register(Object subscriber) {
        Class<?> subscriberClass = subscriber.getClass();
        //根据类名称，反射获取到使用了注解的方法
        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
        synchronized (this) {
            // 对订阅方法进行注册
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                subscribe(subscriber, subscriberMethod);
            }
        }
    }
```

可以看到，注册时，通过单例方式获取EventBus对象，然后根据传入的类名，获取到对应的订阅的方法，然后再将订阅的方法进行注册。

我们先从获取订阅的方法**findSubscriberMethods()**来跟踪看一下

```java
    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        //先获取缓存
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        if (subscriberMethods != null) {
            return subscriberMethods;
        }
        if (ignoreGeneratedIndex) {//默认是false
            subscriberMethods = findUsingReflection(subscriberClass);
        } else {
            subscriberMethods = findUsingInfo(subscriberClass);
        }
        
        if (subscriberMethods.isEmpty()) {
            throw new EventBusException("Subscriber " + subscriberClass
                    + " and its super classes have no public methods with the @Subscribe annotation");
        } else {
            //放入缓存
            METHOD_CACHE.put(subscriberClass, subscriberMethods);
            return subscriberMethods;
        }
    }
```

先根据类名从缓存中获取，如果缓存中不存在，根据相应的属性进行不同的处理。我们这里按照默认配置，会通过**findUsingInfo**方法来获取订阅的方法。

```java
private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
    //准备FindState，FindState对象用于保存注解的方法的相关信息。prepareFindState是通过享元方法，在池中获取对应的对象。
    FindState findState = prepareFindState();
    //初始化FindState
    findState.initForSubscriber(subscriberClass);
    //遍历订阅者，一直遍历循环父类，
    while (findState.clazz != null) {
        //获取订阅者信息
        findState.subscriberInfo = getSubscriberInfo(findState);
        if (findState.subscriberInfo != null) {
            //如果使用了MyEventBusIndex，将会进入到这里并获取订阅方法信息
            SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();
            for (SubscriberMethod subscriberMethod : array) {
                if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
                    findState.subscriberMethods.add(subscriberMethod);
                }
            }
        } else {
            //通过反射获取类中注解的方法
            findUsingReflectionInSingleClass(findState);
        }
        findState.moveToSuperclass();
    }
    return getMethodsAndRelease(findState);
}
```

在上面的代码中，会从当前订阅者类开始直到它最顶层的父类进行遍历来获取订阅方法信息。这里在循环的内部会根据我们是否使用了**MyEventBusIndex**走两条路线，对于我们没有使用它的，会直接使用反射来获取订阅方法信息，即进入2处。

```java
private void findUsingReflectionInSingleClass(FindState findState) {
    Method[] methods;
    //获取类中所有的方法
    methods = findState.clazz.getDeclaredMethods();
    ...
    for (Method method : methods) {
        int modifiers = method.getModifiers();
        if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            //参数长度为1
            if (parameterTypes.length == 1) {
                //获取是否为Subscribe注解的类
                Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                if (subscribeAnnotation != null) {
                    //符合EventBus的监听者使用条件
                    Class<?> eventType = parameterTypes[0];
                    if (findState.checkAdd(method, eventType)) {
                        //获取设置的ThreadMode属性
                        ThreadMode threadMode = subscribeAnnotation.threadMode();
                        //封装SubscriberMethod对象，放入到subscriberMethods列表中
                        findState.subscriberMethods.add(new SubscriberMethod(method, eventType, threadMode,
                                subscribeAnnotation.priority(), subscribeAnnotation.sticky()));
                    }
                }
            } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new EventBusException("@Subscribe method " + methodName +
                        "must have exactly 1 parameter but has " + parameterTypes.length);
            }
        } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
            String methodName = method.getDeclaringClass().getName() + "." + method.getName();
            throw new EventBusException(methodName +
                    " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
        }
    }
}
```

这个方法通过遍历所有的方法，查找使用了**Subscribe**的类，并且参数个数是1的方法，将其进行包装为**SubscriberMethod**对象之后，放入到**subscriberMethods**队列中。

到现在为止，已经拿到了所有的方法。那么是如何将相关的方法进行订阅处理的呢？

```java
private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
    //订阅方法中的参数类型
    Class<?> eventType = subscriberMethod.eventType;
    //将类和方法包装为Subscription对象
    Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
    //缓存中获取同一个消息的订阅者列表
    CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
    if (subscriptions == null) {
        subscriptions = new CopyOnWriteArrayList<>();
        subscriptionsByEventType.put(eventType, subscriptions);
    } else {
        //列表中已经存在了，那么报错，不允许重复注册
        if (subscriptions.contains(newSubscription)) {
            throw new EventBusException("Subscriber " + subscriber.getClass() + " already registered to event "
                    + eventType);
        }
    }
    int size = subscriptions.size();
    for (int i = 0; i <= size; i++) {
        //根据优先级进行添加或者放到队尾
        if (i == size || subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
            subscriptions.add(i, newSubscription);
            break;
        }
    }
    //获取类中所有的订阅方法列表
    List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
    if (subscribedEvents == null) {
        subscribedEvents = new ArrayList<>();
        typesBySubscriber.put(subscriber, subscribedEvents);
    }
    //将订阅的消息类型放入到列表
    subscribedEvents.add(eventType);
    // 如果是黏性事件还要进行如下的处理
    if (subscriberMethod.sticky) {
        //黏性事件，是指在发送黏性事件之后再订阅该事件也能收到该事件
        if (eventInheritance) {//默认false
            Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
            for (Map.Entry<Class<?>, Object> entry : entries) {
                Class<?> candidateEventType = entry.getKey();
                if (eventType.isAssignableFrom(candidateEventType)) {
                    Object stickyEvent = entry.getValue();
                    checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                }
            }
        } else {
            //根据事件类型获取对应的黏性事件，
            Object stickyEvent = stickyEvents.get(eventType);
            //发送订阅消息
            checkPostStickyEventToSubscription(newSubscription, stickyEvent);
        }
    }
}
```

这里面根据消息类型进行了分类保存。**subscriptionsByEventType**根据对象根据消息类型，维护了一个列表。列表中保存了同一个消息类型的所有方法。**subscribedEvents**则根据订阅者的类名称，维护了一个列表。列表中保存了该类中所订阅的所有消息类型。

在程序的最后，则对黏性事件进行了处理。将对应的黏性事件进行了消息的发送。

到这里，我们已经知道了是如何对消息进行注册的。那么当有新的消息来临时，肯定是根据这里面维护的相关列表来进行查询。我们来看一下源码的处理

```java
public void post(Object event) {
    //ThreaLocal获取当前线程的PostingThreadState对象。
    PostingThreadState postingState = currentPostingThreadState.get();
    //获取PostingThreadState对象中的消息队列
    List<Object> eventQueue = postingState.eventQueue;
    //放入到队列中
    eventQueue.add(event);
    //未进行消息发送
    if (!postingState.isPosting) {
        postingState.isMainThread = isMainThread();
        //设置标记为，标记为正在进行消息的发送
        postingState.isPosting = true;
        if (postingState.canceled) {
            throw new EventBusException("Internal error. Abort state was not reset");
        }
        try {
            while (!eventQueue.isEmpty()) {
                //循环队列，发送消息
                postSingleEvent(eventQueue.remove(0), postingState);
            }
        } finally {
            //进行状态的处理
            postingState.isPosting = false;
            postingState.isMainThread = false;
        }
    }
}
```

**post**方法主要是将消息放入到当前线程的队列，然后遍历队列进行消息的发送。这里最主要的就是**postSingleEvent**方法。

```java
private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
    Class<?> eventClass = event.getClass();
    boolean subscriptionFound = false;
    if (eventInheritance) {//默认为false
        List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
        int countTypes = eventTypes.size();
        for (int h = 0; h < countTypes; h++) {
            Class<?> clazz = eventTypes.get(h);
            subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
        }
    } else {
        //进行消息的发送，返回是否有消息接收者
        subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
    }
    if (!subscriptionFound) {
        //如果消息没有响应的接收者，打印消息并且发送一个NoSubscriberEvent消息
        if (logNoSubscriberMessages) {
            logger.log(Level.FINE, "No subscribers registered for event " + eventClass);
        }
        if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                eventClass != SubscriberExceptionEvent.class) {
            post(new NoSubscriberEvent(this, event));
        }
    }
}
```

这段代码主要根据eventInheritance进行了不同的处理。如果没有对应的消息接收者，进行了日志的打印，并发送了一个**NoSubscriberEvent**时间消息。

我们看一下**postSingleEventForEventType**函数，是如何发送消息的。

```java
private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
    CopyOnWriteArrayList<Subscription> subscriptions;
    synchronized (this) {
        //获取注册的订阅者列表
        subscriptions = subscriptionsByEventType.get(eventClass);
    }
    if (subscriptions != null && !subscriptions.isEmpty()) {
        //遍历订阅者，然后逐个发送注册消息
        for (Subscription subscription : subscriptions) {
            postingState.event = event;
            postingState.subscription = subscription;
            boolean aborted;
            try {
            	//发送给订阅者
                postToSubscription(subscription, event, postingState.isMainThread);
                //如果消息已经停止，则直接跳出
                aborted = postingState.canceled;
            } finally {
                //postingState进行复原
                postingState.event = null;
                postingState.subscription = null;
                postingState.canceled = false;
            }
            if (aborted) {
                break;
            }
        }
        return true;
    }
    return false;
}
```

这里比较简单，就是通过遍历获取订阅者列表，然后逐个发送相关消息，每发送一个都进行一下检测，检测消息是否进行了取消操作。如果取消的话，直接跳出循环。在最后对**postingState**的变量进行了资源的释放。这里面最重要的就是发送给注册者的函数**postToSubscription**。

```java
private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
    switch (subscription.subscriberMethod.threadMode) {
        case POSTING://没有线程处理，直接反射调用方法
            invokeSubscriber(subscription, event);
            break;
        case MAIN:
            if (isMainThread) {//订阅者在主线程，当前是主线程，则直接调用方法
                invokeSubscriber(subscription, event);
            } else {//否则直接放入到主线程队列操作
                mainThreadPoster.enqueue(subscription, event);
            }
            break;
        case MAIN_ORDERED://不关心消息是否在主线程，都使用队列进行入栈，防止导致阻塞。
            if (mainThreadPoster != null) {
                mainThreadPoster.enqueue(subscription, event);
            } else {
                // temporary: technically not correct as poster not decoupled from subscriber
                invokeSubscriber(subscription, event);
            }
            break;
        case BACKGROUND://要在子线程进行消息的处理
            if (isMainThread) {//放入到子线程队列
                backgroundPoster.enqueue(subscription, event);
            } else {
                invokeSubscriber(subscription, event);
            }
            break;
        case ASYNC:
            //BACKGROUND的区别是，它直接通过线程池去执行。而backgrand会通过synchronize和标志位，来保证消息队列是一个个执行，而不会两个同时执行。而ASYNC则不会这么做，而是直接就通过线程池去执行。
            asyncPoster.enqueue(subscription, event);
            break;
        default:
            throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
    }
}
```

可以看到这里主要根据订阅者的注解以及当前消息所在的线程，进行了不同的处理。如果两者线程不同，则通过队列来进行操作。

这里对**mainThreadPoster**进行一下跟踪，看看里面做了什么处理。**mainThreadPoster**这里默认的是HandlerPoster对象，继承了Handler对象。

```java
public void enqueue(Subscription subscription, Object event) {
    PendingPost pendingPost = PendingPost.obtainPendingPost(subscription, event);
    synchronized (this) {
        queue.enqueue(pendingPost);
        if (!handlerActive) {
            handlerActive = true;
            if (!sendMessage(obtainMessage())) {
                throw new EventBusException("Could not send handler message");
            }
        }
    }
}
```

这里是将消息进行了入队列操作，然后向主线程handler发送了消息，唤醒消息的处理机制。

### 总结

EventBus消息主要使用了**观察者模式**，**单例模式（双重锁）**，**Handler**，**CopyOnWriteArrayList队列**等相关技术

EventBus是一种事件总线，是基于模式（发布者、订阅者模式）。register()方法，通过反射获取到对应的类中Subscribe方法，并将其封装为SubsriberMethod对象，并缓存起来，这里会缓存观察者和对应的方法。在反射获取方法对象的过程中使用了一种享元技术，会存在4个对象来进行对应的临时数据的保存，当超过4个的时候，才会进行重新创建。
当通过post的时候，会通过ThreadLocal获取当前线程所使用的消息队列。然后通过遍历将消息队列发送到总线中。
会去内存中获取到具体的订阅者的方法并调用。

post消息的时候，会根据post的数据类型，在缓存中找到对应的订阅方法，然后调用其对应方法。这里会有一个线程切换的，比如说我订阅的在主线程执行，但是post是在子线程发送的消息，那么会通过handler发送到对应的线程去执行。
在往子线程去post消息的时候，会使用线程池来进行消息的处理。线程池默认使用的是newCachedThreadPool。

EventBus的订阅模式有4种：MAIN（只会在主线程执行，如果post是主线程，则直接调用，可能会阻塞线程，如果不在主线程，通过Handler发送到主线程去执行）；POSTING：不处理，哪儿调用就在哪儿执行，直接invoke；MAIN_ORDERED：不管当前啥线程，都通过Handler放入到队列去执行，这样可以保证不会阻塞主线程。BACKGROUND：子线程执行，通过线程池来处理，会通过synchronize和标志位，来保证消息队列是一个个执行，而不会两个消息同时执行。ASYNC：异步线程，直接扔给线程池处理，不会按照顺序去执行。

BACKGROUND和ASYNC使用的是同一个线程池，都是EventBus默认的newCachedThreadPool。
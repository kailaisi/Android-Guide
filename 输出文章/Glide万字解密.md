# Glide万字解密

Glide现在应用最广的一个图片加载框架了，一直都想对它下手，每次都是深陷其中。。。这次狠下心来，对它来个全面的剖析，争取对整个流程和其中的细节都有一个覆盖。

本文的Glide的解析是基于最新的4.11.0版本来进行的。

其实从一般的网络加载图片，可以简单分析下大体的流程，无非就是建立相关的请求信息，然后通过线程池技术对请求信息进行请求，然后将下载的图片文件进行转化显示。

先来看个简单的测试使用代码开始，然后逐步深入

```java
Glide.with(view.getContext())
                .load(url)
                .into(view);
```

## **with()**

Glide的**with**函数为我们提供了不同的入参，其最终的返回对象都是 **RequestManager** 

![image-20200213095828075](http://cdn.qiniu.kailaisii.com/typora/20200213095837-616832.png)

我们的测试代码用的是 **Context** 那么这里我们就跟踪一下这个函数，其实其他几个都是相似的

```java
  @NonNull
  public static RequestManager with(@NonNull Context context) {
    return getRetriever(context).get(context);
  }
  
 @NonNull
  private static RequestManagerRetriever getRetriever(@Nullable Context context) {
    //校验Context不能为空
    Preconditions.checkNotNull(context,"....");
    return Glide.get(context).getRequestManagerRetriever();
  }
  //直接获取Glide对象的requestManagerRetriever属性
  public RequestManagerRetriever getRequestManagerRetriever() {
    return requestManagerRetriever;
  }

```

可以看到 **RequestManagerRetriever** 对象的创建，肯定是在 **Glide.get()** 中进行了处理

```
  //通过双重加锁单例方法，创建Glide对象
  public static Glide get(@NonNull Context context) {
    if (glide == null) {
      GeneratedAppGlideModule annotationGeneratedModule =
          getAnnotationGeneratedGlideModules(context.getApplicationContext());
      synchronized (Glide.class) {
        if (glide == null) {
          checkAndInitializeGlide(context, annotationGeneratedModule);
        }
      }
    }
    return glide;
  }
  //校验并初始化Glide对象
  @GuardedBy("Glide.class")
  private static void checkAndInitializeGlide(
      @NonNull Context context, @Nullable GeneratedAppGlideModule generatedAppGlideModule) {
    // In the thread running initGlide(), one or more classes may call Glide.get(context).
    // Without this check, those calls could trigger infinite recursion.
    if (isInitializing) {//如果正在创建，则直接报错
      throw new IllegalStateException(
          "You cannot call Glide.get() in registerComponents(),"
              + " use the provided Glide instance instead");
    }
    isInitializing = true;
    //真正的创建方法
    initializeGlide(context, generatedAppGlideModule);
    isInitializing = false;
  }
  //初始化Glide
  @GuardedBy("Glide.class")
  private static void initializeGlide(
      @NonNull Context context, @Nullable GeneratedAppGlideModule generatedAppGlideModule) {
    initializeGlide(context, new GlideBuilder(), generatedAppGlideModule);
  }

  @GuardedBy("Glide.class")
  @SuppressWarnings("deprecation")
  private static void initializeGlide(@NonNull Context context, @NonNull GlideBuilder builder, @Nullable GeneratedAppGlideModule annotationGeneratedModule) {
    ...
    //创建了RequestManagerFactory工厂对象，用来创建对应的RequestManager
    RequestManagerRetriever.RequestManagerFactory factory =
        annotationGeneratedModule != null
            ? annotationGeneratedModule.getRequestManagerFactory()
            : null;
    builder.setRequestManagerFactory(factory);
    ...
    //建造者设计模式，创建Glide对象
    Glide glide = builder.build(applicationContext);
    ...
    applicationContext.registerComponentCallbacks(glide);
    Glide.glide = glide;
  }
```

可以看到，这里使用建造者设计模式，来创建了 **glide** 对象。在 **builder** 中设置了一个 **RequestManagerFactory** 的属性。看下在builder中，具体帮我们做了什么工作。

```
  @NonNull
  Glide build(@NonNull Context context) {
    if (sourceExecutor == null) {//创建资源执行器
      sourceExecutor = GlideExecutor.newSourceExecutor();
    }
    if (diskCacheExecutor == null) {//磁盘缓存执行器
      diskCacheExecutor = GlideExecutor.newDiskCacheExecutor();
    }
    if (animationExecutor == null) {//动画执行器
      animationExecutor = GlideExecutor.newAnimationExecutor();
    }
    if (memorySizeCalculator == null) {//内存大小的计算器，根据相关的
      memorySizeCalculator = new MemorySizeCalculator.Builder(context).build();
    }
    if (connectivityMonitorFactory == null) {//连接监控的工厂
      connectivityMonitorFactory = new DefaultConnectivityMonitorFactory();
    }
    if (bitmapPool == null) {//bitmap池
      int size = memorySizeCalculator.getBitmapPoolSize();
      if (size > 0) {
        bitmapPool = new LruBitmapPool(size);
      } else {
        bitmapPool = new BitmapPoolAdapter();
      }
    }
    if (arrayPool == null) {
      arrayPool = new LruArrayPool(memorySizeCalculator.getArrayPoolSizeInBytes());
    }
    if (memoryCache == null) {//内存缓存策略，默认使用Lru缓存策略
      memoryCache = new LruResourceCache(memorySizeCalculator.getMemoryCacheSize());
    }
    if (diskCacheFactory == null) {//硬盘缓存策略
      diskCacheFactory = new InternalCacheDiskCacheFactory(context);
    }
    if (engine == null) {//引擎，里面包括了创建的执行器、缓存的信息
      engine = new Engine(
              memoryCache,
              diskCacheFactory,
              diskCacheExecutor,
              sourceExecutor,
              GlideExecutor.newUnlimitedSourceExecutor(),
              animationExecutor,
              isActiveResourceRetentionAllowed);
    }

    if (defaultRequestListeners == null) {//请求监听器，是一个不可变的List
      defaultRequestListeners = Collections.emptyList();
    } else {
      defaultRequestListeners = Collections.unmodifiableList(defaultRequestListeners);
    }
    //这里创建了一个RequestManagerRetriever对象，参数是之前设置的Factory对象
    RequestManagerRetriever requestManagerRetriever = new RequestManagerRetriever(requestManagerFactory);
    return new Glide(//创建Glide对象
        context,
        engine,
        memoryCache,
        bitmapPool,
        arrayPool,
        requestManagerRetriever,
        connectivityMonitorFactory,
        logLevel,
        defaultRequestOptionsFactory,
        defaultTransitionOptions,
        defaultRequestListeners,
        isLoggingRequestOriginsEnabled,
        isImageDecoderEnabledForBitmaps);
  }
```

在 **GlideBuilder** 中，帮我们创建了很多的对象，包括线程池、缓存器、缓存大小、Engine、RequestManagerRetriever。

因为我们在调用 **with()** 方法时，使用了 **requestManagerRetriever** ，我们这里去看一眼，里面有没有做什么特殊处理

```
public RequestManagerRetriever(@Nullable RequestManagerFactory factory) {
  this.factory = factory != null ? factory : DEFAULT_FACTORY;
  handler = new Handler(Looper.getMainLooper(), this /* Callback */);
}

private static final RequestManagerFactory DEFAULT_FACTORY =
      new RequestManagerFactory() {
        @NonNull
        @Override
        public RequestManager build(@NonNull Glide glide, @NonNull Lifecycle lifecycle, @NonNull RequestManagerTreeNode requestManagerTreeNode, @NonNull Context context) {
          return new RequestManager(glide, lifecycle, requestManagerTreeNode, context);
        }
      };
```

很简单的构造函数，里面创建了 **handler**对象，并设置了 **RequestManagerFactory** 对象。

回到主干，看看Glide的构造函数。

```
  Glide(
      @NonNull Context context,
      @NonNull Engine engine,
      @NonNull MemoryCache memoryCache,
      @NonNull BitmapPool bitmapPool,
      @NonNull ArrayPool arrayPool,
      @NonNull RequestManagerRetriever requestManagerRetriever,
      @NonNull ConnectivityMonitorFactory connectivityMonitorFactory,
      int logLevel,
      @NonNull RequestOptionsFactory defaultRequestOptionsFactory,
      @NonNull Map<Class<?>, TransitionOptions<?, ?>> defaultTransitionOptions,
      @NonNull List<RequestListener<Object>> defaultRequestListeners,
      boolean isLoggingRequestOriginsEnabled,
      boolean isImageDecoderEnabledForBitmaps) {
    this.engine = engine;
    this.bitmapPool = bitmapPool;
    this.arrayPool = arrayPool;
    this.memoryCache = memoryCache;
    this.requestManagerRetriever = requestManagerRetriever;
    this.connectivityMonitorFactory = connectivityMonitorFactory;
    this.defaultRequestOptionsFactory = defaultRequestOptionsFactory;

    final Resources resources = context.getResources();
    //注册机，里面维护了编码、解码、加载、图片请求头、支持的图片等的各种注册表信息
    registry = new Registry();
    registry.register(new DefaultImageHeaderParser());
    registry
        .append(int.class, InputStream.class, resourceLoaderStreamFactory)
        .......
    ImageViewTargetFactory imageViewTargetFactory = new ImageViewTargetFactory();
    glideContext =
        new GlideContext(//创建glideContext对象
            context,
            arrayPool,
            registry,
            imageViewTargetFactory,
            defaultRequestOptionsFactory,
            defaultTransitionOptions,
            defaultRequestListeners,
            engine,
            isLoggingRequestOriginsEnabled,
            logLevel);
  }
```

这里面将一些属性赋值，并且创建了 **GlideContext** 对象，以及registry对象。

到此为止，我们的Glide单例对象创建完成了....

### RequestManger对象的获取

```
public static RequestManager with(@NonNull Context context) {
  return getRetriever(context).get(context);
}
```

在获取到 **RequestManagerRetriever** 对象以后，通过 **get** 方法来获取到 **RequestManager** 对象，我们现在来跟踪一下代码的实现。

```
  @NonNull
  public RequestManager get(@NonNull Context context) {
    if (context == null) {//context为空抛异常
      throw new IllegalArgumentException("You cannot start a load on a null Context");
    } else if (Util.isOnMainThread() && !(context instanceof Application)) {
      //如果当前线程是主线程，并且context不是Application，那么对应的生命周期是和UI(Activity或者Fragment)进行绑定的，
      //通过创建隐藏Fragment的方法来监听context的生命周期。然后将RequestManager和Fragment来绑定。
      //因为v4和普通的Fragment中创建Fragment的方式是不同的，所以这里根据不同的context类型，来进行不同的处理
      if (context instanceof FragmentActivity) {//如果是FragmentActivity
        return get((FragmentActivity) context);
      } else if (context instanceof Activity) {//如果是普通的Activity
        return get((Activity) context);
      } else if (context instanceof ContextWrapper
          // Only unwrap a ContextWrapper if the baseContext has a non-null application context.
          // Context#createPackageContext may return a Context without an Application instance,
          // in which case a ContextWrapper may be used to attach one.
          && ((ContextWrapper) context).getBaseContext().getApplicationContext() != null) {
        return get(((ContextWrapper) context).getBaseContext());
      }
    }
    //返回应用RequestManager
    return getApplicationManager(context);
  }
```

在get()方法中，根据Context的类型的不同，来进行了不同的处理。我们这里跟踪一个  **FragmentActivity** 类型的，其他的是相似的

```java
  @NonNull
  public RequestManager get(@NonNull FragmentActivity activity) {
    if (Util.isOnBackgroundThread()) {//如果是后台线程，则context按照application处理，即和UI的生命周期解绑
      return get(activity.getApplicationContext());
    } else {
      //在UI线程进行处理
      assertNotDestroyed(activity);//判断activity是否被销毁了
      FragmentManager fm = activity.getSupportFragmentManager();
      return supportFragmentGet(activity, fm, /*parentHint=*/ null, isActivityVisible(activity));
    }
  }
  //获取FragmentManager所管理的RequestManager
  private RequestManager supportFragmentGet(@NonNull Context context, @NonNull FragmentManager fm, @Nullable Fragment parentHint,
      boolean isParentVisible) {
    //创建一个隐形的SupportRequestManagerFragment，来监听对应的Context的生命周期
    SupportRequestManagerFragment current = getSupportRequestManagerFragment(fm, parentHint, isParentVisible);
    //获取Fragment中对应的请求管理器，（每个Fragment只有一个唯一的请求管理器）
    RequestManager requestManager = current.getRequestManager();
    if (requestManager == null) {//如果当前没有设置过请求管理器，那么创建并设置
      Glide glide = Glide.get(context);
      //通过工厂方法，创建requestManager对象
      requestManager = factory.build(glide, current.getGlideLifecycle(), current.getRequestManagerTreeNode(), context);
      current.setRequestManager(requestManager);
    }
    return requestManager;
  }
```

实际的 **RequestManager** 是通过factory来构建的

```java
RequestManager(Glide glide, Lifecycle lifecycle, RequestManagerTreeNode treeNode,
    RequestTracker requestTracker, ConnectivityMonitorFactory factory, Context context) {
  this.glide = glide;
  this.lifecycle = lifecycle;
  this.treeNode = treeNode;
  this.requestTracker = requestTracker;
  this.context = context;
  //连接监听器
  connectivityMonitor = factory
      .build(context.getApplicationContext(), new RequestManagerConnectivityListener(requestTracker));
  if (Util.isOnBackgroundThread()) {
    mainHandler.post(addSelfToLifecycle);
  } else {
    lifecycle.addListener(this);
  }
  lifecycle.addListener(connectivityMonitor);
  defaultRequestListeners =
      new CopyOnWriteArrayList<>(glide.getGlideContext().getDefaultRequestListeners());
  //设置请求配置信息
  setRequestOptions(glide.getGlideContext().getDefaultRequestOptions());
  //将RequestManager注册到全局glide中
  glide.registerRequestManager(this);
}
```

到现在为止，我们已经对于创建了 **RequestManager** ，那么后续就是其调用 **load()** 方法了。

## load()

 **RequestManager** 支持对多种参数形式的图片加载：

![image-20200213163514155](http://cdn.qiniu.kailaisii.com/typora/20200213163515-251016.png)

我们从我们的案例跟踪，传入的参数是String类型。

```java
//相当于先调用了asDrawable(),然后调用了load()方法
  public RequestBuilder<Drawable> load(@RawRes @DrawableRes @Nullable Integer resourceId) {
    return asDrawable().load(resourceId);
  }

  public RequestBuilder<Drawable> asDrawable() {
    return as(Drawable.class);
  }

  //创建能够解码对应类型的图片的RequestBuilder
  public <ResourceType> RequestBuilder<ResourceType> as(
      @NonNull Class<ResourceType> resourceClass) {
    return new RequestBuilder<>(glide, this, resourceClass, context);
  }
```

我们看下 **RequestBuilder** 的构造方法做了什么处理

```
  protected RequestBuilder(@NonNull Glide glide, RequestManager requestManager, 
      Class<TranscodeType> transcodeClass, Context context) {
    this.glide = glide;
    this.requestManager = requestManager;
    this.transcodeClass = transcodeClass;
    this.context = context;
    //这里的transcodeClass大概率是Drawable类对象
    this.transitionOptions = requestManager.getDefaultTransitionOptions(transcodeClass);
    this.glideContext = glide.getGlideContext();

    initRequestListeners(requestManager.getDefaultRequestListeners());
    apply(requestManager.getDefaultRequestOptions());
  }
```

在创建完RequestBuilder对象之后，直接调用了 **RequestBuilder** 的 **load()** 方法。

```
  public RequestBuilder<TranscodeType> load(@Nullable String string) {
    return loadGeneric(string);
  }
  
  private RequestBuilder<TranscodeType> loadGeneric(@Nullable Object model) {
    this.model = model;
    isModelSet = true;
    return this;
  }
```

这里也没有进行特殊的操作，只是将 **isModelSet** 设置为了true，标记model已经进行了设置。



## into()

在 **with()** 和 **load()** 方法中，主要是进行了一些前期的准备工作。真正执行图片的加载、缓存、转化到View上等等这些操作，都是在 **into()** 中执行的。

```
  public ViewTarget<ImageView, TranscodeType> into(@NonNull ImageView view) {
    Util.assertMainThread();//方法需要在主线程执行
    Preconditions.checkNotNull(view);//view不能为空
    BaseRequestOptions<?> requestOptions = this;
    if (!requestOptions.isTransformationSet()
        && requestOptions.isTransformationAllowed()
        && view.getScaleType() != null) {//根据View上配置的scaleType，设置requestOptions
      switch (view.getScaleType()) {
        case CENTER_CROP:
          requestOptions = requestOptions.clone().optionalCenterCrop();
          break;
      }
    }
    return into(glideContext.buildImageViewTarget(view, transcodeClass),/*targetListener=*/ null, 
        requestOptions,
        Executors.mainThreadExecutor());
  }
```

这里根据设置的信息配置 **requestOptions** 相关参数，然后调用了 **buildImageViewTarget** 方法，构造了一个 **viewTarget** 对象。

我们现在看一下这个方法

```
  @NonNull
  public <X> ViewTarget<ImageView, X> buildImageViewTarget(
      @NonNull ImageView imageView, @NonNull Class<X> transcodeClass) {
    //通过工厂方法创建ViewTarget对象,这里一般返回的是DrawableImageViewTarget，如果是使用了asBitmap那么返回的是BitmapImageViewTarget
    return imageViewTargetFactory.buildTarget(imageView, transcodeClass);
  }
  
public class ImageViewTargetFactory {
  public <Z> ViewTarget<ImageView, Z> buildTarget( @NonNull ImageView view, @NonNull Class<Z> clazz) {
    if (Bitmap.class.equals(clazz)) {//如果使用了asBitmap方法，那么这里的clazz回事bitmap
      return (ViewTarget<ImageView, Z>) new BitmapImageViewTarget(view);
    } else if (Drawable.class.isAssignableFrom(clazz)) {//如果只是正常的使用，一般会返回DrawableImageViewTarget
      return (ViewTarget<ImageView, Z>) new DrawableImageViewTarget(view);
    } else {
      throw new IllegalArgumentException("Unhandled class: " + clazz + ", try .as*(Class).transcode(ResourceTranscoder)");
    }
  }
}
```

可以看到这里，大部分情况下返回的是一个 **DrawableImageViewTarget** 对象信息，

回到主线。继续

```
  private <Y extends Target<TranscodeType>> Y into(@NonNull Y target, @Nullable RequestListener<TranscodeType> targetListener,
      BaseRequestOptions<?> options, Executor callbackExecutor) {
    Preconditions.checkNotNull(target);
    if (!isModelSet) {//如果model没有进行设置(没有调用load方法会导致这种情况的方法)，直接报错。
      throw new IllegalArgumentException("You must call #load() before calling #into()");
    }
    //创建一个request请求
    Request request = buildRequest(target, targetListener, options, callbackExecutor);
    //获取target上是否已经存在了相应的请求，如果存在，则进行清空处理
    Request previous = target.getRequest();
    if (request.isEquivalentTo(previous) && !isSkipMemoryCacheWithCompletePreviousRequest(options, previous)) {
      //target上标记点的请求和生成的请求相同，直接启动相应的请求，然后返回target对象
      if (!Preconditions.checkNotNull(previous).isRunning()) {
        previous.begin();
      }
      return target;
    }
    //将原有的target从请求管理类requestManager中移除掉
    requestManager.clear(target);
    //将新的请求设置进target
    target.setRequest(request);
    //requestManager开启对于target和request的跟踪处理（主要是启动请求，并且将请求类和target进行统一管理）
    requestManager.track(target, request);
    return target;
  }
```

这段代码是Glide的核心，里面进行了 **Request** 请求对象的创建以及执行。

### 请求对象的创建过程

我们先看下 **Request** 请求对象的创建过程: **buildRequest()** 

```
  private Request buildRequest(Target<TranscodeType> target, @Nullable RequestListener<TranscodeType> targetListener,
      BaseRequestOptions<?> requestOptions, Executor callbackExecutor) {
    return buildRequestRecursive(/*requestLock=*/ new Object(), target, targetListener,
        /*parentCoordinator=*/ null, transitionOptions, requestOptions.getPriority(),
        requestOptions.getOverrideWidth(), requestOptions.getOverrideHeight(), requestOptions, callbackExecutor);
  }
  
  //创建请求类，分为处理错误显示的请求和正常显示的请求
  private Request buildRequestRecursive(Object requestLock, Target<TranscodeType> target,
      @Nullable RequestListener<TranscodeType> targetListener, @Nullable RequestCoordinator parentCoordinator,
      TransitionOptions<?, ? super TranscodeType> transitionOptions, Priority priority,
      int overrideWidth, int overrideHeight,
      BaseRequestOptions<?> requestOptions, Executor callbackExecutor) {
    .....
    Request mainRequest =//正常显示的请求
        buildThumbnailRequestRecursive(requestLock, target, targetListener, parentCoordinator, transitionOptions,
            priority, overrideWidth, overrideHeight, requestOptions, callbackExecutor);
    ...
    return errorRequestCoordinator;
  }
```

这个函数将请求进行了分类，一种是错误显示的请求信息，一种是正常显示的请求信息。

我们看一下正常显示的请求处理函数中，是如何创建的。

```
  //生成处理正常显示的请求，会根据相关的设置，创建缩略图或者原图
  private Request buildThumbnailRequestRecursive(Object requestLock,Target<TranscodeType> target,
      RequestListener<TranscodeType> targetListener,@Nullable RequestCoordinator parentCoordinator,
      TransitionOptions<?, ? super TranscodeType> transitionOptions,Priority priority,
      int overrideWidth,int overrideHeight,BaseRequestOptions<?> requestOptions,Executor callbackExecutor) {
    if (thumbnailBuilder != null) {//存在缩略图生成器，根据builder信息创建缩略图请求
      ...
    } else if (thumbSizeMultiplier != null) {//存在图片放大缩小指数，则根据指数信息创建缩略图请求
      ...
    } else {
      //一般会通过这个方法进行处理
      return obtainRequest(requestLock, target, targetListener, requestOptions, parentCoordinator,
          transitionOptions, priority, overrideWidth, overrideHeight, callbackExecutor);
    }
  }
```

这个代码段很长，我们进行了省略，里面大部分都是对于生成缩略图请求的一些特殊处理。后面我们有机会再分析。我们主要看一下 **obtainRequest** 这个方法是如何来创建标准请求的。

```
  private Request obtainRequest(Object requestLock, Target<TranscodeType> target,
      RequestListener<TranscodeType> targetListener, BaseRequestOptions<?> requestOptions,
      RequestCoordinator requestCoordinator, TransitionOptions<?, ? super TranscodeType> transitionOptions,
      Priority priority, int overrideWidth, int overrideHeight, Executor callbackExecutor) {
    return SingleRequest.obtain(context, glideContext, requestLock, model, transcodeClass,
        requestOptions, overrideWidth, overrideHeight, priority, target, targetListener,
        requestListeners, requestCoordinator, glideContext.getEngine(), transitionOptions.getTransitionFactory(),
        callbackExecutor);
  }
```

这个方法继续跟踪，可以发现只是创建了一个 **SingleRequest** 对象，并且把相关参数进行了赋值，里面并没有什么特殊的处理。

到此为止，我们的 **Request** 已经创建完成了。下一步就是看一下，是如何进行网络请求的。

###  请求的执行

回到原来的 **into()** 代码块中，这次我们跟踪的是 **requestManager.track(target, request)** 这个函数

```
  synchronized void track(@NonNull Target<?> target, @NonNull Request request) {
    //target管理类，里面通过set列表方式保存了所有的target信息，并且实现了生命周期接口，能够根据生命周期，启动或者暂停target的动画
    targetTracker.track(target);
    //requestTracker管理类，里面通过set列表方式保存了所有的request信息，通过runRequest方法，将请求保存到set列表，并启动请求
    requestTracker.runRequest(request);
  }
  //启动并跟踪请求
  public void runRequest(@NonNull Request request) {
    requests.add(request);//保存到set列表
    if (!isPaused) {//加载处于可用状态，直接调用begin()
      request.begin();
    } else {//加载处于暂停状态，将request请求保存起来的，等可以状态时，从列表里面逐个启动
      request.clear();
      if (Log.isLoggable(TAG, Log.VERBOSE)) {
        Log.v(TAG, "Paused, delaying request");
      }
      //将请求放到pendingRequests，因为requests是弱引用，防止被回收
      pendingRequests.add(request);
    }
  }
```

这里就是个简单的处理，如果当前的 **RequestManager** 处于可用状态（即绑定的Context是处于可见的），那么就直接进行任务的请求处理。否则将请求放到 pendingRequests 所对应的的set列表中，虽然 requests中已经保存了对应的请求信息，但是由于其是弱引用，所以存在会被回收的情况，所以使用了pendingRequests来进行保存。

在创建请求对象的分析中，我们知道最后创建的是 **SingleRequest** 对象，继续 **begin()** 方法的跟踪

```
 public void begin() {
    synchronized (requestLock) {
      assertNotCallingCallbacks();
      stateVerifier.throwIfRecycled();
      startTime = LogTime.getLogTime();
      if (model == null) {//如果加载的图片源（url,file等）为null,则直接onLoadFailed
        ...
        onLoadFailed(new GlideException("Received null model"), logLevel);
        return;
      }
      if (status == Status.RUNNING) {//如果请求正在进行，则直接抛异常
        throw new IllegalArgumentException("Cannot restart a running request");
      }
      if (status == Status.COMPLETE) {//如果请求已经完成，则直接调用onResourceReady接口
        //如果我们重新启动后完成(通常是通过一个notifyDataSetChanged将一个相同的请求开始到相同的目标或视图),
        //我们可以简单地使用资源和大小,而不需要获得一个新的尺寸,开始一个新的加载等。
        // 这确实意味着，如果客户确实需要重新加载，那么需要在加载之前，显示的调用clear清除视图或目标。
        onResourceReady(resource, DataSource.MEMORY_CACHE);
        return;
      }
      status = Status.WAITING_FOR_SIZE;
      if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
        //如果要加载的视图的宽和高已经固定，直接进行加载
        onSizeReady(overrideWidth, overrideHeight);
      } else {
        //设置回调信息，当视图的宽高完成以后，进行回调处理。通过target(Target类，里面包含要了View的信息)的getViewTreeObserver方法，来监听控件的绘制，从而能够获取到对应的宽高,最后通过接口回调调用onSizeReady(width,height)方法
        target.getSize(this);
      }
      if ((status == Status.RUNNING || status == Status.WAITING_FOR_SIZE)
          && canNotifyStatusChanged()) {
        //设置PlaceholderDrawable这些信息，然后去后台加载资源，最后将对应的数据显示在target上面
        target.onLoadStarted(getPlaceholderDrawable());
      }
      if (IS_VERBOSE_LOGGABLE) {
        logV("finished run method in " + LogTime.getElapsedMillis(startTime));
      }
    }
  }
```

在 **begin()** 函数中最主要的是通过View宽高已知（不论是已经固定还是通过绘制后的回调）的情况下，调用 **onSizeReady** 方法来执行具体的加载过程。而且在加载未完成的情况下，在View上设置了对应的占位图(也就是我们经常在代码里面使用的 **.placeholder()** 方法)。

```
  public void onSizeReady(int width, int height) {
    stateVerifier.throwIfRecycled();
    synchronized (requestLock) {
      ...
      float sizeMultiplier = requestOptions.getSizeMultiplier();//根据缩放比例来进行宽高的重新处理
      this.width = maybeApplySizeMultiplier(width, sizeMultiplier);
      this.height = maybeApplySizeMultiplier(height, sizeMultiplier);
      ...
      loadStatus =
          engine.load(//去加载
              glideContext,
              model,
              requestOptions.getSignature(),
              this.width,
              this.height,
              requestOptions.getResourceClass(),
              transcodeClass,
              priority,
              requestOptions.getDiskCacheStrategy(),
              requestOptions.getTransformations(),
              requestOptions.isTransformationRequired(),
              requestOptions.isScaleOnlyOrNoTransform(),
              requestOptions.getOptions(),
              requestOptions.isMemoryCacheable(),
              requestOptions.getUseUnlimitedSourceGeneratorsPool(),
              requestOptions.getUseAnimationPool(),
              requestOptions.getOnlyRetrieveFromCache(),
              this,
              callbackExecutor);
       ...
    }
  }
```

函数最终是通过调用了 **engine.load** 来执行的加载过程。

```
  //真正的加载，通过多级缓存来进行处理
  public <R> LoadStatus load(
      GlideContext glideContext,
      Object model,
      Key signature,
      int width,
      int height,
      Class<?> resourceClass,
      Class<R> transcodeClass,
      Priority priority,
      DiskCacheStrategy diskCacheStrategy,
      Map<Class<?>, Transformation<?>> transformations,
      boolean isTransformationRequired,
      boolean isScaleOnlyOrNoTransform,
      Options options,
      boolean isMemoryCacheable,
      boolean useUnlimitedSourceExecutorPool,
      boolean useAnimationPool,
      boolean onlyRetrieveFromCache,
      ResourceCallback cb,
      Executor callbackExecutor) {
    long startTime = VERBOSE_IS_LOGGABLE ? LogTime.getLogTime() : 0;

    EngineKey key =
        keyFactory.buildKey(//根据相关参数创建一个key值
            model,
            signature,
            width,
            height,
            transformations,
            resourceClass,
            transcodeClass,
            options);

    EngineResource<?> memoryResource;
    synchronized (this) {
      //从内存加载
      memoryResource = loadFromMemory(key, isMemoryCacheable, startTime);
      if (memoryResource == null) {
        //如果在内存中没有查询到，那么内部创建一个Job并进行执行，返回LoadStatus
        return waitForExistingOrStartNewJob(
            glideContext,
            model,
            signature,
            width,
            height,
            resourceClass,
            transcodeClass,
            priority,
            diskCacheStrategy,
            transformations,
            isTransformationRequired,
            isScaleOnlyOrNoTransform,
            options,
            isMemoryCacheable,
            useUnlimitedSourceExecutorPool,
            useAnimationPool,
            onlyRetrieveFromCache,
            cb,
            callbackExecutor,
            key,
            startTime);
      }
    }
```

我们都知道，现有的任何一个图片加载框架都使用了缓存来进行数据的处理，以此来加快图片的加载速度。Glide 也不例外，从代码可以看到对于资源的加载。

通过相关的参数生成了一个key

1. 通过key在内存查找
2. 如果查找到了，那么直接通过接口回调来返回资源信息。
3. 如果没有，则会创建新的任务请求来加载。

我们先从内存加载函数 **loadFromMemory()** 来分析一下是如何处理的。

```java
  private EngineResource<?> loadFromMemory(EngineKey key, boolean isMemoryCacheable, long startTime) {
    if (!isMemoryCacheable) {//如果不允许在缓存加载，直接返回  通过.skipMemoryCache(false)来关闭缓存功能
      return null;
    }
    //从正在使用的图片列表中获取
    EngineResource<?> active = loadFromActiveResources(key);
    if (active != null) {
      return active;
    }
    //从MemoryCache(这里使用的一般是LurMemoryCache)中获取
    EngineResource<?> cached = loadFromCache(key);
    if (cached != null) {
      return cached;
    }
    return null;
  }

  //从正在使用的资源中加载的
  private EngineResource<?> loadFromActiveResources(Key key) {
    //从已经被加载的资源中获取
    EngineResource<?> active = activeResources.get(key);
    if (active != null) {//占用资源的计数器+1
      active.acquire();
    }
    return active;
  }
  //从cache中获取
  private EngineResource<?> loadFromCache(Key key) {
    //从Cache中获取
    EngineResource<?> cached = getEngineResourceFromCache(key);
    if (cached != null) {
      //如果缓存中存在，则从缓存中移除，并放入到正在使用的Resource列表中
      cached.acquire();
      activeResources.activate(key, cached);
    }
    return cached;
  }

```

源码相对来说比较简单。

1. loadFromActiveResources() 先从正在使用的资源中去查找。如果查找到，将资源的引用计数器+1，返回资源文件。
2. 如果没有查找到，则从缓存中获取，缓存默认使用的LruMemoryCache(可以使用)。如果获取成功，则从缓存中移除，并放置到activeResources中（强引用）。防止缓存被回收导致的资源失效。
3. 如果仍然没有，则整个函数返回null

如果最终没有从内存获取到资源文件，那么代码会通过 **waitForExistingOrStartNewJob** 来执行资源的加载。

```
  //使用已有的Job或者创建新的Job来进行资源加载
  private <R> LoadStatus waitForExistingOrStartNewJob(...) {
    //从在执行的jobs列表中查询，onlyRetrieveFromCache为仅从缓存加载图片标志位。
    EngineJob<?> current = jobs.get(key, onlyRetrieveFromCache);
    if (current != null) {
      //如果存在，则为当前任务增加一个回调接口，
      //可能一个资源在多个地方同时使用的情况，因此，加载完成后，需要回调多个接口来进行通知
      current.addCallback(cb, callbackExecutor);
      return new LoadStatus(cb, current);
    }
    //创建一个EngineJob
    EngineJob<R> engineJob =engineJobFactory.build(...);
    //负责从缓存的数据资源或者原始资源获取数据，是数据的获取类
    DecodeJob<R> decodeJob =decodeJobFactory.build(...);
    //将engineJob存放到jobs，engineJob中有onlyRetrieveFromCache的字段，所以可以根据该字段放置到不同的list中
    jobs.put(key, engineJob);
    //增加回调接口
    engineJob.addCallback(cb, callbackExecutor);
    //启动engineJob去加载资源
    engineJob.start(decodeJob);
    return new LoadStatus(cb, engineJob);
  }

```

我们这里只关心新建 **EngineJob** 并执行加载的过程，所以这里我们主要看一下 **engineJob.start(decodeJob)** 的执行

```
  public synchronized void start(DecodeJob<R> decodeJob) {
    this.decodeJob = decodeJob;
    //根据decodeJob的设置，使用不存的执行器
    GlideExecutor executor = decodeJob.willDecodeFromCache() ? diskCacheExecutor : getActiveSourceExecutor();
    //启动decodeJob，decodeJob实现了Runnable接口，会调用其run()方法
    executor.execute(decodeJob);
  }
```

我们现在看看DecodeJob的run方法

```
  @Override
  public void run() {
    DataFetcher<?> localFetcher = currentFetcher;
    try {
      if (isCancelled) {//如果已经取消了，则直接返回失败。isCancelled是volatile。保证了多线程的可见性
        notifyFailed();//通知上层(EngineJob)调用失败,并且进行相关资源的回收工作
        return;
      }
      //主要的加载函数
      runWrapped();
    } catch (CallbackException e) {
      //如果已经进入了encode阶段时，我们已经调用了callback上层接口，这时候需要通过调用失败接口来进行资源释放,否则是不安全的
      // 可以查看notifyEncodeAndRelease(Resource, DataSource)方法里面
      if (stage != Stage.ENCODE) {
        throwables.add(t);
        notifyFailed();
      }
      throw t;
    } finally {
      if (localFetcher != null) {//进行localFetcher的清理工作，因为DecodeJob是进行复用的。
        localFetcher.cleanup();
      }
      GlideTrace.endSection();//记录整个Glide的跟踪记录信息
    }
  }
```

**run** 方法只有一个最主要的函数，就是 **runWrapped()** 。

```
  private void runWrapped() {
    switch (runReason) {
      case INITIALIZE://如果是初始化阶段
        stage = getNextStage(Stage.INITIALIZE);
        currentGenerator = getNextGenerator();
        runGenerators();
        break;
      case SWITCH_TO_SOURCE_SERVICE:
        runGenerators();
        break;
      case DECODE_DATA:
        decodeFromRetrievedData();
        break;
      default:
        throw new IllegalStateException("Unrecognized run reason: " + runReason);
    }
  }

  //数据资源获取生成器， 初始化->资源缓存解码->数据缓存->源->结束
  // 根据不同的阶段，生成不同的数据加载器
  private DataFetcherGenerator getNextGenerator() {
    switch (stage) {
      case RESOURCE_CACHE://从缓存文件加载数据(包括了向下采样/转化后的资源)
        return new ResourceCacheGenerator(decodeHelper, this);
      case DATA_CACHE://数据缓存加载数据(原始的缓存资源)
        return new DataCacheGenerator(decodeHelper, this);
      case SOURCE://资源的源地址加载数据
        return new SourceGenerator(decodeHelper, this);
      case FINISHED:
        return null;
      default:
        throw new IllegalStateException("Unrecognized stage: " + stage);
    }
  }
  /**
   * 根据当前状态，返回下一个状态。由于diskCacheStrategy默认的使用AUTOMATIC的缓存策略，
   * decodeCachedResource()和decodeCachedData()返回的都是true
   * 下一个阶段图：
   * 初始化->资源缓存解码->数据缓存->源->结束
   *
   */
  private Stage getNextStage(Stage current) {
    switch (current) {
      case INITIALIZE:
        return diskCacheStrategy.decodeCachedResource()? Stage.RESOURCE_CACHE: getNextStage(Stage.RESOURCE_CACHE);
      case RESOURCE_CACHE:
        return diskCacheStrategy.decodeCachedData()? Stage.DATA_CACHE: getNextStage(Stage.DATA_CACHE);
      case DATA_CACHE:
        return onlyRetrieveFromCache ? Stage.FINISHED : Stage.SOURCE;
      case SOURCE:
      case FINISHED:
        return Stage.FINISHED;
      default:
        throw new IllegalArgumentException("Unrecognized stage: " + current);
    }
  }
```

Glide的资源加载的主要流程依次为

1. 资源缓存解码
2. 数据缓存
3. 数据源源

其对应的数据加载类为:

1. ResourceCacheGenerator :从缓存文件加载数据(包括了向下采样/转化后的资源)
2. DataCacheGenerator :从数据缓存加载数据(原始的缓存资源)
3. SourceGenerator :从资源的源地址加载数据

根据用户的实际配置信息，可能会跳过中间的某一个或者多个步骤。

在创建了数据加载类以后，通过 *runGenerators()** 方法启动了相关的数据加载。

```
  private void runGenerators() {
    currentThread = Thread.currentThread();
    startFetchTime = LogTime.getLogTime();
    boolean isStarted = false;
    //停止循环的条件：已经取消，当前数据加载器不为空，并且当前加载器未加载到相关资源
    while (!isCancelled && currentGenerator != null && !(isStarted = currentGenerator.startNext())) {
      stage = getNextStage(stage);
      currentGenerator = getNextGenerator();
      if (stage == Stage.SOURCE) {//如果是从资源源获取，则进入到源获取的相关调度
        reschedule();
        return;
      }
    }
    if ((stage == Stage.FINISHED || isCancelled) && !isStarted) {//如果已经结束或者取消了，直接回调失败
      notifyFailed();
    }
  }
```

在该函数里面，通过while循环来遍历执行相关的数据加载器，直到所有的数据加载器都执行完，或者某个加载器加载到了相关的数据。

### ResourceCacheGenerator#startNext

我们先看看 **ResourceCacheGenerator** 是如何执行加载过程的。

```
 public boolean startNext() {
    //每种model都对应着多个解析器，最后根据model的格式(String,Uri等)，来找到可以使用的LoadData列表。每个LoadData都会存在一个对应的key。
    //这里获取了对应的key列表
    List<Key> sourceIds = helper.getCacheKeys();//如果model没有对应的映射出来的key。则直接返回false
    if (sourceIds.isEmpty()) {
      return false;
    }
    //获取由model转化为resource的类，也即是可以decode的资源类信息
    List<Class<?>> resourceClasses = helper.getRegisteredResourceClasses();
    if (resourceClasses.isEmpty()) {
      if (File.class.equals(helper.getTranscodeClass())) {
        return false;
      }
      throw new IllegalStateException(
          "Failed to find any load path from "
              + helper.getModelClass()
              + " to "
              + helper.getTranscodeClass());
    }
    while (modelLoaders == null || !hasNextModelLoader()) {//双层遍历
      resourceClassIndex++;
      if (resourceClassIndex >= resourceClasses.size()) {
        sourceIdIndex++;
        if (sourceIdIndex >= sourceIds.size()) {
          return false;
        }
        resourceClassIndex = 0;
      }
      Key sourceId = sourceIds.get(sourceIdIndex);
      Class<?> resourceClass = resourceClasses.get(resourceClassIndex);
      Transformation<?> transformation = helper.getTransformation(resourceClass);se.
      currentKey =
          new ResourceCacheKey( // NOPMD Avoid Instantiating Objects InLoops
              helper.getArrayPool(),
              sourceId,
              helper.getSignature(),
              helper.getWidth(),
              helper.getHeight(),
              transformation,
              resourceClass,
              helper.getOptions());
      //根据当前的key获取缓存的文件
      cacheFile = helper.getDiskCache().get(currentKey);
      if (cacheFile != null) {//获取到缓存文件了，设置对应的modelLoader和key，而且会跳出循环
        sourceKey = sourceId;
        modelLoaders = helper.getModelLoaders(cacheFile);
        modelLoaderIndex = 0;
      }
    }
    loadData = null;
    boolean started = false;
    while (!started && hasNextModelLoader()) {
      ModelLoader<File, ?> modelLoader = modelLoaders.get(modelLoaderIndex++);
      loadData = modelLoader.buildLoadData(cacheFile, helper.getWidth(), helper.getHeight(), helper.getOptions());
      if (loadData != null && helper.hasLoadPath(loadData.fetcher.getDataClass())) {
        //有对应的LoadData
        started = true;
        //通过fetcher去加载对应的文件
        loadData.fetcher.loadData(helper.getPriority(), this);
      }
    }
    return started;
  }
```

我们总结一下大体的流程：

> 1. 根据传入的参数，获取对应的 **sourceIds** 和 **resourceClasses** 
> 2. 正交遍历，迭代每一组。根据相关参数生成在缓存文件使用的key，然后根据key去查询是否有缓存文件
> 3. 如果查找到对应的缓存文件，则设置sourceKey 和modelLoaders。然后通过这两个的设置可以跳出循环。
> 4. **modeLoaders** 遍历，获取 **modelLoader** ，并创建对应的 **loadData** 。
> 5. 如果 **LoadData** 存在，并且其内部的 **dataFatcher** (数据读取器)获取的数据类存在，则设置started标志位，表明该从 **ResourceCacheGenerator** 已经加载到相关数据了(这样后面所有的加载器都不再执行)。然后通过dataFatcher去获取数据。
> 6. 如果遍历没有加载相关数据，则返回started标志位为false。表明 **ResourceCacheGenerator** 没有加载到相关资源。之后的 **加载器(DataCacheGenerator，也可能是SourceGenerator，也可能是跳出循环，根据之前讲的相应设置有关)** 会继续执行。

如果第一次加载，那么在 **DataCacheGenerator** 中，肯定是获取不到资源的，那么下一个会执行到 **DataCacheGenerator** 的 **startNext()** 方法

### DataCacheGenerator#startNext

```java
  @Override
  public boolean startNext() {
    while (modelLoaders == null || !hasNextModelLoader()) {
      sourceIdIndex++;
      if (sourceIdIndex >= cacheKeys.size()) {
        return false;
      }
      //获取源资源的Key信息
      Key sourceId = cacheKeys.get(sourceIdIndex);//cacheKeys=helper.getCacheKeys()
      //根据当前sourceId，获取对应的原始Key。
      Key originalKey = new DataCacheKey(sourceId, helper.getSignature());
      //根据原始的Key从磁盘缓存获取缓存文件，并且跳出循环
      cacheFile = helper.getDiskCache().get(originalKey);
      if (cacheFile != null) {
        this.sourceKey = sourceId;
        modelLoaders = helper.getModelLoaders(cacheFile);
        modelLoaderIndex = 0;
      }
    }
    loadData = null;
    boolean started = false;
    while (!started && hasNextModelLoader()) {
      ModelLoader<File, ?> modelLoader = modelLoaders.get(modelLoaderIndex++);
      loadData = modelLoader.buildLoadData(cacheFile, helper.getWidth(), helper.getHeight(), helper.getOptions());
      if (loadData != null && helper.hasLoadPath(loadData.fetcher.getDataClass())) {
        //有对应的LoadData
        started = true;
        //通过fetcher去加载对应的文件
        loadData.fetcher.loadData(helper.getPriority(), this);
      }
    }
    return started;
  }
```

**DataCacheGenerator** 主要是获取原始的缓存文件。可以看到大体的流程是和 **ResourceCacheGenerator** 相似的。唯一的不同是，获取的Key不一样。在获取缓存文件的时候，使用的参数是 **DataCacheKey** ,也就是原始缓存文件的键值。

在首次进行加载的时候，这个肯定也是获取不到的，返回的是空，那么这时候，那么下一个会执行到 **SourceGenerator** 的 **startNext()** 方法。

### SourceGenerator#startNext

```
  public boolean startNext() {
    if (dataToCache != null) {
      //当第一次资源加载完成以后，会进行一次线程切换，而再次调用本方法，这时候dataToCache不为空，然后进行data的保存，此地是磁盘缓存源文件
      // 并且生成了DataCacheGenerator类
      Object data = dataToCache;
      dataToCache = null;
      cacheData(data);
    }
    if (sourceCacheGenerator != null && sourceCacheGenerator.startNext()) {
      //在有DataCacheGenerator类的情况下，会调用startNext方法，执行从源文件的加载文件
      return true;
    }
    sourceCacheGenerator = null;
    loadData = null;
    boolean started = false;
    while (!started && hasNextModelLoader()) {
      //循环获取当前model相关的所有的modelLoaders,获取loadData数据
      loadData = helper.getLoadData().get(loadDataListIndex++);
      //这里需要注意，假如缓存策略认为可以缓存data，那么就不需要管后面的loadPath，直接先把data获取。
      //因为缓存之后将会移交给DataCacheGenerator处理，所以可以跳过后面的hasLoadPath判断。
      //hasLoadPath是根据Registry中已经注册的解码器，转换器判断是否可以完成dataclass->resourceclass->transcodeclass的变换。
      if (loadData != null &&
          (helper.getDiskCacheStrategy().isDataCacheable(loadData.fetcher.getDataSource())
              || helper.hasLoadPath(loadData.fetcher.getDataClass()))) {
        started = true;
        startNextLoad(loadData);
      }
    }
    return started;
  }

  private void startNextLoad(final LoadData<?> toStart) {
    loadData.fetcher.loadData(//进行资源的加载
        helper.getPriority(),
        new DataCallback<Object>() {
          @Override
          public void onDataReady(@Nullable Object data) {
            if (isCurrentRequest(toStart)) {
              onDataReadyInternal(toStart, data);
            }
          }

          @Override
          public void onLoadFailed(@NonNull Exception e) {
            if (isCurrentRequest(toStart)) {
              onLoadFailedInternal(toStart, e);
            }
          }
        });
  }
```

当进行资源加载完成以后，会通过回调 **onDataReady** 接口。我们看一下 **onDataReadyInternal** 的执行。

```
 @Synthetic
  void onDataReadyInternal(LoadData<?> loadData, Object data) {
    DiskCacheStrategy diskCacheStrategy = helper.getDiskCacheStrategy();
    if (data != null && diskCacheStrategy.isDataCacheable(loadData.fetcher.getDataSource())) {
      //如果DataFetcher对应的DataSource可以进行缓存
      dataToCache = data;
      //这里的cb，指的是DecodeJob类->callback.reschedule(this),callback指的是EngineJob类
      //->getActiveSourceExecutor().execute(job);
      //即通过线程池，重新执行了EngineJob,然后会执行到本类的startNext方法，因为dataToCache不为空，会执行里面的代码块
      cb.reschedule();
    } else {
      //如果不能进行缓存，则直接调用onDataFetcherReady方法
      cb.onDataFetcherReady(
          loadData.sourceKey, data, loadData.fetcher, loadData.fetcher.getDataSource(), originalKey);
    }
  }
```

我们先分析第一种情况。

我们注释写的很详细了，也知道最后会重新调用本类的 **startNext** 方法，那么我们看看这个方法开始的部分。

```
    if (dataToCache != null) {
      //当第一次资源加载完成以后，会进行一次线程切换，而再次调用本方法，这时候dataToCache不为空，然后进行data的保存，此地是磁盘缓存源文件
      // 并且生成了DataCacheGenerator类
      Object data = dataToCache;
      dataToCache = null;
      cacheData(data);
    }
    if (sourceCacheGenerator != null && sourceCacheGenerator.startNext()) {
      //在有DataCacheGenerator类的情况下，会调用startNext方法，执行从源文件的加载文件
      return true;
    }
   
  private void cacheData(Object dataToCache) {
    long startTime = LogTime.getLogTime();
    try {
      //获取到编码器，通过遍历注册表中编码器列表，获取到对应的cache类的编码器
      Encoder<Object> encoder = helper.getSourceEncoder(dataToCache);
      //生成
      DataCacheWriter<Object> writer = new DataCacheWriter<>(encoder, dataToCache, helper.getOptions());
      //生成原始缓存文件的key，用于进行DataCacheGenerator中进行编码查找
      originalKey = new DataCacheKey(loadData.sourceKey, helper.getSignature());
      //通过DiskCache进行数据的缓存
      helper.getDiskCache().put(originalKey, writer);
    } finally {
      loadData.fetcher.cleanup();
    }

    sourceCacheGenerator =
        new DataCacheGenerator(Collections.singletonList(loadData.sourceKey), helper, this);
  }
```

也就是先在磁盘上保存原文件，然后通过DataCacheGenerator类再去进行加载。

现在我们看第二种情况：

```
  public void onDataFetcherReady(
      Key sourceKey, Object data, DataFetcher<?> fetcher, DataSource dataSource, Key attemptedKey) {
    ...
    if (Thread.currentThread() != currentThread) {
      //设置执行原因为DECODE_DATA，再重新调用unwrapper时，就可以直接执行解码阶段了。也就是下面else中的decodeFromRetrievedData方法
      runReason = RunReason.DECODE_DATA;
      callback.reschedule(this);
    } else {
      GlideTrace.beginSection("DecodeJob.decodeFromRetrievedData");
      try {
        decodeFromRetrievedData();
      } finally {
        GlideTrace.endSection();
      }
    }
  }
```

这时候会根据线程进行一次重新调用，或者直接调用 **decodeFromRetrievedData()** 方法。

```
 //解码检索到的的数据
  private void decodeFromRetrievedData() {
    if (Log.isLoggable(TAG, Log.VERBOSE)) {
      logWithTimeAndKey(
          "Retrieved data",
          startFetchTime,
          "data: "
              + currentData
              + ", cache key: "
              + currentSourceKey
              + ", fetcher: "
              + currentFetcher);
    }
    Resource<R> resource = null;
    try {
      //进行资源的解码操作
      resource = decodeFromData(currentFetcher, currentData, currentDataSource);
    } catch (GlideException e) {
      e.setLoggingDetails(currentAttemptingKey, currentDataSource);
      throwables.add(e);
    }
    if (resource != null) {
      //通过接口回调资源加载成功接口，然后进行层层回调由DecodeJon->EngineJob->Target中，最后由Target操作ImageView,将资源渲染到View上
      notifyEncodeAndRelease(resource, currentDataSource);
    } else {
      runGenerators();
    }
  }
```

大体的流程基本完成了。后续再对细节一点点进行增加。

补充几个文件：
![](http://cdn.qiniu.kailaisii.com/typora/20200217171720-302601.png)

ModelLoader(资源加载类)：包含两个方法

> buildLoadData()：创建LoadData类
>
> handles(Model):该类能否能够加载给定的模型

LoadData类：包含了3个属性

>sourceKey：资源key
>
>alternateKeys：临时key的列表
>
>fetcher：持有的一个DataFetcher接口实例，定义了真正获取数据的loadData方法

DataFetcher接口：真正的资源获取

>loadData()：获取能够被解码的数据
>
>cleanup()：清空或者回收资源
>
>getDataClass()：当前实现类能获取的资源的类
>
>getDataSource()：此fetcher将从哪种数据源返回数据，枚举类型。


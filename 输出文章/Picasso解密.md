# Picasso解密

之前做过一篇[Glide万字解密](https://mp.weixin.qq.com/s/e1E-S0jvCogHLOc6GjM2Og)，整体来说Glide的源码是很多的，阅读起来比较费劲。而如果我们使用一些简单的图片加载的话，建议使用Picasso，因为他的类库文件比较少，功能也相对来说能满足大部分使用场景的。

这一次我们对Picasso来做一次源码的解析工作。如果你看过Glide那一套源码解析，那么相信你，看Picasso的源码的话，会容易很多。

Picasso的一般使用方式：

```
        Picasso.get()
                .load(url)
                .into(view);
```

和Glide一样，使用很简单。

我们去跟踪源码。看看

### get()

```
  public static Picasso get() {
    if (singleton == null) {
      synchronized (Picasso.class) {
        if (singleton == null) {
          if (PicassoProvider.context == null) {
            throw new IllegalStateException("context == null");
          }
          singleton = new Builder(PicassoProvider.context).build();
        }
      }
    }
    return singleton;
  }
```

可以看到，很简单，使用了双重检测的单例模式来创建了 **Picasso** 对象。

其中创建过程使用了 **builder** 建造者模式。

```
    public Builder(@NonNull Context context) {
      if (context == null) {
        throw new IllegalArgumentException("Context must not be null.");
      }//默认使用的Context是ApplicationContext，所以其生命周期适合应用的生命周期绑定的
      //此方式主要是为了避免context和单例模式的生命周期不同而造成内存泄漏的问题
      this.context = context.getApplicationContext();
    }
    //生成单例
    public Picasso build() {
      Context context = this.context;
      //图片下载器
      if (downloader == null) {
        downloader = new OkHttp3Downloader(context);
      }//缓存策略
      if (cache == null) {
        cache = new LruCache(context);
      }//线程池
      if (service == null) {
        service = new PicassoExecutorService();
      }
      if (transformer == null) {//使用标准的请求转换器
        transformer = RequestTransformer.IDENTITY;
      }
      Stats stats = new Stats(cache);
      //分发器
      Dispatcher dispatcher = new Dispatcher(context, service, HANDLER, downloader, cache, stats);
      return new Picasso(context, dispatcher, cache, listener, transformer, requestHandlers, stats,
          defaultBitmapConfig, indicatorsEnabled, loggingEnabled);
    }
  }
```

可以看到，Picasso的单例创建比较简单，主要是通过建造者模式进行了一些下载器、线程池、缓存策略、分发器等的创建工作。

### load()

```
  public RequestCreator load(@Nullable String path) {
    if (path == null) {//如果路径是空，不会发生任何请求
      return new RequestCreator(this, null, 0);
    }
    if (path.trim().length() == 0) {
      throw new IllegalArgumentException("Path must not be empty.");
    }
    return load(Uri.parse(path));
  }
  public RequestCreator load(@Nullable Uri uri) {
    return new RequestCreator(this, uri, 0);
  }
```

最终通过参数，创建了一个 **RequestCreator** 对象，这个从命名就可以看到是一个请求生成器。那么我们可以简单在构造函数里面做了什么处理

```java
  RequestCreator(Picasso picasso, Uri uri, int resourceId) {
    if (picasso.shutdown) {
      throw new IllegalStateException(
          "Picasso instance already shut down. Cannot submit new requests.");
    }
    this.picasso = picasso;
    //创建了Request.Builder
    this.data = new Request.Builder(uri, resourceId, picasso.defaultBitmapConfig);
  }
```

其实前面的get()和load()方法，最终只是创建了一个 **RequestCreator** 对象，然后里面封装了请求的 **uri** 信息

### into()

当我们创建完 **RequestCreator** 对象以后，就是调用 **into()** 方法来进行图片的加载了。

```
  public void into(ImageView target) {
    into(target, null);
  }
  public void into(ImageView target, Callback callback) {
    long started = System.nanoTime();
    checkMain();//需要在主线程发起请求
    if (target == null) {
      throw new IllegalArgumentException("Target must not be null.");
    }
    if (!data.hasImage()) {//判断uri和source都为空，说明没有设置load(),直接调用取消请求
      picasso.cancelRequest(target);
      if (setPlaceholder) {
        setPlaceholder(target, getPlaceholderDrawable());
      }
      return;
    }
    //当设置了fit()时，deferred为true,也就是完全填充
    if (deferred) {
      if (data.hasSize()) {//如果已经有宽高了，则表示同时使用了fit()和resize(),两个不能同时使用
        throw new IllegalStateException("Fit cannot be used with resize.");
      }
      int width = target.getWidth();
      int height = target.getHeight();
      if (width == 0 || height == 0) {//如果还没有绘制完成，宽高都是空，放置Placeholder
        if (setPlaceholder) {
          setPlaceholder(target, getPlaceholderDrawable());
        }
        picasso.defer(target, new DeferredRequestCreator(this, target, callback));
        return;
      }
      //设置目标要填充的大小，在下载完成以后会根据这个大小来进行图片的处理
      data.resize(width, height);
    }
    //创建加载请求
    Request request = createRequest(started);
    //根据请求信息request创建key值,key值是个String类型，里面包含了uri，大小，填充方式等的拼接
    String requestKey = createKey(request);
    //根据用户设置的缓存策略来进行处理，如果设置了缓存，则先从缓存查找
    if (shouldReadFromMemoryCache(memoryPolicy)) {
      //从Picasso的缓存（默认设置的是Lru缓存）中，根据key值来进行查询。
      Bitmap bitmap = picasso.quickMemoryCacheCheck(requestKey);
      if (bitmap != null) {//查找成功，取消请求，然后设置bitmap，然后回调成功
        picasso.cancelRequest(target);
        setBitmap(target, picasso.context, bitmap, MEMORY, noFade, picasso.indicatorsEnabled);
        if (picasso.loggingEnabled) {
          log(OWNER_MAIN, VERB_COMPLETED, request.plainId(), "from " + MEMORY);
        }
        if (callback != null) {
          callback.onSuccess();
        }
        return;
      }
    }
    //如果设置了默认显示的图片，则先显示出来
    if (setPlaceholder) {
      setPlaceholder(target, getPlaceholderDrawable());
    }
    //根据本次请求的相关处理器。里面封装了相关的target,request,网络策略,缓存策略等
    Action action =
        new ImageViewAction(picasso, target, request, memoryPolicy, networkPolicy, errorResId,
            errorDrawable, requestKey, tag, callback, noFade);
    //入队并提交请求的处理
    picasso.enqueueAndSubmit(action);
  }
```

可以看到这个方法里面主要有3个处理

1. 根据设置的参数信息，创建 **Request** 请求对象
2. 创建了 **ImageViewAction** 对象，里面封装了相关的target、request、网络策略、缓存策略等
3. 入队并执行请求

执行请求方法，经过层层的调用，最后调用了 Dispatcher 的 **performSubmit()** 方法。

```
  void performSubmit(Action action, boolean dismissFailed) {
    //如果Picasso下发了暂停指令，那么将当前的action保存起来。从弱引用移到强引用pausedActions中
    //有一种使用场景，就是我们在Recyclerview或者listview滑动的时候，暂停对于图片的加载
    if (pausedTags.contains(action.getTag())) {
      pausedActions.put(action.getTarget(), action);
      return;
    }
    //hunterMap中保存着还未执行的下载请求。是按照key来进行分类的。如果多个地方加载的是同一个uri和相关配置
    //那么会将多个action保存在同一个BitmapHunter中，然后在加载完成时，根据BitmapHunter的actions来进行分发显示处理
    BitmapHunter hunter = hunterMap.get(action.getKey());
    if (hunter != null) {
      hunter.attach(action);
      return;
    }
    if (service.isShutdown()) {
      return;
    }
    //根据相关的请求参数，查找到具体的请求处理器，然后生成一个BitmapHunter对象
    hunter = forRequest(action.getPicasso(), this, cache, stats, action);
     //通过线程池执行请求，然后将future赋值给hunter的future属性
    hunter.future = service.submit(hunter);
    //保存到hunterMap中
    hunterMap.put(action.getKey(), hunter);
    if (dismissFailed) {
      failedActions.remove(action.getTarget());
    }
  }
```

这这里面主要有两个要关注的点。

1. 一个是BitmapHunter对象的创建：forRequest
2. 一个是方法的执行：service.submit(hunter)

先看看对象的创建过程。主要是根据into()的参数信息，来选择具体的请求处理器。

```
  static BitmapHunter forRequest(Picasso picasso, Dispatcher dispatcher, Cache cache, Stats stats,
      Action action) {
    Request request = action.getRequest();
    //请求的处理器，picasso支持assert,network,drawable等7种处理器
    List<RequestHandler> requestHandlers = picasso.getRequestHandlers();
    for (int i = 0, count = requestHandlers.size(); i < count; i++) {
      RequestHandler requestHandler = requestHandlers.get(i);
      //根据请求信息，查看当前处理器是否能够进行处理
      if (requestHandler.canHandleRequest(request)) {
        return new BitmapHunter(picasso, dispatcher, cache, stats, action, requestHandler);
      }
    }
    return new BitmapHunter(picasso, dispatcher, cache, stats, action, ERRORING_HANDLER);
  }
```

再继续跟踪一下submit方法的执行。

在创建Picasso单例的时候，我们知道默认使用的线程池是PicassoExecutorService，所以这里我们直接进入到它的submit方法中。

```java
  public Future<?> submit(Runnable task) {
    PicassoFutureTask ftask = new PicassoFutureTask((BitmapHunter) task);
    //execute调用ftask的run方法，也就是传入的task(BitmapHunter)的run方法
    execute(ftask);
    return ftask;
  }
  
  @Override public void run() {
    try {
      updateThreadName(data);
      //获取结果
      result = hunt();
      //根据结果进行数据的处理
      if (result == null) {
        dispatcher.dispatchFailed(this);
      } else {
        dispatcher.dispatchComplete(this);
      }
  }
```

这里进入了最后的数据加载阶段，这里面主要有两个要关注的点。

1. 一个是请求的最终执行：hunt()
2. 一个是请求执行完毕后的分发：dispatcher

```
Bitmap hunt() throws IOException {
  Bitmap bitmap = null;
  //再次检测缓存，如果存在，则直接从缓存获取
  if (shouldReadFromMemoryCache(memoryPolicy)) {
    bitmap = cache.get(key);
    if (bitmap != null) {
      stats.dispatchCacheHit();
      loadedFrom = MEMORY;
      if (picasso.loggingEnabled) {
        log(OWNER_HUNTER, VERB_DECODED, data.logId(), "from cache");
      }
      return bitmap;
    }
  }
  //重试次数，网络请求是2次，其他是0次。
  networkPolicy = retryCount == 0 ? NetworkPolicy.OFFLINE.index : networkPolicy;
  //请求处理器进行加载处理。返回请求结果
  RequestHandler.Result result = requestHandler.load(data, networkPolicy);
  if (result != null) {
    loadedFrom = result.getLoadedFrom();
    exifOrientation = result.getExifOrientation();
    bitmap = result.getBitmap();
    //如果没有bitmap，那么就进行解码，因为result可能保存的是source，而不是bitmap
    if (bitmap == null) {
      Source source = result.getSource();
      try {
        bitmap = decodeStream(source, data);
      } finally {
        try {
          source.close();
        } catch (IOException ignored) {
        }
      }
    }
  }
  if (bitmap != null) {
    stats.dispatchBitmapDecoded(bitmap);
    //如果进行了图片的转换设置，那么就需要根据设置来进行bitmap的处理操作
    if (data.needsTransformation() || exifOrientation != 0) {
      ....
    }
  }
  return bitmap;
}
```

可以看到，最终加载是由具体的请求处理器的 **load()** 方法来处理的。我们这里主要跟踪一下网络请求，所以我们选择 **NetworkRequestHandler** 来分析，其他的如果有机会，再慢慢去分析了。

```
  @Override public Result load(Request request, int networkPolicy) throws IOException {
    //创建okhttp3的请求，里面根据设置的缓存策略进行了缓存的处理。从sdcard的缓存处理都是通过okhttp来进行了处理，而没有单独进行处理
    okhttp3.Request downloaderRequest = createRequest(request, networkPolicy);
    //发起请求
    Response response = downloader.load(downloaderRequest);
    ResponseBody body = response.body();
    if (!response.isSuccessful()) {
      body.close();
      throw new ResponseException(response.code(), request.networkPolicy);
    }
    //因为支持okhttp缓存模式，所以这里根据是否命中了缓存，来进行Result相关参数的设置
    Picasso.LoadedFrom loadedFrom = response.cacheResponse() == null ? NETWORK : DISK;
    if (loadedFrom == DISK && body.contentLength() == 0) {
      body.close();
      throw new ContentLengthException("Received response with 0 content-length header.");
    }
    if (loadedFrom == NETWORK && body.contentLength() > 0) {
      stats.dispatchDownloadFinished(body.contentLength());
    }
    return new Result(body.source(), loadedFrom);
  }
```

通过 **load()**  方法返回了 **Result** 之后，通过一系列的转化，在 **hunt()** 中返回了 **bitmap** 。

在 **hunt()** 返回之后，会调用dispatcher.dispatchComplete(this)来进行成功结果的分发处理。经过层层的跟踪调用，可以看到最后会调用到 Dispatch的 **performComplete** 方法

```
  //数据加载完成之后进行的处理操作
  void performComplete(BitmapHunter hunter) {
    //如果设置了内存缓存，则将数据写入到缓存
    if (shouldWriteToMemoryCache(hunter.getMemoryPolicy())) {
      cache.set(hunter.getKey(), hunter.getResult());
    }
    //从还未执行完毕的列表中移除
    hunterMap.remove(hunter.getKey());
    //批量处理
    batch(hunter);
  }
  
  private void batch(BitmapHunter hunter) {
    //将BitmapHunter放到batch变量中
    batch.add(hunter);
    //延迟200ms处理。能够将200ms内的所有请求的hunter结果进行一个批量的操作。
    if (!handler.hasMessages(HUNTER_DELAY_NEXT_BATCH)) {
      handler.sendEmptyMessageDelayed(HUNTER_DELAY_NEXT_BATCH, BATCH_DELAY);
    }
  }
```

这里Picasso做了一个优化，就是将相对小的时间段请求返回信息存放到batch，然后200ms之后对其进行一个批量处理。感觉有点像后台大并发中常用的合并请求的操作。

我们现在跟踪一下这个Handler中消息的处理。

```
void performBatchComplete() {
  List<BitmapHunter> copy = new ArrayList<>(batch);
  batch.clear();
  //向主线程的Handler发送一个批量处理完成的消息，
  mainThreadHandler.sendMessage(mainThreadHandler.obtainMessage(HUNTER_BATCH_COMPLETE, copy));
  //批量打印日志信息
  logBatch(copy);
}
```

可以看到，主要是将200ms内的数据合并之后又通过消息机制进行了处理。并且进行了日志的打印操作。继续跟踪

```
static final Handler HANDLER = new Handler(Looper.getMainLooper()) {
  @Override public void handleMessage(Message msg) {
    switch (msg.what) {
      case HUNTER_BATCH_COMPLETE: {
        List<BitmapHunter> batch = (List<BitmapHunter>) msg.obj;
        for (int i = 0, n = batch.size(); i < n; i++) {
          BitmapHunter hunter = batch.get(i);
          hunter.picasso.complete(hunter);
        }
        break;
      }
```

这里对返回的batch通过遍历，调用了 **complete()** 方法。

```
void complete(BitmapHunter hunter) {
  //在submit的时候，BitmapHunter会根据key值，将多个相同key的请求合并处理，BitmapHunter里面保存了请求的所有的Action信息
  //在hunter中action是保存的第一个Action信息，Actions是保存的除了第一个之外所有的信息。
  Action single = hunter.getAction();
  List<Action> joined = hunter.getActions();

  boolean hasMultiple = joined != null && !joined.isEmpty();
  boolean shouldDeliver = single != null || hasMultiple;

  if (!shouldDeliver) {
    return;
  }

  Uri uri = hunter.getData().uri;
  Exception exception = hunter.getException();
  Bitmap result = hunter.getResult();
  LoadedFrom from = hunter.getLoadedFrom();
  //如果有多个请求action，则进行分发处理
  if (single != null) {
    deliverAction(result, from, single, exception);
  }
  //遍历分发处理剩下的请求action信息
  if (hasMultiple) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, n = joined.size(); i < n; i++) {
      Action join = joined.get(i);
      deliverAction(result, from, join, exception);
    }
  }

  if (listener != null && exception != null) {
    listener.onImageLoadFailed(this, uri, exception);
  }
}
```

在进行请求提交的时候我们提过，每个BitmapHunter中可能会存在多个Action信息，而每个Action信息对应着一个要加载显示图片的请求。在BitmapHunter的加载请求处理完成以后，需要将其中对应的每一个Action信息进行处理，从而在对应的位置都显示出来图片信息。而这个complete函数的作用就是完成分发的处理。最后通过 **deliverAction** 来实现图片的显示的操作。

```
 private void deliverAction(Bitmap result, LoadedFrom from, Action action, Exception e) {
      //关键的处理，执行完成结果
      action.complete(result, from);
  }
  
  @Override public void complete(Bitmap result, Picasso.LoadedFrom from) {
    ImageView target = this.target.get();
    if (target == null) {
      return;
    }
    Context context = picasso.context;
    boolean indicatorsEnabled = picasso.indicatorsEnabled;
    //设置target的bitmap
    PicassoDrawable.setBitmap(target, context, result, from, noFade, indicatorsEnabled);
    if (callback != null) {
      callback.onSuccess();
    }
  }
  //设置bitmap
  static void setBitmap(ImageView target, Context context, Bitmap bitmap,
      Picasso.LoadedFrom loadedFrom, boolean noFade, boolean debugging) {
    Drawable placeholder = target.getDrawable();
    if (placeholder instanceof Animatable) {
      ((Animatable) placeholder).stop();
    }
    PicassoDrawable drawable =
        new PicassoDrawable(context, bitmap, placeholder, loadedFrom, noFade, debugging);
    target.setImageDrawable(drawable);
  }
```

在分发处理中，主要是将bitmap中在target(ImageView)中进行显示。

到此Picasso完结了，感觉代码量相对于Glide还是简单很多的。分层也比较好理解。
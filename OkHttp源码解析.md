### OkHttp源码解析

现在OkHttp在安卓的网络请求框架已经占据了绝对的领导地位，今天我们就从源码层次一点点拨开云雾，来看看它具体的实现原理。

我们先讲解几个重点类：

```cpp
OkHttpClient ：我们的入口类，里面持有了分发器，拦截器等等。能够将分发器和拦截器等进行统一的管理。通过Builder模式来进行创建。
```

```：L
Dispatcher:分发器，也是我们的请求最终执行的地方，里面有一个线程池，能够将我们的网络请求进行分发处理。
```

```：
Interceptor：拦截器，通过责任链模式，一层层的将请求处理交给具体的类进行处理
```

首先我们来看一下具体的调用

```
OkHttpClient.Builder builder = new OkHttpClient.Builder();
//设置超时时间
builder.connectTimeout(TIMEOUT_CONNECT, TimeUnit.MILLISECONDS);
builder.writeTimeout(TIMEOUT_WRITES, TimeUnit.MILLISECONDS);
builder.readTimeout(TIMEOUT_READ, TimeUnit.MILLISECONDS);
okHttpClient = builder.build();
```

在 **Builder** 中进行了一些数据的初始化工作，我们在这里可以进行一些自己的设置，比如代理，超时时间，线程池，SSL认证等。

```
public Builder() {
  dispatcher = new Dispatcher();
  protocols = DEFAULT_PROTOCOLS;
  connectionSpecs = DEFAULT_CONNECTION_SPECS;
  eventListenerFactory = EventListener.factory(EventListener.NONE);
  proxySelector = ProxySelector.getDefault();
  if (proxySelector == null) {
    proxySelector = new NullProxySelector();
  }
  cookieJar = CookieJar.NO_COOKIES;
  socketFactory = SocketFactory.getDefault();
  hostnameVerifier = OkHostnameVerifier.INSTANCE;
  certificatePinner = CertificatePinner.DEFAULT;
  proxyAuthenticator = Authenticator.NONE;
  authenticator = Authenticator.NONE;
  connectionPool = new ConnectionPool();
  dns = Dns.SYSTEM;
  followSslRedirects = true;
  followRedirects = true;
  retryOnConnectionFailure = true;
  callTimeout = 0;
  connectTimeout = 10_000;
  readTimeout = 10_000;
  writeTimeout = 10_000;
  pingInterval = 0;
}
```

当初始化完成以后，我们就可以执行我们的网络请求了。

```cpp
String run(String url) throws IOException {
  Request request = new Request.Builder()
      .url(url)
      .build();
  client.newCall(request).enqueue(new Callback(){
      @Override
      public void onResponse(Call call, final Response response) throws IOException {
      }
       @Override
       public void onFailure(Call call, final IOException e) {
           
       }
  });
}
```

先调用了 **newCall(request)** 方法，我们继续跟踪

```
@Override public Call newCall(Request request) {
  //生成了一个RealCall类
  return RealCall.newRealCall(this, request, false /* for web socket */);
}
```

所以，**enqueue** 的具体实现，是由 **RealCall** 这个类来实现的。

```
@Override public void enqueue(Callback responseCallback) {
  synchronized (this) {
    //线程安全，为了保证方法不能重复执行
    if (executed) throw new IllegalStateException("Already Executed");
    executed = true;
  }
  captureCallStackTrace();
  //将我们的responseCallback进行了封装，然后将最后的网络请求任务交给了Dispatcher来处理
  client.dispatcher().enqueue(new AsyncCall(responseCallback));
}
```

我们看下一 **Dispatcher** 这个类的具体操作

```
//线程安全方法，保证多线程执行时，该方法不会有线程安全问题
synchronized void enqueue(AsyncCall call) {
  //如果现在正在执行的请求数小于允许的最大请求数，而且host数据 也小于允许的host数据，则执行方法
  if (runningAsyncCalls.size() < maxRequests && runningCallsForHost(call) < maxRequestsPerHost) {
    //将call请求放到正在执行的异步请求队列中
    runningAsyncCalls.add(call);
    //执行call请求
    executorService().execute(call);
  } else {
  	//将请求放到等待队列中
    readyAsyncCalls.add(call);
  }
}
```

我们知道，OkHttp对于请求的处理，是可以多线程并发处理的，那么可以想到，其实最后的执行肯定是通过线程池来处理。我们看一下 **executorService()** 这个方法

```
//线程安全，防止创建多个线程池
public synchronized ExecutorService executorService() {
  if (executorService == null) {
    executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(), Util.threadFactory("OkHttp Dispatcher", false));
  }
  return executorService;
}
```

可以看到，其实最后返回的是一个线程池，然后通过线程池的 **execute()** 方法来执行 **AsyncCall** 里面的 **run()** 方法。

```
final class AsyncCall extends NamedRunnable {
  private final Callback responseCallback;

  AsyncCall(Callback responseCallback) {
    super("OkHttp %s", redactedUrl());
    this.responseCallback = responseCallback;
  }

  String host() {
    return originalRequest.url().host();
  }

  Request request() {
    return originalRequest;
  }

  RealCall get() {
    return RealCall.this;
  }
  //网络请求最终执行方法
  @Override protected void execute() {
    boolean signalledCallback = false;
    try {
      //OkHttp最神秘的地方，通过责任链模式，返回了所需要的网络请求信息
      //这里说所需要的,是因为我们可以在这里进行一系列操作，包括加密解密等，来进行统一的处理后，将数据展示给我们
      Response response = getResponseWithInterceptorChain();
      //下面就是根据response返回的信息，来回调我们定义的Callback接口了
      if (retryAndFollowUpInterceptor.isCanceled()) {
        signalledCallback = true;
        responseCallback.onFailure(RealCall.this, new IOException("Canceled"));
      } else {
        signalledCallback = true;
        responseCallback.onResponse(RealCall.this, response);
      }
    } catch (IOException e) {
      if (signalledCallback) {
        // Do not signal the callback twice!
        Platform.get().log(INFO, "Callback failure for " + toLoggableString(), e);
      } else {
        responseCallback.onFailure(RealCall.this, e);
      }
    } finally {
      //执行完毕后，要进行资源的释放，队列的移除等操作
      client.dispatcher().finished(this);
    }
  }
}

对应的父类：
public abstract class NamedRunnable implements Runnable {
  protected final String name;

  public NamedRunnable(String format, Object... args) {
    this.name = Util.format(format, args);
  }

  @Override public final void run() {
    String oldName = Thread.currentThread().getName();
    Thread.currentThread().setName(name);
    try {
      //抽象方法，将具体的执行操作交给子类处理
      execute();
    } finally {
      Thread.currentThread().setName(oldName);
    }
  }

  protected abstract void execute();
}

```

最神秘的 **getResponseWithInterceptorChain()** 方法我们稍后再讲，现在我们看看最后的资源释放 **finished(this)** 方法

```
void finished(AsyncCall call) {
  //方法复用
  finished(runningAsyncCalls, call, true);
}
//calls:正在执行的请求队列信息
private <T> void finished(Deque<T> calls, T call, boolean promoteCalls) {
    int runningCallsCount;
    Runnable idleCallback;
    //同步方法，保证在进行队列操作的时候，不会发生线程安全问题。
    synchronized (this) {
      //将执行完毕的请求，从队列中移除，移除失败，报错
      if (!calls.remove(call)) throw new AssertionError("Call wasn't in-flight!");
      //进行队列的处理，此处主要是从等待队列取出要执行的任务，并执行。
      if (promoteCalls) promoteCalls();
      //重新获取正在执行的队列数据
      runningCallsCount = runningCallsCount();
      idleCallback = this.idleCallback;
    }

    if (runningCallsCount == 0 && idleCallback != null) {
      idleCallback.run();
    }
  }
```

我们看下 **promoteCalls()** 的处理方法

```
private void promoteCalls() {
  //异步执行的队列已经满了，则直接返回
  if (runningAsyncCalls.size() >= maxRequests) return; // Already running max capacity.
  //没有等待执行的任务，直接返回
  if (readyAsyncCalls.isEmpty()) return; // No ready calls to promote.
  //遍历等待执行的异步任务
  for (Iterator<AsyncCall> i = readyAsyncCalls.iterator(); i.hasNext(); ) {
    AsyncCall call = i.next();
    if (runningCallsForHost(call) < maxRequestsPerHost) {
      //如果Host并没有超过最大限制数，将任务从待执行队列删除，并放到正在执行队列中，然后通过线程池的execute()方法执行任务
      i.remove();
      runningAsyncCalls.add(call);
      executorService().execute(call);
    }
    //如果正在执行的任务已经到达上限，则直接返回，否则继续从待执行队列中获取任务并放到执行队列
    if (runningAsyncCalls.size() >= maxRequests) return; // Reached max capacity.
  }
}
```

上面就是Okhttp的网络请求的一个整体的过程。我们还遗留了一个最重要的 **getResponseWithInterceptorChain()** 这个方法还没有解析。之前我分析过一篇 [Mybatis Plugin插件源码分析](http://www.kailaisii.com//archives/Mybatis Plugin插件源码分析) ，其实两个有异曲同工之妙，都通过责任链模式来实现对于拦截器的处理，只是责任链的实现方式有所区别。

```
Response getResponseWithInterceptorChain() throws IOException {
  // Build a full stack of interceptors.
  List<Interceptor> interceptors = new ArrayList<>();
  //获取我们自定义的拦截器信息
  interceptors.addAll(client.interceptors());
  //OkHttp内部使用的拦截器，包括缓存，连接等等
  interceptors.add(retryAndFollowUpInterceptor);
  interceptors.add(new BridgeInterceptor(client.cookieJar()));
  interceptors.add(new CacheInterceptor(client.internalCache()));
  interceptors.add(new ConnectInterceptor(client));
  if (!forWebSocket) {
    interceptors.addAll(client.networkInterceptors());
  }
  interceptors.add(new CallServerInterceptor(forWebSocket));
  //将所有的请求信息封装为一个责任链，责任链里面
  Interceptor.Chain chain = new RealInterceptorChain(
      interceptors, null, null, null, 0, originalRequest);
  //执行责任链的proceed()方法
  return chain.proceed(originalRequest);
}
```

我们看看 **proceed()** 这个方法的执行。

```
public final class RealInterceptorChain implements Interceptor.Chain {
  private final List<Interceptor> interceptors;//记录了所有的拦截器
  private final StreamAllocation streamAllocation;//
  private final HttpCodec httpCodec;
  private final Connection connection;//连接
  private final int index;//当前拦截器所处的层数
  private final Request request;//原始的request请求
  private int calls;

public Response proceed(Request request, StreamAllocation streamAllocation, HttpCodec httpCodec,
    Connection connection) throws IOException {
  if (index >= interceptors.size()) throw new AssertionError();
  calls++;
  ...
  //生成一个新的链，链对应拦截器的层数+1，
  RealInterceptorChain next = new RealInterceptorChain(
      interceptors, streamAllocation, httpCodec, connection, index + 1, request);
  //根据index和拦截器列表获取到当前拦截器
  Interceptor interceptor = interceptors.get(index);
  //将新生成的链作为参数，执行当前拦截器的方法。并返回Response
  Response response = interceptor.intercept(next);
  ...
  //将拦截器的返回值Response作为当前了链的proceed()的返回值返回
  return response;
}
```

可能这里有些迷惑，那我们来个图来解释一下

![image.png](http://cdn.qiniu.kailaisii.com/FmLBMuTly57Ga6zShH6h6Z1X8bzu)
此图来源于https://www.jianshu.com/p/cb444f49a777

每次调用拦截器的时候，都会生成一个责任链，责任链中保存了当前拦截器的pos位置信息，然后在拦截器中去调用下一个下一个责任链的方法（其实核心是拦截器中的方法intecepter），通过层层调用，最后通过 **CallServerInterceptor** 拦截器实现网络的请求调用。然后将返回值层层返回。
到此，我们的Okhttp的源码解析结束了。。
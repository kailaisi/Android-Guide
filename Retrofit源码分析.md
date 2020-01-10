## Retrofit源码分析

现在使用的网络请求框架基本是Retrofit+OkHttp了，代码实现简单而又方便。之前对OkHttp的源码进行过分析，现在再挖一挖Retrofit的实现原理。

我们看一下Retrofit的简单使用

先定义一个接口文件

```java
public interface RequestService {
    /**
     * apk检测升级
     */
    @POST("/appupdate")
    Call<BaseResponse<UpdateInfoResponse>> getUpdateInfo(@Body UpdateInfoRequest request);
}

//生成对应的service
private RequestClient() {
    OkHttpClient.Builder builder = getBuilder();
    baseUrl = SettingPreference.getBaseUrl();
    mRetrofit = new Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .client(builder.build())
            .build();
    mService = mRetrofit.create(RequestService.class);
}

//对方法的调用
public Call<BaseResponse<UpdateInfoResponse>> getUpdateInfo(UpdateInfoRequest request){
	returnmService.getUpdateInfo(request)
}
```

封装之后，调用就是这么简单，我们现在跟踪一下具体的源代码实现。

我们从 **Retrofit** 的创建开始看起

```java
  public Retrofit build() {
    //必须设置基地址
    if (baseUrl == null) {
      throw new IllegalStateException("Base URL required.");
    }
    //如果没有设置工厂，则使用默认的OkHttpClient
    if (callFactory == null) {
      callFactory = new OkHttpClient();
    }
    //执行器
    Executor callbackExecutor = this.callbackExecutor;
    if (callbackExecutor == null) {
      callbackExecutor = platform.defaultCallbackExecutor();
    }
    //CallAdapter
    List<CallAdapter.Factory> adapterFactories = new ArrayList<>(this.adapterFactories);
    adapterFactories.add(platform.defaultCallAdapterFactory(callbackExecutor));
    //转化器
    List<Converter.Factory> converterFactories = new ArrayList<>(this.converterFactories);

    return new Retrofit(callFactory, baseUrl, converterFactories, adapterFactories,
        callbackExecutor, validateEagerly);
  }
}
//将对应的属性赋值
Retrofit(okhttp3.Call.Factory callFactory, HttpUrl baseUrl,
      List<Converter.Factory> converterFactories, List<CallAdapter.Factory> adapterFactories,
      Executor callbackExecutor, boolean validateEagerly) {
    this.callFactory = callFactory;
    this.baseUrl = baseUrl;
    this.converterFactories = unmodifiableList(converterFactories); // Defensive copy at call site.
    this.adapterFactories = unmodifiableList(adapterFactories); // Defensive copy at call site.
    this.callbackExecutor = callbackExecutor;
    this.validateEagerly = validateEagerly;
  }
```

上面这个方法没有什么特殊的地方，只是把我们的设置的相关参数设置为了 **Retrofit** 的具体的属性。

我们直接写一个接口，然后通过 **create()** 方法就可以对接口里面的方法进行调用，感觉应该是使用了JDK的动态代理。我们去跟踪方法瞅一眼~~

```java
public <T> T create(final Class<T> service) {
  //校验接口的合法性
  Utils.validateServiceInterface(service);
  if (validateEagerly) {
    //如果设置了提前校验，在生成service的时候就进行接口方法的加载处理
    eagerlyValidateMethods(service);
  }
  //动态代理
  return (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] { service },
      new InvocationHandler() {
        //获取平台信息 有两种：一种安卓，一种JAVA
        private final Platform platform = Platform.get();
        @Override public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
          //如果方法是Object中的方法，直接执行，（比如我们调用了service.toString()方法）
          if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
          }
          //如果是平台相关的方法，直接调用平台对应的方法
          if (platform.isDefaultMethod(method)) {
            return platform.invokeDefaultMethod(method, service, proxy, args);
          }
          //将方法进行转化处理，生成一个ServiceMethod对象
          ServiceMethod<Object, Object> serviceMethod =
              (ServiceMethod<Object, Object>) loadServiceMethod(method);
          //用serviceMethod对象和参数信息，生成一个OkHttpCall对象
          OkHttpCall<Object> okHttpCall = new OkHttpCall<>(serviceMethod, args);
          //通过callAdapter，将返回的call对象进行处理，转化为所需要的数据类型
          return serviceMethod.callAdapter.adapt(okHttpCall);
        }
      });
}
```

这个方法其实就是我们Retrofit相关功能的实现方法了。我们看一下在 **loadServiceMethod** 中对我们所调用的方法做了什么处理。

```
ServiceMethod<?, ?> loadServiceMethod(Method method) {
  //先检查缓存中是否已经有这个方法了相关信息了
  ServiceMethod<?, ?> result = serviceMethodCache.get(method);
  if (result != null) return result;
  //同步代码块
  synchronized (serviceMethodCache) {
    result = serviceMethodCache.get(method);
    if (result == null) {
      //通过Builder模式，生成一个ServiceMethod对象，然后进行缓存
      result = new ServiceMethod.Builder<>(this, method).build();
      serviceMethodCache.put(method, result);
    }
  }
  return result;
}
```

其中 **ServiceMethod.Builder<>(this, method).build()** 是我们需要重点关注的

```java
public ServiceMethod build() {
  //根据传入的信息生成一个callAdapter
  callAdapter = createCallAdapter();
  responseType = callAdapter.responseType();
  if (responseType == Response.class || responseType == okhttp3.Response.class) {
    throw methodError("'"
        + Utils.getRawType(responseType).getName()
        + "' is not a valid response body type. Did you mean ResponseBody?");
  }
  //生成对应的返回数据转化器
  responseConverter = createResponseConverter();
  //对方法上的数据进行解析，比如@GET，解析对应的路径和参数等。
  for (Annotation annotation : methodAnnotations) {
    parseMethodAnnotation(annotation);
  }
  ...
  int parameterCount = parameterAnnotationsArray.length;
  parameterHandlers = new ParameterHandler<?>[parameterCount];
  for (int p = 0; p < parameterCount; p++) {
    //对方法上的参数进行遍历处理,包括注解的正确性，url上{}参数的处理等
    Type parameterType = parameterTypes[p];
    if (Utils.hasUnresolvableType(parameterType)) {
      throw parameterError(p, "Parameter type must not include a type variable or wildcard: %s",
          parameterType);
    }

    Annotation[] parameterAnnotations = parameterAnnotationsArray[p];
    if (parameterAnnotations == null) {
      throw parameterError(p, "No Retrofit annotation found.");
    }
    parameterHandlers[p] = parseParameter(p, parameterType, parameterAnnotations);
  }
  ...
  return new ServiceMethod<>(this);
}
```

对于这个类，我们一点点的剖析，先从 **createCallAdapter()** 开始

```java
private CallAdapter<T, R> createCallAdapter() {
  Type returnType = method.getGenericReturnType();
  if (Utils.hasUnresolvableType(returnType)) {
    throw methodError(
        "Method return type must not include a type variable or wildcard: %s", returnType);
  }
  if (returnType == void.class) {
    throw methodError("Service methods cannot return void.");
  }
  Annotation[] annotations = method.getAnnotations();
  try {
    //noinspection unchecked
    return (CallAdapter<T, R>) retrofit.callAdapter(returnType, annotations);
  } catch (RuntimeException e) { // Wide exception range because factories are user code.
    throw methodError(e, "Unable to create call adapter for %s", returnType);
  }
}
```

此处比较简单，获取了方法的返回类型，然后获取了方法上的注解信息，然后调用了 **retrofit.callAdapter()** 方法

```java
public CallAdapter<?, ?> callAdapter(Type returnType, Annotation[] annotations) {
  return nextCallAdapter(null, returnType, annotations);
}

//根据返回值类型和相关注解，从callAdapterFactories中获取对应的CallAdapter
public CallAdapter<?, ?> nextCallAdapter(CallAdapter.Factory skipPast, Type returnType,
    Annotation[] annotations) {
  checkNotNull(returnType, "returnType == null");
  checkNotNull(annotations, "annotations == null");

  int start = adapterFactories.indexOf(skipPast) + 1;
  for (int i = start, count = adapterFactories.size(); i < count; i++) {
    //遍历所有的adapterFactories,根据返回类型和注解，查找是否有匹配的CallAdapter
    CallAdapter<?, ?> adapter = adapterFactories.get(i).get(returnType, annotations, this);
    if (adapter != null) {
      //如果存在，则直接返回
      return adapter;
    }
  }
  ...
  //如果找不到，则抛出错误信息
  throw new IllegalArgumentException(builder.toString());
}
```

到现在为止我们对 **ServiceMethod** 的相关功能已经解析完了。汇总一下：

 **ServiceMethod** 通过传入的 Retrofit和Method，对方法进行全面解析，包括路径、参数、返回类型、以及对应要使用的CallAdapter等，并将这些数据保存到ServiceMethod类中。

我们回到调用的主路线，当生成 **ServiceMethod** 对象以后，生成了 **OkHttpCall** 对象。OkHttpCall是实现了 **Call** 接口的，也是真正发送Http请求的类，当进行Http请求时，**OkHttpCall** 会根据里面的 **ServiceMethod** 来组装请求数据

```java
private okhttp3.Call createRawCall() throws IOException {
    //调用serviceMethod的toRequest方法，将相关的数据组装为Request请求参数
    Request request = serviceMethod.toRequest(args);
    //封装为okhttp里面的Call对象，然后调用okhttp里面的execute()或者enqueue()方法即可。
    okhttp3.Call call = serviceMethod.callFactory.newCall(request);
    if (call == null) {
      throw new NullPointerException("Call.Factory returned null.");
    }
    return call;
  }
```
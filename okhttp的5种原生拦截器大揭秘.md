## Okhttp原生拦截器大揭秘

### 前言

在[OkHttp的源码解析篇](https://mp.weixin.qq.com/s/DiZyEM77diQhKonoSCjrcQ)中，我们大体讲解了一下okhttp的整个调用流程，而且也知道了是采用责任链模式来一点点的处理各种拦截器的。而其中有5种自带的拦截器，我们并没有去进行详细的剖析。

其实每次我们去使用okhttp的时候，只是根据实际的需要封装自己的拦截器处理，对于如何实现网络的连接、数据的传输、重试、缓存等都是5大原生拦截器来进行处理的。

### 源码剖析

我们回顾一下之前使用拦截器的那部分代码

```java
    //RealCall.java
	Response getResponseWithInterceptorChain() throws IOException {
        List<Interceptor> interceptors = new ArrayList<>();
        //应用设置的拦截器，由用户来设置
        interceptors.addAll(client.interceptors());
        //重试或者重定向拦截器
        interceptors.add(new RetryAndFollowUpInterceptor(client));
        //桥接拦截器，主要对request和response的header进行一些默认的设置
        interceptors.add(new BridgeInterceptor(client.cookieJar()));
        //缓存拦截器
        interceptors.add(new CacheInterceptor(client.internalCache()));
        //连接使用的拦截器
        interceptors.add(new ConnectInterceptor(client));
        if (!forWebSocket) {
            //如果使用的是websocket的话，增加客户端的networkInterceptors
            interceptors.addAll(client.networkInterceptors());
        }
        //调用服务器拦截器，
        interceptors.add(new CallServerInterceptor(forWebSocket));
```

对于拦截器的调用，是从前往后来进行的。然后返回的结果则是从后往前来进行的。所以我们这按照顺序来进行一个个解析。

#### RetryAndFollowUpInterceptor

重试和重定向拦截器。见名思意，这个拦截器的主要功能是进行重试或者对重定向的返回信息进行处理的。

##### 基础知识

重定向：即服务端返回重定向错误码，并且会返回需要重定向的url地址，客户端需要请求返回的这个新的地址信息才能获取到想要的Response信息。

重定向错误码：

* 308、307 ：只能对GET和HEAD请求进行重定向
* 300、301、302、303：都可以

##### 源码解读

之前的解析中我们也讲解过，对于拦截器的处理，主要工作都是在**intercept**中

```java
    public Response intercept(Chain chain) throws IOException {
		//获取请求。这里的请求已经不是最初始的request了，而是经过了前面的层层封装处理之后的request信息
        Request request = chain.request();
        RealInterceptorChain realChain = (RealInterceptorChain) chain;
        //OkHttp的网络层，连接啥的都是他干的
        Transmitter transmitter = realChain.transmitter();
        int followUpCount = 0;
        Response priorResponse = null;
        while (true) {
        	...   
        }

```

从整体的框架来看，这里会一直进行循环处理。也就是如果满足了重试或者重定向的条件的话，方法内的循环就会一直执行。

我们看一下内部的处理。

```java
            //标记是否执行过请求
            boolean success = false;
            try {
                //执行责任链的proceed方法，其实是下一个拦截器的intercept方法
                response = realChain.proceed(request, transmitter, null);
                success = true;
            } catch (RouteException e) {
                //如果通过某个route连接失败则根据具体的情况尝试恢复。
                if (!recover(e.getLastConnectException(), transmitter, false, request)) {
                    throw e.getFirstConnectException();
                }
                continue;
            } catch (IOException e) {
                //如果发生IO异常，则根据具体的情况尝试恢复。
                boolean requestSendStarted = !(e instanceof ConnectionShutdownException);
                if (!recover(e, transmitter, requestSendStarted, request)) throw e;
                continue;
            } finally {
                if (!success) {
                    //释放资源
                    transmitter.exchangeDoneDueToException();
                }
            }
```

这里可以看到，会调用后续的拦截器，如果抛出了异常的话，会通过**recover**函数判断发生的异常是否满足重试条件，不满足的话，会抛出异常，从而跳出循环，那么网络请求到此也就停止了。

```java
    //判断发生的异常情况是否需要尝试恢复。只有当请求体被缓冲或者请求体在请求发送之前发生故障时，才可以恢复带有请求体的请求。
    // true: 可以尝试进行恢复，false:不允许恢复
    private boolean recover(IOException e, Transmitter transmitter, boolean requestSendStarted, Request userRequest) {
        //如果应用层设置了不允许失败后重试，则返回false
        if (!client.retryOnConnectionFailure()) return false;
        //如果请求体已经开始发送了，但是请求体只允许写一次，则返回false
        if (requestSendStarted && requestIsOneShot(e, userRequest)) return false;
        //异常是致命的 返回false
        if (!isRecoverable(e, requestSendStarted)) return false;
        //没有更多的route可供尝试 返回fasle
        if (!transmitter.canRetry()) return false;
        return true;
    }
```

这里设置了不允许恢复的几种情况：

* 应用设置了不允许失败后重试。即设置了builder.retryOnConnectionFailure(false)
* 发送了请求，而且request请求体设置了只能使用一次。
* 发生了致命异常。
* 连接不允许进行重试。

这4种以外的情况都会进行恢复，然后进行后面的流程。

```java
            //如果发生过重定向的话，会将重定向的信息priorResponse，封装到本次的response中。保证用户知道发生了重定向
            if (priorResponse != null) {
                response = response.newBuilder()
                        .priorResponse(priorResponse.newBuilder()
                                .body(null)
                                .build())
                        .build();
            }
```

这里会对返回的信息封装一个priorResponse，能够使用户知道发生了重定向。

那么什么时候会进行重定向呢？

```java
            //判断返回的信息能否进行重试或者重定向， 如果是可以，会生成新的请求信息，
            // 重定向：url指向了新的地址，body信息则根据实际情况进行处理，header也会根据需要进行移除
            // 重试：使用原来的请求信息
            Request followUp = followUpRequest(response, route);
            //如果重定向请求为空，则返回本次的response
            if (followUp == null) {
                if (exchange != null && exchange.isDuplex()) {
                    transmitter.timeoutEarlyExit();
                }
                return response;
            }
```

**followUpRequest()**函数会对本次的返回信息进行解析。然后尝试生成一个新的请求(重试或者重定向的请求)。如果生成失败，则网络请求结束，返回本次的response信息。

```java
   //根据返回的response生成请求
    private Request followUpRequest(Response userResponse, @Nullable Route route) throws IOException {
        if (userResponse == null) throw new IllegalStateException();
        //返回的网络码
        int responseCode = userResponse.code();
        //请求的类型（POST，GET，HEAD，DELETE）
        final String method = userResponse.request().method();
        switch (responseCode) {
            case HTTP_PROXY_AUTH://407错误码
                Proxy selectedProxy = route != null
                        ? route.proxy()
                        : client.proxy();
                if (selectedProxy.type() != Proxy.Type.HTTP) {//如果没有使用代理，但是返回了407，那么直接抛出异常
                    throw new ProtocolException("Received HTTP_PROXY_AUTH (407) code while not using proxy");
                }
                return client.proxyAuthenticator().authenticate(route, userResponse);//代理验证

            case HTTP_UNAUTHORIZED:
                return client.authenticator().authenticate(route, userResponse);//身份认证

            case HTTP_PERM_REDIRECT://308错误码  重定向
            case HTTP_TEMP_REDIRECT://307错误码  临时重定向
                if (!method.equals("GET") && !method.equals("HEAD")) {//307、308 两种code不对 GET、HEAD 以外的请求重定向
                    return null;
                }
            case HTTP_MULT_CHOICE://300
            case HTTP_MOVED_PERM://301
            case HTTP_MOVED_TEMP://302
            case HTTP_SEE_OTHER://303
                //如果客户端不允许重定向，则直接返回失败。
                if (!client.followRedirects()) return null;
                //获取header中的Location重定向的url信息
                String location = userResponse.header("Location");
                if (location == null) return null;
                //校验返回的url地址的合法性
                HttpUrl url = userResponse.request().url().resolve(location);
                if (url == null) return null;
                //如果配置了不允许SSL(https)和non-SSL(http)之间重定向，则返回空
                boolean sameScheme = url.scheme().equals(userResponse.request().url().scheme());
                //如果不允许followSslRedirects重定向，但是重定向的地址是ssl
                if (!sameScheme && !client.followSslRedirects()) return null;
                //大部分的重定向是没有requestbody的，所以需要使用原来的请求中的request信息
                Request.Builder requestBuilder = userResponse.request().newBuilder();
                if (HttpMethod.permitsRequestBody(method)) {//如果原来的请求支持requestbody
                    //是否带body进行重定向
                    final boolean maintainBody = HttpMethod.redirectsWithBody(method);
                    if (HttpMethod.redirectsToGet(method)) {
                        requestBuilder.method("GET", null);
                    } else {
                        RequestBody requestBody = maintainBody ? userResponse.request().body() : null;
                        requestBuilder.method(method, requestBody);
                    }
                    if (!maintainBody) {//移除原来请求中的header信息
                        requestBuilder.removeHeader("Transfer-Encoding");
                        requestBuilder.removeHeader("Content-Length");
                        requestBuilder.removeHeader("Content-Type");
                    }
                }
                //跨域进行重定向的时候，移除所有的认证信息
                if (!sameConnection(userResponse.request().url(), url)) {
                    requestBuilder.removeHeader("Authorization");
                }
                return requestBuilder.url(url).build();
            case HTTP_CLIENT_TIMEOUT://408 实际很少用到，一般需要重复发送一个相同的请求。一般在HA中可能会用到。这种情况，直接使用原来的url以及body即可
                ...
                return userResponse.request();
            case HTTP_UNAVAILABLE:
                if (userResponse.priorResponse() != null
                        && userResponse.priorResponse().code() == HTTP_UNAVAILABLE) {
                    //priorResponse不为空，代表已经重试过一次了。不再进行重试
                    return null;
                }
                if (retryAfter(userResponse, Integer.MAX_VALUE) == 0) {
                    return userResponse.request();
                }
                return null;
            default:
                return null;
        }
    }

```

这段代码对于不同的情况进行了处理。

1. 如果是407错误码：代理身份验证错误，会使用代理请求进行尝试。
2. 如果是401权限错误，会使用身份验证请求进行尝试。
3. 如果是重定向错误码，校验返回的header中的**Location**字段、校验其url的合法性、校验是否跨域等。如果满足则进行重定向。
4. 408错误码：直接使用原来的请求信息。如果返回的信息设置的Retry-After>0，则不再重试。
5. 503错误码：会进行一次重试，如果第二次仍然失败，则不再重试。

当函数返回null的时候，则表示不会再进行重定向或者重试请求了，会将本次的response返回给客户。

如果函数返回了followUp，则进行后续的校验

```java
            //如果是重定向，则获取返回的重定向的请求body
            RequestBody followUpBody = followUp.body();
            //神秘方法isOneShot是一个可以覆写的方法，如果返回true，那么就消息体最多一次对{@link #writeTo}的调用，并且可以最多一次传输
            if (followUpBody != null && followUpBody.isOneShot()) {
                return response;
            }
            //关闭
            closeQuietly(response.body());
            if (transmitter.hasExchange()) {
                exchange.detachWithViolence();
            }
            //重定向次数不能超过设置的上限（20次）
            if (++followUpCount > MAX_FOLLOW_UPS) {
                throw new ProtocolException("Too many follow-up requests: " + followUpCount);
            }
            //将请求变化为重定向的那个请求，然后通过while循环去执行
            request = followUp;
            //将本次的返回信息保存到priorResponse中
            priorResponse = response;
        }
```

剩下的这部分代码主要进行了3个处理操作

1. 用户设置的请求体设置了isOneShot()为true，则不再重试
2. 重试次数超过上限，则不再重试
3. 如果满足重试或者重定向条件。将followUp作为下次请求的request，并将本次的返回信息保存到priorResponse中.

##### 总结

重试重定向拦截器的主要功能是根据实际的网络情况判断是否需要进行重试操作，当满足条件的时候，会通过while循环不断的进行尝试。知道不满足条件退出为止。

### BridgeInterceptor

BridgeInterceptor是应用代码到网络代码的一种桥接，是一种两者之间的转化。主要作用点也有2点

* 将用户的请求request，转化为网络请求request
* 将网络的response信息转化为用户的response

##### 基础知识

HTTP消息头，在超文本传输协议的请求和响应消息中，协议头部分的那些组件。用来准确描述正在获取的资源、服务器或者客户端的行为，定义了HTTP事务中的具体操作参数。

##### 源码解读

```java
  @Override public Response intercept(Chain chain) throws IOException {
    //上一层的拦截器传入进来的Request
    Request userRequest = chain.request();
    Request.Builder requestBuilder = userRequest.newBuilder();

    RequestBody body = userRequest.body();
    if (body != null) {
      //如果有requestBody，设置"Content-Type"，"Content-Length"，"Transfer-Encoding"等信息
      MediaType contentType = body.contentType();
      ...
    }
    //设置Host，Connection
    ...
    //标识是否是自动添加的Gzip压缩
    boolean transparentGzip = false;
    //Accept-Encoding，以及Gzip压缩传输。如果增加了这个的话，最后需要对返回的信息进行解压缩处理
    if (userRequest.header("Accept-Encoding") == null && userRequest.header("Range") == null) {
      transparentGzip = true;
      requestBuilder.header("Accept-Encoding", "gzip");
    }
    //设置的Cookie列表
    List<Cookie> cookies = cookieJar.loadForRequest(userRequest.url());
    if (!cookies.isEmpty()) {
      requestBuilder.header("Cookie", cookieHeader(cookies));
    }
    //User-Agent
    if (userRequest.header("User-Agent") == null) {
      requestBuilder.header("User-Agent", Version.userAgent());
    }
    //网络返回信息networkResponse
    Response networkResponse = chain.proceed(requestBuilder.build());

    HttpHeaders.receiveHeaders(cookieJar, userRequest.url(), networkResponse.headers());

    Response.Builder responseBuilder = networkResponse.newBuilder().request(userRequest);

    if (transparentGzip
        && "gzip".equalsIgnoreCase(networkResponse.header("Content-Encoding"))
        && HttpHeaders.hasBody(networkResponse)) {
      //如果代码里面自动使用了gzip压缩，那么需要通过Gzip进行处理，移除对应的Header，将读取的数据进行解压缩
      GzipSource responseBody = new GzipSource(networkResponse.body().source());
      //移除header中的Content-Length和"Content-Encoding
      Headers strippedHeaders = networkResponse.headers().newBuilder()
          .removeAll("Content-Encoding")
          .removeAll("Content-Length")
          .build();
      responseBuilder.headers(strippedHeaders);
      String contentType = networkResponse.header("Content-Type");
      responseBuilder.body(new RealResponseBody(contentType, -1L, Okio.buffer(responseBody)));
    }

    return responseBuilder.build();
  }
```

这个拦截器的主要作用是增加了一些网络使用的Header信息。有个比较特殊的地方就是gzip。如果是拦截器增加的gzip的话，这里需要对返回的信息进行解压缩处理。

#### CacheInterceptor

OkHttp自带了缓存，当我们开启了缓存的话，就会根据相关设置将获取到的返回信息进行缓存处理

##### 基础知识

http缓存根据是否需要重新向服务器发起请求来分类，可以将其分为两大类(强制缓存，对比缓存)，强制缓存如果生效，不需要再和服务器发生交互，而对比缓存不管是否生效，都需要与服务端发生交互。
两类缓存规则可以同时存在，强制缓存优先级高于对比缓存，也就是说，当执行强制缓存的规则时，如果缓存生效，直接使用缓存，不再执行对比缓存规则。

强制缓存：在返回数据的header中会有两个字段来标明失效规则（Expires/Cache-Control），指的是当前资源的有效期。

对比缓存：当第一次请求服务器的时候，服务器会将缓存标识与数据一起返回给客户端，客户端将二者进行缓存处理。
再次请求数据时，客户端将备份的缓存标识发送给服务器，服务器根据缓存标识进行判断，判断成功后，返回304状态码，通知客户端比较成功，可以使用缓存数据。否则需要重新请求。

##### 源码解读

```java
    public Response intercept(Chain chain) throws IOException {
        //如果cache存在，则尝试根据request获取缓存
        Response cacheCandidate = cache != null ? cache.get(chain.request()) : null;
        long now = System.currentTimeMillis();
        //构造缓存策略
        CacheStrategy strategy = new CacheStrategy.Factory(now, chain.request(), cacheCandidate).get();
        //networkRequest 为null，说明是强制缓存，直接就使用本地缓存了
        Request networkRequest = strategy.networkRequest;
        //该请求上次缓存的结果
        Response cacheResponse = strategy.cacheResponse;

```

这里首先会根据请求从缓存中获取上次请求返回的信息。然后创建了一个缓存策略类。

我们看一下缓存策略类中的两个变量

```java
  //networkRequest 为null，说明是强制缓存，直接就使用本地缓存了
  public final @Nullable Request networkRequest;

  //请求上次缓存的结果,为null说明没有缓存信息
  public final @Nullable Response cacheResponse;
```

我们看一下这个缓存策略的最中生成方法，也就是get()。

```java
    private CacheStrategy getCandidate() {
      //首先确定了4种必须进行网络请求的情况，不会使用缓存
      //没有缓存结果
      if (cacheResponse == null) {
        return new CacheStrategy(request, null);
      }
      //如果缺少必要的握手，那么就清除掉缓存
      if (request.isHttps() && cacheResponse.handshake() == null) {
        return new CacheStrategy(request, null);
      }
      //不允许使用缓存
      if (!isCacheable(cacheResponse, request)) {
        return new CacheStrategy(request, null);
      }
      //request没有设置Cachecontrol的
      CacheControl requestCaching = request.cacheControl();
      if (requestCaching.noCache() || hasConditions(request)) {
        return new CacheStrategy(request, null);
      }
        
      //上面的情况都是需要去使用网络请求的。而具体的缓存策略主要是下面的部分。
      //获取上次返回的数据的CacheControl信息
      CacheControl responseCaching = cacheResponse.cacheControl();
      ...
      //如果设置了使用缓存，而且缓存未过期，则直接强制使用本地缓存
      if (!responseCaching.noCache() && ageMillis + minFreshMillis < freshMillis + maxStaleMillis) {
        Response.Builder builder = cacheResponse.newBuilder();
        if (ageMillis + minFreshMillis >= freshMillis) {
          builder.addHeader("Warning", "110 HttpURLConnection \"Response is stale\"");
        }
        long oneDayMillis = 24 * 60 * 60 * 1000L;
        if (ageMillis > oneDayMillis && isFreshnessLifetimeHeuristic()) {
          builder.addHeader("Warning", "113 HttpURLConnection \"Heuristic expiration\"");
        }
        //这了里是设置强制使用本地缓存
        return new CacheStrategy(null, builder.build());
      }

      // Find a condition to add to the request. If the condition is satisfied, the response body
      // will not be transmitted.
      String conditionName;
      String conditionValue;
      if (etag != null) {
        conditionName = "If-None-Match";
        conditionValue = etag;
      } else if (lastModified != null) {
        conditionName = "If-Modified-Since";
        conditionValue = lastModifiedString;
      } else if (servedDate != null) {
        conditionName = "If-Modified-Since";
        conditionValue = servedDateString;
      } else {
        return new CacheStrategy(request, null); // No condition! Make a regular request.
      }

      Headers.Builder conditionalRequestHeaders = request.headers().newBuilder();
      Internal.instance.addLenient(conditionalRequestHeaders, conditionName, conditionValue);

      Request conditionalRequest = request.newBuilder().headers(conditionalRequestHeaders.build()).build();
      return new CacheStrategy(conditionalRequest, cacheResponse);
    }
```

这个里面使用的缓存策略实际上是依赖于刚才所讲的基础知识的。

首先是4中不使用缓存的情况：

* 没有缓存
* HTTPS请求而且缺少必要的握手
* 上次换回的信息设置了不允许缓存
* 客户端的request设置了不允许使用缓存

强制使用缓存的情况：

* 上次返回的信息设置了过期时间，而本次请求并未过期。

后面的情况就是根据缓存的数据和本次请求的具体情况来判断是否使用缓存了。

到这里为止，缓存策略CacheStrategy创建完成。那么剩下的就是依据缓存策略来进行数据的处理了。

```java
        //如果设置禁止从网络获取响应 且 缓存不可用 那么返回504失败
        if (networkRequest == null && cacheResponse == null) {
            //networkRequest==null，表明是强制使用本地缓存。CacheResponse==null，表示没有缓存结果。这里就会自己创建一个返回信息，错误码是504
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(504)
                    .message("Unsatisfiable Request (only-if-cached)")
                    .body(Util.EMPTY_RESPONSE)
                    .sentRequestAtMillis(-1L)
                    .receivedResponseAtMillis(System.currentTimeMillis())
                    .build();
        }

        //这个分支表示，使用了强制本地缓存，而且存在着上次的缓存结果。那么就将上次的缓存结果进行返回
        if (networkRequest == null) {
            return cacheResponse.newBuilder()
                    .cacheResponse(stripBody(cacheResponse))
                    .build();
        }
        //从这里往后，证明走的是对比缓存，需要请求网络，如果返回的是304错误码，则使用原来的缓存结果，否则就使用网络请求的结果
        Response networkResponse = null;
        try {
            //获取网络返回数据
            networkResponse = chain.proceed(networkRequest);
        } finally {
            if (networkResponse == null && cacheCandidate != null) {
                closeQuietly(cacheCandidate.body());
            }
        }
        //缓存非空
        if (cacheResponse != null) {
            //从网络获取respone，如果返回码为304 则使用本地的缓存结果，但是会调整对应的一些header，请求的时间等相关参数
            if (networkResponse.code() == HTTP_NOT_MODIFIED) {
                Response response = cacheResponse.newBuilder()
                        .headers(combine(cacheResponse.headers(), networkResponse.headers()))
                        .sentRequestAtMillis(networkResponse.sentRequestAtMillis())
                        .receivedResponseAtMillis(networkResponse.receivedResponseAtMillis())
                        .cacheResponse(stripBody(cacheResponse))
                        .networkResponse(stripBody(networkResponse))
                        .build();
                networkResponse.body().close();
                cache.trackConditionalCacheHit();
                //更新缓存
                cache.update(cacheResponse, response);
                return response;
            } else {
                closeQuietly(cacheResponse.body());
            }
        }
        //走到这里说明没有缓存可以使用，或者对比缓存不通过，表示缓存失效了。需要使用网络返回的response信息
        Response response = networkResponse.newBuilder()
                .cacheResponse(stripBody(cacheResponse))
                .networkResponse(stripBody(networkResponse))
                .build();
    }

```

这个大的方法体依据生成的策略来进行数据的返回处理

1. 强制缓存、没有本地缓存：返回504错误
2. 强制缓存、存在本地缓存：将使用缓存
3. 进行网络请求
4. 如果返回码是304，证明对比缓存通过，使用缓存
5. 剩下的情况则使用网络返回的数据。

在最后一部中则会将返回的数据进行缓存。

```java
        if (cache != null) {
            //cache不为空,则将最新的response缓存到cache
            if (HttpHeaders.hasBody(response) && CacheStrategy.isCacheable(response, networkRequest)) {
                // Offer this request to the cache.
                //进行缓存，这里的put只会缓存对应的header
                CacheRequest cacheRequest = cache.put(response);
                //这里会将response的body信息也缓存到对应的文件中。
                // 由于body是以流的形式读取的，不像Header可以一次性写入，所以body的缓存必然是在读取的时候，一边从流里读，一边缓存到文件。
                // 由于流只能读一次，如果把流里面的内容都读出来返回给app调用层,就没办法重新读一遍缓存到文件中了，所以需要把流内容拷贝，这也是为什么要返回通过cacheWritingResponse()方法处理过后的Response的原因。
                return cacheWritingResponse(cacheRequest, response);
            }
            if (HttpMethod.invalidatesCache(networkRequest.method())) {
                try {
                    cache.remove(networkRequest);
                } catch (IOException ignored) {
                }
            }
        }
        return response;
```
这里可以看到，如果支持本地缓存的话，会将网络返回的数据通过**cacheWritingResponse()**进行一层处理。这里面会将返回的数据缓存到本地。具体的缓存方法后续再研究。这里先到此为止了。

#### ConnectInterceptor

连接拦截器的主要功能是建立和服务器端的类连接工作。

##### 基础知识

这里面涉及到的几个比较重要的类。我们先从他们开始入手

**RealConnectio连接类。**

这个类是连接的真正实现，内部通过socket建立和服务器之间的连接工作。

```java
public final class RealConnection extends Http2Connection.Listener implements Connection {
    //连接使用的Route
    //这个连接使用的socket。
    private Socket socket;
    //如果是https，这里表示的握手信息
    private Handshake handshake;
    //应用协议
    private Protocol protocol;
    //http2连接
    private Http2Connection http2Connection;
    //okio库的buffersource，相当于Java的IO的输入输入输出流
    private BufferedSource source;
    private BufferedSink sink;
```

我们关注一下里面的connect方法，这个方法就是实现连接的

```java
    //进行连接
    public void connect(int connectTimeout, int readTimeout, int writeTimeout,
                        int pingIntervalMillis, boolean connectionRetryEnabled, Call call,
                        EventListener eventListener) {
        ...
        //尝试连接
        while (true) {
            try {
                if (route.requiresTunnel()) {//通道连接
                    connectTunnel(connectTimeout, readTimeout, writeTimeout, call, eventListener);
                    if (rawSocket == null) {
                        break;
                    }
                } else {//socket连接
                    connectSocket(connectTimeout, readTimeout, call, eventListener);
                }
                //建立http连接
                establishProtocol(connectionSpecSelector, pingIntervalMillis, call, eventListener);
                eventListener.connectEnd(call, route.socketAddress(), route.proxy(), protocol);
                break;
            } catch (IOException e) {
                //异常的处理
                ...
            }
        }
        ...
    }
```

大部分使用的是socket连接，所以这里我们只关注**connectSocket**方法即可。

```java
    private void connectSocket(int connectTimeout, int readTimeout, Call call,
                               EventListener eventListener) throws IOException {
        Proxy proxy = route.proxy();
        Address address = route.address();

        rawSocket = proxy.type() == Proxy.Type.DIRECT || proxy.type() == Proxy.Type.HTTP
                ? address.socketFactory().createSocket()
                : new Socket(proxy);
        eventListener.connectStart(call, route.socketAddress(), proxy);
        rawSocket.setSoTimeout(readTimeout);
        try {
            //Platform.get()会根据具体的平台返回。属于工厂模式？
            //connectSocket实际是调用rawSocket.connect创建连接
            Platform.get().connectSocket(rawSocket, route.socketAddress(), connectTimeout);
        } catch (ConnectException e) {
            ...
        }
        try {
            //获取连接的输入输出流
            source = Okio.buffer(Okio.source(rawSocket));
            sink = Okio.buffer(Okio.sink(rawSocket));
       ...
    }
```

Platform.get()会根据不同的平台返回具体的实现类。当我们在安卓中使用的时候，返回的是AndroidPlatform对象。

```java
//AndroidPlatform.java
public void connectSocket(Socket socket, InetSocketAddress address,
                          int connectTimeout) throws IOException {
    try {
        socket.connect(address, connectTimeout);
    ...
}
```

所以最终是建立的是socket连接。当建立完socket连接之后，就可以获取输入输出流了。这里使用了Okio对输入输出流进行了一次封装。因为Okio封装之后对于流的操作更加的简单。

**RealConnectionPool连接池**

不管是建立socket连接，还是连接的保持，都是很浪费资源的。对于这种问题，最常见的解决方案就是池技术。Okhttp对于连接的处理就采用了池技术。

```java
//RealConnectionPool.java   
	//最大空闲连接数
    private final int maxIdleConnections;
    //保持连接的时间
    private final long keepAliveDurationNs;
    //保存的连接队列。因为连接比较耗时，所以使用连接池技术
    private final Deque<RealConnection> connections = new ArrayDeque<>();
    final RouteDatabase routeDatabase = new RouteDatabase();
    //是否正在执行连接清理工作
    boolean cleanupRunning;
    //用于清理连接的任务，需要在线程池executor去调用
    //连接使用完毕，长时间不释放，也会造成资源的浪费，所以进行定时的清理
    private final Runnable cleanupRunnable = () -> {
        while (true) {
            //执行连接的清理工作
            long waitNanos = cleanup(System.nanoTime());
            if (waitNanos == -1) return;
            if (waitNanos > 0) {
                long waitMillis = waitNanos / 1000000L;
                waitNanos -= (waitMillis * 1000000L);
                synchronized (RealConnectionPool.this) {
                    try {
                        //等待多少秒以后再次执行清理
                        RealConnectionPool.this.wait(waitMillis, (int) waitNanos);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
    };
```

RealConnectionPool中维护了一个连接的队列，并且会定期的去执行连接的清理工作。在HTTP中，默认的keepAlive为5分钟。当超过这个时间以后，连接就会进入空闲状态。

这里的定期清理任务**cleanupRunnable**是什么时候开始执行的呢？

```java
    //RealConnectionPool.java   
	void put(RealConnection connection) {
        assert (Thread.holdsLock(this));
        if (!cleanupRunning) {
            cleanupRunning = true;
            executor.execute(cleanupRunnable);
        }
        connections.add(connection);
    }
```

当我们放入第一个连接之后，会通过线程池启动清理任务。清理任务中的**cleanup**会返回下一次清理的时间，然后线程就会等待直到下一次清理时间的到来，通过while循环再次执行清理工作，周而复始的工作。当执行了cleanup以后，如果发现已经没有连接了，那么就跳出循环，不再执行清理任务，直到下次再通过put方法添加了连接之后，启动清理工作

这里我们看一下cleanup这个函数是如何清理连接的

```java
//RealConnectionPool.java   
//主要是在线程池中执行。如果连接超过了keep alive或者空闲连接数。那么就会关闭连接
//返回下一次要清理的时间
long cleanup(long now) {
    int inUseConnectionCount = 0;
    int idleConnectionCount = 0;
    RealConnection longestIdleConnection = null;
    long longestIdleDurationNs = Long.MIN_VALUE;
    synchronized (this) {
        for (Iterator<RealConnection> i = connections.iterator(); i.hasNext(); ) {
            RealConnection connection = i.next();
            //当前连接正在使用，直接跳过
            if (pruneAndGetAllocationCount(connection, now) > 0) {
                //正在使用的线程数+1
                inUseConnectionCount++;
                continue;
            }
            //空闲连接数+1
            idleConnectionCount++;
            //记录keepalive时间最长的那个空闲连接
            long idleDurationNs = now - connection.idleAtNanos;
            if (idleDurationNs > longestIdleDurationNs) {
                longestIdleDurationNs = idleDurationNs;
                longestIdleConnection = connection;
            }
        }
        //超过了最大连接时长 或者 最大空闲连接数超了。则移除空闲最长的那个连接
        if (longestIdleDurationNs >= this.keepAliveDurationNs || idleConnectionCount > this.maxIdleConnections) {
            connections.remove(longestIdleConnection);
        } else if (idleConnectionCount > 0) {
            //返回时间最长空闲连接的连接需要清理的时间点
            return keepAliveDurationNs - longestIdleDurationNs;
        } else if (inUseConnectionCount > 0) {
            //所有的连接都处于使用之中。
            return keepAliveDurationNs;
        } else {
            //没有连接
            cleanupRunning = false;
            return -1;
        }
    }
    //走到这里说明移除了空闲时间最长的那个连接。将连接关闭。这里为什么弄出来啊？很好奇
    closeQuietly(longestIdleConnection.socket());
    return 0;
}
```

如果超过了最大连接时长或者超过了最大空闲熟练。就会找到空闲时间最长的那个连接，然后清理掉。否则的话，会计算下一次清理的时间。比如说keepAlive是5分钟，而最大空闲的连接已经空闲了3分钟了。那么就会返回2分钟。

这里如果已经没有连接了，就会返回-1从而终止清理工作。

对于okhttp使用的连接类和连接池类都已经讲解完了。我们回头再去看拦截器，就会发现，so easy~

##### 源码解析

```java
    public Response intercept(Chain chain) throws IOException {
        RealInterceptorChain realChain = (RealInterceptorChain) chain;
        Request request = realChain.request();
        //获取transmitter
        Transmitter transmitter = realChain.transmitter();
        boolean doExtensiveHealthChecks = !request.method().equals("GET");
        //***重点方法**获取一个Exchange，负责从创建的连接的IO流中写入请求和读取响应，完成一次请求/响应的过程
        Exchange exchange = transmitter.newExchange(chain, doExtensiveHealthChecks);

        return realChain.proceed(request, transmitter, exchange);
    }
```

整个代码段比较简短，但是其中的逻辑并不简单。这里唯一需要关注的就是里面的重点方法

```java
    //Transmitter.java
    Exchange newExchange(Interceptor.Chain chain, boolean doExtensiveHealthChecks) {
        ...
        //获取对应的http的编码解码器
        ExchangeCodec codec = exchangeFinder.find(client, chain, doExtensiveHealthChecks);
        //用来进行发送和接收HTTP request和respone。
        Exchange result = new Exchange(this, call, eventListener, exchangeFinder, codec);
        synchronized (connectionPool) {
            this.exchange = result;
            this.exchangeRequestDone = false;
            this.exchangeResponseDone = false;
            return result;
        }
    }
```

我们看一下，如何获取对应的http编码解码器的。

```java
    //ExchangeFinder.java
	public ExchangeCodec find(OkHttpClient client, Interceptor.Chain chain, boolean doExtensiveHealthChecks) {
        int connectTimeout = chain.connectTimeoutMillis();
        int readTimeout = chain.readTimeoutMillis();
        int writeTimeout = chain.writeTimeoutMillis();
        int pingIntervalMillis = client.pingIntervalMillis();
        //客户端设置的失败后是否重连
        boolean connectionRetryEnabled = client.retryOnConnectionFailure();

        try {
            //获取到一个连接
            RealConnection resultConnection = findHealthyConnection(connectTimeout, readTimeout, writeTimeout, pingIntervalMillis, connectionRetryEnabled, doExtensiveHealthChecks);
            //返回一个http的编码解码器
            return resultConnection.newCodec(client, chain);
        } catch (RouteException e) {
            trackFailure();
            throw e;
        } catch (IOException e) {
            trackFailure();
            throw new RouteException(e);
        }
    }

    //获取到一个可用的连接，如果获取到的连接不可用，那么会循环直到获取到结果
    private RealConnection findHealthyConnection(int connectTimeout, int readTimeout,int writeTimeout, int pingIntervalMillis, boolean connectionRetryEnabled,boolean doExtensiveHealthChecks) throws IOException {
        while (true) {
            RealConnection candidate = findConnection(connectTimeout, readTimeout, writeTimeout, pingIntervalMillis, connectionRetryEnabled);
       ...
```

这里最终会调用**findConnection**方法来获取实际的连接。

```java
    //返回一个持有新的流（即输入输出流）连接。如果连接已存在则从池中获取，否则就创建一个新的连接
    private RealConnection findConnection(int connectTimeout, int readTimeout, int writeTimeout, int pingIntervalMillis, boolean connectionRetryEnabled) throws IOException {
        //标识是否是从池中获取的连接
        boolean foundPooledConnection = false;
        //返回的结果
        RealConnection result = null;
        Route selectedRoute = null;
        //之前创建的连接
        RealConnection releasedConnection;
        Socket toClose;
        synchronized (connectionPool) {
            //已经取消了，则直接抛出异常
            if (transmitter.isCanceled()) throw new IOException("Canceled");
            hasStreamFailure = false;
            //获取已经创建过的连接。
            releasedConnection = transmitter.connection;
            //创建过的连接可能不允许创建新的流。如果不允许创建新的流，则需要将其对应的socket关闭
            toClose = transmitter.connection != null && transmitter.connection.noNewExchanges
                    ? transmitter.releaseConnectionNoEvents()
                    : null;
            if (transmitter.connection != null) {
                //证明之前创建的连接是可以使用的。这里就使用之前的结果。那么连接不需要释放了
                result = transmitter.connection;
                releasedConnection = null;
            }
            if (result == null) {
                //如果连接不可用，则尝试从连接池获取到一个连接
                if (connectionPool.transmitterAcquirePooledConnection(address, transmitter, null, false)) {
                    foundPooledConnection = true;
                    result = transmitter.connection;
                } else if (nextRouteToTry != null) {
                    selectedRoute = nextRouteToTry;
                    nextRouteToTry = null;
                } else if (retryCurrentRoute()) {
                    selectedRoute = transmitter.connection.route();
                }
            }
        }
        //关闭之前的连接
        closeQuietly(toClose);
        if (result != null) {
            //已经找到一个可以使用的连接（之前申请的或者从池中获取到的），则直接返回
            return result;
        }
        //看看是否需要路由选择，多IP操作
        boolean newRouteSelection = false;
        if (selectedRoute == null && (routeSelection == null || !routeSelection.hasNext())) {
            newRouteSelection = true;
            routeSelection = routeSelector.next();
        }

        List<Route> routes = null;
        synchronized (connectionPool) {
            if (transmitter.isCanceled()) throw new IOException("Canceled");
            if (newRouteSelection) {
                //使用多IP，从连接池中尝试获取
                routes = routeSelection.getAll();
                if (connectionPool.transmitterAcquirePooledConnection(address, transmitter, routes, false)) {
                    foundPooledConnection = true;
                    result = transmitter.connection;
                }
            }
            //在连接池没有找到可用的连接
            if (!foundPooledConnection) {
                if (selectedRoute == null) {
                    selectedRoute = routeSelection.next();
                }
                //创建一个新的连接
                result = new RealConnection(connectionPool, selectedRoute);
                connectingConnection = result;
            }
        }
        //连接池中找到了，则直接返回
        if (foundPooledConnection) {
            eventListener.connectionAcquired(call, result);
            return result;
        }
        //进入这里证明是新创建的连接，调用connect方法进行连接。进行TCP+TLS握手。这是一种阻塞操作
        result.connect(connectTimeout, readTimeout, writeTimeout, pingIntervalMillis,connectionRetryEnabled, call, eventListener);
        connectionPool.routeDatabase.connected(result.route());
        Socket socket = null;
        synchronized (connectionPool) {
            ...
                //将连接放入到连接池
                connectionPool.put(result);
                //把刚刚新建的连接保存到Transmitter的connection字段
                transmitter.acquireConnectionNoEvents(result);
            }
        }

        return result;
    }
```

这个findConnection方法是获取连接的核心代码。这里依次尝试上次的连接、连接池、多路由选择等方式去获取，如果仍然获取不到的话，就会创建一个新的连接。整个流程如下：

1. transmitter.connection是否可用？可用的话直接返回
2. 如果不可用，则尝试通过连接池获取连接。如果获取到可用连接，则使用它。
3. 如果仍然无法获取到。则尝试多路由方法从连接池获取。获取成功就使用。
4. 如果获取失败，则创建一个新的连接、TSL握手、将其放入到连接池、将连接赋值给transmitter.connection。

到这里就已经成功 获取到了对应的本次请求的连接了。然后会依据这个连接进创建对应的编码解码其ExchangCodec，以及Exchange对象。

##### 总结

1. okhttp是支持路由的动态配置功能。之前听说过一次某个地方访问服务无法访问，最后通过动态路由来进行处理解决的方案。
2. 当使用HTTPS协议进行了连接的时候需要使用TLS协议
3. 每次创建Call的时候回创建Transmitter，而每一个Call是可以发起多次请求的。
4. 连接池中的连接对于所有的Call都是共享的，所有的call都可以尝试从连接池中获取连接并复用

#### CallServerInterceptor

在 ConnectInterceptor 中，已经创建了与服务器之间的Socket连接。并且通过okio封装了对应的输入输出流。而CallServerInterceptor的作用就是通过建立的输入输出流进行数据的传输工作。

##### 基础知识

在HTTP/1.1协议中，新增了一种“Expect: 100-continue”的头域。这种的头域的目的是，在客户端发送 Request Message 之前，允许客户端先判定服务器是否愿意接受客户端发来的消息主体（基于 Request Headers）。这么做的原因是，如果客户端直接发送请求数据，但是服务器又将该请求拒绝的话，这种行为将带来很大的资源开销。一般对于超过1024字节的使用这种方案。毕竟两次的传输会带来请求延迟的加大。

Expect: 100-continue的进行分为两步：

1. 发送一个请求，包含一个 "Expect: 100-continue" 头域，询问 Server 是否愿意接收数据；

2. 接收到 Server 返回的 100-continue 应答以后（这里会传输空的body体），才把数据 POST 给 Server；

#####  源码解析

```java
//CallServerInterceptor.java
public Response intercept(Chain chain) throws IOException {
  RealInterceptorChain realChain = (RealInterceptorChain) chain;
  Exchange exchange = realChain.exchange();
  //获取请求。这里的请求已经不是最初始的request了，而是经过了前面的层层封装处理之后的request信息
  Request request = realChain.request();
  long sentRequestMillis = System.currentTimeMillis();
  //向服务器端写请求头信息
  exchange.writeRequestHeaders(request);
  boolean responseHeadersStarted = false;
  Response.Builder responseBuilder = null;
  if (HttpMethod.permitsRequestBody(request.method()) && request.body() != null) {
    //用于客户端在发送POST请求数据之前，征询服务器情况，看服务器是否处理POST的数据，如果不处理，客户端则不上传POST数据，如果处理，则POST上传数据。
    if ("100-continue".equalsIgnoreCase(request.header("Expect"))) {
      //判断服务器是否允许发送body
      exchange.flushRequest();
      responseHeadersStarted = true;
      //获取返回的头信息
      exchange.responseHeadersStart();
      //读取返回的头信息，如果服务器接收RequestBody，会返回null
      responseBuilder = exchange.readResponseHeaders(true);
    }
    //如果RequestBuilder为null，说明Expect不为100-continue或者服务器同意接收RequestBody
    if (responseBuilder == null) {
      //向服务器发送body
      ...
        BufferedSink bufferedRequestBody = Okio.buffer(exchange.createRequestBody(request, false));
        //写入body体
        request.body().writeTo(bufferedRequestBody);
        bufferedRequestBody.close();
      }
    } 
    ..
  //读取相应的header
  if (responseBuilder == null) {
    responseBuilder = exchange.readResponseHeaders(false);
  }
  //通过Builder模式构造返回response
  Response response = responseBuilder
      .request(request)
      .handshake(exchange.connection().handshake())
      .sentRequestAtMillis(sentRequestMillis)
      .receivedResponseAtMillis(System.currentTimeMillis())
      .build();

  int code = response.code();
  if (code == 100) {
    //100的状态码的处理继续发送请求，继续接收数据
    response = exchange.readResponseHeaders(false)
        .request(request)
        .handshake(exchange.connection().handshake())
        .sentRequestAtMillis(sentRequestMillis)
        .receivedResponseAtMillis(System.currentTimeMillis())
        .build();
    code = response.code();
  }

  exchange.responseHeadersEnd(response);
  ...
    //获取返回的body体
    response = response.newBuilder()
        .body(exchange.openResponseBody(response))
        .build();
  }
  ...
  //返回为空的数据处理
  if ((code == 204 || code == 205) && response.body().contentLength() > 0) {
    throw new ProtocolException("HTTP " + code + " had non-zero Content-Length: " + response.body().contentLength());
  }
  return response;
}
```

这个拦截器的作用主要是根据实际的需要进行数据的输入和读取。大体的流程如下：

1. 向服务器写入请求头。
2. 如果请求头中有"Expect: 100-continue"，判断服务器是否允许发送Body。如果允许则返回null。
3. 根据2的判断，如果RequestBody为null，则写入body体。
4. 通过构造者模式构造返回的response信息。
5. 针对204/205状态码处理。
6. 返回Response。

**写请求头**

```java
    //Exchange.java
	//写请求头
    public void writeRequestHeaders(Request request) throws IOException {
        try {
            //注册的监听事件
            eventListener.requestHeadersStart(call);
            //写入请求头
            codec.writeRequestHeaders(request);
            eventListener.requestHeadersEnd(call, request);
        } catch (IOException e) {
            eventListener.requestFailed(call, e);
            trackFailure(e);
            throw e;
        }
    //Http1ExchangeCodec.java    
    public void writeRequestHeaders(Request request) throws IOException {
        //返回请求的地址以及协议版本号：HTTP/1.1
        String requestLine = RequestLine.get(request, realConnection.route().proxy().type());
        //写请求头
        writeRequest(request.headers(), requestLine);
    }
        
    public void writeRequest(Headers headers, String requestLine) throws IOException {
        if (state != STATE_IDLE) throw new IllegalStateException("state: " + state);
        //换行
        sink.writeUtf8(requestLine).writeUtf8("\r\n");
        //遍历消息头，并按照 name:value的形式写入
        for (int i = 0, size = headers.size(); i < size; i++) {
            sink.writeUtf8(headers.name(i))
                    .writeUtf8(": ")
                    .writeUtf8(headers.value(i))
                    .writeUtf8("\r\n");
        }
        sink.writeUtf8("\r\n");
        state = STATE_OPEN_REQUEST_BODY;
    }
```

这里会将请求的消息头按照一定的格式发送给服务器端。

**读请求头**

对于HTTP的返回头信息而言，第一条的信息表示是HTTP的版本号，以及对应的返回状态码和协议信息。后面的才是其他相关的头信息。

```java
    //Exchange.java
    Response.Builder readResponseHeaders(boolean expectContinue) throws IOException {
        //重点方法
            Response.Builder result = codec.readResponseHeaders(expectContinue);
            ...
    }
    //Http1ExchangeCodec.java
    public Response.Builder readResponseHeaders(boolean expectContinue) throws IOException {
        ...
            //读取第一行Header信息，然后通过parse进行转化。
            StatusLine statusLine = StatusLine.parse(readHeaderLine());
            Response.Builder responseBuilder = new Response.Builder()
                    .protocol(statusLine.protocol)
                    .code(statusLine.code)
                    .message(statusLine.message)
                    //读取剩余的其他所有的头信息，并设置到response的header中
                    .headers(readHeaders());
```

这里看一下**readHeaderLine**是如何读取一行头信息的

```java
//Http1ExchangeCodec.java
	private long headerLimit = HEADER_LIMIT;
    //消息头最多有256k个字节
    private static final int HEADER_LIMIT = 256 * 1024;
	//按行读取Header信息
    private String readHeaderLine() throws IOException {
        //使用了okio的BufferedSource来读取一行数据，headerLimit是我们的最大读取的字符数
        String line = source.readUtf8LineStrict(headerLimit);
        //header的字符限制数减少
        headerLimit -= line.length();
        return line;
    }
```

这里面使用了okio封装的数据流来读取了一行数据。由于在数据传输中，Header中能够传输的最大传输字符是有限制的。所以每次读取以后，都会将计算一下还可以读取多少个字节信息。

HTTP返回的Header信息中，第一行数据标识的是状态的信息，会通过**StatusLine.parse**来解析。

```java
  public static StatusLine parse(String statusLine) throws IOException {
    // H T T P / 1 . 1   2 0 0   T e m p o r a r y   R e d i r e c t
    // 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0
    int codeStart;
    //协议版本号
    Protocol protocol;
    
    if (statusLine.startsWith("HTTP/1.")) {//HTTP协议类型
      ...
    } else if (statusLine.startsWith("ICY ")) {//ICY协议类型，使用HTTP1.0来处理
      ...
    } else {//其他的不支持
      throw new ProtocolException("Unexpected status line: " + statusLine);
    }
    //返回的状态码信息  200，301，302等等，都是4位的数字
    int code;
    try {
      code = Integer.parseInt(statusLine.substring(codeStart, codeStart + 3));
    } catch (NumberFormatException e) {
      throw new ProtocolException("Unexpected status line: " + statusLine);
    }
    //协议后面的信息
    String message = "";
    if (statusLine.length() > codeStart + 3) {
      ...
    }
    return new StatusLine(protocol, code, message);
  }

```

这个里面会严格按照格式来解析对应的协议类型，状态码以及协议的信息等。

而其他的Header信息则以如下的方式进行传输。

![image-20200529114827491](http://cdn.qiniu.kailaisii.com/typora/202005/29/114829-52705.png)

##### 总结

1. 消息头最多有256*1024个字符。
2. 消息头的第一行是协议以及状态等相关信息
3. 消息头是按照 name:value的样式来处理的

### 大总结

1. okhttp的拦截器具体的职责分工十分明确。不同的拦截器只负责自己的业务处理。而责任链模式能够保证整个流程的递归执行。这种模式去处理我们常用的if else的魔鬼分支处理其实是一种很好的解决方案。

2. 对于okhttp，可以注册一个EventListener，来监听所有的内部过程的回调信息。

项目注释源地址:[okhttp_source](https://github.com/kailaisi/okhttp_source-3.14.git)



> 本文由 [开了肯](http://www.kailaisii.com/) 发布！ 
>
> 同步公众号[开了肯]

![image-20200404120045271](http://cdn.qiniu.kailaisii.com/typora/20200404120045-194693.png)
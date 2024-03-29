## 透视HTTP协议

### HTTP是什么？

HTTP协议是     **超文本**      **传输**      **协议**

* ##### **协议**

HTTP是用在计算机世界里的协议。它使用计算机能够理解的语言确立了一种计算机之间交流通信的规范，以及相关的各种控制和错误处理方式。

* ##### **传输**

HTTP是一个在计算机世界里专门用来在两点之间传输数据的协定和规范。

* ##### **超文本**

超文本就是超越了普通的文本的文本，它包含了文字、图片、音频和视频等的混合体。

**所以HTTP就是一个在计算机世界里专门在两点之间传输文字、图片、音频和视频等超文本数据的约定和协议。**

HTTP协议是跑在TCP/IP协议栈之上的，依靠IP协议实现寻址和路由、TCP协议实现可靠地数据传输、DNS协议实现域名查找解析、SSL/TLS协议实现安全的通信机制。

### HTTP相关协议

#### TCP/IP

帮助实现寻址、路由、可靠传输等。

#### DNS

使用IP地址来标识计算机。然后通过有意义的名字作为ip地址的等价替代。从而实现寻址。

#### URI/URL

DNS和IP地址只是标记了互联网上的主机，但是主机上有文本、图片、页面等等。需要使用URI（统一资源标识符）来唯一的标记互联网上的资源。

URL（统一资源定位符），也就是网址。它是URI的一个自己。

#### HTTPS

https其实相当于SSL+HTTP。也就是运行在SSL/TLS协议上的HTTP。这里的HTTP不再是之前所说的的TCP/IP之上的协议了。而是说HTTP运行在SSL协议之上，而SSL协议运行在TCP/IP协议之上。

所以**HTTPS=HTTP+SSL/TSL+TCP/IP**。

代理是 HTTP 传输过程中的“中转站”，可以实现缓存加速、负载均衡等功能。

### HTTP

#### 报文

HTTP的协议基本流程是“请求-应答”“一发一收”的模式，工作模式非常简单，主要是因为TCP/IP协议负责底层的具体传输工作。

HTTP协议报文规定了组成部分，解析规则，处理策略等。可以在TCP/IP层上实现更灵活丰富的功能，例如连接控制、缓存管理、数据编码、内容协商等等。

HTTP协议的请求报文和响应报文由三部分组成

* 起始行：描述请求或响应的基本信息
* 头部字段集合：使用key-value形式更详细的说明报文
* 消息正文：事件传输的数字。

前两部分又成为“**请求头、响应头**”，消息正文又称为**实体（body）**

####  URI

URI：统一资源定位符。用来唯一的表示资源的位置或者名称。

格式：scheme://           host:port    path    ?query

* scheme：协议名称，例如http、ftp、file等
* host:port：主机名+端口号。如果端口号没有的话，浏览器会默认根据scheme使用默认的端口号。
* path：资源所在位置。必须以 /  开始。

##### URI编码

URI只能使用ASCII码。如果使用其他语言的话，会进行对应的转义。

URI转义简单粗暴：直接把非ASCII码或特殊字符转换为16进制字节值，然后前面加上 一个 %。

#### 状态码

这里说的是**状态码**，而不是**错误码**。也就是说它的含义不仅是错误，更重要的在于表达返回的数据处理的状态。客户端可以根据状态适时的进行处理。比如说继续请求、切换协议、重定向等等。

种类：

1XX：提示信息，表示目前是协议处理的中间状态。

2XX：成功，报文接收并被处理

3XX：重定向，资源位值变动，需要重新发送请求

4XX：客户端错误，请求报文有误，服务器无法处理

5XX：服务器错误，服务器在处理请求的时候发生了错误

#### 特点

**扩展灵活**：能够灵活扩展，通过消息头和消息体，可以进行任何形式的处理。

**可靠传输**：基于TCP的可靠传输协议

**应用层协议**：HTTP凭借着可携带任意头字段和实体数据的报文结构，以及连接控制、缓存代理等方便易用的特性，成为应用层的“明星”协议

**应答：**HTTP协议使用的是**请求-应答通信模式**

**无状态**：HTTP协议是无状态的。不会在服务器里保存一些数据或者标志，记录通信过程中的变化信息。

#### 优缺点

明文传输：协议的报文不实用二进制数据，而是简单可阅读的文本形式。可以通过抓包就可以查看和修改。调试方便。但是可能会被黑客抓包。

不安全：在身份认证和完整性校验方面比较欠缺。这种如果数据被篡改，那么http是无法获知数据是真的还是被修改之后的数据。

性能：“不算差，不够好”。HTTP采用“请求-应答”的模式，可能会导致著名的“队头阻塞”问题：当顺序发送的请求序列中的一个请求因为某种原因被阻塞时，在后面排队的所有请求也一并被阻塞，会导致客户端迟迟收不到数据。

### HTTP数据实体

#### 数据类型和编码

HTTP通过**MIME type**来标记对应的数据类别。然后通过**Encoding type**来定义对应的压缩格式（gzip、deflate、br等）。

##### 数据类型头字段

HTTP通过**MIME type**来标记对应的数据类型。定义了Accept请求头字端和两个Content实体头字段。客户端通过**Accept**头告诉服务器希望接收的数据格式类型。而服务器通过Content头告诉客户端实际发送的数据格式。

```http
Accept: text/html,application/xml,image/webp,image/png
```

accept可以提供多个类型，让服务器有更多的选择余地。

服务器的**Content-Type**头字段能够告诉具体的数据类型。

```http
Content-Type: text/html
Content-Type: image/png
```

**Accept-Encoding**字段标记的是客户端支持的压缩格式，也可以用，来列出多个。同事服务器也可以返回具体的压缩格式。

```http
Accept-Encoding: gzip, deflate, br
Content-Encoding: gzip
```

#### 语言类型和编码

HTTP引入了：语言类型和字符集概念。

**语言类型是人类使用的自然语言**。“type-subtype”形式也可以区分不同区域。举几个例子：en 表示任意的英语，en-US 表示美式英语，en-GB 表示英式英语，而 zh-CN 就表示我们最常使用的汉语。

**字符集是计算机处理的语言**。比如说ASCII、GBK、UTF-8、Unicode等等。

##### 语言类型字段头

Accept-Language字段标记了客户端可理解的自然语言，也允许用“,”做分隔符列出多个类型

```http
Accept-Language: zh-CN, zh, en
```

上面的意思是：最好给我zh-CN的汉语文字，如果没有就用其他汉语方言，如果还没有就给英文。

Content-Language**告诉客户端实体数据使用的实际语言类型

```http
Content-Language: zh-CN
```

字符集在 HTTP 里使用的请求头字段是**Accept-Charset**。而在相应头，没有对应的Content-Charset。而是在**Content-Type**字段的类型后面用“charset==xxx”来表示。

**对于post请求，不能使用Accept-\*来处理。因为post相当于是发送数据给服务器，需要像服务器使用Content-\*一样。来提交对应的数据**

#### 数据压缩

* GZIP压缩（文本压缩）
* 分块传输（用于大文件下载等）
* 范围请求（用于播放视频，然后拖动直接跳转到某个位置开始播放）。需要使用“Content-Range”
* 多段数据：需要使用“**multipart/byteranges**”这个MIME类型

#### 连接

##### 短连接

HTTP之前的0.9、1.0协议是采用最简单的‘请求-应答’方式。建立连接-传输通信-关闭连接。所以称之为**短连接**。

然而，TCP/IP的三次握手，四次挥手。如果每次进行http的通讯，都进行这种握手挥手的操作，那么相当于。三次握手，说句话，四次挥手。**造成了很大的资源浪费**。

##### 长连接

针对短连接的问题，HTTP提出了**长连接**。即：持久连接，保活连接，连接复用。

![image-20200828222335729](http://cdn.qiniu.kailaisii.com/typora/20200828222336-546672.png)

在HTTP1.1中默认开始长连接。不需要特殊头字段指定。也可以在请求头里面使用“Connection: keep-alive”来进行设置。

如果长连接一直占用连接而不发送数据的话，会导致资源浪费，所以这里需要根据需要手动关闭长连接。可以使用**Connection:close** 来关闭。

##### 队头阻塞

##### 性能优化

只要使用“请求-应答”模式，那么队头阻塞就会存在。可以使用“**并发连接**”来进行处理

##### 域名分片

使用多个域名，域名都指向同一个服务器，就可以解决一台机器只能有两三个长连接的问题。

#### Cookie

#### 缓存

缓存是优化系统性能最重要的手段

##### 服务端控制：

服务器返回资源的超时时间：**这个时间是服务器创建报文的时间，而不是客户端接收到报文的时间**

```http
Cache-control:max-age=30
no_store	不允许缓存，秒杀页面最常用
no_cache	可以缓存，但是使用前跟服务器交互去校验是否过期
must-revalidate		如果缓存不过期则继续使用，过期的话就必须去服务器验证
```

只有一个“Expires”标识，能够标识过期时间，优先级比cache-contro优先级低

##### 浏览器控制

也可以通过

```http
Cache-control:no_cache
```

强制让服务器返回最新的报文

##### 条件请求

如果浏览器用**Cache-control**做缓存，会导致直接刷新数据。

为了避免这种情况，可以使用条件请求来进行缓存的使用。

* 服务端首次返回对应的：**ETag**，表示资源标签
* 客户端再次请求：使用 **If-None-Match:Etag** 或者 **If-Modified-Since**标签  来咨询服务器是否过期。
* 如果服务器返回了**304  Not Modified** 表示可以使用缓存。

#### 代理

##### 服务器

**代理服务器**需要使用**“Via”**来表明代理身份。每一层代理都会在这个字段里面加上自己的身份信息。

##### 客户端

服务器需要获取客户端的真实IP来进行数据分析等

为了获取真实的地址。可以通过**“X-Forwarded-For”** 和**“X-Real-IP”**

##### 缓存代理

带有缓存功能的代理。

对于一些敏感数据，不能保存在缓存代理服务器上。所以有专门的字段来控制。

**private**和**public** 。

**private**表示缓存只能在客户端保存，不能放在代理上与别人共享。（比如登录后的token，其实只有一个机器可以用，肯定不能在代理机器上让其他用户也能用）

**publice**表示可以缓存（比如一些缓存的页面信息等）。

条件请求的也需要使用单独的处理：**proxy-revalidate** 要求代理的缓存过期后必须验证，客户端不必回源，只验证代理环节。

缓存生存时间：**s-maxage**，限定在代理上的保存时间

### 安全

#### TLS/SSL

安全套接层：OSI的第五层（会话层）

SSL的第三版V3，变名为TSL。所以TSL 1.0就是SSL V3.1。

**TSL的发展是紧跟密码学的发展和物联网的现状的**

现在最广泛的是TSL的1.2版本，之前的被认为是不安全的。会在2020年被废弃。

TLS由记录协议、握手协议、警告协议、变更密码规范协议、扩展协议等几个子协议组成，综合使用了对称加密、非对称加密、身份认证等许多密码学前沿技术

浏览器和服务器在使用TLS建立连接的时候，会选择一组恰当的加密算法来实现安全通信。这些算法的组合被称为“密码套件”。

最后的格式是固定的：**基本的形式是“密钥交换算法 + 签名算法 + 对称加密算法 + 摘要算法”**

比如说最后协商的是：ECDHE-RSA-AES256-GCM-SHA384。

##### OpenSSL

一个开原密码学程序库和工具包。很多应用软件都使用它来作为底层库来实现TLS功能。

#### 加密算法

对称加密：AES、DES等等

非对称加密：DSA、RSA、ECC（在安全性和性能上都有明显的优势）。

相对于对称加密，非对称加密都是基于复杂的数学难题，所以运算速度都慢一些

#### 数字签名和证书

##### 摘要算法

：MD5、SHA-1、SHA-2。

完整性：通过数据+摘要算法能够保证数据的完整性。

##### 数字签名

私钥+摘要算法，来实现数字签名。数字签名是使用私钥进行加密，公钥进行解密

因为私钥加密效率低，所以私钥只加密原文的摘要信息。

##### 数字证书

由于使用公钥解密，所以对于**公钥信任**问题进行处理。需要通过CA来认证，形成**数字证书**。

#### HTTPS

Https是运行在SSL/TLS协议上的HTTP。这里的HTTP不再是之前所说的的TCP/IP之上的协议了。而是说HTTP运行在SSL协议之上，而SSL协议运行在TCP/IP协议之上。SSL是一个安全层。

**其本质是在客户端和服务器之间用非对称协议协商出一套对称加密协议，然后每次都使用对称加密协议来进行数据的传输**。

TLS之所以不采用非对称加密，是因为非对称加密相对来说比较耗时，而数据传输的频率有特别高，在传输频率比较多时，时间影响还是比较大。

![image-20210312233528649](http://cdn.qiniu.kailaisii.com/typora/20210312233530-390721.png)

### HTTP2

#### 特性

##### 兼容HTTP1

HTTP2完全兼容HTTP1的特性。TLS是对HTTP的安全性能上的提升。而HTTP2则是对HTTP1的性能上的提升。

##### 头部压缩

HTTP1的Header头部信息经常携带大量的头部信息。但是对应的body信息可能并不是特别多。所以HTTP2把头部压缩作为了性能优化的重点。

**优化的方式仍然是压缩**。压缩算法是：“HPACK”算法

##### 二进制格式

http2采用的报文全面使用二进制格式。

在二进制的基础上，将TCP的协议特性挪到了应用层上。把“Header+Body”的形式打散，成为了“二进制帧”的形式。

由于采用了流的机制。所以HTTP2不仅仅只是传统上的“请求-应答”工作模式了，还能够主动给客户端推送消息

![image-20200905161921080](http://cdn.qiniu.kailaisii.com/typora/20200905161926-419701.png)

##### QUIC

下一代HTTP3的基础，在UDP的基础上封装，实现了基于UDP的可靠传输，属于**新时代的TCP**

### 服务器

#### Nginx

#### OpenResty

#### WAF

#### CDN

内容分发网络。用于网络加速。

从我们访问的地方开始到访问的节点。中间会经历各种运行商网络、路由器、网关等等。而且都会经历二层、三层的解析。再加上距离的原因。可能会导致一个简单的请求就会有20-30ms，甚至更多的时间消耗。

所以提出了基于““**就近访问**”的解决方式。也就是CDN。

CDN缓存的都是**静态资源**，也就是超文本、图片、视频、安装包等一些不变的资源信息。而对于实时计算的资源，则不能通过CDN来进行缓存。

#### WebSocket沙盒

websocket是基于TCP的轻量级网络通信协议。是为了解决HTTP的“请求-应答”通讯模式不能双向收发的问题

### 性能

#### 服务器性能

吞吐量、并发数、响应时间

CPU、硬盘、网卡、内存

性能测试工具：ab

监控工具：uptime、top、vmstat、netstat、sar

#### 客户端

##### 指标

延迟

因素：光速、带宽、DNS查询、TCP握手、正常的数据收发。

#### 传输链路





https://blog.csdn.net/xiaoming100001/article/details/81109617
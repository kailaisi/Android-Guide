## Gson源码解析

### 前言

JSON是一种文本形式的数据交换格式，相对于XML来说，更加轻量级。而且更加便于阅读。在实际的开发过程中，总是需要将Java对象和JSON数据进行互相的转化。因此网络上诞生了各种优质的二者转化的开源类库，比如**fastjson**，**Gson**，**org.JSON**，**Jackson**等等。

其中**Gson**是**Google**提供的一种用来在Java对象和JSON数据之间进行映射的关系类库。

### 基本用法

Gson提供了最简单的用法，里面有很多设置直接按照默认的来处理

```java
        UserInfo userInfo = getUserInfo();
        Gson gson = new Gson();
        String jsonStr = gson.toJson(userInfo); // 序列化
        UserInfo user = gson.fromJson(jsonStr,UserInfo.class);  // 反序列化
```

### 源码解析

在进行源码解析之前，我们先介绍一下几个关键的类和属性

#### Gson

Gson类可以说是直面用户的类了。我们看一下它里面几个比较关键的属性和方法

```java
//缓存的 TypeToken和TpyeAdapter对应关系，采用ConcurrentHashMap，保障了多个线程操作时，不会发生线程安全问题
private final Map<TypeToken<?>, TypeAdapter<?>> typeTokenCache = new ConcurrentHashMap<TypeToken<?>, TypeAdapter<?>>();
//json对应的
private final JsonAdapterAnnotationTypeAdapterFactory jsonAdapterFactory;
//TypeAdapterFactory列表，里面包含了对各种常用的对象类型的解析工厂，
// 如果需要自定义解析方法的话，需要通过注册，将其添加到这个列表中
final List<TypeAdapterFactory> factories;
//用于配置一些你不希望被转换成JSON格式的对象的成员变量的
final Excluder excluder;
//字段命名策略，默认的是属性名称
final FieldNamingStrategy fieldNamingStrategy;
final Map<Type, InstanceCreator<?>> instanceCreators;
//是否序列化空值。默认为false。如果为true，那么如果字段没有设置对应的值，则会序列化为 a:null 这种
final boolean serializeNulls;
```

##### GsonBuilder

通过**new Gson** 方式创建的Gson对象，其配置都是默认的。如果我们需要根据自己的实际情况进行一些默认值的修改，比如说设置**serializeNulls**为true。这时候就需要通过GsonBuilder来进行处理了。通过名称其实就可以知道，这是一种Builder模式。

##### TypeAdapter

TypeAdapter类属于Gson的核心类。TypeAdapter是一种适配器模式的使用。在整个适配器模式中担任适配者的角色。由于Type接口和Json数据接口无法兼容，所以通过TypeAdapter类来解决两者的不兼容问题，从而能够实现二者的相互转化工作。

```java
  	//将T对象写入到JsonWriter中。这这面JsonWriter代表Json数据，T代表Type对应的对象
  	public abstract void write(JsonWriter out, T value) throws IOException;
	//从JsonReader中读取数据，然后生成Type所对应的的T对象
  	public abstract T read(JsonReader in) throws IOException;

```

TypeAdapter是一个抽象方法，子类通过覆写**write**和**read**方法就能够实现对应的Type和Json字串的相互转化。

#### Type和TypeAdapter的对应关系

在Gson中，会为每一种Type创建一个唯一对应的TypeAdapter。在Gson中将所有的Type划分为了两种：基本类型和符合类型。

* 基本类型（例如Integer,String,Url,Time,Calender...）：这里的基本类型属于一些常见的类型了。这种一般都有唯一的TypeAdapter对应。
* 复合类型：一般是我们自定义的一些类型了。这种一般由**ReflectiveTypeAdapter**来完成适配

**TypeAdapterFactory**

Gson中有众多的TypeAdapter，都是通过工厂模式创建的。

```java
public interface TypeAdapterFactory {
    // 创建TypeAdapter的接口
  <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type);
}
```



上面是属于Gson的最简便的用法了，里面到底为我们做了什么骚操作，我们一点点看。

#### 创建

对于Gson的创建，直接调用构造方法即可。

```java
    public Gson() {
    	//方法重载
        this(Excluder.DEFAULT, FieldNamingPolicy.IDENTITY,
                Collections.<Type, InstanceCreator<?>>emptyMap(), DEFAULT_SERIALIZE_NULLS,
                DEFAULT_COMPLEX_MAP_KEYS, DEFAULT_JSON_NON_EXECUTABLE, DEFAULT_ESCAPE_HTML,
                DEFAULT_PRETTY_PRINT, DEFAULT_LENIENT, DEFAULT_SPECIALIZE_FLOAT_VALUES,
                LongSerializationPolicy.DEFAULT, null, DateFormat.DEFAULT, DateFormat.DEFAULT,
                Collections.<TypeAdapterFactory>emptyList(), Collections.<TypeAdapterFactory>emptyList(),
                Collections.<TypeAdapterFactory>emptyList());
    }
```





- 源码解析
- 自定义TypeAdatper
- JsonReader/JsonWriter
- fromJson
- toJson

#### 



总结

* 这里面使用了适配器模式
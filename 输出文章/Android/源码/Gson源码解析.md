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

### 关键类和属性

在进行源码解析之前，我们先介绍一下几个关键的类和属性。

##### Gson

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

##### TypeToken

TypeToken是支持泛型，通过反射获取Type和Class（由于JVM中泛型的类型插除,所以来反射获取Type)。这个类的主要功能就是提供Type，获得相应的TypeAdapter。

##### TypeAdapter

TypeAdapter类属于Gson的核心类。TypeAdapter是一种适配器模式的使用。在整个适配器模式中担任适配者的角色。由于Type接口和Json数据接口无法兼容，所以通过TypeAdapter类来解决两者的不兼容问题，从而能够实现二者的相互转化工作。

```java
  	//将T对象写入到JsonWriter中。这这面JsonWriter代表Json数据，T代表Type对应的对象
  	public abstract void write(JsonWriter out, T value) throws IOException;
	//从JsonReader中读取数据，然后生成Type所对应的的T对象
  	public abstract T read(JsonReader in) throws IOException;

```

TypeAdapter是一个抽象方法，子类通过覆写**write**和**read**方法就能够实现对应的Type和Json字串的相互转化。

##### Type和TypeAdapter的对应关系

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

##### JsonReader/JsonWriter

在Gson中，Java对象与JSON字符串之间的转化主要是通过字符流来进行操作的。**JsonReaer**继承**Reader**用来读取字符串。**JsonWriter**继承**Writer**用来写入字符。

上面是属于Gson的最简便的用法了，里面到底为我们做了什么骚操作，我们一点点看。

### 源码解析

我们就从最简单的序列号开始进行跟踪分析。测试代码

```java
new Gson().toJson(object)
```

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

这个里面调用了重载的方法。

```java
    //重载方法
	Gson(Excluder excluder, FieldNamingStrategy fieldNamingStrategy,
         Map<Type, InstanceCreator<?>> instanceCreators, boolean serializeNulls,
         boolean complexMapKeySerialization, boolean generateNonExecutableGson, boolean htmlSafe,
         boolean prettyPrinting, boolean lenient, boolean serializeSpecialFloatingPointValues,
         LongSerializationPolicy longSerializationPolicy, String datePattern, int dateStyle,
         int timeStyle, List<TypeAdapterFactory> builderFactories,
         List<TypeAdapterFactory> builderHierarchyFactories,
         List<TypeAdapterFactory> factoriesToBeAdded) {
        this.excluder = excluder;
        this.fieldNamingStrategy = fieldNamingStrategy;
        this.instanceCreators = instanceCreators;
        this.constructorConstructor = new ConstructorConstructor(instanceCreators);
        this.serializeNulls = serializeNulls;
        this.complexMapKeySerialization = complexMapKeySerialization;
        this.generateNonExecutableJson = generateNonExecutableGson;
        this.htmlSafe = htmlSafe;
        this.prettyPrinting = prettyPrinting;
        this.lenient = lenient;
        this.serializeSpecialFloatingPointValues = serializeSpecialFloatingPointValues;
        this.longSerializationPolicy = longSerializationPolicy;
        this.datePattern = datePattern;
        this.dateStyle = dateStyle;
        this.timeStyle = timeStyle;
        this.builderFactories = builderFactories;
        this.builderHierarchyFactories = builderHierarchyFactories;

        List<TypeAdapterFactory> factories = new ArrayList<TypeAdapterFactory>();
        factories.add(TypeAdapters.JSON_ELEMENT_FACTORY);
        //object类型的
        factories.add(ObjectTypeAdapter.FACTORY);
        //excluder必须在处理用户自定义的类型之前进行添加
        factories.add(excluder);
        //添加用户自定义的TypeAdapterFactory
        factories.addAll(factoriesToBeAdded);
        //基础类型的TypeAdapterFactory
        // type adapters for basic platform types
        factories.add(TypeAdapters.STRING_FACTORY);
        factories.add(TypeAdapters.INTEGER_FACTORY);
        factories.add(TypeAdapters.BOOLEAN_FACTORY);
        factories.add(TypeAdapters.BYTE_FACTORY);
        factories.add(TypeAdapters.SHORT_FACTORY);
        TypeAdapter<Number> longAdapter = longAdapter(longSerializationPolicy);
        factories.add(TypeAdapters.newFactory(long.class, Long.class, longAdapter));
        factories.add(TypeAdapters.newFactory(double.class, Double.class,
                doubleAdapter(serializeSpecialFloatingPointValues)));
        factories.add(TypeAdapters.newFactory(float.class, Float.class,
                floatAdapter(serializeSpecialFloatingPointValues)));
        factories.add(TypeAdapters.NUMBER_FACTORY);
        factories.add(TypeAdapters.ATOMIC_INTEGER_FACTORY);
        factories.add(TypeAdapters.ATOMIC_BOOLEAN_FACTORY);
        factories.add(TypeAdapters.newFactory(AtomicLong.class, atomicLongAdapter(longAdapter)));
        factories.add(TypeAdapters.newFactory(AtomicLongArray.class, atomicLongArrayAdapter(longAdapter)));
        factories.add(TypeAdapters.ATOMIC_INTEGER_ARRAY_FACTORY);
        factories.add(TypeAdapters.CHARACTER_FACTORY);
        factories.add(TypeAdapters.STRING_BUILDER_FACTORY);
        factories.add(TypeAdapters.STRING_BUFFER_FACTORY);
        factories.add(TypeAdapters.newFactory(BigDecimal.class, TypeAdapters.BIG_DECIMAL));
        factories.add(TypeAdapters.newFactory(BigInteger.class, TypeAdapters.BIG_INTEGER));
        factories.add(TypeAdapters.URL_FACTORY);
        factories.add(TypeAdapters.URI_FACTORY);
        factories.add(TypeAdapters.UUID_FACTORY);
        factories.add(TypeAdapters.CURRENCY_FACTORY);
        factories.add(TypeAdapters.LOCALE_FACTORY);
        factories.add(TypeAdapters.INET_ADDRESS_FACTORY);
        factories.add(TypeAdapters.BIT_SET_FACTORY);
        factories.add(DateTypeAdapter.FACTORY);
        factories.add(TypeAdapters.CALENDAR_FACTORY);
        factories.add(TimeTypeAdapter.FACTORY);
        factories.add(SqlDateTypeAdapter.FACTORY);
        factories.add(TypeAdapters.TIMESTAMP_FACTORY);
        factories.add(ArrayTypeAdapter.FACTORY);
        factories.add(TypeAdapters.CLASS_FACTORY);
        //用于组合和用户定义类型的类型适配器
        factories.add(new CollectionTypeAdapterFactory(constructorConstructor));
        factories.add(new MapTypeAdapterFactory(constructorConstructor, complexMapKeySerialization));
        //用来进行@JsonAdapter注解的处理
        this.jsonAdapterFactory = new JsonAdapterAnnotationTypeAdapterFactory(constructorConstructor);
        factories.add(jsonAdapterFactory);
        factories.add(TypeAdapters.ENUM_FACTORY);
        //用来进行反射的处理
        factories.add(new ReflectiveTypeAdapterFactory(
                constructorConstructor, fieldNamingStrategy, excluder, jsonAdapterFactory));

        this.factories = Collections.unmodifiableList(factories);
    }

```

这构造方法中，将一些重要的属性进行赋值。这里面可以看到，注册了很多**TypeAdapterFactory**。包括**用户自定义** 以及一些**基础类型对应的工厂**、**CollectionTypeAdapterFactory**、**JsonAdapterAnnotationTypeAdapterFactory**、**ReflectiveTypeAdapterFactory**。

这里我们分析几个重点的TypeAdapterFactory。

##### ObjectTypeAdapter.FACTORY

```java
    public static final TypeAdapterFactory FACTORY = new TypeAdapterFactory() {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            //如果type对应的RawType是Object，则创建一个ObjectTypeAdapter
            if (type.getRawType() == Object.class) {
                return (TypeAdapter<T>) new ObjectTypeAdapter(gson);
            }
            return null;
        }
    };
```

可以看到这个Factory通过create方法会创建一个**ObjectTypeAdapter**对象。我们在之前的关键类中说过，TypeAdapter的作用就是进行对应的Type和Json字串的相互转化。这里所创建的**ObjectTypeAdapter**就是Object类型和Json字符串的转化所使用的类。

```java

public final class ObjectTypeAdapter extends TypeAdapter<Object> {
    private final Gson gson;
    ObjectTypeAdapter(Gson gson) {
        this.gson = gson;
    }
    //in代表JSON解析器，能够对读入的JSON字符串进行解析处理
    @Override
    public Object read(JsonReader in) throws IOException {
        //获取元素类型
        JsonToken token = in.peek();
        switch (token) {
            case BEGIN_ARRAY://如果是数组类型的话，需要使用List来保存
                List<Object> list = new ArrayList<Object>();
                in.beginArray();//开始标识
                while (in.hasNext()) {//如果有下一个元素，则递归读取
                    list.add(read(in));
                }
                in.endArray();//结束
                return list;
            case BEGIN_OBJECT://对象类型，对象类型，则使用map来保存，key是对象的属性名称，value保存属性值
                Map<String, Object> map = new LinkedTreeMap<String, Object>();
                in.beginObject();
                while (in.hasNext()) {//遍历循环
                    map.put(in.nextName(), read(in));
                }
                in.endObject();
                return map;
            case STRING://String类型，直接读取值
                return in.nextString();
            case NUMBER://number类型
                return in.nextDouble();
            case BOOLEAN:
                return in.nextBoolean();
            case NULL:
                in.nextNull();
                return null;
            default:
                throw new IllegalStateException();
        }
    }

    //将Object对象通过JsonWriter输出出去，也即是将Object对象变为JSON字符串
    @Override
    public void write(JsonWriter out, Object value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        //获取对应类型的TypeAdapter
        TypeAdapter<Object> typeAdapter = (TypeAdapter<Object>) gson.getAdapter(value.getClass());
        if (typeAdapter instanceof ObjectTypeAdapter) {
            out.beginObject();
            out.endObject();
            return;
        }
        //调用
        typeAdapter.write(out, value);
    }
}

```

通过read方法将JSON数据转化为Object对象过程中，通过JsonReader来获取JSON数据所对应的标签，然后通过嵌套调用达到对于Object对象的生成返回。这里我们可以看到，对于Object，按照数组、Object、String、Number、Boolean、Null这几种类型来进行了区分处理。

##### TypeAdapters.STRING_FACTORY

```java
  //创建一个TypeAdapterFactory，生成的TypeAdapter是STRING，对应的Type是String
  public static final TypeAdapterFactory STRING_FACTORY = newFactory(String.class, STRING);

  public static final TypeAdapter<String> STRING = new TypeAdapter<String>() {
    @Override
    public String read(JsonReader in) throws IOException {
      //获取JSON的类型
      JsonToken peek = in.peek();
      //如果是空，则直接返回控制
      if (peek == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      //如果JSON的类型是Boolean，那么将Boolean转化为String类型进行返回
      if (peek == JsonToken.BOOLEAN) {
        return Boolean.toString(in.nextBoolean());
      }
      //其他的正常情况则返回字符串
      return in.nextString();
    }
    @Override
    public void write(JsonWriter out, String value) throws IOException {
      //直接将字符串输出
      out.value(value);
    }
  };
```

STRING_FACTORY是一种创建String所对应的TypeAdapter的工厂。在进行JSON到String的解析过程中，我们看到是有一个对Boolean类型的兼容处理的。

其实对于Boolean、Number、Byte、Short等等，都是相似的处理方式。

##### JsonAdapterAnnotationTypeAdapterFactory

这个工厂方法是用来处理JsonAdapter注解的。我们知道，在使用Gson进行解析的时候，我们有时候会给属性或者类使用一个JsonAdapter的注解来实现自定义的解析器。而对于使用了JsonAdapter注解的方法或者类，就会通过这个工程来创建对应的TypeAdapter。

我们看看其**create**方法

```java
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> targetType) {
        Class<? super T> rawType = targetType.getRawType();
        JsonAdapter annotation = rawType.getAnnotation(JsonAdapter.class);
        if (annotation == null) {
            return null;
        }
        return (TypeAdapter<T>) getTypeAdapter(constructorConstructor, gson, targetType, annotation);
    }

    TypeAdapter<?> getTypeAdapter(ConstructorConstructor constructorConstructor, Gson gson, TypeToken<?> type, JsonAdapter annotation) {
        //获取注解的value所对应的对象
        Object instance = constructorConstructor.get(TypeToken.get(annotation.value())).construct();
        TypeAdapter<?> typeAdapter;
        if (instance instanceof TypeAdapter) {
            //如果注解的value是TypeAdapter，那么直接使用即可
            typeAdapter = (TypeAdapter<?>) instance;
        } else if (instance instanceof TypeAdapterFactory) {
            //如果注解的value是个TypeAdapterFactory，则将其create方法创建的TypeAdapter作为其TypeAdapter
            typeAdapter = ((TypeAdapterFactory) instance).create(gson, type);
        } else if (instance instanceof  || instance instanceof JsonDeserializer) {
            //如果注解的value是JsonSerializer或者JsonDeserializer。则创建TreeTypeAdapter
            JsonSerializer<?> serializer = instance instanceof JsonSerializer? (JsonSerializer) instance: null;
            JsonDeserializer<?> deserializer = instance instanceof JsonDeserializer? (JsonDeserializer) instance: null;
            typeAdapter = new TreeTypeAdapter(serializer, deserializer, gson, type, null);
        } else {
            throw new IllegalArgumentException("...");
        }
        if (typeAdapter != null && annotation.nullSafe()) {
            typeAdapter = typeAdapter.nullSafe();
        }
        return typeAdapter;
    }
```

这里会根据注解的value的类型进行不同的处理。前面两个是**自定义TypeAdapte**r和**自定义TypeAdapterFactory**，后面这个则是**JsonSerializer**或者**JsonDeserializer**。前面两个我们就不看了，我们看看最后创建的**TreeTypeAdapter**。我们只关注read和write方法

```java
    @Override
    public T read(JsonReader in) throws IOException {
        //如果没有设置deserializer，则通过标准的方法进行处理
        if (deserializer == null) {
            return delegate().read(in);
        }
        JsonElement value = Streams.parse(in);
        if (value.isJsonNull()) {
            return null;
        }
        //调用deserialize方法来实现反序列化
        return deserializer.deserialize(value, typeToken.getType(), context);
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
        if (serializer == null) {
            delegate().write(out, value);
            return;
        }
        if (value == null) {
            out.nullValue();
            return;
        }
        JsonElement tree = serializer.serialize(value, typeToken.getType(), context);
        Streams.write(tree, out);
    }
```

##### ReflectiveTypeAdapterFactory

方法比较绕。一点点分析。我们知道所有的**TypeAdapterFactory**都会实现一个**create**方法来创建一个对应的TypeAdapter。

我们先看看这个类的**create**方法的实现。

```java
    public <T> TypeAdapter<T> create(Gson gson, final TypeToken<T> type) {
        Class<? super T> raw = type.getRawType();
        //如果不是Object的子类类，则不匹配，只直接返回
        if (!Object.class.isAssignableFrom(raw)) {
            return null; // it's a primitive!
        }
        //获取Type所对应构造器
        ObjectConstructor<T> constructor = constructorConstructor.get(type);
        //创建一个TypeAdapter 重点方法***getBoundFields
        return new Adapter<T>(constructor, getBoundFields(gson, type, raw));
    }
```

在这个里面会调用一个**getBoundFields**方法，然后将其返回值作为Adapter的构造函数的一部分。我们先看看**getBoundFields**这个方法。

```java
    private Map<String, BoundField> getBoundFields(Gson context, TypeToken<?> type, Class<?> raw) {
        Map<String, BoundField> result = new LinkedHashMap<String, BoundField>();
        //接口直接返回
        if (raw.isInterface()) {
            return result;
        }
        Type declaredType = type.getType();
        while (raw != Object.class) {
            //获取所有属性
            Field[] fields = raw.getDeclaredFields();
            for (Field field : fields) {
                //是否序列化，主要是根据字段名称，以及是否忽略等相关设置信息进行判断
                boolean serialize = excludeField(field, true);
                //是否反序列化
                boolean deserialize = excludeField(field, false);
                //既不参与序列化，也不参与反序列化，则直接跳过
                if (!serialize && !deserialize) {
                    continue;
                }
                //设置属性可见
                accessor.makeAccessible(field);
                Type fieldType = $Gson$Types.resolve(type.getType(), raw, field.getGenericType());
                List<String> fieldNames = getFieldNames(field);
                BoundField previous = null;
                for (int i = 0, size = fieldNames.size(); i < size; ++i) {
                    String name = fieldNames.get(i);
                    if (i != 0) serialize = false; // only serialize the default name
                    //根据filed、type、以及是否支持序列化和反序列化来创建一个BoundField对象
                    BoundField boundField = createBoundField(context, field, name, TypeToken.get(fieldType), serialize, deserialize);
                    //将属性名称作为key，boundField作为value保存到result中
                    BoundField replaced = result.put(name, boundField);
                    //如果之前解析过对应的这个值的话，这里就会导致previous不为空，从而报错
                    if (previous == null) previous = replaced;
                }
                if (previous != null) {
                    throw new IllegalArgumentException(declaredType + " declares multiple JSON fields named " + previous.name);
                }
            }
            type = TypeToken.get($Gson$Types.resolve(type.getType(), raw, raw.getGenericSuperclass()));
            raw = type.getRawType();
        }
        return result;
    }
```

这个函数主要功能就是对于给定的type，通过反射遍历获取对应的属性，然后将结果以Map的方式保存起来。key是属性名称，value是属性的相关信息类。

我们再看看创建的TypeAdapter对象。主要看看其**write**和**read**方法。

```java
    //继承TypeAdapter的一个类
    public static final class Adapter<T> extends TypeAdapter<T> {
        private final ObjectConstructor<T> constructor;
        private final Map<String, BoundField> boundFields;

        Adapter(ObjectConstructor<T> constructor, Map<String, BoundField> boundFields) {
            this.constructor = constructor;
            this.boundFields = boundFields;
        }

        @Override
        public T read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            T instance = constructor.construct();
            try {
                in.beginObject();//从"{"开始
                while (in.hasNext()) {
                    String name = in.nextName();//逐个获取属性
                    BoundField field = boundFields.get(name);//从map中获取属性所对应的的BoundField类
                    if (field == null || !field.deserialized) {
                        in.skipValue();
                    } else {
                        //属性值能够序列化，则进行序列化操作。这里会对field进行赋值
                        field.read(in, instance);
                    }
                }
            } catch (IllegalStateException e) {
                throw new JsonSyntaxException(e);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
            in.endObject();
            return instance;
        }

        @Override
        public void write(JsonWriter out, T value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            //输出"{
            out.beginObject();
            try {
                for (BoundField boundField : boundFields.values()) {
                    if (boundField.writeField(value)) {
                        out.name(boundField.name);//先输出属性名称
                        boundField.write(out, value);//再输出属性值
                    }
                }
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
            //输出"}"
            out.endObject();
        }
    }
```

这里面不管是write还是read方法，都是通过之前保存的HashMap来获取属性值，然后去进行赋值工作。

对于TypeAdapterFactory的几个实现类，我们就先说这几个，剩下的可以自己去慢慢研究。我们继续我们的主线

#### 序列化toJson

当创建完成以后，就会调用**toJson()**方法来进行数据的序列化工作。

```java
    //将src对象转化为string
    public String toJson(Object src) {
        if (src == null) {
            return toJson(JsonNull.INSTANCE);
        }
        //调用重载方法
        return toJson(src, src.getClass());
    }

    public String toJson(Object src, Type typeOfSrc) {
        //创建了一个StringWriter对象
        StringWriter writer = new StringWriter();
        //重载方法
        toJson(src, typeOfSrc, writer);
        return writer.toString();
    }
	//重载方法
    public void toJson(Object src, Type typeOfSrc, JsonWriter writer) throws JsonIOException {
        //****重点方法****   获取对应的TypeAdapter
        TypeAdapter<?> adapter = getAdapter(TypeToken.get(typeOfSrc));
        //设置配置,并且保存原有的配置，在最后会将配置进行还原
        boolean oldLenient = writer.isLenient();
        writer.setLenient(true);
        boolean oldHtmlSafe = writer.isHtmlSafe();
        writer.setHtmlSafe(htmlSafe);
        boolean oldSerializeNulls = writer.getSerializeNulls();
        writer.setSerializeNulls(serializeNulls);
        try {
            //调用TypeAdapter里面的write方法
            ((TypeAdapter<Object>) adapter).write(writer, src);
        } catch (IOException e) {
            throw new JsonIOException(e);
        } catch (AssertionError e) {
            AssertionError error = new AssertionError("AssertionError (GSON " + GsonBuildConfig.VERSION + "): " + e.getMessage());
            error.initCause(e);
            throw error;
        } finally {
            writer.setLenient(oldLenient);
            writer.setHtmlSafe(oldHtmlSafe);
            writer.setSerializeNulls(oldSerializeNulls);
        }
    }

```

之前我们讲过每一个Type和TypeAdapter都是一一对应的。所以只要我们知道了Type，那么就可以获取到type所对应的TypeAdapter。这个获取的方法就是这里的**getAdapter()**方法。

##### getAdapter

```java
    public <T> TypeAdapter<T> getAdapter(TypeToken<T> type) {
        //先尝试从缓存获取，缓存使用的是ConcurrentHashMap，能够保障线程的安全性。
        TypeAdapter<?> cached = typeTokenCache.get(type == null ? NULL_KEY_SURROGATE : type);
        if (cached != null) {
            //如果获取到则直接返回
            return (TypeAdapter<T>) cached;
        }
        //all属于一个ThreadLocal变量，保存了Map对象，而map对象则缓存了FutureTypeAdapter类型。
        //获取当前线程对应的解析器。这里为嘛用一个线程安全的calls？？好奇
        Map<TypeToken<?>, FutureTypeAdapter<?>> threadCalls = calls.get();
        boolean requiresThreadLocalCleanup = false;
        if (threadCalls == null) {//如果为空，则创建，保证后面不会出现空指针问题
            threadCalls = new HashMap<TypeToken<?>, FutureTypeAdapter<?>>();
            calls.set(threadCalls);
            //最后需要根据这个字段进行线程的清空处理
            requiresThreadLocalCleanup = true;
        }
        // the key and value type parameters always agree
        //如果从ThreadLocal内部的Map缓存中获取到对应的TypeAdapter则直接返回
        FutureTypeAdapter<T> ongoingCall = (FutureTypeAdapter<T>) threadCalls.get(type);
        if (ongoingCall != null) {
            return ongoingCall;
        }
        try {
            //创建一个FutureTypeAdapter对象
            FutureTypeAdapter<T> call = new FutureTypeAdapter<T>();
            //将其缓存
            threadCalls.put(type, call);
            for (TypeAdapterFactory factory : factories) {
                //通过注册的factory来创建type类型，如果创建成功，则表明factory能够进行type类型的创建，
                //如果返回为null，则表明factory不能进行type类型的创建
                TypeAdapter<T> candidate = factory.create(this, type);
                if (candidate != null) {
                    call.setDelegate(candidate);
                    //创建成功则进行缓存
                    typeTokenCache.put(type, candidate);
                    return candidate;
                }
            }
            throw new IllegalArgumentException("GSON (" + GsonBuildConfig.VERSION + ") cannot handle " + type);
        } finally {
            //因为已经将type缓存到typeTokenCache中了。所以ThreadLocal里面的缓存用不到了。移除
            threadCalls.remove(type);
            //如果创建了ThreadLocal对象，则进行清除
            if (requiresThreadLocalCleanup) {
                calls.remove();
            }
        }
    }

```

这里使用了两级缓存，而且操作

1. 尝试从**typeTokenCache**去获取，获取到则返回
2. 获取不到则尝试从ThreadLocal中的Map中获取，获取到则返回
3. 获取不到则遍历factories，来查看其**create**能否创建和Type对应的TypeAdapter，如果可以的话，就进行缓存，不可以的话则抛出异常了
4. 最后将临时的变量信息进行释放处理。

这里有个**FutureTypeAdapter**对象，一直很好奇，为什么有这么东西，后来通过文章才发现是为了解决嵌套导致的无线递归问题。

```java
    static class FutureTypeAdapter<T> extends TypeAdapter<T> {
        //代理模式
        private TypeAdapter<T> delegate;

        public void setDelegate(TypeAdapter<T> typeAdapter) {
            if (delegate != null) {
                throw new AssertionError();
            }
            delegate = typeAdapter;
        }

        @Override
        public T read(JsonReader in) throws IOException {
            if (delegate == null) {
                throw new IllegalStateException();
            }
            return delegate.read(in);
        }

        @Override
        public void write(JsonWriter out, T value) throws IOException {
            if (delegate == null) {
                throw new IllegalStateException();
            }
            delegate.write(out, value);
        }
    }
```

这是一个典型的代理模式，代理模式的作用是为了屏蔽底层的具体实现，但是这里面却没有体现这方面的作用。

我们考虑一种情况

```java
class User{
    User user;
    String name;
    ...
}
```

之前我们说过，对于我们自定义的对象，其对应的TypeAdapter是由**ReflectiveTypeAdapterFactory**创建的。在进行创建TypeAdapter时，会逐个遍历其所有的属性，然后获取其对应的TypeAdapter(也是通过这个getAdapter()方法)。如果正常不进行处理的话，肯定就会陷入了死循环中了。

但是这里不会，为什么？**因为当我们在第一次创建了一个FutureTypeAdapter对象以后，将其缓存到了ThreadLocal中，对于同一个对象的解析，肯定是在同一个线程中的，当再次进行getAdapter的时候，就能够在ThreadLocal的Map中获取到了。而且这也是为什么先将创建的FutureTypeAdapter进行缓存，然后再进行代理的设置。如果先进行代理处理，再创建TypeAdapter的话，里面循环获取，就会发生死循环了**

可能比较绕，我们做个总结。

1. User对象在threadCalls中不存在。创建一个FutureTypeAdapter对象，然后将其缓存到threadCalls中，key是user。
2. 通过ReflectiveTypeAdapterFactory创建对应的TypeAdapter。
   1. 里面遍历到属性user，通过getAdapter获取对应的TypeAdapter。
   2. 这时候在threadCalls能够找到对应的缓存。直接将其返回就可以了。
   3. 遍历完成，将所有的属性所对应的TypeAdapter也都进行了保存。
3. 将获取到的TypeAdapter缓存到typeTokenCache。

这种解决循环的方式，在后台的[Spring的循环依赖注入的解决方案]()中我们也提到过，两者有异曲同工之妙，有兴趣的可以去看一看。

对于序列化工作，当获取到TypeAdapter以后，就是调用其write方法来进行处理了。这个write方法对于不同的TypeAdapter有不同的实现方法，之前已经介绍了一部分了，这里就不再展开了。

#### 反序列化（fromJson）

```java
   //将json字符串转化为T对象
    public <T> T fromJson(String json, Class<T> classOfT) throws JsonSyntaxException {
        //调用重载方法
        Object object = fromJson(json, (Type) classOfT);
        //如果解析以后，发现是int类型，会进行包装返回，也就是int->Integer
        return Primitives.wrap(classOfT).cast(object);
    }

    public <T> T fromJson(String json, Type typeOfT) throws JsonSyntaxException {
        //如果字符串是空，则直接返回null
        if (json == null) {
            return null;
        }
        //创建一个StringReader，入参是json字符串，
        StringReader reader = new StringReader(json);
        //重载方法
        T target = (T) fromJson(reader, typeOfT);
        return target;
    }

   public <T> T fromJson(Reader json, Type typeOfT) throws JsonIOException, JsonSyntaxException {
        //将Read进行包装，创建一个JsonReader
        JsonReader jsonReader = newJsonReader(json);
        //重载方法
        T object = (T) fromJson(jsonReader, typeOfT);
        //判断是否读取完了
        assertFullConsumption(object, jsonReader);
        return object;
    }
```

这里是通过了好几次的重载方法的调用。

```java
    public <T> T fromJson(JsonReader reader, Type typeOfT) throws JsonIOException, JsonSyntaxException {
        boolean isEmpty = true;
        boolean oldLenient = reader.isLenient();
        reader.setLenient(true);
        try {
            reader.peek();
            isEmpty = false;
            //将传入的class类型包装，生成一个TypeToken
            TypeToken<T> typeToken = (TypeToken<T>) TypeToken.get(typeOfT);
            //根据typeToken获取对应的TypeAdapter
            TypeAdapter<T> typeAdapter = getAdapter(typeToken);
            //通过typeAdapter转化对象
            T object = typeAdapter.read(reader);
            return object;
        }
        ...
    }
```

这个方式是我们最终进行反序列化的地方了。这里面的getAdapter方法在序列化中已经讲解过了。最终会调用TypeAdapter的read方法进行对象的创建并返回。

### 自定义解析器

#### 注册方法

其实对于自定义解析器有很多种实现方案。我们会根据之前源码解析中的各种情况进行分析，然后知道可以如下的几种方案来进行自定义解析器的注册

##### 注册自定义TypeAdapterFactory

在我们的Gson的构造函数中，会首先将自定义的**TypeAdapterFactory**添加到列表中，我们可以在这里插入我们自定义的TypeAdapterFactory来实现自定义解析器。

```java
new StatsTypeAdapterFactory();
gson = new GsonBuilder().registerTypeAdapterFactory(stats).create();
```

##### 注册自定义TypeAdapter

GsonBuilder给我们提供了注册自定义TypeAdapter的方法，能够注册我们的实现方案

```java
Gson gson = new GsonBuilder().registerTypeAdapter(A.class, typeAdapter).create();
String json = gson.toJson(new A("abcd"));
```

其实**registerTypeAdapter**的参数是一个Object类型，不仅可以是TypeAdapter，也可以是JsonDeserializer，也可以是InstanceCreator。其内部会按照不同的类型帮我们进行处理。

##### JsonAdapter注解

JsonAdapter注解不仅可以使用在类上，而且可以使用在属性上。使用JsonAdapter不需要我们进行注册的处理，会直接按照我们JsonAdapterAnnotationTypeAdapterFactory中解析所说的那样。会直接按照注解来进行处理。

##### 解析方法

对于上面不同的注册方法，其实本质上还是需要我们去自己实现对于JSON的序列化还是反序列化操作的。

而Gson支持两种不同的自定义解析方法

* 自己实现继承TypeAdapter：这种需要自己同时实现write，read方法。效率高，但是不太灵活。
* 实现JsonSerializer/JsonDeserializer接口：操作简单，可以根据自己需要去实现序列化或者反序列化。但是其实现经过了一层JsonElement的包装处理，所以效率方面会有所影响

### 总结

* 这里面使用了适配器模式，将Type和TypeAdapter进行了适配。
* 对于TypeAdapter的创建，则使用了工厂模式
* JsonAdapter注解支持**自定义TypeAdapter**和**自定义TypeAdapterFactory**，以及**JsonSerializer**和**JsonDeserializer**。
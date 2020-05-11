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

TypeToken是支持泛型，通过反射获取Type和Class（由于JVM中泛型的类型插除,所以来发射获取Type)。这个类的主要功能就是提供Type，获得相应的TypeAdapter。

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

    //将Object对象通过JsonWriter属于出去，也即是将Object对象变为JSON字符串
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
                    //如果之前解析过对应的这个值的话，这里机会导致previous不为空，从而报错
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

#### 序列化

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
```





- 源码解析
- 自定义TypeAdatper
- JsonReader/JsonWriter
- fromJson
- toJson

#### 



总结

* 这里面使用了适配器模式
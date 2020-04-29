## HashMap源码解析

之前写过一篇[SparseArray的源码解析]()，今天我们就对HashMap下手，撸一撸HashMap的源码。这篇文章的源码是从Android29中扒过来，实现方式是和JDK1.8里面的实现方式相似。

![img](http://cdn.qiniu.kailaisii.com/typora/202004/28/165155-679862.png)

在jdk1.8的结构中，用的是数组+链表+红黑树的的结构来存放数据。使用红黑树能够加快增删改查的效率。

### 重要属性

```java
	public class HashMap<K,V> extends AbstractMap<K,V> implements Map<K,V>, Cloneable, Serializable {
    //默认的数组的最小值
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16
    //默认的数组的最大值
    static final int MAXIMUM_CAPACITY = 1 << 30;
    //默认的负载因子
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    //当数据量大于8的时候，调整为红黑树
    static final int TREEIFY_THRESHOLD = 8;
    //小于6调整为链表
    static final int UNTREEIFY_THRESHOLD = 6;
    //临界值，当HashMap的存储的信息超过这个值的话，就会进行扩容
    int threshold;
    //当整个HashMap中的数量超过64的时候，也会转化为红黑树
    static final int MIN_TREEIFY_CAPACITY = 64;
    //元素的数量
    transient int size;
    //统计当前HashMap修改的次数
    transient int modCount;
    //实际的负载因子值
    final float loadFactor; 
```

### 构造函数

HashMap的构造函数有多个，我们一一的研究

```java
    public HashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR; // all other fields defaulted
    }
    //设置了初始容量
	public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }
    //设置了初始容量和负载因子
    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
        this.loadFactor = loadFactor;
        //根据初始容量计算下一次扩容的临界值
        this.threshold = tableSizeFor(initialCapacity);
    }
```

HashMap有3个构造函数，最后一个构造函数的唯一的需要注意的就是 **tableSizeFor** 这个函数了。

```java
    //寻找大于输入参数且最近的2的整数次幂的数
    static final int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }
```


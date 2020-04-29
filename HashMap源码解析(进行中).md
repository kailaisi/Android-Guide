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

### 重要内部类

 **Node<K,V> ：节点信息类**。在HashMap中，进行存储的时候，每个存储位置都是一个节点，节点类型就是Node<K,V>

```java
    //哈希节点信息，在HashMap中，每个节点都是Node对象
    static class Node<K, V> implements Map.Entry<K, V> {
        //key所对应的哈希值
        final int hash;
        final K key;
        V value;
        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }	
        ....
    }
```

可以看到，节点有个next，指向了下一个节点信息。

**TreeNode<K,V>：红黑树节点信息类**。HashMap的某个槽点的保存的数据较多时，会将保存的结构从链表转化为红黑树，红黑树的节点信息就是TreeNode<K,V>

```java
    //转化为红黑树的时候使用的节点信息
    static final class TreeNode<K, V> extends LinkedHashMap.LinkedHashMapEntry<K, V> {
        TreeNode<K, V> parent;  // red-black tree links
        TreeNode<K, V> left;
        TreeNode<K, V> right;
        TreeNode<K, V> prev;    // needed to unlink next upon deletion
        boolean red;

        TreeNode(int hash, K key, V val, Node<K, V> next) {
            super(hash, key, val, next);
        }
```

红黑树的节点保存了父节点，左子节点，柚子节点以及前方节点信息

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

HashMap这3个构造函数是相似的，最后一个构造函数的唯一的需要注意的就是 **tableSizeFor** 这个函数了。

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

这个代码看起来贼神奇，我们看看他是如何做到能够找到最近的2的整数次幂的。

在二进制里面，2的整数次幂怎么表示？ 0100...000。这种只有中间一个是1，其他位置都是0的，就是一个2的整数次幂。这里的实现方案是通过00111111...1111，然后+1处理，然后获取2的整数次幂。这里的处理方案

* 先来假设n的二进制为01xxx...xxx。接着

* 对n右移1位：001xx...xxx，再位或：011xx...xxx

* 对n右移2为：00011...xxx，再位或：01111...xxx

* 同理，通过右移，然后再位或，让最高位的1后面的位全变为1。也就是001111111111

* 最后再让结果n+1，即得到了2的整数次幂的值了。

除了以上三个，还有一个特殊的构造函数

```java
    //传入一个map，将map的数据保存到HashMap中
	public HashMap(Map<? extends K, ? extends V> m) {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        putMapEntries(m, false);
    }
```

也是设置了默认的加载因子，然后调用了 **putMapEntries** 方法。

```java
    //传入一个map，将其保存到hashmap中，
    final void putMapEntries(Map<? extends K, ? extends V> m, boolean evict) {
        int s = m.size();
        if (s > 0) {
            if (table == null) { // pre-size
                //根据传入的长度计算容量，
                float ft = ((float) s / loadFactor) + 1.0F;
                //容量肯定不能超过HashMap的上下界限
                int t = ((ft < (float) MAXIMUM_CAPACITY) ? (int) ft : MAXIMUM_CAPACITY);
                //初始化临界值，当t大于临界值的时候，进行临界值的计算。这个tableSizeFor方法
                if (t > threshold)
                    threshold = tableSizeFor(t);
            } else if (s > threshold)//table已经初始化了，并且s超过了临界值，则调用resize()进行扩容
                resize();
            //遍历，逐个把map的书放入到hashmap中
            for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
                K key = e.getKey();
                V value = e.getValue();
                putVal(hash(key), key, value, false, evict);
            }
        }
    }
```

这个构造方法，传入的是Map，在进行处理的过程中，会先通过map的大小对HashMap进行容量或者临界值做一个处理。然后再遍历通过**putval()**方法，将数据保存到hashmap中。

### 添加元素

```java
    public V put(K key, V value) {
        //第三个参数表示不管value是否为空，都进行数据的保存
        return putVal(hash(key), key, value, false, true);
    }
```

这个添加元素的方案应该是我们最最最常用的了吧？里面调用了一个函数的重载方法。**hash(key)** 方法是计算key对应的哈希值。

我们先不看重载方法，而是先研究一下，在hashmap中的哈希值是如何计算的。

```java
    static final int hash(Object key) {
        int h;
        //如果key为空，那么hash为0。否则的话取hashCode值h，然后将其和h右移16位的值进行异或操作
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }
```

这个代码看起来很简单，但是因为涉及到异或的处理以及哈希槽的分配问题，所以我们这里重点讲解一下。

为什么要无符号右移16位？

![img](https://i04piccdn.sogoucdn.com/caf3fcdba6304051)

假如说我们现在有一个32位的hashcode值。

![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly9pbWcyMDE4LmNuYmxvZ3MuY29tL2Jsb2cvOTg0NDIzLzIwMTkwNy85ODQ0MjMtMjAxOTA3MTgxMTM3MzczMzAtNjI1NzkxNTQxLnBuZw?x-oss-process=image/format,png)

当h无符号右移16位以后，会将h的高16位移动到低16位位置。然后再和原来的h进行异或操作。这样就**可以将高低位的二进制特征进行混合起来**。高16位不会发生变化，只有低16位发生了变化。为什么要这么做呢？我们知道hashmap的数据存储是将其分配到具体的哈希槽中的。所以需要尽量的保证数据能够均匀的分配到不同的槽中。

而分配到哈希槽点的计算方法是 **(n - 1) & hash**（这个我们后面会讲）。在重要属性中我们知道，HashMap数组的大小（也就是哈希槽点的数量）的默认值是16。我们按照16来进行计算处理。

![img](https://imgconvert.csdnimg.cn/aHR0cHM6Ly9pbWcyMDE4LmNuYmxvZ3MuY29tL2Jsb2cvOTg0NDIzLzIwMTkwNy85ODQ0MjMtMjAxOTA3MTgxMTQyNTU1NzAtMTA1MzA2NDg5Ny5wbmc?x-oss-process=image/format,png)

这时候，hashcode的高16位，因为槽点的数量限制，直接就屏蔽了高16位的信息。**如果我们不做刚才移位异或运算，那么在计算槽位时将丢失高区特征**。这样就会容易造成如果数据的hashcode的值差异在高位，不做移位异或计算，就会发生哈希碰撞。

为什么使用异或？

异或操作能够很好的保留特征信息。不管是 | 还是 & ，都会导致计算的数据偏向 0 或者 1。

为什么槽位必须是2^n?

当槽位数值是2^n的时候，计算槽位的公式-1，能够保证所有的位置都是1，进行 **(n - 1) & hash**操作，分配的槽值位置的均匀度就不会受槽值的影响，而之受hash的影响。而我们在计算hash的时候又通过移位异或的计算从而保证其均匀性，从而减少了哈希碰撞。

对于HashMap的hash值的计算原理我们就说到这里有兴趣的小伙伴可以研究研究。我们这就继续源码了~

```java
    /**
     * 保存key，value
     * @param hash  key对应的hash值
     * @param key   key值
     * @param value value值
     * @param onlyIfAbsent   如果是true:则只有对应的value为空的时候才保存。
     * @param evict
     * @return
     */
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
        //哈希数组
        Node<K, V>[] tab;
        //p 该哈希桶的首节点
        Node<K, V> p;
        //n 哈希的长度
        // i:计算出来数据在哈希数组中的的数组下标
        int n, i;
        //获取长度并进行扩容，使用的是懒加载，table一开始是没有加载的，等put后才开始加载
        if ((tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;
        //查找hash对应的哈希桶首节点
        if ((p = tab[i = (n - 1) & hash]) == null) {
            //如果当前hash所对应的位置节点是空，则将其作为哈希桶的首节点
            tab[i] = newNode(hash, key, value, null);
        } else {
            //哈希冲突了
            //e表示key对应的数据在map中存在的节点信息
            // k代表节点的key值
            Node<K, V> e;
            K k;
            //哈希桶的首节点就是我们的key所在的位置.(需要哈希值相等，equal方法相同。所以这就是为什么在覆写equal方法的时候还要覆写hash)
            if (p.hash == hash &&
                    ((k = p.key) == key || (key != null && key.equals(k))))
                e = p;
                //不是哈希桶的首节点，如果哈希桶的首节点是红黑树节点。则将其放到红黑树
            else if (p instanceof TreeNode)
                e = ((TreeNode<K, V>) p).putTreeVal(this, tab, hash, key, value);
            else {
                //否则就放入到数据链中
                for (int binCount = 0; ; ++binCount) {
                    if ((e = p.next) == null) {//到链表结尾了，那么将key和value放到链表结尾
                        p.next = newNode(hash, key, value, null);
                        if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                            //链表数量超过了8，则变为红黑树
                            treeifyBin(tab, hash);
                        break;
                    }
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k))))
                        break;
                    p = e;
                }
            }
            //如果插入的key所对应的值存在则进行覆盖且则返回旧值
            if (e != null) { // existing mapping for key
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null)
                    e.value = value;
                afterNodeAccess(e);
                return oldValue;
            }
        }
        //增加实际数据长度，如果满足要求则扩容。
        ++modCount;
        if (++size > threshold)
            resize();
        afterNodeInsertion(evict);
        //添加成功，则返回空
        return null;
    }

```

在进行key，value存储的过程中，会根据具体的情况来进行分析处理





学习到的知识点

1. 在进行HashMap存储的过程中，理解了为什么类在重写equal方法的同时，必须重写hashcode方法。
2. hashcode值是32位的。
3. 


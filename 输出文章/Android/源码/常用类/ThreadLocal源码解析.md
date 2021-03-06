## ThreadLocal源码解析

之前在Handler的源码解析中，我们提过一句ThreadLocal。知道它是一种线程安全的操作方式。那么它的内部原理是什么呢？这次就一探究竟吧。

### 使用

```java
public class TheadLocalDemo {
    public static void main(String[] args) {
        ThreadLocal<String> stringLocal = new ThreadLocal<String>() {
            @Override
            protected String initialValue() {//可以进行初始化，防止在未set的时候，直接get导致的空指针崩溃
                return "initValue";
            }
        };
        ThreadLocal<Integer> intLocal = new ThreadLocal<>();
        Random random = new Random();
        IntStream.range(0, 5).forEach(value -> {
            new Thread(() -> {
                intLocal.set(random.nextInt(100));
                System.out.println(stringLocal.get());
                System.out.println(intLocal.get());
                stringLocal.remove();
            }).start();
        });
    }
}
```

这里的测试案例通过ThreadLocal，只需要进行set，get即可。线程安全的处理直接由内部来进行处理。

### 源码解析

对于源码的解析工作，我们仍然从使用的代码入手。

#### 保存

对于保存，只需要调用**set()**方法即可，那么内部是如何处理的呢？

```java
//src\main\java\java\lang\ThreadLocal.java
    public void set(T value) {
        Thread t = Thread.currentThread();
		//获取线程中的ThreadLocalMap对象
        ThreadLocalMap map = getMap(t);
		//如果map存在，则进行保存
        if (map != null)
            map.set(this, value);
        else
			//不存在，则创建并保存
            createMap(t, value);
    }

    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }
```
这里通过获取当前的线程，然后根据线程获取了线程内部保存的**ThreadLocalMap**对象。如果不存在则创建再保存，如果存在则进行数据的保存。

我们这里分析一下**ThreadLocalMap**对象。该类是ThreadLocal的静态内部类。

```java
    static class ThreadLocalMap {
        //用于保存数据
        static class Entry extends WeakReference<ThreadLocal<?>> {
            /** The value associated with this ThreadLocal. */
            Object value;
            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }

        private static final int INITIAL_CAPACITY = 16;
         //保存的是数组，每次保存，会计算对应的hashcode值，如果发生了哈希碰撞，那么就往后挪一个位置依次类推，直到找到空的位置，再将对象存放。
        private Entry[] table;
```

可以看到ThreadLocalMap是通过数组来实现数据的保存的。数据通过Entry对象来进行key，value的保存。而且对于key是通过软引用来处理。

##### ThreadLocalMap的创建

```java
        //创建ThreadLocalMap,并保存第一个key和value值
        void createMap(Thread t, T firstValue) {
            //创建ThreadLocalMap对象，并赋值给t.threadLocals
            t.threadLocals = new ThreadLocalMap(this, firstValue);
        }

        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
        	//初始化信息，创建数组，保存第一个数据
            table = new Entry[INITIAL_CAPACITY];
			//获取保存的位置
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
            table[i] = new Entry(firstKey, firstValue);
            size = 1;
            //设置扩容的边界点
            setThreshold(INITIAL_CAPACITY);
        }
```

如果当前线程threadLocals为空，则会创建ThreadLocalMap对象并保存第一个节点值。在创建该对象的时候，主要是做了3点工作：

* 设置初始数组大小
* 找到保存的节点的位置信息并保存。
* 设置扩容边界点

对于保存位置，则是通过对key值的**threadLocalHashCode**进行取模之后得到。这里对于该值如何生成的，我们后面会重点来讲解，这里暂时略过。

##### 数据的保存

当threadLocals对象不存在的时候，会通过实例的构造方法将数据保存。但是当threadLocals对象已经存在得时候，则会直接通过set方法进行数据的保存处理

```java
        private void set(ThreadLocal<?> key, Object value) {
            Entry[] tab = table;
            int len = tab.length;
			//通过哈希值获取key对应的保存的数组位置
            int i = key.threadLocalHashCode & (len-1);
			//找到没有保存数据的位置。这个跟ThreadLocalMap的保存机制有关。
            for (Entry e = tab[i];e != null;e = tab[i = nextIndex(i, len)]) {
				//获取对应位置保存的数据的key。
                ThreadLocal<?> k = e.get();
                if (k == key) {//key相等，则直接覆盖并返回
                    e.value = value;
                    return;
                }

                if (k == null) {//key为空，但是这个时候e存在。说明这时候，key被回收了。
					//将i位置的值替换为对应的数据
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }
			//找到了空位置，那么进行数据的保存
            tab[i] = new Entry(key, value);
            int sz = ++size;
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
				//如果达到了阈值，则进行扩容
                rehash();
        }
```

当进行数据保存的时候，首先会根据key的threadLocalHashCode来计算其**理想的保存位置**。但是因为存在哈希碰撞的原因，可能会导致不同的ThreadLocal对象所对应的保存位置应该是同一个pos，这时候会采取：**先到先得，后到的则在pos往后的节点中，寻找一个空节点来保存**。

整个保存过程为：

1. 获取理想保存节点i，

2. 获取i位置的Entry
   * 如果Entry不为空
     * 如果i节点的key和我们要保存的key相等，则直接覆盖，并返回
     * 如果i节点的key为空，表示i节点的key已经被回收了（因为key采用的是弱引用），则通过**replaceStaleEntry**方法在i节点保存key和value值并返回
     * 如果都不满足，通过**nextIndex**方法获取下一个位置，重复步骤2
   * 如果Entry为空，则跳出2循环
3. 将数据保存在i节点
4. 如果通过清理脏数据（cleanSomeSlots方法）之后仍然达到扩容的阈值，则通过**rehash**方法进行扩容。

这里我们顺便看下nextIndex()方法

```java
         //获取下一个保节点。
        private static int nextIndex(int i, int len) {
        	//如果i+1之后，超过了数组的长度，则返回0
            return ((i + 1 < len) ? i + 1 : 0);
        }
```

执行到该位置之后，我们的节点就能够保存到数组中了，对于其中的扩容等我们再后面的章节再继续深入研究。

#### 获取

对于数据的获取，是通过get方法来得到数据

```java
    public T get() {
        Thread t = Thread.currentThread();
		//获取线程的ThreadLocalMap
        ThreadLocalMap map = getMap(t);
        if (map != null) {
            //重点方法  获取对应的数据保存节点
            ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null) {//根据当前对象获取保存的数据
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }
		//如果map不存在或则对应的数据不存在，则直接返回初始化数据
        return setInitialValue();
    }
```

如果map已经存在，则通过ThreadLocalMap的get方法获取数据。如果map不存在或者map中没有找到key所对应的值，则通过setInitialValue返回一个默认值。

##### setInitialValue

```java
    //进行初始化
    private T setInitialValue() {
    	//初始化的值
        T value = initialValue();
		//获取当前线程
        Thread t = Thread.currentThread();
		//获取线程对应的ThreadLocalMap
        ThreadLocalMap map = getMap(t);
		//从map中获取数据，数据的key值是ThreadLocal本身。
        if (map != null)
			//如果map存在，则进行保存
            map.set(this, value);
        else
			//如果不存在，则创建，并保存
            createMap(t, value);
		//返回value值
        return value;
    }
```

在我们的使用的代码中，我们设置了一次**initialValue()**。这个就是我们的默认值。这里会将创建之后的key和value值保存到ThreadLocalMap中。保存的方法和**set()**是一样的。

##### getEntry

当map存在的时候，会通过**getEntry()**从map中获取key所对应的节点。

```java
        private Entry getEntry(ThreadLocal<?> key) {
        	//根据theadlocal的threadLocalHashCode来计算在table中保存的值
            int i = key.threadLocalHashCode & (table.length - 1);
            Entry e = table[i];
			//当前的i位置对应Entry的key值和参数key相等，表示就是我们要查找的节点信息。
            if (e != null && e.get() == key)
                return e;
            else
				//如果i位置的Entry的key并不是我们要找的key，则遍历查找，并返回结果
                return getEntryAfterMiss(key, i, e);
        }


```

这里会先从key所对应的理想保存位置i中去获取Entry，在上一节的保存源码分析中，我们知道因为哈希冲突的存在，实际上i位置可能保存的并不是我们所要查找的数据，这时候就需要从i位置开始依次往后查找key所对应的Entry。

```java
        //获取节点i位置以后的数据中，键值和key相等的数据。
        private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
            Entry[] tab = table;
            int len = tab.length;

            while (e != null) {
                ThreadLocal<?> k = e.get();
                if (k == key)
                    return e;
                if (k == null)
					//遇到了空数据，则进行数据的清理。
                    expungeStaleEntry(i);
                else
                    i = nextIndex(i, len);//获取下一个保存节点。
                e = tab[i];
            }
			//没有找到数据
            return null;
        }
```

查找结束条件有两种：

1. 直到找到key所对应的Entry。
2. 遇到了Entry为空的节点。

当通过**getEntryAfterMiss()**返回的数据为null的时候，就会通过setInitiaValue()方法返回默认值。具体的逻辑在刚才的源码解析中已经提到过了。

#### 删除

刪除操作主要是通过remove()方法

```java
     public void remove() {
         ThreadLocalMap m = getMap(Thread.currentThread());
         if (m != null)
             m.remove(this);
     }
```

如果对应的ThreadLocalMap存在，则调用其remove()方法

```java
        private void remove(ThreadLocal<?> key) {
            Entry[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len-1);
            for (Entry e = tab[i];e != null;e = tab[i = nextIndex(i, len)]) {
                if (e.get() == key) {
										//将entry的key置为null
                    e.clear();
										//将entry的value也置为null，然后进行一次内存泄漏的处理
                    expungeStaleEntry(i);
                    return;
                }
            }
        }
```

删除方法相对来说比较简单，主要是**找到对应Entry节点，然后将其key和value值置为null**。

#### 扩容

像绝大多数容器一样，当保存的数据量过多的时候，都有一套对应的扩容机制。对于扩容，有两个关键的点：

* 加载因子，也就是进行扩容的边界值。
* 扩容方案

##### threshold的确定

```java
        private static final int INITIAL_CAPACITY = 16;
        //当下一个size值超过该值时，进行resize
		private int threshold;
        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
        	//初始化信息，创建数组，保存第一个数据
            table = new Entry[INITIAL_CAPACITY];
			//设置扩容的边界点
            setThreshold(INITIAL_CAPACITY);
        }
        //设置调整大小阈值，以维持最坏情况的2/3负载系数。
        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }
```

从源码上来看，我们在第一次创建theadlocal的时候，会创建一个数组初始大小为16的theadLocalMap，并且通过setThreshold设置对应的threshold。其值为当前数组长度的三分之二。也就是说加载因子为2/3（**加载因子用来表示哈希表中的元素填满程度。加载因子越大，则冲突的机会越大，查找的成本越高。如果过小，则会存在内存使用不高。所以加载因子的大小需要有一个平衡**）。这里ThreadLocalMap的初始大小为16，加载因子为2/3，所以可用大小为10。

##### 扩容方案

在进行数据保存的过程中，我们提到过，会通过**rehash()**方法进行扩容。

```java
        private void rehash() {
        	//清理脏数据
            expungeStaleEntries();
            //使用较低的加倍阈值以避免迟滞
            if (size >= threshold - threshold / 4)
                resize();
        }
```

在这个方法中，我们看到其实并不是达到threshold的时候才进行resiz()扩容的处理，而是在数组已使用的大小为threshold的3/4的时候，就调用了resize方法，这样就能够有效的避免迟滞现象的发生(这种处理方案是和HashMap不一样的，HashMap是达到了shreshold才进行扩容的处理)。

```java
        private void resize() {
            Entry[] oldTab = table;
            int oldLen = oldTab.length;
			//新长度加倍
            int newLen = oldLen * 2;
            Entry[] newTab = new Entry[newLen];
            int count = 0;

            for (int j = 0; j < oldLen; ++j) {
                Entry e = oldTab[j];
                if (e != null) {
                    ThreadLocal<?> k = e.get();
                    if (k == null) {
                        e.value = null; // Help the GC
                    } else {
                    	//根据最新的长度，计算其理想保存位置。
                        int h = k.threadLocalHashCode & (newLen - 1);
                        while (newTab[h] != null)
                            h = nextIndex(h, newLen);
                        newTab[h] = e;
                        count++;
                    }
                }
            }
			//重新设置阈值
            setThreshold(newLen);
            size = count;
            table = newTab;
        }
```

对于数组的扩容，主要是两个步骤：

* 创建大小为原来数组长度2倍的数组
* 遍历旧数组，将其中所有的非脏数插入到新的数组中。

这里有一个注意地方：**如果在扩容过程中发现了key为空的节点Entry，会将value置为null，以便能够对其进行垃圾回收，解决隐藏的内存泄漏问题**。

#### 哈希碰撞

在前面讲到创建ThreadLocal实例的时候，我们知道，每个ThreadLocal实例都有一个哈希值**threadLocalHashCode**。而实例在数组中的理想保存位置，则是通过 **key.threadLocalHashCode & (len-1)**得到。通过这种方式，不可避免的会存在**不同的实例，最终计算得到的保存位置pos是一致的，这种就是所谓的哈希碰撞**。

那么ThreadLocal是如何来尽量避免这种情况的发生的呢？**关键点在于threadLocalHashCode的生成**。

```java
    //当前ThreadLocal的hashcode。
    private final int threadLocalHashCode = nextHashCode();

    //这里是个静态变量，所有的ThreadLocal的nextHashCode使用的是同一个
    private static AtomicInteger nextHashCode = new AtomicInteger();
    private static final int HASH_INCREMENT = 0x61c88647;
    //返回下一个hashCode
    private static int nextHashCode() {
        //通过原子操作类来获取哈希值
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }
```

在源码中是通过nextHashCode()方法来获取对应的值。而该方法是通过类的**静态变量**nextHashCode这个**原子操作实例**通过增加0x61c88647来获得。

这里的**0x61c88647**就是那个神奇的数字，能够保证哈希碰撞最小程度上发生。

##### 数组长度

对于实例在map中的实际保存位置，是通过 **key.threadLocalHashCode & (len-1)**来获取的，我们先看一下这里的len。对于数组的大小，一个是默认的初始值，一个则是进行扩容的时候，数组才会变化。

```java
        /**
         * 初始大小，必须是2整数次幂
         */
        private static final int INITIAL_CAPACITY = 16;

        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
        	//初始化信息，创建数组，保存第一个数据
            table = new Entry[INITIAL_CAPACITY];
            ...
        }

        private void resize() {
            Entry[] oldTab = table;
            int oldLen = oldTab.length;
			//新长度加倍
            int newLen = oldLen * 2;
            Entry[] newTab = new Entry[newLen];
            ...
            table = newTab;
        }
```

对于table数组，其初始大小为16，而每次扩容则会进行加倍。所以数组的长度大小始终是2的N次方（len=2^N）。**那么len-1所对应的二进制则为：低位连续N个1.**

那`key.threadLocalHashCode & (len-1)` 的值就是 `threadLocalHashCode`的低 N 位。

##### 魔数0x61c88647

为了尽量保证不发生冲突，我们肯定是希望ThreadLocal所生成的hashCode能够均匀的在2的N次方的数组中。那么通过0x61c88647能够达到这种效果呢？

我们先来写个测试代码

```java
public class MagicNumTest {
    private static int Magic_Num=0x61c88647;

    public static void main(String[] args) {
        hash(16);
        hash(32);
        hash(64);
    }

    public static void hash(int len) {
        int hashCode=0;
        for (int i = 0; i < len; i++) {
            hashCode=i*Magic_Num+Magic_Num;
            System.out.print(hashCode&(len-1));
            System.out.print(" ");
        }
        System.out.println();
    }
}

```

对应的输出结果：

```java
长度为16:7 14 5 12 3 10 1 8 15 6 13 4 11 2 9 0 
长度为32:7 14 21 28 3 10 17 24 31 6 13 20 27 2 9 16 23 30 5 12 19 26 1 8 15 22 29 4 11 18 25 0 
长度为64:7 14 21 28 35 42 49 56 63 6 13 20 27 34 41 48 55 62 5 12 19 26 33 40 47 54 61 4 11 18 25 32 39 46 53 60 3 10 17 24 31 38 45 52 59 2 9 16 23 30 37 44 51 58 1 8 15 22 29 36 43 50 57 0 
```

通过0x61c88647散列出来的结果分布是比较均匀的。其主要是利用了斐波那契散列法，而且**0x61c88647 = 2^32 * 黄金分割比**，这里面的具体算法，大家感兴趣的可以看看[从 ThreadLocal 的实现看散列算法](https://zhuanlan.zhihu.com/p/40515974)。

#### 内存泄漏

对于ThreadLocal，其具体的内容是保存的Entry中的。

```java
        static class Entry extends WeakReference<ThreadLocal<?>> {
            Object value;
            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }
```

我们看一下这里的具体引用示意图

![](https://upload-images.jianshu.io/upload_images/2615789-9107eeb7ad610325.jpg)

当外部的TheadLocal的外部强引用被置为空的时候，threadLocal实例就没有了强引用的链路，当下一次系统进行GC的时候，就会被回收，所以Entry的Key就变为了null。这时候就无法通过Entry的key值来访问到其Value了。但是现在Entry存在于Thread中的map的数组中，**对于Value就存在一条引用链：Thread->ThreadLocalMap->Entry->Value，从而导致Value无法回收，但是实际上，因为Key被回收，Value永远也不会被访问到，这就造成了内存泄漏**。

虽然说当Thread销毁的时候，这些都会进行销毁，但是对于通过线程池创建的线程，为了复用，线程是不会被回收的，这就导致了内存泄漏的持续存在。

对于这种内存泄漏的情况，ThreadLocal将其称之为“脏数据”，并且本身已经对其进行了了一定的处理。

比如说在set数据的时候

```java
        private void set(ThreadLocal<?> key, Object value) {
          ...
                if (k == null) {//key为空，但是这个时候e存在。说明这时候，key被回收了。
										//将i位置的值替换为对应的数据
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }
						...
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
							//如果达到了阈值，则进行扩容
                rehash();
        }
```

在这个方法中，对于脏数据就进行了处理

* 在发生了哈希冲突之后，向后环形查找的时候，如果发现了Entry的key为null，则调用**replaceStaleEntry()**方法进行处理
* 将数据保存到数组之后，调用**cleanSomeSlots()**方法来检测并清理脏数据

##### replaceStaleEntry

```java
       //替换旧数据。该方法还有一个额外的作用，就是能够删除staleSlot所在的节点所在序列（指两个空节点之间的队列）中的脏数据
        private void replaceStaleEntry(ThreadLocal<?> key, Object value,int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;
            Entry e;
            //找到序列所在的第一个脏entry
            int slotToExpunge = staleSlot;
            for (int i = prevIndex(staleSlot, len);(e = tab[i]) != null;i = prevIndex(i, len))
                if (e.get() == null)
                    slotToExpunge = i;
            for (int i = nextIndex(staleSlot, len);(e = tab[i]) != null;i = nextIndex(i, len)) {
                ThreadLocal<?> k = e.get();
                if (k == key) {
					//在向后环形查找过程中发现key相同的entry就覆盖，并且和脏entry进行交换。
                    e.value = value;

                    tab[i] = tab[staleSlot];
                    tab[staleSlot] = e;
                    if (slotToExpunge == staleSlot)
                        slotToExpunge = i;
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                    return;
                }
                if (k == null && slotToExpunge == staleSlot)
                    slotToExpunge = i;
            }
            //如果在查找过程中，没有找到可以覆盖的entry，则将新的entry插入到staleSlot位置
            tab[staleSlot].value = null;
            tab[staleSlot] = new Entry(key, value);
            if (slotToExpunge != staleSlot)
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }

```

这里当遇到脏数据的时候，不仅仅会覆盖当前位置的数据，而且能够删除staleSlot所在的节点所在序列（指两个空节点之间的队列）中的脏数据，从而尽量处理内存泄漏的问题。

##### cleanSomeSlots

```java
        //清除一些key为null的脏数据
        private boolean cleanSomeSlots(int i, int n) {
            boolean removed = false;
            Entry[] tab = table;
            int len = tab.length;
            do {
								//获取下一个节点
                i = nextIndex(i, len);
                Entry e = tab[i];
                if (e != null && e.get() == null) {
										//遇到了脏数据，将标志位置为true，然后将n设置为数据的长度，从而扩大搜索的次数
                    n = len;
                    removed = true;
                    i = expungeStaleEntry(i);
                }
            } while ( (n >>>= 1) != 0);
							//循环条件是n>>>1，也就是如果对于入参n，会搜索log2(n)次，
	            //如果中间发现了脏数据了，那么就将n置为table的大小,扩大搜索次数
            return removed;
        }
```

cleanSomeSlots会从i节点开始，向后查找节点。

1. 如果发现了脏数据，就调用**expungeStaleEntry**方法清理数据，然后会将n设置为数组的长度。
2. 每次循环，n >>>= 1，都会进行n/2。

当发现了脏数据的时候，会认为在脏数据的附近还可能会存在脏数据，所以通过修改n来继续扩大搜索的次数。

###### **expungeStaleEntry**

```java
    	//清理脏数据（也就是key为空的数据，以为key是使用弱引用的，所以存在被回收的情况）
        private int expungeStaleEntry(int staleSlot) {
          	//对应的value置为null
            tab[staleSlot].value = null;
            tab[staleSlot] = null;
            size--;
            Entry e;
            int i;
          	//从staleSlot位置开始，环形向后查找，直到遇到了table[i]=null结束
            for (i = nextIndex(staleSlot, len); (e = tab[i]) != null;i = nextIndex(i, len)) {
                ThreadLocal<?> k = e.get();
              	//如果向后搜索过程中发现了脏数据，将其清理掉
                if (k == null) {
                    e.value = null;
                    tab[i] = null;
                    size--;
                } else {
                    //进行rehash操作，
                    //因为存在哈希冲突，所以当初Entry保存的位置好可能并不是理想应该保存的地方。而现在因为清理了脏数据，那么可能当前保存的位置和理想保存位置之间有某个pos为空了，这时候需要进行移动处理
                    int h = k.threadLocalHashCode & (len - 1);
                    //h！=i，表明当前保存的pos并不是理想位置，说明当初保存到的时候，应该保存的h，但是现在保存在了i位置。
                    if (h != i) {
                        tab[i] = null;

                        // Unlike Knuth 6.4 Algorithm R, we must scan until
                        // null because multiple entries could have been stale.
                        //从h位置开始，往后找空的位置，然后将e保存到位置中。
                        while (tab[h] != null)
                            h = nextIndex(h, len);
                        tab[h] = e;
                    }
                }
            }
            return i;
        }
```

expungeStaleEntry函数的功能，不仅仅是清理staleSlot位置的脏数据，它还会从该位置开始，环形向后进行遍历，向后搜索的过程中如果发现了脏数据也会清理掉。

ThreadLocal这种在每次调用某个函数之后，再进行无用节点的处理方式，跟Android中的SparseArray的处理方式很相似。大家感兴趣的可以去看看对应的源码。

#### 使用场景

SimpleDateFormat

Handler

### 总结

* **每个线程，是一个Thread实例，其内部拥有一个名为threadLocals的实例成员，其类型是ThreadLocal.ThreadLocalMap**
* **通过实例化ThreadLocal实例，我们可以对当前运行的线程设置一些线程私有的变量，通过调用ThreadLocal的set和get方法存取**
* **ThreadLocal本身并不是一个容器，我们存取的value实际上存储在ThreadLocalMap中，ThreadLocal只是作为TheadLocalMap的key**
* ThreadLocal的key和value，会组装为Entry对象的。而ThreadLocalMap中保存的是Entry的数组。
* Entry在数组的位置，是和ThreadLocal中的threadLocalHashCode相关的，而threadLocalHashCode则是根据ThreadLocals中的静态变量nextHashCode来生成的。
* 如果保存的位置发生了冲突，则顺位向下一个位置保存。但是获取的时候，也就不能直接获取了，而是需要获取之后判断Entry的key是否是我们的ThreadLocal对象。
* ThreadLocal是通过弱引用来保存数据的。所以在ThreadLocalMap中的key值可以被回收。这样对应的value值其实不会再被使用到。但是如果Thead一直存在着，那么ThreadLocalMap就不会销毁。从而导致value一直存在而无法被回收，导致内存泄漏。可以通过remove方法移除掉（其实在set的时候，也会将key为空，value不为空的情况进行优化，保存新的数据）。

### 参考

https://www.jianshu.com/p/dde92ec37bd1

https://www.cnblogs.com/ilellen/p/4135266.html

https://www.jianshu.com/p/30ee77732843

https://zhuanlan.zhihu.com/p/40515974

https://www.pianshen.com/article/45011037234/



> 本文由 [开了肯](http://www.kailaisii.com/) 发布！ 
>
> 同步公众号[开了肯]

![image-20200404120045271](http://cdn.qiniu.kailaisii.com/typora/20200404120045-194693.png)
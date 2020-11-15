ThreadLocal源码解析

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

这里ThreadLocal，只需要进行set，get即可。线程安全的处理直接由内部来进行处理。

### 源码解析

对于源码的解析工作，我们仍然从使用的代码入手。

#### 保存set()

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
这里通过获取当前的线程，然后根据线程获取了线程内部保存的**ThreadLocalMap**对象。如果不存在则创建在保存，如果存在则进行数据的保存。

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

可以看到ThreadLocalMap是通过数组来实现数据的保存的。数据通过Entry来进行分装保存。通过软引用来处理。

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

进行保存的时候，可能会存在哈希碰撞的问题。这时候需要寻找下一个没有被使用的位置进行数据的保存。在进行保存以后，需要根据当前已经保存的数据量进行一次数据扩容。

**注意：这里并不能直接根据位置坐标进行覆盖。**

#### 获取get()

对于数据的获取，我们应该可以想到，是通过从Map中来获取数据的

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

这里如果没找到数据的话，会返回一个默认的初始值。

```java
    //进行初始化
    private T setInitialValue() {
    	//初始化的值
        T value = initialValue();
		//获取当前线程
        Thread t = Thread.currentThread();
		//获取线程对应的ThreadLocalMap
        ThreadLocalMap map = getMap(t);
		//从map中获取数据，数据的key值是ThreadLocal本身
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
        return value;
    }
```

在我们的使用的代码中，我们设置了一次**initialValue()**。这个就是我们的默认值。这里可以看到，会将默认的值保存到ThreadLocalMap中。保存的方法和**set()**是一样的。

这里我们需要关心一下是如何进行数据保存的节点的。

```java
        private Entry getEntry(ThreadLocal<?> key) {
        	//根据theadlocal的threadLocalHashCode来计算在table中保存的值
            int i = key.threadLocalHashCode & (table.length - 1);
            Entry e = table[i];
			//当前的i位置可能并不是的实际值，这跟它的保存机制有关。需要去下一个位置去寻找
            if (e != null && e.get() == key)
                return e;
            else
                return getEntryAfterMiss(key, i, e);
        }

         //获取节点i位置以后的数据中，键值和key相等的数据。
        private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
            Entry[] tab = table;
            int len = tab.length;

            while (e != null) {
                ThreadLocal<?> k = e.get();
                if (k == key)
                    return e;
                if (k == null)
                    expungeStaleEntry(i);
                else
                    i = nextIndex(i, len);//获取下一个保存节点。这里不是直接+1，因为可能+1之后超过了table的数组范围。
                e = tab[i];
            }
            return null;
        }
```

在进行获取的时候，需要处理hash冲突的问题。需要依次去下一个节点去获取数据。

#### 扩容



### 总结

* ThreadLocal是通过弱引用来保存数据的。所以在ThreadLocalMap中的key值可以被回收。这样对应的value值其实不会再被使用到。但是如果Thead一直存在着，那么ThreadLocalMap就不会销毁。从而导致value一直存在而无法被回收，导致内存泄漏。可以通过remove方法移除掉（其实在set的时候，也会将key为空，value不为空的情况进行优化，保存新的数据）。

* 在进行数据保存的时候并不一定存在对应的hash节点上。

* **每个线程，是一个Thread实例，其内部拥有一个名为threadLocals的实例成员，其类型是ThreadLocal.ThreadLocalMap**

* **通过实例化ThreadLocal实例，我们可以对当前运行的线程设置一些线程私有的变量，通过调用ThreadLocal的set和get方法存取**

* **ThreadLocal本身并不是一个容器，我们存取的value实际上存储在ThreadLocalMap中，ThreadLocal只是作为TheadLocalMap的key**

  
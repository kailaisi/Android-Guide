### 前言

SparseArray是安卓特有的一种数据结构，跟HashMap相似，都是存储<Key,Value>的实体。但是SparseArray的Key只能是Int类型的。在存储的时候Key按照顺序进行了排序，当查询的时候采用了二分查找法来定位位置。这种方式相对来说更加迅速

#### 变量

```java
private boolean mGarbage = false;//是否可以进行回收，也就是进行key，value的整理

private int[] mKeys;//存储的key值

private Object[] mValues;//存储的value值

private int mSize; //数组的大小
```

可以看到，对于key和value的存储，是分别存储在两个不同的数组中的。而且key的类型是int，而不是封装后的Integer。

这里面有个 **mGarbage** 变量，它标志着我们当前的数据是否可以进行数据的整理工作。比如说，当我们移除某个key以后，会将这个标志位设置为true，在需要的时候（比如说我们要进行数据的存储），会根据这个标志位进行一次数组的整理工作。

#### 构造函数

```java
public SparseArray() {
    //默认数组的大小是10
    this(10);
}

public SparseArray(int initialCapacity) {
    if (initialCapacity == 0) {
        mKeys = EmptyArray.INT;
        mValues = EmptyArray.OBJECT;
    } else {
        mValues = ArrayUtils.newUnpaddedObjectArray(initialCapacity);
        mKeys = new int[mValues.length];
    }
    mSize = 0;
}
```

从构造函数可以看出来，SparseArray的数组 **默认大小是10** ，如果我们在实际的使用过程中能够确定要保存的数据量的大小，最好直接初始化，这样就不会出现扩容的问题。

#### 添加元素

 

既然和HashMap相似，那么肯定是有数据的增删改的

```java
    public void put(int key, E value) {
        //通过ContainerHelpers进行二分查找。如果存在则返回key的位置
        // 如果不存在则返回key在数组中可以存储的位置i的负值。
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);
        //如果key已经存在，则直接赋值
        if (i >= 0) {
            mValues[i] = value;
        } else {
            //binarySearch 方法的返回值分为两种情况：
            //1、如果存在对应的 key，则直接返回对应的索引值
            //2、如果不存在对应的 key
            //  2.1、假设 mKeys 中存在"值比 key 大且大小与 key 最接近的值的索引"为 presentIndex，则此方法的返回值为 ~presentIndex
            //  2.2、如果 mKeys 中不存在比 key 还要大的值的话，则返回值为 ~mKeys.length
            //可以看到，即使在 mKeys 中不存在目标 key，但其返回值也指向了应该让 key 存入的位置
            //通过将计算出的索引值进行 ~ 运算，则返回值一定是 0 或者负数，从而与“找得到目标key的情况（返回值大于0）”的情况区分开
            //且通过这种方式来存放数据，可以使得 mKeys 的内部值一直是按照值递增的方式来排序的
            i = ~i;
            //如果搜索到的i位置可以使用，并且没有数据，则将对应的key，value存入到i位置
            if (i < mSize && mValues[i] == DELETED) {
                mKeys[i] = key;
                mValues[i] = value;
                return;
            }
            //如果可以进行数组的整理，并且当前的数组大小能够进行存储，则进行数据的整理，然后再进行位置的查找
            if (mGarbage && mSize >= mKeys.length) {
                gc();
                // Search again because indices may have changed.
                //GC 后再次进行查找，因为值可能已经发生变化了
                i = ~ContainerHelpers.binarySearch(mKeys, mSize, key);
            }
            //通过复制或者扩容数组，将数据存放到数组中
            mKeys = GrowingArrayUtils.insert(mKeys, mSize, i, key);
            mValues = GrowingArrayUtils.insert(mValues, mSize, i, value);
            mSize++;
        }
    }
```

这里有个辅助类 **ContainerHelpers** ，它的 **binarySearch** 方法会根据实际情况返回key所对应的位置值

* 如果mKeys数组中存在key，那么直接返回key所对应的索引值

* 如果mKeys中不存在key

  * key比mKeys中的所有的数据都大，则返回~mKeys.length

  * key处于mKeys中的某个中间位置，则返回那个值比 key 大且大小与 key 最接近的值的索引。

可以看到，哪怕key在数组中不存在， **binarySearch** ，也会将key保存的最佳位置给返回回来。

当key的位置确定以后，会根据情况进行数组的重新编排，重新编排的话，当前的key和value在数组中的位置就会发生变化，所以会调用 **binarySearch** 重新获取适合的插入位置。

最后调用 **GrowingArrayUtils.insert** 方法进行数据的插入。

这个方法会判断当前的数组大小是否能够继续插入key，如果不可以的话，会进行扩容。如果可以，会将i位置以后的数据往后移动一位，然后将i位置插入我们的key值。

### 移除元素

移除元素的函数有多个，我们一个个来看。

```java
    public void remove(int key) {
        delete(key);
    }
	public void delete(int key) {
        //通过二分查找，获取key所在位置
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);
        if (i >= 0) {
            //将所在位置的value设置为DELETED,然后标记需要进行整理。
            if (mValues[i] != DELETED) {
                mValues[i] = DELETED;
                mGarbage = true;
            }
        }
    }
    public E removeReturnOld(int key) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);
        if (i >= 0) {
            //如果key所在的index的值不为DELETED，则返回数据，并标记对应index的值为DELETED，
            if (mValues[i] != DELETED) {
                final E old = (E) mValues[i];
                mValues[i] = DELETED;
                mGarbage = true;
                return old;
            }
        }
        return null;
    }
    //删除指定索引对应的元素值
    public void removeAt(int index) {
        if (index >= mSize && UtilConfig.sThrowExceptionForUpperArrayOutOfBounds) {
            // The array might be slightly bigger than mSize, in which case, indexing won't fail.
            // Check if exception should be thrown outside of the critical path.
            throw new ArrayIndexOutOfBoundsException(index);
        }
        if (mValues[index] != DELETED) {
            mValues[index] = DELETED;
            mGarbage = true;
        }
    }
```

可以看到，不管哪种remove方法。实际的移除操作，只是把key所在的位置的value值设置为了 DELETED ，然后设置了 **mGrabage** 标志位。并没有进行key数组和value数组的移动操作。

#### 查找元素

```java
    public E get(int key) {
        return get(key, null);
    }

    //获取指定key的值，如果获取不到，则返回指定对象。
    @SuppressWarnings("unchecked")
    public E get(int key, E valueIfKeyNotFound) {
        //获取key对应的index位置
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);
        //如果不存在，或者i位置的数据已经回收了，则直接返回
        if (i < 0 || mValues[i] == DELETED) {
            return valueIfKeyNotFound;
        } else {
            return (E) mValues[i];
        }
    }
    public E valueAt(int index) {
        if (index >= mSize && UtilConfig.sThrowExceptionForUpperArrayOutOfBounds) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        if (mGarbage) {
            gc();
        }
        return (E) mValues[index];
    }
	//根据value值，获取其对应的index信息
    public int indexOfValue(E value) {
        if (mGarbage) {
            gc();
        }
        for (int i = 0; i < mSize; i++) {
            if (mValues[i] == value) {
                return i;
            }
        }
        return -1;
    }
```

 查找方法有的返回的是key所对应的的信息，有的是获取index信息。这里面进行了区分操作。因为如果只是根据key值获取value值的话，不需要进行数组的整理工作。而一旦涉及到了index的查找工作，那么就需要根据 **mGrabage** 先进行一次整理工作，然后才能进行index的相关处理。

### 垃圾回收

SparseArray的垃圾回收并不是我们平时所理解的JVM的垃圾回收，只是因为当我们进行移除value的情况下，并没有进行数据的移除，只是设置了 **mGrabage** ，而且将对应位置的value设置为了 **DELETED** 来表示当前位置是可以回收的。所以当我们需要适应索引时，就会出现索引无效的问题。所以需要通过垃圾回收来进行数组的整理，将数组整理为连续的数据。

```java
    //用于移除无用的引用，通过移动，将现在所有的key和value连续保存到数组中，而不会使某个位置的value为空
    //而且会将mSize赋值为实际使用的大小
    private void gc() {
        int n = mSize;
        //o 值用于表示 GC 后的元素个数
        int o = 0;
        int[] keys = mKeys;
        Object[] values = mValues;
        for (int i = 0; i < n; i++) {
            Object val = values[i];
            //如果value不是DELETED，证明当前的位置数据是可用的
            if (val != DELETED) {
                if (i != o) {
                    keys[o] = keys[i];
                    values[o] = val;
                    values[i] = null;
                }
                o++;
            }
        }
        mGarbage = false;
        mSize = o;
    }

```

### 优劣

优势

* 使用int类型作为key，避免了装箱拆箱操作
* 延迟了垃圾回收时机，只在需要的时候才进行一次回收
* 和Map每个存储节点都是一个类对象不同，SparseArray不需要用于包装的结构体，单个元素存储成本更低廉
* 在小数据量下，二分查找效率更高一些。

劣势

* 插入新元素会导致大量的数组移动
* 数据量较大时，二分查找效率会变低



> 本文由 [开了肯](http://www.kailaisii.com/) 发布！ 
>
> 同步公众号[开了肯]

![image-20200404120045271](http://cdn.qiniu.kailaisii.com/typora/20200404120045-194693.png)
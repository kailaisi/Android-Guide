## 基于Kotlin的LiveDataBus

提供两个基于Kotlin的LiveDataBus。最简单的使用方案

#### 基于Androidx的

```kotlin
package com.example.eventbusdemo

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.LiveData

/**
 * 描述:基于LiveData实现的EventBus
 * <p/>作者：kailaisii
 * <br/>创建时间：2020/4/9 13:41
 */
class LiveDataBusForAndroidx private constructor() {
    val TAG="LiveDataBusForAndroidx"
    val bus by lazy { mutableMapOf<String, BusMutableLiveData<Any?>>() }

    private object SingleHolder {
        val holder = LiveDataBusForAndroidx()
    }

    companion object {
        val instance = SingleHolder.holder
    }

    fun <T> with(target: String, type: Class<T>): BusMutableLiveData<T> {
        if(!bus.containsKey(target)){
            bus[target] = BusMutableLiveData()
        }
        Log.d(TAG,bus[target].toString())
        return bus[target] as BusMutableLiveData<T>
    }

    fun with(target: String): BusMutableLiveData<Any> {
        return with(target, Any::class.java)
    }

    private class ObserverWrapper<T>(private var observer: Observer<in T>?) : Observer<T> {

        override fun onChanged(t: T?) {
            observer?.let {
                if (isCallOnObserve()) {
                    return
                }
                observer?.onChanged(t)
            }
        }

        fun isCallOnObserve(): Boolean {
            return true
        }

    }

    class BusMutableLiveData<T> : MutableLiveData<T>() {
        private val observerMap by lazy { mutableMapOf<Observer<in T>, Observer<T>>() }
        override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
            super.observe(owner, observer)
            runCatching { hook(observer) }
        }

        override fun observeForever(observer: Observer<in T>) {
            observerMap.getOrPut(observer, { ObserverWrapper(observer) })
            super.observeForever(observer)
        }

        override fun removeObserver(observer: Observer<in T>) {
            var realObserver = if (observerMap.containsKey(observer)) {
                observerMap.remove(observer)!!
            } else {
                observer
            }
            super.removeObserver(realObserver)
        }
        //通过hook方法，将添加的obser对象的起始的版本号修改为和当前的数据版本号一致
        private fun hook(observer: Observer<in T>) {
            //    private SafeIterableMap<Observer<? super T>, ObserverWrapper> mObservers = new SafeIterableMap<>();
            var clazz = LiveData::class.java
            //获取mObservers
            var fieldObserver = clazz.getDeclaredField("mObservers")
            fieldObserver.isAccessible = true
            //获取mObservers的值
            var objectObservers = fieldObserver.get(this)
            var javaClass = objectObservers.javaClass
            //获取get方法
            var method = javaClass.getDeclaredMethod("get", Object::class.java)
            method.isAccessible = true
            //调用get方法，返回的是包装的ObserverWrapper对象
            var value = method.invoke(objectObservers, observer)
            var objectWrapper: Any? = null
            if (value is Map.Entry<*, *>) {
                objectWrapper = value.value as Map.Entry<*, *>
            } else {
                throw NullPointerException("Wrapper can not be null")
            }
            //获取到ObserverWrapper的父类，里面有一个版本号
            var superclass = objectWrapper.javaClass.superclass
            var lastVersionField = superclass!!.getDeclaredField("mLastVersion")
            lastVersionField.isAccessible = true
            var mVersionField = clazz.getDeclaredField("mVersion")
            mVersionField.isAccessible = true
            var mVersinoInfo = mVersionField.get(this)
            //将mVersion的值设置给mLastVersion
            lastVersionField.set(objectWrapper, mVersinoInfo)

        }
    }
}
```

### 非Androidx

```kotlin
package com.example.eventbusdemo

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.util.Log
import com.example.eventbusdemo.LiveDataBus.ObserverWrapper


/**
 * 描述:基于LiveData实现的EventBus
 * <p/>作者：wu
 * <br/>创建时间：2020/4/9 13:41
 */
class LiveDataBus private constructor() {
    val TAG = "LiveDataBus"
    val bus by lazy { mutableMapOf<String, BusMutableLiveData<Any?>>() }

    private object SingleHolder {
        val holder = LiveDataBus()
    }

    companion object {
        val instance = SingleHolder.holder
    }

    fun <T> with(target: String, type: Class<T>): BusMutableLiveData<T> {
        if (!bus.containsKey(target)) {
            bus[target] = BusMutableLiveData()
        }
        Log.d(TAG, bus[target].toString())
        return bus[target] as BusMutableLiveData<T>
    }

    fun with(target: String): BusMutableLiveData<Any> {
        return with(target, Any::class.java)
    }

    private class ObserverWrapper<T>(private var observer: Observer<in T>?) : Observer<T> {

        override fun onChanged(t: T?) {
            observer?.let {
                if (isCallOnObserve()) {
                    return
                }
                observer?.onChanged(t)
            }
        }

        fun isCallOnObserve(): Boolean {
            return true
        }

    }

    class BusMutableLiveData<T> : MutableLiveData<T>() {
        private val observerMap by lazy { mutableMapOf<Observer<in T>, Observer<T>>() }
        override fun observe(owner: LifecycleOwner, observer: Observer<T>) {
            super.observe(owner, observer)
            runCatching { hook(observer) }
        }

        override fun observeForever(observer: Observer<T>) {
            observerMap.getOrPut(observer, { ObserverWrapper(observer) })
            super.observeForever(observer)
        }

        override fun removeObserver(observer: Observer<T>) {
            var realObserver = if (observerMap.containsKey(observer)) {
                observerMap.remove(observer)!!
            } else {
                observer
            }
            super.removeObserver(realObserver)
        }

        //通过hook方法，将添加的obser对象的起始的版本号修改为和当前的数据版本号一致
        private fun hook(observer: Observer<in T>) {
            //    private SafeIterableMap<Observer<? super T>, ObserverWrapper> mObservers = new SafeIterableMap<>();
            val clazz = LiveData::class.java
            //获取mObservers
            val fieldObserver = clazz.getDeclaredField("mObservers")
            fieldObserver.isAccessible = true
            //获取mObservers的值
            val objectObservers = fieldObserver.get(this)
            val javaClass = objectObservers.javaClass
            //获取get方法
            val method = javaClass.getDeclaredMethod("get", Object::class.java)
            method.isAccessible = true
            //调用get方法，返回的是包装的ObserverWrapper对象
            val value = method.invoke(objectObservers, observer)
            var objectWrapper: Any? = null
            if (value is Map.Entry<*, *>) {
                objectWrapper = value.value as Map.Entry<*, *>
            } else {
                throw NullPointerException("Wrapper can not be null")
            }
            //获取到ObserverWrapper的父类，里面有一个版本号
            val superclass = objectWrapper.javaClass.superclass
            val lastVersionField = superclass!!.getDeclaredField("mLastVersion")
            lastVersionField.isAccessible = true
            val mVersionField = clazz.getDeclaredField("mVersion")
            mVersionField.isAccessible = true
            val mVersinoInfo = mVersionField.get(this)
            //将mVersion的值设置给mLastVersion
            lastVersionField.set(objectWrapper, mVersinoInfo)

        }
    }
}
```

 **LiveDataBus** 是基于对 **LiveData** 源代码的的 **hook** 而实现的。具体的 **LiveData** 的源码分析可以参考[]
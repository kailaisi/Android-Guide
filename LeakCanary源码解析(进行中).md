## LeakCanary源码解析

### 前言

对于内存泄漏的检测，基于MAT起点较高，所以一般我们都使用**LeakCanary**来作为我们的内存泄漏检测工具来使用。

#### 基础知识

#### 四种引用

LeakCanary主要是基于弱引用来进行对于已经销毁的Activity和Fragment的回收监控来实现的。

* 强引用：无论如何都不会回收。

* 软引用：内存足够不回收。内存不够时，就会回收。

* 弱引用：垃圾回收时直接回收，则直接回收。

* 虚引用：垃圾回收时直接回收。

#### 引用队列（ReferenceQueue）。

软引用和弱引用。可以关联一个引用队列，当回收以后，会将软引用加入到与之关联的引用队列中。**LeakCanary**的基础实现就是将已经销毁的**Activity**和**Fragment**所对应的实例放入到弱引用中，并关联一个引用队列。如果实例进行了回收，那么就会放入到**ReferenceQueue**中，如果一段时间后，所监控的实例还未在**ReferenceQueue**中出现，那么可以证明出现了内存泄漏导致了实例没有被回收。



Leakcanary原理

注册监听

​	注册监听的实现方案：

销毁后放入到弱引用WeakReference中

将WeakReference关联到ReferenceQueue

查看Reference中是否存在Activity的引用。

如果泄露，则Dump出heap信息，然后分析泄露路径


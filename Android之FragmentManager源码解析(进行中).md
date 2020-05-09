## Android之Fragment状态管理源码解析

#### 引言

在之前，我们写过一篇[Fragment事务流程分析](https://mp.weixin.qq.com/s?__biz=MzUzOTE4MTQzNQ==&mid=2247483744&idx=1&sn=c5db3f3a1d2f5a5d7e13562d94d2695c&chksm=facd2974cdbaa062668dae6031a8b1ba41ced7338bb8554a0ae5403143c76e0c6a791b97d713&token=1511138431&lang=zh_CN#rd)，在这片文章中，我们了解到，Activity通过通过FragmentManager来对Fragment进行统一的管理。在文章最后，我们讲到**FragmentManager**会通过 **moveToState()** 方法，将Fragment的生命周期和Activity的生命周期进行同步。这一篇文章，就主要分析一下，**FragmentManager**是如何Fragment的状态管理。

#### 重要知识点



在进行源码解析之前，我们先讲解一下可能会涉及到的基础知识点

##### Fragment的几种状态

* static final int INVALID_STATE = -1;   //无效的状态或者null值
* static final int INITIALIZING = 0;     //未创建
* static final int CREATED = 1;          // 创建完成
* static final int ACTIVITY_CREATED = 2; // 绑定的Activity创建完成
* static final int STOPPED = 3;          // 创建完成，但是没有进行start处理
* static final int STARTED = 4;          // 执行了Created 和started, 但是没有执行resumed.
* static final int RESUMED = 5;          //执行完 Created 、started 和resumed

对于Fragment，在进行创建到显示的过程，其状态会从0逐渐过渡到5。而当其销毁时，状态会从5逐渐回到0。

##### Fragment的创建方式

我们使用Fragment时，主要通过两种方式进行创建

1. 在activity的布局文件中，生命fragment。
2. 通过Java代码，将Fragment添加到已存的**ViewGroup**中

这两种方式其实最终效果是相同的，在进行代码处理中红会根据不同的创建方式进行一定的区分处理。比如说第二种，那么必须保证Fragment有对应的container。而且需要有对应的containerId。





#### 学到的知识点

* 对于Fragment，也可以通过 **registerFragmentLifecycleCallbacks** 方法来注册**Fragment**生命周期的监听功能。
* 
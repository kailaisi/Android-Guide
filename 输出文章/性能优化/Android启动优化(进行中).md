## Android启动优化

应用的启动是给客户的第一体验，就像相亲的时候，第一印象是最重要的（😃，我没相过亲，反正他们都那么说）。如果我们的应用启动比较慢的话，哪怕应用内容很丰富，也很难再建立一个完美的形象了。

所以启动优化

### 基础知识

#### 启动流程

之前发布过一些列和应用启动相关的文章。

[Android启动流程源码解析（一）](https://mp.weixin.qq.com/s/ASjx3aR69rfVKrjeUM94iw)

[Android启动流程源码解析（二）](https://mp.weixin.qq.com/s/EYO3WZIt8IR6e48LuEMmFA)

[Android的inflate源码详解](https://mp.weixin.qq.com/s/46PBiGZSfTaI-UZghCWNtg)

[Android应用进程的创建姿势](https://mp.weixin.qq.com/s/IVU0MYgrh8xDY5vuNhsuuQ)

[Android之窗口布局绘制分析](https://mp.weixin.qq.com/s/XRgd-OEDMSjIjfzoaJ0pKQ)

这一系列文章，将从点击应用图标开始之后，**应用进程的创建**，到**生命周期的调用**，再到**布局的加载绘制**，直到页面展示到我们面前的所有过程都进行了一系列的源码剖析。具体的流程就不再进行详细说明了。我们只放一个应用启动流程图来总结一下。![image-20200630220520419](http://cdn.qiniu.kailaisii.com/typora/20200630220524-215293.png)

从图上可以看到整个启动流程可以划分为：

**IPC->Process.start->ActivityThread->bindApplication->LifeCycle->ViewRootImpl**

其中前半部分都是系统内部的函数调用。**我们所能够操作地地方，是从Application的attachBaseContext()的位置开始。**从感官来说，**整个启动结束，则是页面展示在用户面前为止**。

#### 启动分类

对于应用，[官方](https://developer.android.google.cn/topic/performance/vitals/launch-time)根据不同的启动状态，将应用分为了三类：**冷启动、温启动、热启动**。其中冷启动中，引用从头开始启动。而另外两种状态，系统只需要将后台运行的应用带入前台即可。

##### 冷启动

冷启动是指应用从头开始启动：系统进程在冷启动后创建进程。也就是包含了我们上面所说的整个的启动流程。

特点：

耗时多，是应用启动快慢的衡量标准

##### 热启动

##### 温启动

#### 启动任务

### 现象

### 原因

### 测量耗时方案

### 工具

### 优化

#### 异步优化

##### 常规方案

##### 启动器方案

#### 延迟初始化

##### 常规方案

##### 更优方案

##### 懒加载

##### 其他

### 延伸

对于优化方案的保持
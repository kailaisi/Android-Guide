Android屏幕刷新机制

之前我们讲过布局优化中提到Android系统每16ms发出一个VSYNC信号，然后执行一次UI的渲染工作。如果渲染成功，那么界面基本就是流畅的。

我们看看Android系统是如何做屏幕刷新机制，如果做到16ms执行一次绘制工作，又如何保证我们每次点击或者触摸屏幕的时候，能将相应的事件进行处理的。



EventThread被设计用来接收VSync事件通知，并分发VSync通知给系统中的每一个感兴趣的注册者。

VSync来源自底层硬件驱动程序的上报，对于Android能看到的接口来说，它是来自HAL层的hwc_composer_device的抽象硬件设备

http://dandanlove.com/2018/04/25/android-source-choreographer/

https://blog.csdn.net/stven_king/article/details/80098798

[VSYNC调用流程]https://blog.csdn.net/litefish/article/details/53939882

[Android垂直同步信号VSync的产生及传播结构详解](https://blog.csdn.net/houliang120/article/details/50908098)

EventThread
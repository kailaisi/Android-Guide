vsync机制（进行中）

![image-20210301154424784](http://cdn.qiniu.kailaisii.com/typora/20210301154424-692871.png)

* HWComposer：硬件生成Vsync信号。
* VSyncThread：软件生成Vsync信号，如果硬件模块没有加载成功，就会使用软件模拟生成VSync信号。
* DisSyncThread：线程，用于分发Vsync信号，分成两路：一个分给app-EventThread，一个分给sf-EventThread。
* app-EventThread：分发给应用端，进行UI的绘制
* sf-EventThread：分发给SurfaceFlinger，用于进行buffer的合成以及渲染等。

这里DisSyncThread之所以将Vsync信号进行分发，是为了防止app的绘制和buffer的合成在同一之间处理，抢占CPU资源，所以进行了分发，分发的两路Thread是有一个错峰的，会做不同时间的一个延迟分发处理。



https://www.jianshu.com/p/9dac91bbb9c9
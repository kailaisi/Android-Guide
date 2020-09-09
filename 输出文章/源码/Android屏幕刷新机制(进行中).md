Android屏幕刷新机制

之前我们讲过布局优化中提到Android系统每16ms发出一个VSYNC信号，然后执行一次UI的渲染工作。如果渲染成功，那么界面基本就是流畅的。

我们看看Android系统是如何做屏幕刷新机制，如果做到16ms执行一次绘制工作，又如何保证我们每次点击或者触摸屏幕的时候，能将相应的事件进行处理的。







http://dandanlove.com/2018/04/25/android-source-choreographer/

https://blog.csdn.net/stven_king/article/details/80098798
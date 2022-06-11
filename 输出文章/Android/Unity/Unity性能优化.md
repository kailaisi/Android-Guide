Unity性能优化

#### 基础知识

Unity的跨平台是内置了Mono虚拟机的。

##### DrawCall

DrawCall很简单，就是cpu对图形绘制接口的调用，CPU通过调用图形库（directx/opengl）接口，命令GPU进行渲染操作。

每一次绘制CPU都要调用DrawCall，而在调动DrawCall前，CPU还要进行很多准备工作：检测渲染状态、提交渲染所需要的数据、提交渲染所需要的状态。

如果DrawCall过多，CPU就会额外开销用于准备工作。过多的DrawCall会造成CPU的性能瓶颈：大量时间消耗在DrawCall准备工作上。很显然的一个优化方向就是：尽量把小的DrawCall合并到一个大的DrawCall中，这就是批处理的思想。

展示位置：Windows-Analysis-Profile。

Rendering中的DrawCall。

##### Profiler性能分析工具





[走近DrawCall](https://zhuanlan.zhihu.com/p/26386905)

[深入浅出聊优化：从Draw Calls到GC](http://www.360doc.com/content/17/0424/11/40005136_648190034.shtml)


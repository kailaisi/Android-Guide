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

休息

#### 资源优化

##### 优化标准

* 动态模型：
  * 面数<300
  * 材质数＜3
  * 骨骼数＜50
* 静态模型：
  * 顶点数＜500
* Audio
  * 长时间音乐压缩格式mp3
  * 短时间音乐非压缩格式wav
* Texture
  * 贴图长宽＜1024
  * Shader
  * 尽量减少复杂数学运算
  * 减少discard操作

##### 模型优化

减少面数

减少顶点数

##### 贴图优化

贴图-材质-Drawcall关系

贴图合并

##### 减少冗余资源和重复资源

1. Resouces目录下的资源都会被打包，不是用的资源不要放在这个目录下
2. 不同目录下的相同资源，都会被打包，造成冗余

##### 资源检测与分析

https://www.uwa4d.com/

#### CPU GPU分工
CPU负责计算，GPU负责渲染工作。
GPU适合大量可以并行的简单任务。比如说场景渲染，光照处理等。
CPU用于一些树枝计算，比如说上海，随机数，敌人的AI等等。
##### LOD层级渲染
对于同一个模型，可以准备不同面数的文件。同一个事物，如果离得比较远的情况下，不需要太多的面数，使用粗糙的模型，离得近的情况下，可以使用面数比较精细的模型。
使用方式：增加LOD Group组建，支持设置不同的模型

##### 遮挡剔除
只渲染在视野内的游戏物体，视野外的不渲染，可以极大的优化渲染性能。
所有的游戏物体，选择static下拉箭头=> Occluder Static.

[走近DrawCall](https://zhuanlan.zhihu.com/p/26386905)

[深入浅出聊优化：从Draw Calls到GC](http://www.360doc.com/content/17/0424/11/40005136_648190034.shtml)


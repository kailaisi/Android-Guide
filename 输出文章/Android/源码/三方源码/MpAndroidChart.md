继承关系

LineChart->BarLineChartBase->Chart



内部类：

* Renderer负责渲染绘制。例如坐标轴渲染器：YAxisRenderer，XAxisRenderer；数据渲染器：DataRenderer；图例渲染器：LegendRenderer。
* Transformer负责位移。通过矩阵变换，得到对应的数据在canvas上的像素信息。比如说缩放时，所对应的点变化等等
* XAxis：x轴的设置信息，比如宽度，颜色，显示的位置
* ViewPortHandler：处理图表绘图区域的组件及其偏移量
* Buffer：转化器。将用户数据转化成为视图上的点信息。具体的转化逻辑，是通过Transformer来进行变化
* ViewPortHandler处理图表绘图区域的组件及其偏移量



在Chart内部进行onDraw时，会根据用户的设置绘制不同的数据。但是具体的绘制是交给具体的Renderer去实现的。

> Chart的绘制分小组件逐个绘制的，每个组件定义自己的Render，在对应的drawXXX 方法里进行绘制，比如有专门绘制X轴的XAxisRender， 绘制Y轴的 YAxisRender， 绘制BarChart的BarChartRender， 以及绘制 Line chart的 LineChartRender， 绘制边框 backGround等等，所以剖开整个Chart的绘制逻辑来看，我们会发现Chart的绘制就是通过各种Render去 drawLine、drawRect 、不规则的drawPath, 或者贝塞尔曲线drawCubicPath(其实也是属于drawPath的范畴)；以及部分辅助，坐标轴的label 所需的drawText, 这些各种的小部件的绘制最终完成了 Chart整个的绘制。





一个BarData，绘制一张图。一张图中可以包含多种数据（BarSet）。一种数据有多个柱状图（Entry），一个柱状图对应了相关的[x，y]信息。
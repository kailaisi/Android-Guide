## Android瘦身优化

### 前言

#### 瘦身优势

影响最多的是转换率：下载转化率。

头部App有Lite版本

渠道合作商要求

### APK组成

代码相关：classes.dex

资源相关：res、asserts、resources.arsc

so相关：so文件

### APK分析

* ApkTool，反编译工具。
* Analyze APK：AS2.2之后
  * 位置：Build->Analyze APK
  * 查看Apk组成、大小、占比
  * 查看Dex文件组成
  * Apk对比
* APP性能分析网站：https://nimbledroid.com/ 
  * 文件大小及排行
  * Dex方法数和SDK方法数
  * 启动时间、内存等
* android-classyshark：二进制检查工具
  * 支持多种格式：apk，jar，class,so等
  * 使用非常便捷
  * 源码比较简略

### 瘦身实战

#### 代码混淆

##### **混淆定义**

* 代码中各个元素改成无意义的名字，比如a,b
* 以更难理解的形式重写部分逻辑
* 打乱代码格式

##### **混淆方案**

Proguard混淆

##### **三方库处理**

**基础库统一**：去掉多余的库，比如glide和fessiso。只选择一个

**选择更小的库**：picicss库更小，Android Method Count插件可以查看引入的库的大小

**仅引入所需的部分代码**：通过exclude移除部分支持。

##### 移除无用代码

* 代码业务只加不减
* 代码太多不敢删除
* AOP统计使用情况

#### 冗余资源

右键,Refactor,Remove Unused Resource，preview。可以预览无用的资源。

**图片压缩**

* 快速发展期的App没有相关规范，导致图片可能使用的都是原图
* 图片压缩：https://tinypng.com以及TinyPngPlugin压缩插件
* 图片格式的选择。
  * Webp会有大幅度的压缩。
  * PNG是无损格式，如果图片比较艳丽，那么图片就比较大
  * JPG是有损格式，图片艳丽时，图片相对小一些
* 图片，右键，convert to webp将图片转化为webp格式

**资源混淆**

* AndResGuard
  * 能够使冗长的资源路径（图片名）变短
* 图片只保留一份
* 图片放在远端，去在线加载

#### So瘦身

So是Android上的动态链接库。

**so移除**

abiFilter:设置支持的So架构，一般选择armeabi。

最优方案：

​	都放在armeabi目录，根据CPU类型加载对应架构的so文件
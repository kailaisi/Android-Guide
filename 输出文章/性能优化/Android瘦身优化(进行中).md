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

APK分析

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
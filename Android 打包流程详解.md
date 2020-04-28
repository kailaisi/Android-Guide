Android 打包流程详解（待写）

### 序言

每次点击一下Android Studio上的运行以后，就默默的等待程序运行起来。有时候出现问题百度一下，然后修修改改运行起来就可以了。但是作为一个安卓开发人员，我们应该去深层次的去理解apk的打包流程以及打包中是如何去做处理的。否则的话，什么构建优化，什么插桩技术，什么hook，一切都是免谈的。就像我们之前的一篇[Android的inflate源码详解](https://mp.weixin.qq.com/s?__biz=MzUzOTE4MTQzNQ==&mid=2247483764&idx=1&sn=0918272e5d22c5c2f813a7f4a9e6625d&chksm=facd2960cdbaa0762ee1f56247eae41df1aa45171ef2fbe7d4c99133087f35e70f468980dcd0&token=399722898&lang=zh_CN#rd)中，就涉及到了通过**AssetManager**类调用Native方法完成对 **resources.arsc** 文件的解析。当时我们也没有进行深入的探究这个 **resources.arsc** 里面到底有什么？又是如何生成的?。那么今天我们就把这部分的知识也都整理一下。

### 汇总图

我们先上一张apk的最详细的打包流程图。

![img](http://cdn.qiniu.kailaisii.com/typora/202004/23/115233-754575.png)

1. 打包资源文件，生成R.java文件
2. 处理aidl文件，生成Java文件
3. 将项目源代码编译生成class文件
4. 将所有的calss文件，生成classes.dex文件
5. 打包生成APK文件
6. 对APK进行签名
7. 对签名之后的APK文件进行对其处理。

### 各阶段分析

整体的流程图就是这样了，听着很简单。但是其实每一步都有很多东西需要我们去处理的。而且有时候我们的一些技术可以应用到这些打包的过程中。

#### 打包资源文件，生成R.java文件

使用aapt命令来打包AndroidManifest.xml文件和其他资源文件，生成R.java、Resources.arsc和res文件。

具体流程：

> 编译res和assets目录下的资源并生成resources.arsc文件，resources.arsc文件包含了资源的ID信息
>
> 调用ResourceTable.cpp文件中的parseAndAddEntry方法根据ID信息生成R.java文件。
>
> 对res目录下的xml文件进行编译，这样处理过的xml文件就被简单“加密”了

##### 生成的文件：

* res目录。这里生成的res文件都是转化为二进制xml文件的
  * 包括了animator
  * anim
  * color
  * drawable里面的xml文件
  * layout布局文件
  * menu菜单布局文件
  * values文件
  * xml应用配置文件
* R.java文件
  * R.java文件包含了编写代码时候用的资源的ID值。
* resources.arsc文件
  * resources.arsc这个文件记录了所有的应用程序资源目录的信息，包括每一个资源名称、类型、值、ID以及所配置的维度信息。是一个资源索引表，在给定资源ID和设备配置信息的情况下能快速找到资源。

知识点：

1. 这一步里面处理的资源，在应用层都是通过ID来进行访问的在【Android的inflate源码详解】中，我们获取布局就是使用的资源id。
2. xml之所以编译成二进制，是因为里面都是各种字符，不利于快速遍历。编译成二进制文件，用数字替换各种符号，一方面能快速访问，另一方面也能减少大小。
3. 有时候我们的布局文件写错了，程序报无法找到R文件的原因也可以理解了，因为这一步资源文件有问题，文件ID生成失败，肯定就没办法找到R文件了。
4. apk运行时会根据设备的不同属性（如屏幕密度）寻址，resource.arsc就是通过相同的ID，根据不同的配置索引找到最佳资源
5. aapt打包走的是单线程、流水式任务从上到下构建。所以代码量越多，资源量越多，构建过程越慢

#### 处理aidl，生成Java文件

aidl（Android Interface Definition Language，Android接口描述语言），位于android-sdk/platform-tools目录下。aidl工具解析接口定义文件然后生成相应的Java代码接口供程序调用。如果项目没用到aidl则跳过这一步。

aidl文件很多时候都是跨进行来使用的。

#### 编译项目源代码，生成class文件

Java Compiler阶段。项目中所有的Java代码，R.java和.aidl生成的文件，都会通过Java编译器（javac）编译成.class文件，生成的class文件位于工程中的bin/classes目录下。

这个地方是我们能够进行操作的地方，在AOP切面编程中，可以使用Aspect技术来实现对于通用逻辑的处理。比如点击事件、日志打印等。

#### 将所有的class文件，生成classes.dex文件

这里会将我们工程中生成的class以及第三方的class文件通过dx工具生成classess.dex文件，因为我们的dex文件会有65535的方法数的限制，所以如果方法数超过了，会打包成多个dex文件。

dx工具主要工作是将Java字节码转成Dalvik字节码、压缩常量池、消除冗余信息等。

#### 打包生成APK文件

所有没有编译的资源（如images、assets目录下的资源）；编译过的资源（resources.arsc以及二进制xml文件）、.dex文件通过apkbuilder工具打包到最终的.apk文件中。

对于未编译过的文件，打包时会直接打包到APP中，对于这一类资源的访问，应用层代码需要通过文件名对其进行访问。



调用流程：

>先以resources.arsc文件为基础生成一个apk
>
>调用ApkBuilderMain.java中的addSourceFolder方法添加工程资源，处理包括res和assets目录中的文件，添加完资源后调用addResourceFromJar方法往apk中写入依赖库
>
>调用addNativeLibraries方法添加工程libs目录下的nativey库
>
>调用sealApk关闭Apk文件。

只是点：

1. 3.0之前用apkbuilder脚本，3.0之后用
2. apkbuilder脚本也是调用的sdklib的ApkBuilderMain.java类



#### 对APK进行签名

apk生成以后，必须要进行签名才可以被安装。在开发过程中使用的是debug.keystore。

#### 签名后的APK文件对其

通过zipalign进行文件的对其操作。对其的过程是将apk包中的所有资源文件距离文件起始偏移调整为4字节的整数倍。内存对于硬件文件的读取是按照4字节的整数倍来进行读取的，这样调整以后，内存访问apk的资源文件时，就可以整页的读取，从而减少运行时内存的使用。如果每个资源的开始位置都是上一个资源之后的 4\*n字节，那么访问下一个资源就不用遍历，直接跳到4\*n字节处判断是不是一个新的资源即可。



> 本文由 [开了肯](http://www.kailaisii.com/) 发布！ 
>
> 同步公众号[开了肯]

![image-20200404120045271](http://cdn.qiniu.kailaisii.com/typora/20200404120045-194693.png)
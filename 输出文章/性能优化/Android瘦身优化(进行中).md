## Android瘦身优化

### 前言

#### 瘦身优势

* 影响最多的是转换率：下载转化率。
* 很多应用做极速版，开始做一些功能的删减，来减少Apk的包大小来留住内存和容量比较小的手机用户
* 有的应用的合作厂商会对包的体积有要求

### APK组成

我们的[APK打包流程详解]()中曾经写过APK的具体打包流程，对于代码、资源、so文件的打包过程进行过一次解析工作。从 那篇文章可以了解到，APK的组成包含了以下三部分：

> * 代码相关：classes.dex
>
> * 资源相关：res、asserts、resources.arsc
>
> * so相关：so文件

最简单的方式，我们可以将apk通过解压缩来看一下，就能发现其具体组成。

![image-20200703144344484](http://cdn.qiniu.kailaisii.com/typora/202007/03/144346-637105.png)

### APK分析

#### ApkTool，反编译工具。

* 可以获取较为完整的资源文件集
* 源码较为详细
* 使用复杂，需要多个工具结合
* 不能较好的查看整个APK的架构逻辑

#### Analyze APK：

* AS2.2之后支持
* 位置：Build->Analyze APK
* 查看Apk组成、大小、占比
* 查看Dex文件组成
* Apk对比

#### APP性能分析网站：https://nimbledroid.com/ 

* 文件大小及排行
* Dex方法数和SDK方法数
* 启动时间、内存等

#### android-classyshark：二进制检查工具

* 支持多种格式：apk，jar，class,so等
* 使用非常便捷，
* 源码目录结构清晰，而且可以通过图形化工具查看整个apk的组成架构
* 源码比较简略

### 瘦身实战

#### 代码瘦身

##### **混淆**

> 代码混淆也称为花指令，是将计算机程序的代码转换为功能上等价但是难以阅读、理解的行为。Proguard是一个免费的Java类文件压缩、优化、混淆、预先验证的工具，可以检测和移除未使用的类、字段、方法、属性，优化字节码并移除未使用的指令，并将代码中的类、字段、方法的名字改为简短、无意义的名字。

代码混淆处理的目的是通过缩短应用的类、方法和字段的名称来减小应用的大小。这是因为应用的 DEX 文件将许多类、方法和字段编入索引，那么混淆处理将可以显著缩减应用的大小。

**混淆方法**

开启Proguard混淆。设置**minifyEnabled=true**

**在Android Studio3.0中引入了R8作为原Proguard压缩和优化的替代品**，其压缩优化效果更好一些。

注意：对于Proguard，虽然效果明显，但仍然需要谨慎

>* 代码混淆会拖慢构件速度，所以debug模式下要关掉。
>* 在debug因为没有混淆。那么在Release模式下可能会出现debug模式下不出现的bug。
>* Proguard最好在项目初期就建立起规范。防止后期需要增加大量的混淆规则。

##### **三方库处理**

作为一个搬运工，那么做常做的事情就是使用三方库了。但是作为一个高级搬运工，要谨慎的选择每一个使用的三方库。

**基础库统一**：去掉多余的库，避免出现多套网络请求、图片加载等类库。

**无用的库要移除**：可能由于迭代会不再使用某些依赖的库。这时候要移除。

Android Studio提供了强大的分析工具，能够很好的帮助我们分析无用的资源、无用的lib等等。

![image-20200703153822845](http://cdn.qiniu.kailaisii.com/typora/202007/03/153823-718247.png)

然后我们输入unuse。可以看到这里可以分析很多无用的东西。我们可以使用**Unused library**功能来分析我们的依赖

![image-20200703153643817](http://cdn.qiniu.kailaisii.com/typora/202007/03/153719-782966.png)

![image-20200703153926772](C:\Users\wu\AppData\Roaming\Typora\typora-user-images\image-20200703153926772.png)

通过一段时间的分析，系统可以辅助我们来找到一些没有用到的资源。但是**注意：这里显示出来的类型并不一定是真的没有用处。删除的时候需要小心一些，不要一股脑的全部移除掉。**

**选择小而精的库**：很多的类库是有相同功能的。大而全的三方库可能未必是最好的选择。小而精才是最佳方案。

比如如果只是简单的图片使用，那么使用Picasso完全足够了。虽然Glide的功能更加强大，但是其方法数和大小都更多一些。所以对图片需求不大时，选择Picasso更佳。

**仅引入所需的部分代码**：通过exclude移除部分支持。

查找依赖库，对于使用不到的，可以通过exclude移除。

* 使用Gradle View 插件：View-Tool Windows-Gradle View
* 在Terminal中执行：./gradlew -q :app:dependencies --configuration compile
* Android Studio 右边栏的Gradle插件，找到工程目录，点开Tasks->help->dependencies执行。

通过以上方法都可以查看到项目的依赖关系。

![image-20200702225932090](http://cdn.qiniu.kailaisii.com/typora/20200702225950-210718.png)

**拷贝源码进行使用**：有时候可能我们只是使用了部分功能，那么可以下载源码，只拷贝保留比较少的文件来进行阉割处理

##### 移除无用代码

随着版本迭代，一般可能会存在以下问题：

* 代码业务只加不减
* 代码太多不敢删除

其实要根据实际情况，**移除无用代码以及无用的功能，减少代码量，最直接的体现就是Dex的体积会变小**。

在以前经常使用Lint来进行代码的检测。在Android Studio3.0+上增加了Inspect Code来帮助我们来分析代码。

![image-20200703151321912](C:\Users\wu\AppData\Roaming\Typora\typora-user-images\image-20200703151321912.png)

#### 资源瘦身

资源和代码同样重要，但是从优化效果来讲，**资源文件的瘦身效果比代码瘦身效果更好**。

##### 冗余资源移除

无用的资源文件的查找和移除相对于代码来说更加容易。![查找无用资源](http://cdn.qiniu.kailaisii.com/typora/20200707222324-284677.gif)

右键->Refactor->Remove Unused Resource->preview。

通过上述方式可以预览无用的资源，然后将无用资源移除即可。

##### 图片的处理

**Drawable目录只保留一份**

在我们新建的工程中，对于图片资源，可能会根据不同的分辨率生成了ldpi、mdpi、hdpi、xhdp、xxhdpi、xxxhdpi几种不同的文件夹来存放我们的图片文件。这种方案虽然能够很好的展示我们的图片资源，但是对于包体积来说，是个巨大的灾难。

Android为了能够更好的适配各种屏幕，如果在对应的分辨率下找不到图片，会在其他的资源文件夹中去寻找，然后按照对应的比例进行缩放。

而当低分辨率的图片在高分辨率手机上显示时，首先会放大图片，而且会引起内存的占用增大。

综合考虑以上各种情况以及市面上大部分机型的分辨率，**一般只需要一套xxdpi所对应的图片资源即可**。

**图片压缩**

快速发展期的App没有相关规范，导致图片可能使用的都是原图。从而导致前期Apk急剧增大。可以通过一些手段对图片来进行压缩处理。

可以通过[tinypng](https://tinypng.com/)和[TinyPngPlugin](https://github.com/Deemonser/TinyPngPlugin)帮助我们压缩对应的图片文件。

**使用Webp**

相同质量下，Webp更小，最多可以小30%。Android Studio中通过右击图片，选择*Convert to WebP*

![image-20200702231101306](http://cdn.qiniu.kailaisii.com/typora/20200702231102-293915.png)

可以看到，压缩效果很明显。

> 注意:由于支持无损和透明的WebP图像只能在Android 4.3和更高版本中使用，**所以您的项目必须声明一个minSdkVersion 18或更高版本**，以使用Android Studio创建无损或透明的WebP图像。

**图片格式的选择**

很多时候，我们需要根据不同的情况，来选择不同的图片资源：

* Webp会有大幅度的压缩。
* PNG是无损格式，如果图片比较艳丽，那么图片就比较大
* JPG是有损格式，图片艳丽时，图片相对小一些

* 使用Shape Drawable：很多开发者都是用Bitmap的渐变背景或者圆角图。实际上，Bitmap比Shape Drawable更大。

##### 资源混淆

resources.arsc文件是Android打包之后，资源映射文件，会将res目录下的资源和id做一个映射关系。

我们通过Analyze APK工具查看一下内部的映射，可以看到如下的信息

![image-20200707231319622](http://cdn.qiniu.kailaisii.com/typora/20200707231320-36788.png)

如果我们的资源文件的路径越短，那么映射关系就越简单，资源映射表就越小，从而能够达到瘦身的效果。

**AndResGuard**

[AndResGuard](https://github.com/shwenzhang/AndResGuard/blob/master/README.zh-cn.md)是微信的开源库，能够对资源进行混淆，一方面能够保护res资源的可读性，同时也能够实现对apk的瘦身工作。他会将原本冗长的资源路径变短，例如将`res/drawable/wechat`变为`r/d/a`。通过AndResGuard处理后的文件：

![image-20200708220413364](http://cdn.qiniu.kailaisii.com/typora/20200708220413-79059.png)

可以看到通过混淆处理以后，资源文件的路径变得很短了。

**云端存放**

对于一些图片资源，我们可以通过存放在云端，当软件运行的时候，通过在线加载的方式将图片下载到本地，进行本地的缓存显示。

这种方案对于一些OSS节点来说可能会造成费用问题。需要综合考虑。

#### So瘦身

So是Android上的动态链接库。通过so机制能够让开发者最大化利用已有的C和C++代码，达到重用的效果。

而且so文件是二进制，没有编译的开销，实现的功能比纯Java要快一些。

##### so移除

Android支持7种CPU架构。理论上对应架构的so的执行效率最高。但是会增加文件大小。

一般的应用完全没有必要在APK中添加所有架构的so文件。可以通过abiFilter:设置支持的So架构，一般选择**arm-v7a**（万金油）。

**具体操作：**

如果在build.gradle 中对abiFilters进行了配置，那么只有配置过的目录下的so 文件才会被打包到apk安装包中。

```
ndk {
    abiFilters  "armeabi-v7a"  // 指定要ndk需要兼容的架构(这样其他依赖包里mips,x86,armeabi,arm-v8之类的so会被过滤掉)
}
```

##### 最优方案

对性能敏感的模块，都放在armeabi目录，根据CPU类型加载对应架构的so文件。这种是因为如果某个so使用了x86的话，会要求所有的其他模块也都要有x86的so文件，否则就会崩溃。

通过这中方法既能实现敏感模块加载对应架构so资源，也能够实现对无关紧要的模块进行兼容处理。

```java
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.LoLLIPOP){
            abi=Build.CPU_ABI;
        }else{
            abi=Build.SUPPORTED_ABIS[0];
        }
        if(TextUtils.equals(abi,"ARMv7")){
            //加载特定平台下使用的So文件
            System.loadLibrary(**.so);
        }else{
            //正常加载
        }
```

这样即减少了Apk的体积，也不影响性能敏感模块的执行。

##### 动态加载so文件

Androi加载so文件本身是一种**运行时的动态加载**可执行代码的行为，所以我们完全可以把so文件做成动态下发。发布的 APK 不包含 Native 代码，启动时根据不同的架构下载相应的 so 文件。

具体的流程如下：

1. 判断目录中是否存在so文件，存在则直接调用sdk
2. 不存在so文件则从服务器下载相应的so库（可以通过zip包下载再解压的方法）
3. 将so库复制到/data/data/packagename/..
4. 通过System.load(全路径)加载。**注意有的so依赖于其他so。所以要注意加载顺序**

##### 插件化

通过插件化，实现不同模块的动态下发。从而从根源上解决到包过大的问题。对于插件化实现和原理还不太了解，等待以后再研究吧。o(∩_∩)o 

### 参考

https://www.jianshu.com/p/99f3c09982d4

https://developer.android.google.cn/studio/build/shrink-code?hl=zh-cn

https://www.baidu.com/link?url=M0hz3VFKaed8elPeONVSFtTLoEBisKg5sdbuvEdqEE2hEUFPJoSxB-IbJuwJ7poT&wd=&eqid=94fa2ae1001dc651000000035efdf405

https://www.jianshu.com/p/32b4d92d4195

https://mp.weixin.qq.com/s/X58fK02imnNkvUMFt23OAg

https://blog.csdn.net/tantion/article/details/79634694
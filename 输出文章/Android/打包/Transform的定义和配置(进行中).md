### Transform的定义和配置

Transform对应的英文说明如下：

```E
 A Transform that processes intermediary build artifacts.
 For each added transform, a new task is created.
```

当我们需要对编译时[产生的Class文件，在转换成Dex之前](Android 打包流程详解.md)做一些处理（字节码插桩，替换父类....）。我们可以通过*Gradle*插件来注册我们编写的*Transform*。注册后的*Transform*也会被Gradle包装成一个[Gradle Task](Gradle详解(计划).md)，这个Transform Task会在java compile Task执行完毕之后运行。一般我们使用*Tranform*会有下面几种场景

* 需要对编辑生成的class文件做自定义的处理
* 需要读取编译产生的class文件，做一些其他事情，但是不需要修改它
* 比如Hilt DI框架会修改superclass为特定的class，
* Hugo耗时统计库会在每个方法插入代码来统计方法耗时，
* InstantPatch热修复，在所有方法前插入一个预留的函数，可以将有bug的方法替换成下发的方法
* CodeCheck代码检测，都是使用Transform来做处理

![image-20210709101028258](/Users/jj/Library/Application Support/typora-user-images/image-20210709101028258.png)

在流程图可以看到，在打包过程中，是有多个transform的，而且每个transform都是有输入输出。每个transform的输出会是下一个transform的输入。



### 自定义插件

* 输入输出

  * **TransformInput**是指输入文件的一个抽象，包括：

    DirectoryInput集合是指以源码的方式参与项目编译的所有目录结构及其目录下的源代码。
    JarInput集合是指以jar包方式参与项目编译的所有本地jar包和远程jar包（包括aar）。

  * **TransformOutputProvider** 通过它可以获取到输出路径等信息。

![image-20210709115110917](/Users/jj/Library/Application Support/typora-user-images/image-20210709115110917.png)

####  插件开发模式

* Buildscript：插件写在build.gradle文件中，一般用于简单的逻辑，只在该build.gradlewenjianzhong kejian 
* buildSrc模式：将插件源代码放在/buildSrc/src/main/groovy中，只对该项目可见，适用于逻辑较为复杂
* 独立项目：独立的groovy和java项目，可以将项目打包成jar文件，一个jar文件包可以包含多个插件入口，将文件包发布到托管平台上，供他人使用。



#### Extension扩展配置

它的作用就是通过实现自定义的bean对象，可以在Gradle脚本中增加类似“android”这样命名空间的配置信息，Gradle可以识别这种配置，并读取里面的配置内容。通常使用ExtensionContainer来创建并管理Extension
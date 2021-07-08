Transform的定义和配置

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
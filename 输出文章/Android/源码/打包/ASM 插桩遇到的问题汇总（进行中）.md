### ASM 插桩遇到的问题汇总

* 在进行是否属于本项目判断的时候，通过传入的包名来判断是否需要是属于我们需要插入的项目包。传入的是“com/example”，但是发现打印的文件路径是"\\"。

所以在传入名称需要根据具体的系统来处理了。我这边用的是File.separator来进行了转换。

* 在进行字节码插入的过程中，对后缀是“Activity”的文件插入finish的方法。结果报javassist.bytecode.DuplicateMemberException: duplicate method: closeKeyboard in com.example.selfinspection.base.BaseActivity
* javassist.NotFoundException: com.example.selfinspection.base.BaseViewModel

这个类是我们的基类，在使用过程中发现传入的包名错误

* DexArchiveBuilderException  error processing

遇到这个问题主要是因为buildSrc中使用的build.gradle的版本和主工程的build.gradle的版本不一致，导致的问题。将两个修改为一致的即可。

还有一种情况是因为分包导致的问题，需要使用mutilDex功能
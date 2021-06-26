Android类加载器

主要的系统类加载器

* BootClassLoader：Android系统启动时，用来预加载常用的类

* PathClassLoader，复杂的加载系统类和应用程序类，通常用来加载已经安装的apk的dex文件，实际上外部存储的dex文件也能加载
* DexClassLoader：可以加载dex文件以及包含dex的压缩文件（apk,dex,jar,zip）\
* BaseDexClassLoader：实现应用层类文件的加载，而真正的加载逻辑委托给PathList来完成。



![image-20210623100537105](/Users/jj/booknote/输出文章/计算机基础/image-20210623100537105.png)

当我们加载某个Activity的时候，DexPathClassLoader会依次从dex1，dex2，dex3文件中去加载类，如果我们的activity在dex3中，那么就会导致加载比较慢。

**所以有一种启动优化方案是，将我们启动时候所需要的类都打包到dex1文件中。**

同时如果类在dex1，dex2中都存在，那么其实只加载dex1中的类文件，这种机制引申出了**通过Patch.dex来实现热修复功能。**
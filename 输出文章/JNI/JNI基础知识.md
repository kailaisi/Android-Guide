## JNI基础知识

### JNI基础知识

JNI（Java Native Interface），即Java本地接口。是Java与其他语言的桥梁。



#### 加载动态库

加载动态库的两种方法：

* System.load(String filename)      绝对路径
* System.loadLibrary()  //从System lib路径下加载

##### JNI_OnLoad

调用System.loadLibrary()函数时，内部会去查找so中的JNI_OnLoad函数，如果存在，则调用。JNI_OnLoad返回JNI的版本。比如 JNI_VERSION_1_6、JNI_VERSION_1_8。

### Native方法注册

##### 静态注册

使用的**Java_com_example_cdemo_MainActivity_printPerson**这种，通过名称和包名来与java方法进行匹配的就是静态注册。通过指针来与JNI进行关联。

声明Native方法的类需要用javah生成头文件。

* JNI层的函数名称过长。

##### 动态注册

将Java中的方法在代码中动态的与JNI方法进行对应。

##### 

Java层调用Native层所使用的。

https://juejin.im/post/5ee89c96e51d45788619e13e
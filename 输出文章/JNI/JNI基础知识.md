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

将Java中的方法在代码中动态的与JNI方法进行对应。从而解决静态注册时包名过长的问题

动态注册使用方法：

```java
public class MainActivity extends AppCompatActivity {
    public native void dynamicJavaFunc1();

    public native int dynamicJavaFunc2(int i);
}

```

在对应的c++类中进行注册

```c++
void dynamicJavaFunc1() {
    LOGE("调用了 dynamicJavaFunc1");
}

jint dynamicJavaFunc2(JNIEnv *env, jobject thiz, jint i) {
    LOGE("调用了 dynamicJavaFunc2");
    return 666;
}

/*这里是需要动态注册的方法对应的数组*/
static const JNINativeMethod methods[] = {
        {"dynamicJavaFunc1", "()V",  (void *) dynamicJavaFunc1},
        {"dynamicJavaFunc2", "(I)I", (void *) dynamicJavaFunc2}
};
/*需要动态注册native方法的类名*/
static const char *mClassName = "com/example/cdemo/MainActivity";

jint JNI_OnLoad(JavaVM *vm, void *unused) {
    JNIEnv *env = NULL;
    /*获取JNIEnv,这里的第一个参数是二级指针*/
    int result = vm->GetEnv(reinterpret_cast<void **>(env), JNI_VERSION_1_6);
    if (result != JNI_OK) {
        LOGE("获取Env失败");
        return JNI_VERSION_1_6;
    }
    /*注册方法的类*/
    jclass classMain = env->FindClass("com/example/cdemo/MainActivity");
    /*调用动态注册方法，将方法进行注册*/
    result = env->RegisterNatives(classMain, methods, 2);
    if (result != JNI_OK) {
        LOGE("动态方法注册失败");
        return JNI_VERSION_1_2;
    }
    return JNI_VERSION_1_6;
}
```

##### NDK

NDK原生开发套件。是一套工具，能够使用户在Android应用中使用C和C++代码，并且能够提供多个平台的对应库。

##### makefile

自动化编译。定义了一些列的规则，指定哪些文件先编译，哪些文件后编译，如何进行链接等等。在Android中使用Android.mk文件来配置makefile。

##### cmake

CMake是跨平台的构件工具，可以用简单的语句来描述所有平台的安装编译过程。能够输出各种makefile或者project文件。

Cmake并不是直接构建软件，而是一个工具。用于产生其他工具的脚本（makefile或者project），然后再依据这个产生的工具进行构建。

Android Studio利用CMake生成ninja。

##### CMakeLists.txt



#### 使用第三方库

##### 

Java层调用Native层所使用的。

https://juejin.im/post/5ee89c96e51d45788619e13e
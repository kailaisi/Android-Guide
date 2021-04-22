JVM

#### 字节码结构

Java文件在虚拟机运行，最终是将其转化为二进制文件来进行记录文件名、属性、方法名等信息的。

对应的字节码结构如下：

```java
ClassFile {
    u4             magic;             //魔数，4个字节固定值0xCAFEBABE
    u2             minor_version;     //小版本号，2个字节
    u2             major_version;	  //主版本号，2个字节
    u2             constant_pool_count; //常量池个数
    cp_info        constant_pool[constant_pool_count-1];//常量池数据数组，大小为count-1。
    u2             access_flags;  //访问标志（private/protected/public）
    u2             this_class;    //类名
    u2             super_class;   //父类名
    u2             interfaces_count; //实现的接口数量，2个字节，所以最多
    u2             interfaces[interfaces_count];//接口信息
    u2             fields_count; 	//属性数量
    field_info     fields[fields_count];	//属性信息
    u2             methods_count;	//方法数量
    method_info    methods[methods_count];//方法信息
    u2             attributes_count;	//
    attribute_info attributes[attributes_count];
}
```

#### 反编译

字节码是交给计算机去处理的程序，我们可以对应的字节码程序反编译成对应的文件来查看

```bash
javap Hello.class

public class Hello {
  public Hello();
  public static void main(java.lang.String[]);
}
```

我们也可以通过*-c  -v*来显示的更加详细一些

```bash
  javap -v Hello.class
  
  Last modified 2021-4-22; size 413 bytes
  MD5 checksum 84e6dff93470c06b67bbb3cc92a7e7e1
  Compiled from "Hello.java"
public class Hello
  minor version: 0
  major version: 52
  flags: ACC_PUBLIC, ACC_SUPER
Constant pool:
   #1 = Methodref          #6.#15         // java/lang/Object."<init>":()V
   .........
  #28 = Utf8               (Ljava/lang/String;)V
{
  public Hello();
    descriptor: ()V
    flags: ACC_PUBLIC
    Code:
      stack=1, locals=1, args_size=1   // stack=1，表示要分配1个大小的操作数栈。
      								// locals=1, 本地变量，在字节码结构中，我们说过，本地变量表中的数组个数是count-1，就是因为总会有一个0位置的本地变量表，也就是我们常用的this！。
      								// args_size=1,参数个数是1，其实这种参数是this
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: return
      LineNumberTable:
        line 1: 0

  public static void main(java.lang.String[]);
    descriptor: ([Ljava/lang/String;)V
    flags: ACC_PUBLIC, ACC_STATIC
    Code:
      stack=2, locals=1, args_size=1
         0: getstatic     #2                  // Field java/lang/System.out:Ljava/io/PrintStream;
         3: ldc           #3                  // String Hello JVM
         5: invokevirtual #4                  // Method java/io/PrintStream.println:(Ljava/lang/String;)V
         8: return
      LineNumberTable:
        line 3: 0
        line 4: 8
}
```

我们这里对其进行逐个分析

##### 构造方法

````bash
  public Hello();
    descriptor: ()V 			//方法描述
    flags: ACC_PUBLIC			//类型是public
    Code:
      stack=1, locals=1, args_size=1   // stack=1，表示要分配1个大小的操作数栈。
      								// locals=1, 本地变量，在字节码结构中，我们说过，本地变量表中的数组个数是count-1，就是因为总会有一个0位置的本地变量表，也就是我们常用的this。
      								// args_size=1,参数个数是1，其实这种参数是this
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: return
      LineNumberTable:
        line 1: 0

````



解释：

-c：表明会显示对应的方法的执行过程

-v：最详细的信息展示。可以拿到常量池信息，方法信息等等。

#### 参考：

[JVM字节码](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html)


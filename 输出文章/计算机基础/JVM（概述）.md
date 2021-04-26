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

我们也可以通过*-c  -v*来显示的更加详细一些。

* -c：表明会显示对应的方法的执行过程

* -v：最详细的信息展示。可以拿到常量池信息，方法信息等等。

```bash
  javap -v Hello.class
  
  Last modified 2021-4-22; size 413 bytes
  MD5 checksum 84e6dff93470c06b67bbb3cc92a7e7e1
  Compiled from "Hello.java"
public class Hello
  minor version: 0
  major version: 52
  flags: ACC_PUBLIC, ACC_SUPER               //类的属性
Constant pool:   //常量池数据
   #1 = Methodref          #6.#15         // java/lang/Object."<init>":()V
   #2 = Fieldref           #16.#17        // java/lang/System.out:Ljava/io/PrintStream;
   #3 = String             #18            // Hello JVM
   #4 = Methodref          #19.#20        // java/io/PrintStream.println:(Ljava/lang/String;)V
   #5 = Class              #21            // Hello
   #6 = Class              #22            // java/lang/Object
   #7 = Utf8               <init>
   #8 = Utf8               ()V
   #9 = Utf8               Code
  #10 = Utf8               LineNumberTable
  #11 = Utf8               main
  #12 = Utf8               ([Ljava/lang/String;)V
  #13 = Utf8               SourceFile
  #14 = Utf8               Hello.java
  #15 = NameAndType        #7:#8          // "<init>":()V
  #16 = Class              #23            // java/lang/System
  #17 = NameAndType        #24:#25        // out:Ljava/io/PrintStream;
  #18 = Utf8               Hello JVM
  #19 = Class              #26            // java/io/PrintStream
  #20 = NameAndType        #27:#28        // println:(Ljava/lang/String;)V
  #21 = Utf8               Hello
  #22 = Utf8               java/lang/Object
  #23 = Utf8               java/lang/System
  #24 = Utf8               out
  #25 = Utf8               Ljava/io/PrintStream;
  #26 = Utf8               java/io/PrintStream
  #27 = Utf8               println
  #28 = Utf8               (Ljava/lang/String;)V
{
  public Hello();
    descriptor: ()V			          //V 表示返回值是void
    flags: ACC_PUBLIC
    Code:
      stack=1, locals=1, args_size=1   // stack=1，表示要分配1个大小的操作数栈。
      								// locals=1, 本地变量表，在字节码结构中，我们说过，本地变量表中的数组个数是count-1，就是因为总会有一个0位置的本地变量表，也就是我们常用的this！。
      								// args_size=1,参数个数是1，其实这种参数是this。所有的方法其实都有一个this的参数。
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
      								// args_size=1,参数个数是1，其实这种参数是this。所有的方法其实都有一个this的参数。
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: return
      LineNumberTable:
        line 1: 0

````

##### main方法

```bash
  public static void main(java.lang.String[]);
    descriptor: ([Ljava/lang/String;)V
    flags: ACC_PUBLIC, ACC_STATIC
    Code:
      stack=2, locals=1, args_size=1    // stack=2，表示要分配1个大小的操作数栈。
      								// locals=1, 本地变量，在字节码结构中，我们说过，本地变量表中的数组个数是count-1，就是因为总会有一个0位置的本地变量表，也就是我们常用的this。
      								// args_size=1,参数个数是1，其实这种参数是this。所有的方法其实都有一个this的参数。
         0: getstatic     #2                  // Field java/lang/System.out:Ljava/io/PrintStream;   getstatic指令：获取指定类的静态域（位于常量池的#2位置，也就是System.out对象的PrintStream），压入到栈顶。
         3: ldc           #3                  // String Hello JVM  ldc：将int，float或string常量从常量池（#3号位置）取出推送至栈顶
         5: invokevirtual #4                  // Method java/io/PrintStream.println:(Ljava/lang/String;)V                     .//调用实例方法，栈中的方法PrintStream，传入的是一个string参数
         8: return
      LineNumberTable:
        line 3: 0
        line 4: 8
```

##### 实战

通过字节码我们可以看下为什么内部类会持有外部类的应用。

Java代码如下

```java
public class Hello{
	public static void main(String[] args){
		System.out.println("Hello JVM");
	}

	public class Inner
	{
		
	}
}
```

反编译Inner类：javap -v Hello$Inner.class

![image-20210426222834555](http://cdn.qiniu.kailaisii.com/typora/20210426222836-886102.png)

可以看到，在生成的字节码中，是**持有一个其所在的类的变量**，

#### 参考：

[JVM字节码](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html)


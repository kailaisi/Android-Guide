## Android的inflate源码详解







引申知识点：

1. 在通过native方法获取资源时，会完成对 **resources.arsc** 文件的解析，创建一个 **ResTable** 对象，该对象包含了应用程序的全部资源信息。之后通过 **ResTable** 的getResource来获取指定的资源，对于xml布局文件，则只是获取一个引用，需要通过 res.resolveReference进行二次解析，得到id对应的资源项(一个字符串，布局文件的路径，指向经过编译的二进制格式保存的xml资源文件）。然后通过 **loadXmlResourceParser** 对这个路径的xml进行解析，得到不问文件解析对象 **XmlResourceParser** 。
2. 在进行解析的时候，对View的解析，是使用的参数为(Context.class, AttributeSet)的那个构造方法


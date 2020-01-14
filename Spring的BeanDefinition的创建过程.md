## Spring的BeanDefinition的创建过程

本文是基于注解的：AnnotationBeanDefinition的

1    初始化AnnotationConfigApplicationContext类，里面生成了路径扫描器

2. 在scan类中，通过doSacn()执行扫描过程，将指定的包下面所有能够被Spring容器管理的类，并包装成为BeanDefinition（具体的是ScannedGenericBeanDefinition），并且设置其对应的别名，作用域等。
3. 将BeanDefinition进行一层封装，变成BeanDefinitionHolder对象，这个类里面还有对应的类的名称。->可以学到一点，如果类不能动了，但是需要扩展，那么可以通过这种方式，来进行扩展包装。包装者模式
4. 注册BeanDefinition->这里从scan类，然后回来调用AnnotationConfigApplicationContext里面的注册方法，注册方法交给了beanFactory类(也实现了BeanDefinitionRegistry)去注册->委派模式
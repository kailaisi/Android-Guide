AspectJ切入点@Pointcut语法详解

>\*：匹配任何数量字符；
>..：匹配任何数量字符的重复，如在类型模式中匹配任何数量子包；而在方法参数模式中匹配任何数量参数
>+：匹配指定类型的子类型；仅能作为后缀放在类型模式后边

表达式示例：

```
execution(* com.sample.service.impl..*.*(..))
```

详述：

- execution()，表达式的主体
- 第一个“*”符号，表示返回值类型任意；
- com.sample.service.impl，AOP所切的服务的包名，即我们的业务部分
- 包名后面的“..”，表示当前包及子包
- 第二个*，表示类名，*即所有类
- .*(..)，表示任何方法名，括号表示参数，两个点表示任何参数类型



execution表达式语法格式

```
execution(<修饰符模式>？<返回类型模式><方法名模式>(<参数模式>)<异常模式>?)
```

### 常用示例

通过方法名定义切点

```
execution(public * *(..))
```

匹配所有目标类的public方法，第一个\*代表方法返回值类型，第二个\*代表方法名，而".."代表任意入参的方法；

```
execution(* *on*(..))
```

匹配所有目标类的以on为前缀的方法，第一个\*代表任意方法返回类型，而on\*代表任意以on开头的方法名。

### **通过类定义切点**

```
execution(* com.taotao.Waiter.*(..))
```

匹配Waiter接口的所有方法，第一个\*代表任意返回类型，“com.taotao.Waiter.*”代表Waiter接口中的所有方法。

```
execution(* com.taotao.Waiter+.*(..))
```

匹配Waiter接口及其所有实现类的方法

### **通过包名定义切点**

注意：在包名模式串中，"**.***"表示包下的所有类，而“**..***”表示包、子孙包下的所有类。

```
execution(* com.taotao.*(..))
```

匹配com.taotao包下所有类的所有方法

```
execution(* com.taotao..*(..))
```

匹配 **com.taotao** 包及其子孙包下所有类的所有方法，如com.taotao.ui,com.taotao.user.fragmeng等包下的所有类的所有方法。

```
execution(* com..*.*Dao.find*(..))
```

匹配以com开头的任何包名下后缀为Dao的类，并且方法名以find为前缀，如com.taotao.UserDao#findByUserId()、com.taotao.dao.ForumDao#findViewById()的方法都是匹配切点。

### **通过方法入参定义切点**

切点表达式中方法入参部分比较复杂，可以使用“*”和“..”通配符，其中“*”表示任意类型的参数，而“..”表示任意类型参数且参数个数不限。

```java
* joke(String, *)
```

匹配目标类中joke()方法，该方法第一个入参为String类型，第二个入参可以是任意类型

```java
execution(* joke(String, int))
```

匹配类中的joke()方法，且第一个入参为String类型，第二个入参 为int类型

```java
execution(* joke(String, ..))
```

匹配目标类中joke()方法，该方法第一个入参为String，后面可以有任意个且类型不限的参数

## 常见的切点表达式

- **匹配方法签名**

```java
// 匹配指定包中的所有方法
execution(* com.xys.service.*(..))

// 匹配当前包中的所有public方法
execution(public * UserService.*(..))

// 匹配指定包中的所有public方法，并且返回值是int类型的方法
execution(public int com.xys.service.*(..))

// 匹配指定包中的所有public方法，并且第一个参数是String，返回值是int类型的方法
execution(public int com.xys.service.*(String name, ..))
```

- **匹配类型签名**

```java
// 匹配指定包中的所有方法，但不包括子包
within(com.xys.service.*)

// 匹配指定包中的 所有方法，包括子包
within(com.xys.service..*)

// 匹配当前包中的指定类中的方法
within(UserService)

// 匹配一个接口的所有实现类中的实现的方法
within(UserDao+)
```

- **匹配Bean名字**

```java
// 匹配以指定名字结尾的bean中的所有方法
bean(*Service)
```

- **切点表达式组合**

```java
// 匹配以Service或ServiceImpl结尾的bean
bean(*Service || *ServiceImpl)

// 匹配名字以Service结尾，并且在包com.xys.service中的Bean
bean(*Service) && within(com.xys.service.*)
```
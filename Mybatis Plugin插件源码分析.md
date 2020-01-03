## Mybatis Plugin插件源码分析

之前在[SpringBoot+MyBatis读写分离实现]([http://www.kailaisii.com//archives/SpringBoot+MyBatis%E8%AF%BB%E5%86%99%E5%88%86%E7%A6%BB%E5%AE%9E%E7%8E%B0](http://www.kailaisii.com//archives/SpringBoot+MyBatis读写分离实现))这一篇文章中，通过Mybatis的Plugin方式实现了对于MySQL数据库的读写分离功能。本文就通过对Mybatis的Plugin的源码进行了阅读解析，来一步步分析它的具体实现方式。[参考链接](https://www.jianshu.com/p/b82d0a95b2f3)

在进行Mybatis源码解析的过程中，感觉是它对 **代理模式**+ **责任链模式** 的一种很好的应用场景，所以特此记录一下。

### JDK动态代理

jdk动态代理是代理模式的一种实现方式，其只能代理接口

#### 实现步骤

1. 新建接口
2. 为接口创建实现类
3. 创建代理类，代理类实现java.lang.reflect.InvocationHandler接口
4. 通过**Proxy.newProxyInstance** 方法，生成对应的代理类

```
interface Target {
    String execute(String name);
}
public class TargetImpl implements Target {
    @Override
    public String execute(String name) {
        System.out.println("execute()"+name);
        return name;
    }
}
class TargetProxy implements InvocationHandler {
    private Object target;

    TargetProxy(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("开始...");
        Object result = method.invoke(target, args);
        System.out.println("结束...");
        return result;
    }

    //静态方法，用于生成代理类
    public static Object wrap(Object target){
        return Proxy.newProxyInstance(target.getClass().getClassLoader(),target.getClass().getInterfaces(),new TargetProxy(target));
    }
}
class TestProxy {
    public static void main(String[] args) {
        Target target = new TargetImpl();
        Target proxyTarget = (Target) TargetProxy.wrap(target);
        proxyTarget.execute("kailaisi");
    }
}
```

最后的运行结果

```
开始...
execute()kailaisi
结束...
```

这种是最简单的代理模式的实现。但是在实际应用中，使用的并不特别多。因为上面的代码，把代理类的业务代码耦合到了 **invoke** 方法中了。这是不符合面向对象编程的思想的，而且不具有可复用性。

那么有办法，把我们的业务逻辑抽取出来么？

其实代理对象对被代理对象的方法的调用主要是靠

```
Object result = method.invoke(target, args);
```

这个方式来实现的，这个里面主要是 **method** ,**target** ,**args** 这三个参数。我们的代理类，主要是在该方法的前后来进行自己的逻辑处理。我们现在把这三个参数放到一个 **Invocation** 类中，然后将这个类交给其他人，其他人就可以对这个类想怎么玩就怎么玩了~~~。

```
public class Invocation {

    /**
     * 目标对象
     */
    private Object target;
    /**
     * 执行的方法
     */
    private Method method;
    /**
     * 方法的参数
     */
    private Object[] args;
    
    //省略getset

    public Invocation(Object target, Method method, Object[] args) {
        this.target = target;
        this.method = method;
        this.args = args;
    }

    /**
     * 执行目标对象的方法
     * @return
     * @throws Exception
     */
    public Object process() throws Exception{
       return method.invoke(target,args);
    }
}
```

然后呢，我们需要把业务的处理放到其他地方。

现在我们定义一个接口

```
interface Interceptor {
    /**
     * 具体拦截处理
     * @param invocation
     * @return
     * @throws Exception
     */
    Object intercept(Invocation invocation) throws Exception;
}
```

这样，我们就可以在 **Intercept** 的实现类中的  **intercept** 方法中使用 **Invocation** 对象，在这里面就可以进行业务的处理，在需要的时候通过 **process()** 对被代理类进行方法的调用。

现在看一下我们改造之后的实现

```
interface Target {
    String execute(String name);
}
//拦截接口
interface Interceptor {
    /**
     * 具体拦截处理
     * @param invocation
     * @return
     * @throws Exception
     */
    Object intercept(Invocation invocation) throws Exception;
}
//拦截中做的具体的业务处理
public class TimeIntercepter implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Exception {
        System.out.println("开始时间....");
        Object result = invocation.process();
        System.out.println("结束时间...");
        return result;
    }
}
class TargetProxy implements InvocationHandler {
    //代理对象
    private Object target;
    //拦截器
    private Interceptor interceptor;
    TargetProxy(Object target, Interceptor interceptor) {
        this.target = target;
        this.interceptor = interceptor;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Invocation invocation = new Invocation(target,method,args);
        Object result =interceptor.intercept(invocation);
        return result;
    }

    //静态方法，用于生成代理类
    public static Object wrap(Object target,Interceptor interceptor){
        return Proxy.newProxyInstance(target.getClass().getClassLoader(),target.getClass().getInterfaces(),new TargetProxy(target, interceptor));
    }
}

//测试类
class TestProxy {
    public static void main(String[] args) {
        Target target = new TargetImpl();
        TimeIntercepter timeIntercepter = new TimeIntercepter();
        Target proxyTarget = (Target) TargetProxy.wrap(target,timeIntercepter);
        proxyTarget.execute("kailaisi");
    }
}

```

最后运行结果如下

```
开始时间....
execute()kailaisi
结束时间...
```

到这里，我们通过Intercepter这个具体的类可以将业务代码和原有的逻辑进行分离了。

仔细看看，代码还是不太优雅，在我们测试方法中，还得需要通过 **TargetProxy.wrap()** 方法将拦截器和代理对象进行关联。我们可以再进一步，将**TargetProxy.wrap()** 这个方法在 **Intercepter** 实现类中进行处理。我们继续优化代码....

```
interface Interceptor {
    /**
     * 具体拦截处理
     * @param invocation
     * @return
     * @throws Exception
     */
    Object intercept(Invocation invocation) throws Exception;

    /**
     * 要代理的类
     * @param target
     * @return
     */
    Object wrap(Object target);
}


public class TimeIntercepter implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Exception {
        System.out.println("开始时间....");
        Object result = invocation.process();
        System.out.println("结束时间...");
        return result;
    }

    @Override
    public Object wrap(Object target) {
        return TargetProxy.wrap(target,this);
    }
}

class TestProxy {
    public static void main(String[] args) {
        Target target = new TargetImpl();
        TimeIntercepter timeIntercepter = new TimeIntercepter();
        Target proxyTarget= (Target) timeIntercepter.wrap(target);
        proxyTarget.execute("kailaisi");
    }
}
```

在我们进行拦截器处理时，很多时候是多个拦截器一起使用的，比如过，一个拦截器打印sql语句，一个拦截器统计sql执行时间，一个拦截器进行主从切换。这时候我们再看一下我们的测试类要怎么写

```
class TestProxy {
    public static void main(String[] args) {
        Target target  = new TargetImpl();
        TimeIntercepter timeIntercepter = new TimeIntercepter();
        Target proxyTarget= (Target) timeIntercepter.wrap(target);
        TransactionIntercepter transactionIntercepter = new TransactionIntercepter();
        proxyTarget = (Target) transactionIntercepter.wrap(proxyTarget);
        proxyTarget.execute("kailaisi");
    }
}
```

我们看一下运行的结果

```
开启事务........
开始时间....
execute()kailaisi
结束时间...
提交事务........
```

上面其实已经实现了我们的功能了，但是对于多个拦截器，使用的时候不太美观，现在 **责任链模式** 可以闪亮登场了

### 责任链模式

#### 概念

~~~
请求创建了一个接收者对象的链。这种模式给予请求的类型，对请求的发送者和接收者进行解耦。
~~~

我们通过一个类来统一管理这些拦截器。

```
public class InterceptorChain {
    private List<Interceptor> interceptorList = new ArrayList<Interceptor>();
    //这个方法可以将所有的拦截器通过链式来进行统一的代理处理
    public Object pluginAll(Object target) {
        for (Interceptor interceptor : interceptorList) {
            target = interceptor.wrap(target);
        }
        return target;
    }

    public InterceptorChain addInterceptor(Interceptor interceptor) {
        interceptorList.add(interceptor);
        return this;
    }

    /**
     * 为了安全，此处返回一个不可修改的集合
     * @return
     */
    public List<Interceptor> getInterceptorList() {
        return Collections.unmodifiableList(interceptorList);
    }
}

class TestProxy {
    public static void main(String[] args) {
        Target target = new TargetImpl();
        InterceptorChain chain = new InterceptorChain();
        TimeIntercepter timeIntercepter = new TimeIntercepter();
        TransactionIntercepter transactionIntercepter = new TransactionIntercepter();
        //想加多少加多少
        chain.addInterceptor(timeIntercepter)
                .addInterceptor(timeIntercepter)
                .addInterceptor(transactionIntercepter)
                .addInterceptor(transactionIntercepter);
        Target proxyTarget = (Target) chain.pluginAll(target);
        proxyTarget.execute("kailaisi");
    }
}

```

到现在已经逐步实现了Mybatis中的Plugin功能，实现了对于拦截器的统一封装处理。
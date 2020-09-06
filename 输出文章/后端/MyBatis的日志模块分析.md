## MyBatis的日志模块分析

在实际使用过程中MyBatis是不会自带日志系统的，如果我们想进行日志的打印，只要把相关的第三方日志系统添加到我们的系统中，那么就可以进行MyBatis的日志打印功能了。对于Mybatis如何实现的这种功能一直很好奇，今天就打开源码撸一把~~

Mybatis的logging包很好找，是一个单独的模块，里面定义了一个Log接口

```
public interface Log {
  boolean isDebugEnabled();
  boolean isTraceEnabled();
  void error(String s, Throwable e);
  void error(String s);
  void debug(String s);
  void trace(String s);
  void warn(String s);
}
```

![image-20200108151808991](C:\Users\wu\AppData\Roaming\Typora\typora-user-images\image-20200108151808991.png)

可以看到，这个接口有很多实现类，名字也特别简单明了，一看就是Slf4打印，Log4j打印，Jdk打印等等。。我们找一个最常用的 **Log4jImpl** 来看一下

```
public class Log4jImpl implements Log {
  
  private static final String FQCN = Log4jImpl.class.getName();

  private final Logger log;

  public Log4jImpl(String clazz) {
    log = Logger.getLogger(clazz);
  }

  @Override
  public boolean isDebugEnabled() {
    return log.isDebugEnabled();
  }
  ....
  @Override
  public void warn(String s) {
    log.log(FQCN, Level.WARN, s, null);
  }

}
```

代码很简单，有一个构造方法，然后实现了上面定义的 **Log** 接口，里面具体的实现方式，是使用的Log4j这个三方库的方法。其实其他的实现类也都很简单，都是将相关的三方打印类库进行了一层包装。那么项目中具体使用的时候，它是如何知道我们用的是哪个三方类呢？

这里其实是有一个工厂方法：**LogFatory** ，就在logging包下。

```
  static {
    tryImplementation(new Runnable() {
      @Override
      public void run() {
        useSlf4jLogging();
      }
    });
    ....
  }
//设置要使用的Log实现类
public static synchronized void useSlf4jLogging() {
    setImplementation(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);
}

private static void setImplementation(Class<? extends Log> implClass) {
    try {
      //获取有一个String类型参数的构造方法
      Constructor<? extends Log> candidate = implClass.getConstructor(String.class);
      //反射生成具体的实现类
      Log log = candidate.newInstance(LogFactory.class.getName());
      if (log.isDebugEnabled()) {
        log.debug("Logging initialized using '" + implClass + "' adapter.");
      }
      logConstructor = candidate;
    } catch (Throwable t) {
      throw new LogException("Error setting Log implementation.  Cause: " + t, t);
    }
  }
```

可以看到，可以通过 **LogFactory** 中的静态方法 use\*\*Logging()方法来实现对于具体的实现类的设置。那么Mybatis是如何知道项目中使用的是哪个三方的日志系统的呢？我们之前写过一篇 [MyBatis3源码解析](http://www.kailaisii.com//archives/MyBatis3源码解析) ，里面有各种加载Configuration的配置信息。在 **settings** 标签下可以设置具体的log类

```
<settings>
    <setting name="logImpl" value="STDOUT_LOGGING"/>
</settings>
```

代码中解析：

```
Class<? extends Log> logImpl = (Class<? extends Log>) resolveClass(props.getProperty("logImpl"));
configuration.setLogImpl(logImpl);

public void setLogImpl(Class<? extends Log> logImpl) {
    if (logImpl != null) {
      this.logImpl = logImpl;
      LogFactory.useCustomLogging(this.logImpl);
    }
}
```

可以看到，在解析的时候，通过对配置文件的解析，可以获取到三方日志系统，然后设置到 **LogFatory** 中，然后其他地方可以通过 **getLogger()** 方法来获取配置的日志实现类了。

这种属于一种适配器模式。

仔细看看，好像logging包下面还有一个jdbc的包。难道这是个关于JDBC方法的日志打印么？随意打开个 **ConnectionLogger** 瞅一眼。

```
public final class ConnectionLogger extends BaseJdbcLogger implements InvocationHandler {

  private final Connection connection;

  private ConnectionLogger(Connection conn, Log statementLog, int queryStack) {
    super(statementLog, queryStack);
    this.connection = conn;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] params)
      throws Throwable {
    try {
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, params);
      }    
      if ("prepareStatement".equals(method.getName())) {
        if (isDebugEnabled()) {
          //支持debug模式，打印Preparing的日志信息
          debug(" Preparing: " + removeBreakingWhitespace((String) params[0]), true);
        }        
        PreparedStatement stmt = (PreparedStatement) method.invoke(connection, params);
        //如果调用的是prepareStatement方法，需要返回PreparedStatement类，
        // 这里我们将返回的类进行动态代理，那么执行prepareStatement的相关方法的时候，就可以进行prepareStatement的日志打印了
        stmt = PreparedStatementLogger.newInstance(stmt, statementLog, queryStack);
        return stmt;
      } else if ("prepareCall".equals(method.getName())) {
        if (isDebugEnabled()) {
          debug(" Preparing: " + removeBreakingWhitespace((String) params[0]), true);
        }        
        PreparedStatement stmt = (PreparedStatement) method.invoke(connection, params);
        stmt = PreparedStatementLogger.newInstance(stmt, statementLog, queryStack);
        return stmt;
      } else if ("createStatement".equals(method.getName())) {
        Statement stmt = (Statement) method.invoke(connection, params);
        stmt = StatementLogger.newInstance(stmt, statementLog, queryStack);
        return stmt;
      } else {
        return method.invoke(connection, params);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

  /*
   * 动态代理方法，返回能够打印相关logg信息Connection代理类
   */
  public static Connection newInstance(Connection conn, Log statementLog, int queryStack) {
    InvocationHandler handler = new ConnectionLogger(conn, statementLog, queryStack);
    ClassLoader cl = Connection.class.getClassLoader();
    return (Connection) Proxy.newProxyInstance(cl, new Class[]{Connection.class}, handler);
  }
```

典型的代理者模式嘛~~类里面有我们刚才分析的Log接口，然后在 **invoke** 方法中，将具体执行的JDBC的执行过程的信息打印了出来。其中有个关键点：在invoke方法中，对于要返回的 **PreparedStatement** 和 **Statement** 类，也实现了相关的代理，从而能够对其执行的相关方法打印出来日志信息。

我们反向找一下，什么地方调用了这个的代理方法呢？

```
BaseExecutor.java：
//当执行增删改查时，先获取了连接Connection。
protected Connection getConnection(Log statementLog) throws SQLException {
  Connection connection = transaction.getConnection();
  if (statementLog.isDebugEnabled()) {
    return ConnectionLogger.newInstance(connection, statementLog, queryStack);
  } else {
    return connection;
  }
}
```

可以看到，当系统支持debug模式的情况下，我们获取的Connection连接，其实是个代理类，代理类能够打印具体的执行过程，而使用的打印工具，则是我们上面分析的 **Log** 接口的实现类。

其实总体来看，日志的打印还是很简单的~~~
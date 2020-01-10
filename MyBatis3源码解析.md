## MyBatis3.4.X源码解析

### 源码解析环境搭建

进行源码解析，最重要的是能够搭建源码环境，能够自己一步步的进行代码的跟踪调试~~

本文主要是进行源码解析，所以环境搭建进行了省略，详细信息可以参考[Mybatis3.x 源码阅读-01环境搭建](https://blog.csdn.net/qq157538651/article/details/88555198)

### 源码解析

#### 测试代码如下

```
    @Test
    public void main() {
        String path = "mybatis-config.xml";
        Reader reader = Resources.getResourceAsReader(path);
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
        SqlSession sqlSession = sqlSessionFactory.openSession();
        PersonDao personDao =  sqlSession.getMapper(PersonDao.class);
        Person p = new Person();
        personDao.insert(p);
        sqlSession.commit();
        sqlSession.close();
    }
```

> 1. 创建SqlSessionFactoryBuilder对象，调用对应的build方法，将xml配置信息进行解析，返回SqlSessionFactory对象。
> 2. 通过openSession()方法返回SqlSession对象
> 3. 通过sqlSession对象获取PersonDao对象
> 4. 通过PersonDao来执行对应的插入指令

#### SqlSessionFactory对象的创建
我们首先跟踪下 **SqlSessionFactoryBuilder().build(reader)**这行代码：

```
  //通过方法重载进行调用的最终方法，它使用了一个参照了XML文档或更特定的SqlMapConfig.xml文件的Reader实例。
  //可选的参数是environment和properties。Environment决定加载哪种环境(开发环境/生产环境)，包括数据源和事务管理器。
  //如果使用properties，那么就会加载那些properties（属性配置文件），那些属性可以用${propName}语法形式多次用在配置文件中。
  public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
    try {
      //根据environment,properties以及配置信息，生成解析mybatis的配置类
      XMLConfigBuilder parser = new XMLConfigBuilder(reader, environment, properties);
      //先通过parse()方法，生成一个Configuration对象，
      // Configuration对象，是将xml里面的配置信息转化成的将里面包含了配置xml里面的所有信息
      return build(parser.parse());
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } finally {
      ErrorContext.instance().reset();
      try {
        reader.close();
      } catch (IOException e) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }
```

这里我们将一行行的代码进行跟踪

```
  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    //先生成了XPathParser解析器
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }
```

最后通过方法重载，调用了如下方法

```
  //将相关数据，设置为XMLConfigBuilder变量信息
  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    //将一些Configuration的基础信息的设置在父类中进行处理
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    //标记xml配置信息未解析
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }
```

生成 **XMLConfigBuilder** 对象后，调用了 **pares()** 方法进行了配置文件的解析工作

```
  //将配置信息进行解析，生成Configuration对象
  public Configuration parse() {
    if (parsed) {//如果已经解析过了，则直接抛出异常
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    //标记xml配置信息已经解析过
    parsed = true;
    //通过xml解析器，读取节点内数据，<configuration>是MyBatis配置文件中的顶层标签
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }
```

这里面最重要的就是 **parseConfiguration()** 方法，在这里面进行了实际的解析处理

```
  //xml解析工作在这里进行处理，将需要的各种对象进行解析设置到configuration对象中
  private void parseConfiguration(XNode root) {
    try {
      //分步骤解析
      //issue #117 read properties first
      //1.properties
      propertiesElement(root.evalNode("properties"));
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      loadCustomVfs(settings);
      //2.类型别名
      typeAliasesElement(root.evalNode("typeAliases"));
      //3.插件
      pluginElement(root.evalNode("plugins"));
      //4.对象工厂
      objectFactoryElement(root.evalNode("objectFactory"));
      //5.对象包装工厂
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      //6.设置
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      //7.环境
      environmentsElement(root.evalNode("environments"));
      //8.databaseIdProvider
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      //9.类型处理器
      typeHandlerElement(root.evalNode("typeHandlers"));
      //10.映射器
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }
```

解析工作完成以后，调用重载方法的build()方法，生成SqlSessionFactory对象

```
  //最后一个build方法使用了一个Configuration作为参数,并返回DefaultSqlSessionFactory
  public SqlSessionFactory build(Configuration config) {
    return new DefaultSqlSessionFactory(config);
  }
```

#### SqlSession对象的创建
现在我们回到我们的测试代码的第二步 ，通过 **sqlSessionFactory.openSession()** 方法返回SqlSession对象。

![image.png](http://cdn.qiniu.kailaisii.com/FnpHIH-_nf0u-pKZvx2Y_qlrUYVc)在上面的源码中我们看到，在生成SqlSessionFactory对象时，返回的实际是 **DefaultSqlSessionFactory** 这个类，现在我们从这个类中的 **openSession** 来进行代码的跟踪处理

```
  @Override
  public SqlSession openSession() {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, false);
  }
  
  //最终都会调用这个方法来生成SqlSession对象
  //ExecutorType 为Executor的类型，TransactionIsolationLevel为事务隔离级别，autoCommit是否开启事务
  //openSession的多个重载方法可以指定获得的SeqSession的Executor类型和事务的处理
  private SqlSession openSessionFromDataSource(ExecutorType execType, TransactionIsolationLevel level, boolean autoCommit) {
    Transaction tx = null;
    try {
      final Environment environment = configuration.getEnvironment();
      //事务工厂
      final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
      //生成一个事务
      tx = transactionFactory.newTransaction(environment.getDataSource(), level, autoCommit);
      //生成一个执行器(事务包含在执行器里)
      final Executor executor = configuration.newExecutor(tx, execType);
      //然后产生一个DefaultSqlSession
      return new DefaultSqlSession(configuration, executor, autoCommit);
    } catch (Exception e) {
      //如果出错，这时候可能已经打开了一个事务的连接，需要关闭事务
      closeTransaction(tx); // may have fetched a connection so lets call close()
      throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }
```
#### PersonDao对象的获取
到现在为止，我们已经知道了Mybatis是如何生成对应的Configuration和SqlSession对象了。我们继续我们的测试代码的第三步

```
PersonDao personDao =  sqlSession.getMapper(PersonDao.class);
```

在之前的源码中，我们看到sqlSession其实返回的实际是**DefaultSqlSession**对象。

```
  @Override
  public <T> T getMapper(Class<T> type) {
    //从configuration对象中获取到mapper类
    return configuration.<T>getMapper(type, this);
  }

  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    //从mapperRegistry中获取
    return mapperRegistry.getMapper(type, sqlSession);
  }
  
    @SuppressWarnings("unchecked")
  //返回代理对象
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    //从MapperRegistry中的HashMap中拿MapperProxyFactory这个代理类
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
    if (mapperProxyFactory == null) {
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }
    try {
      // 通过动态代理工厂生成示例。
      return mapperProxyFactory.newInstance(sqlSession);
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
  }
```

我们发现，提供通过configuration对象里面的mapperRegistry对象，获取到了一个对应的代理工厂。然后通过工厂方法，生成了对应的代理类。我们现在看看 **mapperProxyFactory.newInstance** 的具体实现

```
  @SuppressWarnings("unchecked")
  //通过jdk的代理，来生成对应的对象
  protected T newInstance(MapperProxy<T> mapperProxy) {
    return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy);
  }

  public T newInstance(SqlSession sqlSession) {
    //创建了JDK动态代理的Handler类
    final MapperProxy<T> mapperProxy = new MapperProxy<T>(sqlSession, mapperInterface, methodCache);
    return newInstance(mapperProxy);
  }

```

到现在为止，我们知道了，每次都是从Configuration中来获取对应的Mapper的一个代理类。
#### Mapper中方法的执行
这时候，如果我们执行Mapper中的相关方法时，就像我们示例代码中的 **insert()** 最终会进入到创建的代理类中的Invoke方法。我们继续跟踪，进入创建的动态代理Handler类中

```
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -6424540398559729838L;
  private final SqlSession sqlSession;
  private final Class<T> mapperInterface;
  
  private final Map<Method, MapperMethod> methodCache;

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    //代理以后，所有Mapper的方法调用时，都会调用这个invoke方法
    //并不是任何一个方法都需要执行调用代理对象进行执行，如果这个方法是Object中通用的方法（toString、hashCode等）无需执行
    try {
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      } else if (isDefaultMethod(method)) {
        return invokeDefaultMethod(proxy, method, args);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
    //这里优化了，去缓存中找MapperMethod,如果缓存不存在，再添加方法
    final MapperMethod mapperMethod = cachedMapperMethod(method);
    //执行
    return mapperMethod.execute(sqlSession, args);
  }
  //去缓存中找MapperMethod,如果不存在，则创建，并放到缓存中
  private MapperMethod cachedMapperMethod(Method method) {
    MapperMethod mapperMethod = methodCache.get(method);
    if (mapperMethod == null) {
      mapperMethod = new MapperMethod(mapperInterface, method, sqlSession.getConfiguration());
      methodCache.put(method, mapperMethod);
    }
    return mapperMethod;
  }
```

我们来进入 **execute()** 这个方法来看一下

```
  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    switch (command.getType()) {
      //可以看到执行时就是5种情况，insert|update|delete|select|flush，分别调用SqlSession的5大类方法
      case INSERT: {
        //将参数信息转化为对象信息
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
        if (method.returnsVoid() && method.hasResultHandler()) {
          //有结果处理器
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) {
          //结果有多条记录
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) {
          //结果是map
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {
          //结果是Cursor
          result = executeForCursor(sqlSession, args);
        } else {
          //结果是返回一条数据
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
        }
        break;
      case FLUSH:
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName() 
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }
```

可以看到，最终是通过代理方法中的方法来执行又交给了 **SqlSession** 来进行处理CRUD。我们来跟踪一个方法insert()

```
  @Override
  public int insert(String statement, Object parameter) {
    return update(statement, parameter);
  }
  
  //update 核心代码
  @Override
  public int update(String statement, Object parameter) {
    try {
      //每次要更新之前，dirty标志设为true
      dirty = true;
      MappedStatement ms = configuration.getMappedStatement(statement);
      //最终由executor来执行语句
      return executor.update(ms, wrapCollection(parameter));
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error updating database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }
```

继续跟踪executor

```
@Override
public int update(MappedStatement ms, Object parameter) throws SQLException {
  ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
  if (closed) {
    throw new ExecutorException("Executor was closed.");
  }
  //先清理缓存，再更新
  clearLocalCache();
  return doUpdate(ms, parameter);
}
```

利用 **建造者模式** ，将具体的处理操作，交给子类来处理。我们来看一下子类的实现

```
 SimpleExecutor.java
 
 @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      //新建一个StatementHandler
      //这里看到ResultHandler传入的是null
      StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
      //生成一个Statement
      stmt = prepareStatement(handler, ms.getStatementLog());
      //由StatementHandler来执行stmt语句
      return handler.update(stmt);
    } finally {
      closeStatement(stmt);
    }
  }
  
 private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    //获取一个connect连接
    Connection connection = getConnection(statementLog);
    stmt = handler.prepare(connection, transaction.getTimeout());
    handler.parameterize(stmt);
    return stmt;
  }
```

SimpleStatementHandler.java代码

```
  @Override
  public int update(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    Object parameterObject = boundSql.getParameterObject();
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    int rows;
    if (keyGenerator instanceof Jdbc3KeyGenerator) {
      statement.execute(sql, Statement.RETURN_GENERATED_KEYS);
      rows = statement.getUpdateCount();
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else if (keyGenerator instanceof SelectKeyGenerator) {
      statement.execute(sql);
      rows = statement.getUpdateCount();
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else {
      statement.execute(sql);
      rows = statement.getUpdateCount();
    }
    return rows;
  }
```

可以看到，在执行器中，通过Connection创建一个Statement对象，然后通过调用Statement的execute方法执行sql语句。

到现在为止，我们的sql语句执行完毕了~~~
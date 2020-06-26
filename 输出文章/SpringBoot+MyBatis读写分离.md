## SpringBoot+MyBatis读写分离实现
读写分离有很多种，我们这里的方案是基于spring的AbstractRoutingDataSource和MyBatis的Plugin拦截器来实现的

### MyBatis的Plugin拦截器的设置。

```
/**
 * 动态实现读写分离
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
@Component
public class DynamicDataSourceInterceptor implements Interceptor {
    private Logger logger = LoggerFactory.getLogger(DynamicDataSourceInterceptor.class);
    // 验证是否为写SQL的正则表达式
    private static final String REGEX = ".*insert\\u0020.*|.*delete\\u0020.*|.*update\\u0020.*";

    /**
     * 主要的拦截方法
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 判断当前是否被事务管理
        boolean synchronizationActive = TransactionSynchronizationManager.isActualTransactionActive();
        String lookupKey;
        if (!synchronizationActive) {
            //如果是非事务的，则再判断是读或者写。
            // 获取SQL中的参数
            Object[] objects = invocation.getArgs();
            // object[0]会携带增删改查的信息，可以判断是读或者是写
            MappedStatement ms = (MappedStatement) objects[0];
            // 如果为读，且为自增id查询主键，则使用主库
            // 这种判断主要用于插入时返回ID的操作，由于日志同步到从库有延时
            // 所以如果插入时需要返回id，则不适用于到从库查询数据，有可能查询不到
            if (ms.getSqlCommandType().equals(SqlCommandType.SELECT)
                    && ms.getId().contains(SelectKeyGenerator.SELECT_KEY_SUFFIX)) {
                lookupKey = DynamicDataSourceHolder.INSTANCE.getDB_MASTER();
            } else {
                BoundSql boundSql = ms.getSqlSource().getBoundSql(objects[1]);
                String sql = boundSql.getSql().toLowerCase(Locale.CHINA).replaceAll("[\\t\\n\\r]", " ");
                // 正则验证
                if (sql.matches(REGEX)) {
                    // 如果是写语句
                    lookupKey = DynamicDataSourceHolder.INSTANCE.getDB_MASTER();
                } else {
                    lookupKey = DynamicDataSourceHolder.INSTANCE.getDB_SLAVE();
                }
            }
        } else {
            // 如果是通过事务管理的，一般都是写语句,直接通过主库
            lookupKey = DynamicDataSourceHolder.INSTANCE.getDB_MASTER();
        }

        logger.info("在" + lookupKey + "中进行操作");
        DynamicDataSourceHolder.INSTANCE.setDbType(lookupKey);
        // 最后直接执行SQL
        return invocation.proceed();
    }

    /**
     * 返回封装好的对象，或代理对象
     */
    @Override
    public Object plugin(Object target) {
        // 如果存在增删改查，则直接拦截下来，否则直接返回
        if (target instanceof Executor)
            return Plugin.wrap(target, this);
        else
            return target;
    }

    /**
     * 类初始化的时候做一些相关的设置
     */
    @Override
    public void setProperties(Properties properties) {
        // TODO Auto-generated method stub
    }
}
```

在MyBatis的Plugin代码中，我们通过注解 **@Intercepts** 来表明拦截器是进行sql方法的，在代码实现中，根据要执行的语句判断是读还是写，以及是否是事务来设置数据源。

### SpringBoot的设置

```

/**
 *描述：spring提供了AbstractRoutingDataSource，提供了动态选择数据源的功能，替换原有的单一数据源后，即可实现读写分离。
 * AbstractRoutingDataSource中持有targetDataSources对象，里面保存了所有能够切换的数据源信息
 *<p/>作者：wu
 *<br/>创建时间：2019/12/21 22:38
 */
class DynamicDataSource : AbstractRoutingDataSource() {
    //返回对应的数据源名称，就可以根据从map中取到对应的数据源
    override fun determineCurrentLookupKey(): Any? {
        return DynamicDataSourceHolder.getDbType()
    }
}

```

```
/**
 *描述：静态类，里面持有了数据源信息,在MyBatis的拦截器中，将要使用的数据源的名称设置到了这个静态类中
 * 然后在DynamicDataSource中直接获取对应的数据源名称即可
 *<p/>作者：wu
 *<br/>创建时间：2020/1/2 22:52
 */
object  DynamicDataSourceHolder {
    val log = logger(this)
    val contextHolder=ThreadLocal<String?>()
    val DB_MASTER="master"
    val DB_SLAVE="slave"
    fun getDbType():String{
        return contextHolder.get()?:"master"
    }
    fun setDbType(str:String){
        log.info("所使用的数据源是：${str}")
        contextHolder.set(str)
    }
    fun clear(){
        contextHolder.remove()
    }
}
```

现在我们看一下，如何将数据源添加到DynamicDataSource这个类中的

```
    @Bean(name = ["masterDataSource"])
    @ConfigurationProperties(prefix = "spring.datasource.master")
    fun masterProperties(): DataSource {
        return DruidDataSource()
    }

    @Bean(name = ["slaveDataSource"])
    @ConfigurationProperties(prefix = "spring.datasource.slave")
    fun slaveProperties(): DataSource {
        return DruidDataSource()
    }

    @Bean
    fun myRoutingDataSource(@Qualifier("masterDataSource") masterDataSource: DataSource,
                            @Qualifier("slaveDataSource") slaveDataSource: DataSource): DynamicDataSource {
        var map = hashMapOf<Any, Any>()
        map["master"] = masterDataSource
        map["slave"] = slaveDataSource
        val myRoutingDataSource = DynamicDataSource()
        //将两个master和slave两个数据源信息写入到DynamicDataSource的targetDataSources这个属性中
        myRoutingDataSource.setDefaultTargetDataSource(masterDataSource)
        myRoutingDataSource.setTargetDataSources(map)
        return myRoutingDataSource
    }

    @Bean
    @Throws(Exception::class)
    fun sqlSessionFactory(dataSource: DynamicDataSource): SqlSessionFactory? {
        val sqlSessionFactoryBean = SqlSessionFactoryBean()
        sqlSessionFactoryBean.setDataSource(dataSource)
        //为MyBatis增加Plugin拦截器功能
        sqlSessionFactoryBean.setPlugins(arrayOf(DynamicDataSourceInterceptor()))
        sqlSessionFactoryBean.setMapperLocations(PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"))
        return sqlSessionFactoryBean.getObject()
    }

    @Bean
    fun platformTransactionManager(dataSource: DynamicDataSource): PlatformTransactionManager? {
        return DataSourceTransactionManager(dataSource)
    }
```

首先我们注册了两个数据源的类，然后将两个数据源Bean类添加到 **DynamicDataSource** 这个类中的targetDataSources属性中。这样就可以实现我们的读写分离了。。。。
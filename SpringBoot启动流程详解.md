springboot是一个服务于spring框架的框架，能够简化配置文件，快速构建web应用，内置tomcat，无需打包部署，直接运行。
当我们启动一个SpringBoot应用的时候，都会用到如下的启动类
```
@SpringBootApplication
public class Application {
     public static void main(String[] args) {
         SpringApplication.run(Application.class, args);
     }
```
只要加上@SpringBootApplication，然后执行run()方法，就可以启动一个应用程序，简单粗暴，有木有？
那么具体的一个应用程序是如何启动起来的，依赖所使用的jar包中的类又是如何加载到我们我们的应用程序的呢？本文将带你一步步揭开SpringBoot启动的神秘面纱~
## 大体加载流程
run()方法的执行过程是我们本次主要追踪的路线，我们先大体了解一下流程

1. 调用静态run()方法时，我们首先创建一个SpringApplication的对象实例。在创建实例时，进行了一些基本的初始化操作。大体如下
>   - 根据classpath的类推断ApplicationContext类型，设置为webApplicationType
>   - 加载所有的ApplicationContextInitializer
>   - 加载所有的ApplicationListener
>   - 根据入参，设置启动类的类信息webApplicationType

2. 初始化完成后，执行run()方法。先查找并加载所有的SpringApplicationRunListener，放入到SpringApplicationRunListeners这个集合类里面来进行统一管理。然后调用他们的starting()来通知所有的listeners程序要启动。。。
3.  创建并配置当前应用的Environment环境（包括配置property和对应的profile信息，将其放入environment变量），然后通过SpringApplicationRunListeners的environmentPrepared()来进行通知
4. 根据初始化类时webApplicationType信息，创建具体的ApplicationContext实例context
5. 加载所有的ApplicationContextInitializer，然后遍历调用initialize()方法。
6. 将environment和context进行绑定，然后调用SpringApplicationRunListeners的contextPrepared()方法
7. 通过自动装配，将获取的所有配置@EnableAutoConfiguration以及其他形式的IoC容器配置加载到已经准备完毕的ApplicationContext。然后调用SpringApplicationRunListeners的contextLoaded()方法
8. 调用SpringApplication的refresh()方法，配置beanfactory，将所有的标注有@EnableAutoConfiguration中@Import注解进行解析处理，将获取的所有bean类进行初始化，进行ioc容器的最终处理。
9. 调用SpringApplicationRunListeners的started(context)方法;
10. 当前ApplicationContext中是否注册有CommandLineRunner，如果有，则遍历执行它们。
11. 调用SpringApplicationRunListeners的running(context)方法。
### 启动
每个SpringBoot程序都有一个主入口，就是main()方法，在main()方法中调用SpringApplication.run()来启动整个程序。该方法所启动的类，必须要使用@SpringBootApplication注解，它包括了三个注解：
@EnableAutoConfiguration：根据改依赖对Spring框架进行自动配置
@SpringBootConfiguration（@Configuration）：装备所有的配置的Bean类，提供Spring的上下文环境
@ComponentScan：组件扫描，可以自动发现和装配Bean类
### SpringApplication的启动类
首先进入run()
```
	public static ConfigurableApplicationContext run(Class<?>[] primarySources,
			String[] args) {
		return new SpringApplication(primarySources).run(args);
	}
```
在run方法中，先创建了SpringApplication的实例
```
	public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
		this.resourceLoader = resourceLoader;
		Assert.notNull(primarySources, "PrimarySources must not be null");
		this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
		this.webApplicationType = WebApplicationType.deduceFromClasspath();
		//通过SpringFactoriesLoader获取所有的ApplicationContextInitializer监听器
		setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
		//通过SpringFactoriesLoader获取所有的ApplicationListener监听器
		setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
		this.mainApplicationClass = deduceMainApplicationClass();
	}
```
我们看一眼最简单的程序启动后，所赋值的监听器的类：
![image.png](http://cdn.qiniu.kailaisii.com/FqqsKJuk5C92HQ4WNIYCzxUBWAFs)
这里主要是初始化一些属性，执行完构造函数之后，进行run()方法的调用。这个方法里面完成了Spring的整个启动过程：准备Environment环境-发布事件-创建上下文-创建bean类-刷新上下文-结束。中间穿插了很多监听器的方法调用，来执行一系列的操作。

```
	public ConfigurableApplicationContext run(String... args) {
		//启动时钟，用来统计应用的启动时间，并记录相关的日志
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		ConfigurableApplicationContext context = null;
		Collection<SpringBootExceptionReporter> exceptionReporters = new ArrayList<>();
		configureHeadlessProperty();
		//初始化SpringApplicationRunListener监听器
		SpringApplicationRunListeners listeners = getRunListeners(args);
        //调用starting()遍历调用所有的监听器的方法
		listeners.starting();
```
我们来看一下getRunLIsteners()方法中到底执行了什么
```
	private SpringApplicationRunListeners getRunListeners(String[] args) {
		Class<?>[] types = new Class<?>[] { SpringApplication.class, String[].class };
		return new SpringApplicationRunListeners(logger,
				getSpringFactoriesInstances(SpringApplicationRunListener.class, types, this, args));
	}

	private <T> Collection<T> getSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes, Object... args) {
		//获取线程的类加载器，此处是类加载器，能够加载所有的应用下的jar包
		ClassLoader classLoader = getClassLoader();
		// Use names and ensure unique to protect against duplicates
        //Spring类加载工具，能够将所有的jar包下面/META-INF/spring.factory中，获取所有指定的加载类，然后根据具体的class类型，
        //获取并返回相应类型的实现类全限定名，
        //通过getRunListeners()防止执行这个方法的时候，返回的是所有的SpringApplicationRunListener的类名
		Set<String> names = new LinkedHashSet<>(SpringFactoriesLoader.loadFactoryNames(type, classLoader));
        //根据实现类的全限定名，通过反射实例化一个bean类
		List<T> instances = createSpringFactoriesInstances(type, parameterTypes, classLoader, args, names);
		AnnotationAwareOrderComparator.sort(instances);
		return instances;
	}
```
代码跟踪结果：
![image.png](http://cdn.qiniu.kailaisii.com/FmoSQS-IALnFy6voVwGnBpauBQ3u)
可以看到,注册为SpringApplicationRunListener的实现类只有一个，就是**EventPublishingRunListener**，其实如果我们需要自己集成功能到Spring框架中，可以注册SpringApplicationRunListener的实现类，然后就可以监听Spring的启动的各种事件。

继续我们的run()方法。
```
try {
			ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
			ConfigurableEnvironment environment = prepareEnvironment(listeners, applicationArguments);
			configureIgnoreBeanInfo(environment);//设置忽略的类
```
```
	private ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners,
			ApplicationArguments applicationArguments) {
		ConfigurableEnvironment environment = getOrCreateEnvironment();//新建或者获取当前的Environment实例
		configureEnvironment(environment, applicationArguments.getSourceArgs());//配置参数
		ConfigurationPropertySources.attach(environment);
		listeners.environmentPrepared(environment);//发布消息
		//将获取到的environment中的spring.main配置绑定到SpringApplication的source中。
		bindToSpringApplication(environment);
		if (!this.isCustomEnvironment) {
			environment = new EnvironmentConverter(getClassLoader()).convertEnvironmentIfNecessary(environment,
					deduceEnvironmentClass());
		}
		ConfigurationPropertySources.attach(environment);
		return environment;
	}
```
这段配置代码比较长，我们来一个个走，先看下第一个**getOrCreateEnvironment()**函数
```
private ConfigurableEnvironment getOrCreateEnvironment() {
		//如果已经初始化了，则直接返回
		if (this.environment != null) {
			return this.environment;
		}
		switch (this.webApplicationType) {
		case SERVLET:
			//如果是web环境，则返回StandardServletEnvironment
			return new StandardServletEnvironment();
		case REACTIVE:
			//如果是响应式web容器，则返回StandardReactiveWebEnvironment类
			return new StandardReactiveWebEnvironment();
		default:
			//如果是普通程序。一般会走入这个方法，类中会执行两个方法,在相应的Environment环境中放入两个propertySource
			//propertySources.addLast(new PropertiesPropertySource("systemProperties", getSystemProperties()));
			//propertySources.addLast(new SystemEnvironmentPropertySourcesystemEnvironment("", getSystemEnvironment()));
			return new StandardEnvironment();
		}
	}
```
可以看到，这个方法主要是实例化了对应的Environment实例，并设置了相应的propertySource

我们再来看下一个**configureEnvironment()**函数
```
	protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
		if (this.addConversionService) {
			ConversionService conversionService = ApplicationConversionService.getSharedInstance();
			environment.setConversionService((ConfigurableConversionService) conversionService);
		}
		configurePropertySources(environment, args);//配置PropertySources
		configureProfiles(environment, args);//配置Profiles
	}
    
    protected void configurePropertySources(ConfigurableEnvironment environment, String[] args) {
		...
	}
    
	protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
		Set<String> profiles = new LinkedHashSet<>(this.additionalProfiles);
        //获得Profile的配置.Profile配置项,即spring.profiles.active。也就是设置的运行环境
		profiles.addAll(Arrays.asList(environment.getActiveProfiles()));
		environment.setActiveProfiles(StringUtils.toStringArray(profiles));
	}
```
到现在为止，对于Environment的环境配置基本完成了。
然后继续下一个下一个：
```
listeners.environmentPrepared(environment);//发布消息
```
这个方法，将配置的环境信息发布出去，这次我们跟踪这个代码的执行
```
	@Override
	public void environmentPrepared(ConfigurableEnvironment environment) {
		//创建了一个ApplicationEnvironmentPreparedEvent对象，然后将对象发送出去
		this.initialMulticaster.multicastEvent(new ApplicationEnvironmentPreparedEvent(this.application, this.args, environment));
	}
```
ok,现在我们可以回到我们的run()方法了
```
			Banner printedBanner = printBanner(environment);
			//创建应用上下文，并实例化了其三个属性：reader、scanner和beanFactory
			context = createApplicationContext();
			//获取异常报道器，即加载spring.factories中的SpringBootExceptionReporter实现类
			exceptionReporters = getSpringFactoriesInstances(SpringBootExceptionReporter.class,
					new Class[] { ConfigurableApplicationContext.class }, context);
```
可以看到，这里进行了context上下文的创建工作，在完成这步工作之后，肯定就是需要对创建的上下文进行一些加载工作了。
```
prepareContext(context, environment, listeners, applicationArguments,printedBanner);
...
```
现在我们来跟踪一下，一步步了解是如何将Environment，listener等信息进行加载的。
```
	private void prepareContext(ConfigurableApplicationContext context,
								ConfigurableEnvironment environment, SpringApplicationRunListeners listeners,
								ApplicationArguments applicationArguments, Banner printedBanner) {
		// 设置上下文的environment
		context.setEnvironment(environment);
		// 应用上下文后处理
		postProcessApplicationContext(context);
		// 在context refresh之前，对其应用ApplicationContextInitializer
		applyInitializers(context);
		// 上下文准备（目前是空实现，可用于拓展）
		listeners.contextPrepared(context);
		// 打印启动日志和启动应用的Profile
		if (this.logStartupInfo) {
			logStartupInfo(context.getParent() == null);
			logStartupProfileInfo(context);
		}

		// Add boot specific singleton beans
		context.getBeanFactory().registerSingleton("springApplicationArguments",
				applicationArguments);                                // 向beanFactory注册单例bean：命令行参数bean
		if (printedBanner != null) {
			// 向beanFactory注册单例bean：banner bean
			context.getBeanFactory().registerSingleton("springBootBanner", printedBanner);
		}
		if (beanFactory instanceof DefaultListableBeanFactory) {
			((DefaultListableBeanFactory) beanFactory)
					.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
		}
		if (this.lazyInitialization) {
			context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
		}
		// Load the sources
		Set<Object> sources = getAllSources();                        // 获取全部资源，其实就一个：SpringApplication的primarySources属性
		Assert.notEmpty(sources, "Sources must not be empty");        // 断言资源是否为空
		// 将bean加载到应用上下文中
		load(context, sources.toArray(new Object[0]));
		// 向上下文中添加ApplicationListener，并广播ApplicationPreparedEvent事件
		listeners.contextLoaded(context);
	}

```
回到run方法，在完成相关配置之后会进行上下文的刷新工作
```
refreshContext(context);
...
```
	@Override
	public void refresh() throws BeansException, IllegalStateException {
		synchronized (this.startupShutdownMonitor) {
			// 准备刷新
			prepareRefresh();
			//刷新bean工厂，并返回bean工厂
			ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();
			//准备bean工厂的，以便进行上下文的使用
			prepareBeanFactory(beanFactory);
			try {
				// 允许上下文子类中对bean工厂进行后处理
				postProcessBeanFactory(beanFactory);
				// 在bean创建之前调用BeanFactoryPostProcessors后置处理方法
				invokeBeanFactoryPostProcessors(beanFactory);
				// 注册BeanPostProcessor.
				registerBeanPostProcessors(beanFactory);
				// 注册DelegatingMessageSource
				initMessageSource();
				//注册multicaster
				initApplicationEventMulticaster();
				// 初始化子类中的特殊bean类
				onRefresh();
				// 注册Listener
				registerListeners();
				// 完成BeanFactory初始化，初始化剩余单例bean
				finishBeanFactoryInitialization(beanFactory);
				// 发布对应事件
				finishRefresh();
			}
            ...
```
回到run方法，最后的逻辑就是发布启动完成的事件，并调用监听者的方法。
```
	...
	afterRefresh(context, applicationArguments);//给实现类留的钩子，这里是一个空方法。
			stopWatch.stop();//停止计时器
			if (this.logStartupInfo) {
				new StartupInfoLogger(this.mainApplicationClass).logStarted(getApplicationLog(), stopWatch);
			}
			listeners.started(context);//发布ApplicationStartedEvent事件
			callRunners(context, applicationArguments);
		}
		catch (Throwable ex) {
			handleRunFailure(context, ex, exceptionReporters, listeners);
			throw new IllegalStateException(ex);
		}

		try {
			listeners.running(context);//发布ApplicationStartedEvent事件
		}
		catch (Throwable ex) {
			handleRunFailure(context, ex, exceptionReporters, null);
			throw new IllegalStateException(ex);
		}
		return context;
	}
```

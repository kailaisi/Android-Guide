### SpringMVC启动流程源码分析

我们知道，SpringMVC最后是通过Tomcat来进行部署的。当在Servlet中进行进行应用部署时，主要步骤为（引用来自http://download.oracle.com/otn-pub/jcp/servlet-3.0-fr-eval-oth-JSpec/servlet-3_0-final-spec.pdf）：

> When a web application is deployed into a container, the following steps must be
> performed, in this order, before the web application begins processing client
> requests.
> ■ Instantiate an instance of each event listener identified by a <listener> element
> in the deployment descriptor.
> ■ For instantiated listener instances that implement ServletContextListener , call
> the contextInitialized() method.
> ■ Instantiate an instance of each filter identified by a <filter> element in the
> deployment descriptor and call each filter instance’s init() method.
> ■ Instantiate an instance of each servlet identified by a <servlet> element that
> includes a <load-on-startup> element in the order defined by the load-on-
> startup element values, and call each servlet instance’s init() method.

翻译下：当应用部署到容器时，在应用相应客户的请求之前，需要执行以下步骤：

- 创建并初始化由<listener>元素标记的事件监听器。

- 对于时间监听器，如果实现了ServletContextListener接口，那么调用其contextInitialized()方法。

- 创建和初始化由<filter>元素标记的过滤器，并调用其init()方法。

- 根据<load-on-startup>中定义的顺序创建和初始化由<servlet>元素标记的servlet，并调用其init()方法。

  所以在Tomcat下部署的应用，会先初始化listener，然后初始化filter，最后初始化servlet。

  在我们的SpringMVC中，最简单的web.xml配置如下

  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd"
         version="3.1">
  
     <!--告诉加载器，去这个位置去加载spring的相关配置-->
     <context-param>
        <param-name>contextConfigLocation</param-name>
        <param-value>classpath:spring-mvc.xml</param-value>
     </context-param>
     <!--配置前端控制器-->
     <servlet>
        <servlet-name>springMvc</servlet-name>
        <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
        <!--SpringMVC配置文件-->
        <init-param>
           <param-name>contextConfigLocation</param-name>
           <param-value>classpath:spring-mvc.xml</param-value>
        </init-param>
        <load-on-startup>0</load-on-startup>
     </servlet>
  
     <servlet-mapping>
        <servlet-name>springMvc</servlet-name>
        <url-pattern>/</url-pattern>
     </servlet-mapping>
     <listener>
        <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
     </listener>
     <!--解决乱码问题的filter-->
     <filter>
        <filter-name>CharacterEncodingFilter</filter-name>
        <filter-class>org.springframework.web.filter.CharacterEncodingFilter</filter-class>
        <init-param>
           <param-name>encoding</param-name>
           <param-value>utf-8</param-value>
        </init-param>
     </filter>
  
     <filter-mapping>
        <filter-name>CharacterEncodingFilter</filter-name>
        <url-pattern>/*</url-pattern>
     </filter-mapping>
  
  </web-app>
  ```

  现在我们根据这个配置文件和上面的初始化流程一起来看一下，SpringMVC是如何来一步步启动容器，并加载相关信息的。

### 初始化Listener

我们这里定义的的listener类是 **ContextLoaderListener** ，我们看一下具体的实现

```java
/**
 * Bootstrap listener to start up and shut down Spring's root {@link WebApplicationContext}.
 * Simply delegates to {@link ContextLoader} as well as to {@link ContextCleanupListener}.
 *
 * <p>As of Spring 3.1, {@code ContextLoaderListener} supports injecting the root web
 * application context via the {@link #ContextLoaderListener(WebApplicationContext)}
 * constructor, allowing for programmatic configuration in Servlet 3.0+ environments.
 * See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 17.02.2003
 * @see #setContextInitializers
 * @see org.springframework.web.WebApplicationInitializer
 */
public class ContextLoaderListener extends ContextLoader implements ServletContextListener {
```

  **ContextLoaderListener** 类继承了 **ContextLoader** 实现了 **ServletContextListener** 接口，按照启动程序，会调用其 **contextInitialized()** 方法

```java
/**
 * Initialize the root web application context.
 */
//初始化根应用上下文
@Override
public void contextInitialized(ServletContextEvent event) {
   initWebApplicationContext(event.getServletContext());
}

```

那么我们现在再看一下这个应用上下文的初始化过程

```java
//初始化web应用的上下文
//ServletContext官方叫servlet上下文。服务器会为每一个工程创建一个对象，这个对象就是ServletContext对象。这个对象全局唯一，而且工程内部的所有servlet都共享这个对象。所以叫全局应用程序共享对象。
public WebApplicationContext initWebApplicationContext(ServletContext servletContext) {
       /*
          首先通过WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE
          这个String类型的静态变量获取一个根IoC容器，根IoC容器作为全局变量
         存储在application对象中，如果存在则有且只能有一个
          如果在初始化根WebApplicationContext即根IoC容器时发现已经存在
          则直接抛出异常，因此web.xml中只允许存在一个ContextLoader类或其子类的对象
          */
   if (servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE) != null) {
      throw new IllegalStateException(
            "Cannot initialize context because there is already a root application context present - " +
                  "check whether you have multiple ContextLoader* definitions in your web.xml!");
   }
   Log logger = LogFactory.getLog(ContextLoader.class);
   servletContext.log("Initializing Spring root WebApplicationContext");
   if (logger.isInfoEnabled()) {
      logger.info("Root WebApplicationContext: initialization started");
   }
   long startTime = System.currentTimeMillis();

   try {
      // Store context in local instance variable, to guarantee that
      // it is available on ServletContext shutdown.
      //如果context不存在，则进行创建
      if (this.context == null) {
         this.context = createWebApplicationContext(servletContext);
      }
      if (this.context instanceof ConfigurableWebApplicationContext) {
         ConfigurableWebApplicationContext cwac = (ConfigurableWebApplicationContext) this.context;
         if (!cwac.isActive()) {
            // The context has not yet been refreshed -> provide services such as
            // setting the parent context, setting the application context id, etc
            if (cwac.getParent() == null) {
               // The context instance was injected without an explicit parent ->
               // determine parent for root web application context, if any.
               ApplicationContext parent = loadParentContext(servletContext);
               cwac.setParent(parent);
            }
            /**
             * 配置并刷新应用的根IOC容器，这里会进行bean的创建和初始化工作。这里面最终会调用
             * {@link org.springframework.context.support.AbstractApplicationContext#refresh refresh()方法}
             * 并且IOC容器中的bean类会被放在application中
             */
            configureAndRefreshWebApplicationContext(cwac, servletContext);
         }
      }
      //以属性的配置方式将application配置servletContext中，因为servletContext是整个应用唯一的，所以可以根据key值获取到application，从而能够获取到应用的所有信息
      servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, this.context);
       ....
}
```

可以看到，**initWebApplicationContext()** 方法的整个执行过程都是为了创建应用的上下文，即根IOC容器。并且以 **setAttribute** 的方式将应用上下文设置到了servletContext中，这样在整个应用中都可以使用servletContext来进行各种应用信息的获取。

我们重点跟踪一下 **configureAndRefreshWebApplicationContext()** 这个方法。

```java
protected void configureAndRefreshWebApplicationContext(ConfigurableWebApplicationContext wac, ServletContext sc) {
   if (ObjectUtils.identityToString(wac).equals(wac.getId())) {
      // The application context id is still set to its original default value
      // -> assign a more useful id based on available information
      String idParam = sc.getInitParameter(CONTEXT_ID_PARAM);
      if (idParam != null) {
         wac.setId(idParam);
      } else {
         // Generate default id...
         wac.setId(ConfigurableWebApplicationContext.APPLICATION_CONTEXT_ID_PREFIX +
               ObjectUtils.getDisplayString(sc.getContextPath()));
      }
   }
   //将ServletContext设置到application的属性中
   wac.setServletContext(sc);
   //获取web.xml中配置的contextConfigLocation参数值
   String configLocationParam = sc.getInitParameter(CONFIG_LOCATION_PARAM);
   if (configLocationParam != null) {
      //将web.xml的配置信息设置到application中
      wac.setConfigLocation(configLocationParam);
   }

   // The wac environment's #initPropertySources will be called in any case when the context
   // is refreshed; do it eagerly here to ensure servlet property sources are in place for
   // use in any post-processing or initialization that occurs below prior to #refresh
   ConfigurableEnvironment env = wac.getEnvironment();
   if (env instanceof ConfigurableWebEnvironment) {
      ((ConfigurableWebEnvironment) env).initPropertySources(sc, null);
   }

   customizeContext(sc, wac);
   //调用应用的refresh方法，进行IOC容器的装载
   wac.refresh();
}
```

我们跟踪一下debug代码，看看实际的的信息。

![image-20200116231627610](D:\gongcheng\booknote\image-20200116231627610.png)

我们进入到 **refresh()** 方法中

![image-20200116231923007](D:\gongcheng\booknote\image-20200116231923007.png)

可以看到，在refresh中完成了对于IOC容器中bean类的加载处理。

到此为止，SpringMVC已经完成了对于由<listener>元素标记的事件监听器。

### 初始化Filter

在完成了对于 **listener** 的初始化操作以后，会进行 **filter** 的创建和初始化操作。我们后面会

### Servlet的初始化

web应用启动的最后一个步骤就是创建和初始化 **Servlet** ，我们就从我们使用的 **DispatcherServlet** 这个类来进行分析，这个类是前端控制器，主要用于分发用户请求到具体的实现类，并返回具体的响应信息。

![image-20200116234412880](D:\gongcheng\booknote\image-20200116234412880.png)

我们根据类图可以看到，**DispatchServlet** 实现了 **Servlet** 接口，所以按照加载过程，最终会调用其 **init(ServletConfig config)** 方法。我们从 **DispatchServlet** 中寻找 **init()** 方法的实现，会发现该方法不存在，那么我们继续向上查找，在其父类中去寻找，最终在 **GenericServlet** 中找到了方法

```java
public void init(ServletConfig config) throws ServletException {
this.config = config;
//交由子类来实现。
this.init();
}
```

我们在 **HttpServletBean** 中找到了 **init()** 方法的具体实现。

```java
public final void init() throws ServletException {
   if (logger.isDebugEnabled()) {
      logger.debug("Initializing servlet '" + getServletName() + "'");
   }
   // Set bean properties from init parameters.
   //设置属性信息
   PropertyValues pvs = new ServletConfigPropertyValues(getServletConfig(), this.requiredProperties);
   if (!pvs.isEmpty()) {
      try {
         //将具体的实现来进行包装，使用了包装者模式
         BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(this);
         ResourceLoader resourceLoader = new ServletContextResourceLoader(getServletContext());
         bw.registerCustomEditor(Resource.class, new ResourceEditor(resourceLoader, getEnvironment()));
         initBeanWrapper(bw);
         //将web.xml里面设置的属性信息设置到bw中
         bw.setPropertyValues(pvs, true);
      }
      catch (BeansException ex) {
         if (logger.isErrorEnabled()) {
            logger.error("Failed to set bean properties on servlet '" + getServletName() + "'", ex);
         }
         throw ex;
      }
   }
   // Let subclasses do whatever initialization they like.
   //由子类来实现
   initServletBean();

   if (logger.isDebugEnabled()) {
      logger.debug("Servlet '" + getServletName() + "' configured successfully");
   }
}
```

其中，对于 **initServletBean()** 方法则又交给了子类来处理，我们最终在 **FrameworkServlet** 类中找到了对应的实现 
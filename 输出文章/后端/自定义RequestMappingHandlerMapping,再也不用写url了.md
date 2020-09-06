#自定义RequestMappingHandlerMapping,再也不用写url了~
在springmvc中，是通过**RequestMappingHandlerMapping**来将url地址和对应的method方法俩进行绑定的，每次写RequstMapping("/url")是不是很麻烦？其实我们可以通过自定义RequestMappingHandlerMapping来完全省掉，不需要再自己写url地址了，直接将我们的类或者方法名称作为url地址的。
本文基于SpringMvc5.1.7来实现的。
语言是Kotlin语言，使用的时候，逻辑是完全一样的。
首先，我们自定义一个配置类，配置类继承WebMvcRegistrations接口。
我们先来简单看下这个接口，
![image.png](http://cdn.qiniu.kailaisii.com/FozidDOKyZ3xM2nQn7_8QJg5ccyq)

>ExceptionHandlerExceptionResolver是异常处理，
RequestMappingHandlerAdapter是请求映射处理适配器，
RequestMappingHandlerMapping是地址与方法映射关系

今天我们主要就是在第三个方法上进行处理，来实现我们自己的映射方案。
```
/**
 *描述：通过继承WebMvcRegistrations，里面覆写getRequestMappingHandlerMapping()方法，可以注入自己的RequestMappingHandler
 * 来实现对于@RequestMapping缺少value的自动url注入，
 *<p/>作者：wu
 *<br/>创建时间：2019/12/27 23:14
 */
@Configuration
class RequestMappingHandlerConfig : WebMvcRegistrations {
    override fun getRequestMappingHandlerMapping(): RequestMappingHandlerMapping {
        return ControllerRequestMapping()
    }
}
```
剩下的就是我们自定义自己的 **ControllerRequestMapping**这个方法了。。。
```
/**
 *描述：RequestMappingHandlerMapping
 *<p/>作者：wu
 *<br/>创建时间：2019/12/28 11:44
 */
class ControllerRequestMapping : RequestMappingHandlerMapping() {
    val log = logger(this)

    init {
        log.info("ControllerRequestMapping 被加载")
    }

    //父类的属性
    private val useSuffixPatternMatch = true
    private val useTrailingSlashMatch = true

    var basePackage: String? = null
        @Value("\${basePackage}")
        set(value) {
            field = value
        }

    override fun getMappingForMethod(method: Method, handlerType: Class<*>): RequestMappingInfo? {
        var info: RequestMappingInfo? = null
        var annotation = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java)
        annotation?.let {
            var methodCondition = getCustomMethodCondition(method)
            info = createRequestMappingInfo(annotation, methodCondition, method, handlerType)
            var typeAnnotation = AnnotatedElementUtils.findMergedAnnotation(handlerType, RequestMapping::class.java)
            typeAnnotation?.let {
                var typeCondition = getCustomTypeCondition(handlerType)
                info = createRequestMappingInfo(typeAnnotation, typeCondition, method, handlerType).combine(info!!)
            }
        }
        return info
    }

    private fun createRequestMappingInfo(annotation: RequestMapping, customCondition: RequestCondition<*>?, method: Method, handlerType: Class<*>): RequestMappingInfo {
        var patterns = resolveEmbeddedValuesInPatterns(annotation.value)
        if (patterns.isEmpty()) {
            //证明RequestMapping里面没有写value属性，此时要进行自己url的拼装。
            val builder = StringBuilder()
            basePackage?.let {
                //例如我的类位置是  com.kailaisi.eshopdatalinkservice.controller.EsProductController，那么我们这里会去掉
                //basePackage  com.kailaisi.eshopdatalinkservice   然后再去掉最后的controller，只剩下了文件名
                //然后文件名去掉Controller后缀，我们的url地址就是/esproduct
                val packagePath = ClassUtil.getPackagePath(handlerType)
                var substring = packagePath.substring(it.length)//去掉包名
                if (substring.endsWith("/controller")) {
                    //去掉controller这个包文件
                    substring = substring.dropLast("/controller".length)
                }
                builder.append(substring.toLowerCase()).append("/")
            }
            //去掉后缀名小写字母,例如我的类名是UserLoginController   那么最后名字是userlogin
            var clazzName = handlerType.simpleName.dropLastString("Controller")
            builder.append(clazzName.toLowerCase()).append("/").append(method.name.toLowerCase())
            patterns = arrayOf(builder.toString())
        }
        return RequestMappingInfo(
                PatternsRequestCondition(patterns, urlPathHelper, pathMatcher,
                        this.useSuffixPatternMatch, this.useTrailingSlashMatch, fileExtensions),
                RequestMethodsRequestCondition(*annotation.method),
                ParamsRequestCondition(*annotation.params),
                HeadersRequestCondition(*annotation.headers),
                ConsumesRequestCondition(annotation.consumes, annotation.headers),
                ProducesRequestCondition(annotation.produces, annotation.headers, contentNegotiationManager),
                customCondition)
    }

}
```


至此我们的自定义ControllerRequestMapping已经完全实现了~~
![](http://cdn.qiniu.kailaisii.com/FhhvIXPl4e-II0Bz5MUlqPrLiUgH)
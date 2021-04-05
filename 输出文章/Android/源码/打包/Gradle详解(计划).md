## Gradle详解



### 学习Gradle的必要性

* 一款新的，功能最强大的构建工具，逼格更高：maven，Ant 可以做到的功能，Gradle都可以做到，但是Gradle可以实现的功能，Ant和Maven不一定能过实现
* 使用程序代替传统的xml配置，项目构建更灵活
* 有丰富的第三方插件，可以随心所欲的使用。
* 可以完善Android，Java的开发技术体系
* 提升自动化构建技术深度
* 进阶高阶工程师

适合人群：

* 从事Android，Java相关的开发
* 对Android，Java有一定基础，对工程构建有一定了解

### 内功

#### 相关介绍和开发环境搭建

* 相关概念
* 开发环境搭建和工程创建

#### 核心讲解及实战

* 常见数据结构
* Gradle面向对象特性

#### 高级用法实战

* json文件处理及json，model互换

* xml文件读取

* 普通文件读取

#### Gradle核心Project详解及实战

Gradle其实不仅仅是一种打包工具，它更是一种编程语言。

##### Gradle的优势

* 在灵活性上，可以灵活的设置构建流程。
* 粒度细：gradle对于源代码和资源的编译处理，都是通过Task的构建执行来处理，粒度更精细
* 扩展性：扩展性强，有很多插件可以使用
* 兼容性：兼容所有Ant、Maven的功能

##### Gradle执行流程

整个执行过程分为3部分：

* Initialization初始化阶段：解析整个工程中所有的Project，构建所有的Project对应的Project对象。解析Setting.property文件
* Configuration配置阶段：解析所有的Project对象中的Task，构建好Task所对应的拓扑图
* Execution执行阶段：执行具体的task及其依赖的task.

几个重要的阶段监听：

```groovy
this.beforeEvaluate{
    //初始化阶段执行完毕，配置阶段执行之前
    println "配置阶段开始执行"
}

this.afterEvaluate{
    //配置阶段执行之后，执行阶段执行之前
    println "配置阶段执行完毕"
}
this.gradle.buildFinished{
    //gradle执行完毕之后，也就是执行阶段也执行完毕了
    println "gradle执行完毕"
}

this.gradle.beforeProject {
    //相当于beforeEvaluate
}

this.gradle.afterProject {
    //相当于afterEvaluate
}

```

#### Project

##### 定义

在一个工程中，其实所有的module也是当作gradle中的project来处理的。通过**gradle projects**指令就可以看到项目中所有的project信息。我们以一个Android项目为例，看一下输出的信息：

```groovy
Root project 'gradleDemo'
\--- Project ':app'
```

在gradle中，每个project都要有一个build.gradle文件。在我们的工程中，有两种类型的project：

* Root project，用于配置我们的子project。
* 子project：每个project都对应一个输出。

##### 核心API

Project中的API比较多，我们将其分为6类来讲解，分别为：

* gradle生命周期api：生命周期的监听处理等
* project相关api：操作父project和管理子project的能力
* task相关api：提供新增task和使用已有task的能力
* 属性相关api：为project增加额外属性的能力
* file相关api：操作当前project下的文件的能力
* 其他api：

**project相关api：**

获取所有project：

```groovy
//获取所有的project
this.getAllprojects()
//获取所有的子project
this.getSubprojects()

def getProjects(){
    println "------"
    println "root project"
    println "----"
    this.getAllprojects().eachWithIndex { Project entry, int i ->
        if (i==0){
            println "Root project:${entry.name}"
        }else{
            println " ---  projec:t${entry.name}"
        }
    }
}
```

配置固定project：

```groovy
//在父工程中找到名字为app的子工程，然后对其进行配置
project("app"){p->
    println p.name
    apply plugin:'com.android.application'
    group 'com.kailaisi'
    version '1.0.0-release'
    dependencies {
        //增加依赖
    }
    android{
        //android的一些配置
    }
}

```

配置所有的project：

```groovy
//配置当前节点工程和其所有的subproject的所有project
allprojects{
    apply plugin:'com.android.application'
    group 'com.kailaisi'
}

//不包括当前节点工程，只包括它的subproject
subprojects {Project project->
    //只有lib才上传maven
    if (project.plugins.hasPlugin('com.android.library')) {
        apply from: '../publishToMaven.gradle'
    }
}

```

#### **属性API**

对于自定义属性，有多种方案：

##### gradle文件新增

* 在Gradle中，我们可以通过在每个build中去定义属性，或者扩展属性来使用。

```groovy
//在子工程中定义
def compileVersion=25
def androidAppcompatVersion='androidx.appcompat:appcompat:1.1.0'
//可以自定义扩展属性
ext{
    buildVersion="29.0.3"
}
android {
    compileSdkVersion compileVersion
    //自定义属性，使用this来引用
    buildToolsVersion this.buildVersion
...
```

如果每个工程都定义版本号的话，会很麻烦，所以可以通过project相关的api来实现。

* 在根工程中，通过subproject来实现。

```groovy
subprojects{
    ext{
        compileVersion=29
    }
}
```

上述方式，其实从本质上，是为每个project都增加了一个变量。

* 只在根工程中，只定义一遍自定义属性

```groovy
ext {
    compileVersion=29
}
```

* 最佳方案：

如果在根工程中有特别多自定义属性的话，可能会看起来比较繁琐，我们可以通过引入gradle文件的方式，将所有的配置属性，都在自定义的gradle一种来实现。

```groovy
//common.gradle文件
ext {
    android=[
            minSdkVersion :16,
            targetSdkVersion : 19,
            compileSdkVersion : 27  
    ]
}
//根工程中配置
apply from: this.file('common.gradle')

```

##### gradle.properties新增

对于自定义属性，我们可以在properties中进行定义

```properties
isLoadTest=false
```

这样，我们就可以配置是否加载对应的工程了。

```groovy
//setting.gradle
include ':app'
if (hasProperty('isLoadTest')?isLoadTest.toBoolean():false){
    include ':myapplication'
}
```

这种方式在模块化开发中会用到，来将我们的对应module配置为主工程或者library。

#### 文件api

重点是如何在project下如何对文件进行操纵

##### 路径获取

对于文件的路径获取，一般是获取当前工程的路径，主要包括如下3个方法

```groovy

//获取根工程路径
println  getRootDir().absolutePath
//获取build file路径
println getBuildDir().absolutePath
//获取当前工程的路径
println getProjectDir().absolutePath

```

##### 文件操作

在gradle中，我们经常需要进行文件的操作，比如打包后**移动文件的位置**等等，

* 文件定位

一个最常见的文件定位就是file方法

```groovy
println getContent("local.properties")
def getContent(String path) {
    try {
        //file会相对于当前project来查找文件，还有一个相似的files方法，能够批量查询文件
        def file = file(path)
        return "打印文件${path}数据：${file.text}"
    } catch (GradleException e) {
        println "file not find"
    }
    return null
}
```

* 文件拷贝

gradle对于文件的拷贝，有更加简便的拷贝方式

```groovy
copy{
    from file('build/outputs/apk')
    into getRootProject().getBuildDir().absolutePath+"/apk"
    exclude{}//排除不拷贝的数据
    rename{}//进行重命名
}
```

* 文件树遍历

```groovy
//对文件树进行遍历
fileTree("build/outputs/apk"){FileTree files->//将文件夹映射为一个文件树
    files.visit {FileTreeElement element->
        println "the file name is ${element.name}"
        copy{
            from element.file
            into getRootProject().getBuildDir().path+'/test/'
        }
    }
}
```

**注意：gradle中提供文件的定位、拷贝等操作，都只能在本根工程目录下操作，如果操作的范围超过了这个的话，就只能通过groovy的文件操作方式来进行处理了**。

#### 其他api

主要包括2个部分

##### 依赖相关api

是和依赖相关的api，即我们的project是如何管理工程中的依赖的。

工程的相关配置信息，我们可以在**project.java**类中查看，类中所有的非get方法都是用来进行配置的，可以看到，主要是有两个

```java
//Project.java
	/**
     *配置依赖关系
     */
    void dependencies(Closure configureClosure);

	/**
     * 为工程配置编译脚本，闭包的入参是ScriptHandler
     */
    void buildscript(Closure configureClosure);
```

首先看看buildscript方法。

```groovy
//ScriptHandler.java
    /**
     * 配置仓库，闭包参数是RepositoryHandler
     */
    void repositories(Closure configureClosure);


    /**
     * 为script位置依赖，闭包入参是DependencyHandler
     */
    void dependencies(Closure configureClosure);
```

我们看下对于buildscript的配置

```groovy
buildscript { ScriptHandler scriptHandler->
    ext.kotlin_version = "1.3.72"
    //配置工程的仓库地址
    scriptHandler.repositories {
    }
    //配置工程的插件依赖地址
    scriptHandler.dependencies {
        classpath "com.android.tools.build:gradle:4.0.0"
    }
}
```

首先我们看看repositories这个闭包，闭包参数是RepositoryHandler类

可配置的信息。

![image-20210402103536607](http://cdn.qiniu.kailaisii.com/typora/202104/02/103540-289166.png)

所以一般配置如下：

```groovy
    //配置工程的仓库地址
    scriptHandler.repositories {RepositoryHandler handler->
        handler.maven {
            name 'personal'//设置名称
            url "http://localhost:8081/nexus/repository"//对应的地址
            credentials{//配置使用的帐号密码（如果有的话）
                username='admin'
                password='123456'
            }
        }
        handler.mavenCentral()
        handler.mavenLocal()
    }
```

我们再看一下dependencies这个，这个类主要配置的是**gradle本身的对于第三方的依赖**

```groovy
    //配置工程的插件依赖地址
    scriptHandler.dependencies {
        classpath "com.android.tools.build:gradle:4.0.0"
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
    }
```

在我们的子项目中，也是有dependencise的，这个代表的是project对于**三方类库的依赖**

```groovy
dependencies {
    //AndroidX 版本
    implementation('com.king.zxing:zxing-lite:2.0.2') {
        exclude group: 'com.google.zxing', module: 'core' //排除以来
        transitive false   //禁止依赖传递
    }
    implementation 'com.tencent:mmkv-static:1.2.4' {
        changing true//每次都从服务端获取
    }
}
```

##### 外部命令执行

一些其他的外部命令，比如说Linux中的相关命令，在gradle中如何去执行。

我们那之前的依赖api只能对于当前工程中进行操作，如果需要跟系统或者外部的工具进行交流时用的时候，就需要用到外部命令来处理。

#### Gradle核心Task详解及实战

##### Task定义和配置

对于task的定义的方式主要有两种方式

```groovy
//直接通过task函数去创建
task helloTask{
    println "this is helloTask"
}
//通过容器TaskContainer的方式去创建
this.tasks.create("helloTask2"){
    println "this is helloTask2"
}

```

而如果我们想要将task的group和描述信息进行配置，主要方式也是两种

```groovy
//直接通过task函数去创建，在构造函数中进行配置
task helloTask(group: 'kai',description: 'task study'){
    println "this is helloTask"
}
//通过容器TaskContainer的方式去创建
this.tasks.create("helloTask2"){
    //通过设置属性来配置group分组和描述信息
    setGroup('kai')
    setDescription('task study')
    println "this is helloTask2"
}

```

其中，当设置同一个group的时候，gradle会将task配置到同一个组下面，而description则表示任务的描述信息。如上图的配置代码，在gradle中生成的任务信息如下：

![image-20210405221745637](http://cdn.qiniu.kailaisii.com/typora/20210405221745-808181.png)

##### Task执行详解

对于上面的代码，我们会发现不管我们执行哪个指令，我们helloTask和helloTask2中的打印代码都会执行，这是因为gradle的整过过程分为：初始化、配置和执行三个阶段。而Task的代码段是在配置阶段都会运行的。如果我们想要在执行阶段才去打印输出，则需要通过**doFirst**或者**doLast**来配置。

```groovy
//直接通过task函数去创建
task helloTask(group: 'kai',description: 'task study'){
    println "this is helloTask"
    doFirst {//在task内部设置
        println "the task group is :$group"
    }
}
helloTask.doFirst {//在task外部进行配置，该配置会优先于内部设置执行
    println "the task description is :$description"
}
```

我们可以看一下任务输出：

![image-20210405223030146](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210405223030146.png)

**doFirst会在默认的gradle的默认的Task之前执行，可以用来为默认Task进行一些准备工作。而doLast则会在默认的Task之后执行，可以用来执行一些收尾的工作。**

我们这里增加一个小的实战，用于统计build的整个执行时间:

![image-20210405224311748](http://cdn.qiniu.kailaisii.com/typora/20210405224313-130989.png)

当我们执行任何一个task的时候，通过这个地方可以看到，preBuild是配置完成之后，执行的第一个task方法。

```groovy
//计算build执行时长
def startBuildTime,endBuildTime
this.afterEvaluate {//配置阶段执行之后，执行阶段执行之前
    //保证要找的task已经配置完毕
    def preBuildTask=tasks.findByName('preBuild')
    preBuildTask.doFirst {
        startBuildTime=System.currentTimeMillis()
        println "start time is :${startBuildTime}"
    }

    def buildTask=tasks.findByName("build")
    buildTask.doLast {
        endBuildTime=System.currentTimeMillis()
        println "the build  time is ${endBuildTime-startBuildTime}"
    }
}
```

对于gradle的整个build时间还是比较长的，中间各个任务也特别多。我们可以通过可视化的方式来查看整个执行流程的各个任务。

具体的方式可以参考[Android项目中的Gradle Task流程可视化](https://blog.csdn.net/weixin_34235105/article/details/90619141)

##### Task执行顺序

我们可以通过一定的方式来调整Task的执行顺序，主要方式有如下三种：

* dependsOn强依赖方式
* 通过Task输入输出执行
* 通过API执行执行顺序

最简单的一种方式就是通过dependsOn方式来添加依赖了

```groovy
//依赖关系
task taskX{
    doLast {
        println 'taskX'
    }
}
task taskY{
    doLast {
        println 'taskY'
    }
}
task taskZ(dependsOn:[taskX,taskY]){
    doLast {
        println 'taskZ'
    }
}

```

在该测试代码中，**taskZ是依赖于taskX和taskY的，所以当我们执行taskZ的时候，会先执行taskX和taskY任务，然后才会执行taskZ任务**。

* 依赖关系和执行顺序
* Task类型
* Task修改默认构建流程，Task源码解读
* 实战：自动化生成版本说明xml文档
* 实战：自动化实现工程插件更新功能

#### Gradle核心之其他模块详解及实战

* 第三方依赖管理及gradle如何去处理依赖原理讲解
* 工程初始化核心类Setting类作用及自定义
* 源码管理类SourceSet讲解及工作中的妙用

#### Gradle核心之自定义Plugin实战

* 插件类Plugin的定义及如何使用第三方插件
* Gradle如何管理插件的依赖
* 插件类Plugin源码解读
* 实战：将前面实现的自动化脚本封装为插件供他人使用

#### Gradle程序修改默认打包流程

* Android工程打包流程讲解
* 将脚本嵌入到Gradle打包流程中实现特定功能
* 打包流程核心task图解
* 实战：将前面的脚本嵌入到打包流程






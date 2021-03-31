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

**属性API**

* Project类核心作用
* 核心API讲解
* Gradle生命周期流程

#### Gradle核心Task详解及实战

* Task定义和使用，Task执行流程
* 依赖关系和输入输出，Task继承和实现
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






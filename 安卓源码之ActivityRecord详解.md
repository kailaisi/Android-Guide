## ActivityRecord详解

在进行Activity启动源码解析的过程中，总是遇到各种类，有时候因为不理解其作用而总是搞混，所以对其中使用的各种类进行一个源码及作用的记录整理。

ActivityRecord是最常遇到的一个类了。它代表的是堆栈中的一个Activity信息。既然是堆栈中的信息，那么很容易理解，同一个Activity可能在堆栈中存在着多个对应的ActivityRecord对象，这是由Activity的启动模式所决定的。

看一下里面几个重要的属性

* ActivityInfo info  //activity的信息
* ApplicationInfo appInfo//Application的信息
* int launchedFromPid//启动当前activity的pid，也就是processId，进程ID
* int launchedFromUid//启动当前activity的uid，也就是User Identifier，即用户ID，Android 上，一个应用程序只有一个UID，当然多个应用程序也可以共享一个UID
* String packageName//当前Activity的包名
* ActivityRecord resultTo//启动当前ActivityRecord的activityrecord，通过它能够返回信息（比如说onActivityResult？）
* int requestCode//启动时使用的requestCode
* int launchMode//当前activity的启动模式
* TaskRecord task//当前activity所处的任务栈
* ActivityStackSupervisor mStackSupervisor//当前activity所处的ActivityStackSupervisor
* RootActivityContainer mRootActivityContainer//当前activity所处的RootActivityContainer。这个类是10.0中新增的一个类，暂时用来分担ActivityStackSupervisor的部分职责的，主要目的是使ActivityContainer的结构和WindowContainer的结构保持一致。
* 



* ```
  
  
  
  
  ```

ListView详解



#### 两个阶段

##### 视图显示阶段

* 透过接口的**getCount()**，从子类中取得用户设置的信息：多少行选项。
* 继续调用N次**getView()**，从子类里去的各行的视图。

##### 响应阶段

* 基类透过接口的**getItem()**函数从子类里取得用户的响应函数。
* 也可以通过**getItemId()**函数，从子类中去的各选项的ID值。

#### 设计





https://blog.csdn.net/iispring/article/details/50967445
ListView详解



#### 两个阶段

##### 视图显示阶段

* 透过接口的**getCount()**，从子类中取得用户设置的信息：多少行选项。
* 继续调用N次**getView()**，从子类里去的各行的视图。

##### 响应阶段

* 基类透过接口的**getItem()**函数从子类里取得用户的响应函数。
* 也可以通过**getItemId()**函数，从子类中去的各选项的ID值。

#### 设计



HeaderViewListAdapter：当有header或者footer的时候的包装类

VelocityTracker：快速滑动辅助类，速度跟踪

DataSetObserver观察者模式

https://blog.csdn.net/iispring/article/details/50967445

https://blog.csdn.net/sunmc1204953974/article/details/38657137

https://blog.csdn.net/androiddevelop/article/details/8550455

https://developer.android.google.cn/reference/kotlin/android/widget/ListView?hl=en
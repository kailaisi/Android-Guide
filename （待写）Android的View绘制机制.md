## Android的View分发绘制过程源码解析（待写）

#### 引言

在之前的【Android布局窗口绘制分析】一篇文章中，我们介绍过如何将布局加载到PhoneWindows窗口中并显示。而在【Android的inflate源详解】中，我们则分析了如何将xml的布局文件转化为View树。但是View树具体以何种位置、何种大小展现给我们，没有具体讲解的。那么这篇文章，我们就在上两章的基础上继续研究View是如何进行布局和绘制的。

还记得我们在【Android布局窗口绘制分析】一文中的最后的addView代码块中重点标注的requestLayout()方法么？

 不记得了也没关系，我把代码贴出来就是了~

       //ViewRootImpl.java
       public void setView(View view, WindowManager.LayoutParams attrs, View panelParentView) {
            synchronized (this) {
                if (mView == null) {
                    ...
                    // 这里调用异步刷新请求，最终会调用performTraversals方法来完成View的绘制
                    //重点方法  这里面会进行view的 测量，layout，以及绘制工作
                    requestLayout();
                    ...
            }
        }

这句代码就是View的绘制的入口，经过measure,layout,draw最终将我们在【Android的inflate源详解】中所形成的View树绘制出来。当这篇文章完成之后，安卓如何从xml到view树，然后将view树进行绘制，然后将view添加到DecterView并显示出来，这一整套流程就可以结束了。

### 源码

       //ViewRootImpl.java 
        public void requestLayout() {
        	//该boolean变量会在ViewRootImpl.performLayout()开始时置为ture，结束置false
            //表示当前不处于Layout过程
            if (!mHandlingLayoutInLayoutRequest) {
    			//检测线程安全，只有创建这个view的线程才能操作这个线程(也就是主线程)。
                checkThread();
    			//标记请求进行绘制
                mLayoutRequested = true;
                //进行调度绘制工作
                scheduleTraversals();
            }
        }
这段代码主要就是一个检测，如果当前正在进行layout，那么就不处理。否则就进行调度绘制。





### 总结

1. handler是有一种同步屏障机制的，能够屏蔽同步消息(有什么用图以后再开发)。
2. 对于屏幕的帧绘制是通过choreographer来进行的，它来进行屏幕的刷新，帧的丢弃等工作。
3. 


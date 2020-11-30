DataBinding源码分析

#### 使用方式：

```java
 DataBindingUtil.setContentView(this, layoutId)
```

##### 源码

```java
    public static <T extends ViewDataBinding> T setContentView(@NonNull Activity activity,
            int layoutId) {
        return setContentView(activity, layoutId, sDefaultComponent);
    }

    public static <T extends ViewDataBinding> T setContentView(@NonNull Activity activity,
            int layoutId, @Nullable DataBindingComponent bindingComponent) {
        //代替我们做了setContentView的操作
        activity.setContentView(layoutId);
        //获取对应的decorView
        View decorView = activity.getWindow().getDecorView();
        //找到我们的最顶层布局（这个涉及到源码，所有的我们的布局其实都是放在R.id.content父控件中的）
        ViewGroup contentView = (ViewGroup) decorView.findViewById(android.R.id.content);
        return bindToAddedViews(bindingComponent, contentView, 0, layoutId);
    }

	//将我们的布局进行绑定
    private static <T extends ViewDataBinding> T bindToAddedViews(DataBindingComponent component,
            ViewGroup parent, int startChildren, int layoutId) {
        final int endChildren = parent.getChildCount();
        //获取到父控件的子控件总数。这里这么做，是因为有时候Fragment是需要绑定到父布局中的，这时候的count就不是1
        final int childrenAdded = endChildren - startChildren;
        if (childrenAdded == 1) {
            //如果子控件只有一个
            final View childView = parent.getChildAt(endChildren - 1);
            return bind(component, childView, layoutId);
        } else {
            //有多个子控件，则需要遍历将其保存到数组中
            final View[] children = new View[childrenAdded];
            for (int i = 0; i < childrenAdded; i++) {
                children[i] = parent.getChildAt(i + startChildren);
            }
            return bind(component, children, layoutId);
        }
    }
```

当我们在Activity中使用的时候，其实是只有一个布局的，这个的具体情况可以查看一下源码[Inflate源码分析]()，

```java
    //进行数据的绑定
	static <T extends ViewDataBinding> T bind(DataBindingComponent bindingComponent, View root,
            int layoutId) {
        //从mapper中获取对应的T对象
        return (T) sMapper.getDataBinder(bindingComponent, root, layoutId);
    }
```

这里只是简单的获取对象，具体的肯定需要去看看sMapper到底是什么了。

```java
    private static DataBinderMapper sMapper = new DataBinderMapperImpl();

	public class DataBinderMapperImpl extends MergedDataBinderMapper {
    	DataBinderMapperImpl() {
        //需要关注的方法
        addMapper(new com.honeywell.hch.mobilesubphone.DataBinderMapperImpl());
    }
}

```

所以这里的getDataBinder也是这个类中的实现。但是我们需要去父类中去寻找

```java
    //androidx.databinding.MergedDataBinderMapper
	public ViewDataBinding getDataBinder(DataBindingComponent bindingComponent, View view,
            int layoutId) {
        for(DataBinderMapper mapper : mMappers) {
            ViewDataBinding result = mapper.getDataBinder(bindingComponent, view, layoutId);
            if (result != null) {
                return result;
            }
        }
        if (loadFeatures()) {
            return getDataBinder(bindingComponent, view, layoutId);
        }
        return null;
    }
```

可以看到这里的**mMappers**其实就是我们在创建对象的时候，添加进去的数据。也就是*com.honeywell.hch.mobilesubphone.DataBinderMapperImpl()*对象。所以这里的getDataBinder是这个生成的对象。

```java
  @Override
  public ViewDataBinding getDataBinder(DataBindingComponent component, View view, int layoutId) {
      //从缓存中获取到id所对应的实际的布局文件。这个文件是不包含@{}这些绑定信息的的id的值
    int localizedLayoutId = INTERNAL_LAYOUT_ID_LOOKUP.get(layoutId);
    if(localizedLayoutId > 0) {
       //获取对应的tag。
      final Object tag = view.getTag();
      if(tag == null) {
        throw new RuntimeException("view must have a tag");
      }
      // find which method will have it. -1 is necessary becausefirst id starts with 1;
      //这里对mapper实际上进行了拆分，每50个是一个文件组。
      int methodIndex = (localizedLayoutId - 1) / 50;
      switch(methodIndex) {
        case 0: {
            //从第一组查找
          return internalGetViewDataBinding0(component, view, localizedLayoutId, tag);
        }
        case 1: {
          return internalGetViewDataBinding1(component, view, localizedLayoutId, tag);
        }
      }
    }
    return null;
  }
```

这里首先会获取View对应的tag信息。这个Tag是哪儿设置的呢？其实当我们进行编译的时候，会将我们的xml布局文件分解为两个。`****.xml`和`****-layout.xml`。都位于build文件夹下。

![image-20201130232013779](http://cdn.qiniu.kailaisii.com/typora/20201130232015-394577.png)

![image-20201130232126583](http://cdn.qiniu.kailaisii.com/typora/20201130232131-86316.png)

其中布局文件的所有根局部控件都有一个tag，所有使用@{}的也都有一个对应的tag

![image-20201130232443952](http://cdn.qiniu.kailaisii.com/typora/20201130232444-676876.png)

其实这里就是通过跟布局的tag来获取对应的DataBindingImpl文件的。

这里两个方法是相似的，我们只分析第一个方法。

```java
  private final ViewDataBinding internalGetViewDataBinding0(DataBindingComponent component,
      View view, int internalId, Object tag) {
    switch(internalId) {
      case  LAYOUT_ACTIONBARGRAY: {
        if ("layout/action_bar_gray_0".equals(tag)) {
          return new ActionBarGrayBindingImpl(component, view);
        }
        throw new IllegalArgumentException("The tag for action_bar_gray is invalid. Received: " + tag);
      }
      case  LAYOUT_ACTIVITYADDDEVICEMAIN: {
        if ("layout/activity_add_device_main_0".equals(tag)) {
          return new ActivityAddDeviceMainBindingImpl(component, view);
        }
        throw new IllegalArgumentException("The tag for activity_add_device_main is invalid. Received: " + tag);
      }
      ....

```

所以这里只是找到布局文件对应的BindingImpl文件，并将其返回。
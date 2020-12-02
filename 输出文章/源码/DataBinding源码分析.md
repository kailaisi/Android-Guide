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

而另一个文件，则是关于tag的信息

```xml
<?xml version="1.0" encoding="utf-8" standalone="yes"?>
<Layout directory="layout" filePath="app\src\main\res\layout\activity_add_device_main.xml" isBindingData="true" isMerge="false" layout="activity_add_device_main"
    modulePackage="com.honeywell.hch.mobilesubphone" rootNodeType="androidx.constraintlayout.widget.ConstraintLayout">
    <Targets>
        <Target tag="layout/activity_add_device_main_0" view="androidx.constraintlayout.widget.ConstraintLayout">
            <Expressions />
            <location endLine="34" endOffset="55" startLine="5" startOffset="4" />
        </Target>
        <Target tag="binding_1" view="LinearLayout">
            <Expressions />
            <location endLine="33" endOffset="22" startLine="11" startOffset="8" />
        </Target>
        <Target id="@+id/titlebar" include="action_bar_gray" tag="binding_1">
            <Expressions />
            <location endLine="18" endOffset="50" startLine="16" startOffset="12" />
        </Target>
        <Target id="@+id/tab_layout" view="com.google.android.material.tabs.TabLayout">
            <Expressions />
            <location endLine="25" endOffset="46" startLine="20" startOffset="12" />
        </Target>
        <Target id="@+id/view_Pager" view="androidx.viewpager2.widget.ViewPager2">
            <Expressions />
            <location endLine="32" endOffset="50" startLine="27" startOffset="12" />
        </Target>
    </Targets>
</Layout>
```

所以这里就是通过跟布局的tag来获取对应的DataBindingImpl文件的。

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

这里只是找到布局文件对应的BindingImpl文件，并将其返回。

我们看一下`ActivityLoginPageBindingImpl`的具体代码。我们从`setModel`先来跟踪一下。

```java
    public void setModel(@Nullable LoginViewModel Model) {
        this.mModel = Model;
        synchronized(this) {
            mDirtyFlags |= 0x10L;
        }
        //通知数据属性的变化
        notifyPropertyChanged(BR.model);
        super.requestRebind();
    }
```

每次我们进行数据绑定的时候，都需要`executeBindings`来

```java
    @Override
    protected void executeBindings() {
        long dirtyFlags = 0;
        synchronized(this) {
            dirtyFlags = mDirtyFlags;
            mDirtyFlags = 0;
        }
        LoginViewModel model = mModel;
        int modelPageVisibilityGet = 0;
        androidx.databinding.ObservableInt modelSmsVisibility = null;
        androidx.databinding.ObservableField<java.lang.String> modelCountDown = null;
        boolean androidxDatabindingViewDataBindingSafeUnboxModelOverGet = false;
        androidx.databinding.ObservableField<java.lang.Boolean> modelOver = null;
        java.lang.Boolean modelOverGet = null;
        int modelSmsVisibilityGet = 0;
        androidx.databinding.ObservableInt modelPageVisibility = null;
        java.lang.String modelCountDownGet = null;

        if ((dirtyFlags & 0x3fL) != 0) {
            if ((dirtyFlags & 0x32L) != 0) {
                	//我们之前设置了model，这里会从model中获取对应的需要绑定的关系
                    if (model != null) {
                        // read model.countDown
                        modelCountDown = model.getCountDown();
                    }
                    updateRegistration(1, modelCountDown);
                    if (modelCountDown != null) {
                        // read model.countDown.get()
                        modelCountDownGet = modelCountDown.get();
                    }
            }
		   .....	
        }
        // batch finished
        if ((dirtyFlags & 0x32L) != 0) {
		   //重点方法：进行赋值
            androidx.databinding.adapters.TextViewBindingAdapter.setText(this.sendButtonText, modelCountDownGet);
        }
    }
    ...
}
```

这里会读取module中的值，然后通过Adapter来进行赋值工作。

```java
    @BindingAdapter("android:text")
    public static void setText(TextView view, CharSequence text) {
        //获取到对应的旧数据
        final CharSequence oldText = view.getText();
        //同一个字符串，或者两个都是空数据
        if (text == oldText || (text == null && oldText.length() == 0)) {
            return;
        }
        if (text instanceof Spanned) {
            if (text.equals(oldText)) {//值相等
                return; 
            }
        } else if (!haveContentsChanged(text, oldText)) {
            //相同
            return; 
        }
        //赋值
        view.setText(text);
    }
```

我们看一下这里的赋值情况。

继承关系是：DataBindingImpl->DataBinding->ViewDataBinding。

```java
  static {
      	//静态代码块，将id和对应的值保存到SparseIntArray中。
        sIncludes = null;
        sViewsWithIds = new android.util.SparseIntArray();
        sViewsWithIds.put(R.id.title_loginPage, 3);
        sViewsWithIds.put(R.id.phone_layout, 4);
        sViewsWithIds.put(R.id.select_nation_spinner, 5);
        sViewsWithIds.put(R.id.tv_plus, 6);
        sViewsWithIds.put(R.id.nation_code_text, 7);
        sViewsWithIds.put(R.id.login_phone_edit, 8);
        sViewsWithIds.put(R.id.iv_del, 9);
        sViewsWithIds.put(R.id.divider_phone, 10);
        sViewsWithIds.put(R.id.login_pwd_edit, 11);
        sViewsWithIds.put(R.id.iv_del_pwd, 12);
        sViewsWithIds.put(R.id.divider_pwd, 13);
        sViewsWithIds.put(R.id.vcode_edit, 14);
        sViewsWithIds.put(R.id.divider_code, 15);
        sViewsWithIds.put(R.id.login_button, 16);
        sViewsWithIds.put(R.id.forget_button, 17);
        sViewsWithIds.put(R.id.register_button, 18);
    }
    // views
    @NonNull
    private final androidx.constraintlayout.widget.ConstraintLayout mboundView0;

	//构造方法
    public ActivityLoginPageBindingImpl(@Nullable androidx.databinding.DataBindingComponent bindingComponent, @NonNull View root) {
        //重点方法  ：  这里通过mapBindings进行了数据处理，
        this(bindingComponent, root, mapBindings(bindingComponent, root, 19, sIncludes, sViewsWithIds));
    }

    private ActivityLoginPageBindingImpl(androidx.databinding.DataBindingComponent bindingComponent, View root, Object[] bindings) {
        super(bindingComponent, root, 4
            , (android.view.View) bindings[15]
            , (android.view.View) bindings[10]
            , (android.view.View) bindings[13]
            , (android.widget.TextView) bindings[17]
            , (android.widget.ImageView) bindings[9]
            , (android.widget.ImageView) bindings[12]
            , (android.widget.ImageView) bindings[16]
            , (android.widget.EditText) bindings[8]
            , (android.widget.EditText) bindings[11]
            , (android.widget.TextView) bindings[7]
            , (android.widget.LinearLayout) bindings[4]
            , (android.widget.TextView) bindings[18]
            , (android.widget.Spinner) bindings[5]
            , (android.widget.TextView) bindings[2]
            , (androidx.constraintlayout.widget.ConstraintLayout) bindings[1]
            , (android.widget.ImageView) bindings[3]
            , (android.widget.TextView) bindings[6]
            , (android.widget.EditText) bindings[14]
            );
        this.mboundView0 = (androidx.constraintlayout.widget.ConstraintLayout) bindings[0];
        this.mboundView0.setTag(null);
        this.sendButtonText.setTag(null);
        this.smsCodeLayout.setTag(null);
        setRootTag(root);
        // listeners
        invalidateAll();
    }
```

我们先看一下`mapBindings`方法

```java
    protected static Object[] mapBindings(DataBindingComponent bindingComponent, View root,
            int numBindings, IncludedLayouts includes, SparseIntArray viewsWithIds) {
        Object[] bindings = new Object[numBindings];
        mapBindings(bindingComponent, root, bindings, includes, viewsWithIds, true);
        return bindings;
    }
```

我们知道这里的super肯定是对应了DataBinding类的。

```java
  protected ActivityLoginPageBinding(Object _bindingComponent, View _root, int _localFieldCount,
      View dividerCode, View dividerPhone, View dividerPwd, TextView forgetButton, ImageView ivDel,
      ImageView ivDelPwd, ImageView loginButton, EditText loginPhoneEdit, EditText loginPwdEdit,
      TextView nationCodeText, LinearLayout phoneLayout, TextView registerButton,
      Spinner selectNationSpinner, TextView sendButtonText, ConstraintLayout smsCodeLayout,
      ImageView titleLoginPage, TextView tvPlus, EditText vcodeEdit) {
    super(_bindingComponent, _root, _localFieldCount);
    this.dividerCode = dividerCode;
    this.dividerPhone = dividerPhone;
    this.dividerPwd = dividerPwd;
    this.forgetButton = forgetButton;
    this.ivDel = ivDel;
    this.ivDelPwd = ivDelPwd;
    this.loginButton = loginButton;
    this.loginPhoneEdit = loginPhoneEdit;
    this.loginPwdEdit = loginPwdEdit;
    this.nationCodeText = nationCodeText;
    this.phoneLayout = phoneLayout;
    this.registerButton = registerButton;
    this.selectNationSpinner = selectNationSpinner;
    this.sendButtonText = sendButtonText;
    this.smsCodeLayout = smsCodeLayout;
    this.titleLoginPage = titleLoginPage;
    this.tvPlus = tvPlus;
    this.vcodeEdit = vcodeEdit;
  }
```

对父类中的控件，进行了赋值工作。这就相当于我们以前经常写的`findViewById`方法一样。
DataBinding源码分析

### 使用方式：

```java
 DataBindingUtil.setContentView(this, layoutId)
```

### 源码

#### setContentView()

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

可以看到这里的**mMappers**其实就是我们在创建对象的时候，添加进去的数据。也就是*.*****DataBinderMapperImpl()*对象。所以这里的getDataBinder是这个生成的对象。

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

![image-20201205101530108](http://cdn.qiniu.kailaisii.com/typora/202012/05/101531-720081.png)

而另一个文件，则是关于tag的信息

![image-20201205101348770](http://cdn.qiniu.kailaisii.com/typora/202012/08/150009-344849.png)

所以这里就是通过根布局的tag来获取对应的DataBindingImpl文件的。

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

这里会根据根布局的tag找到布局文件对应的BindingImpl文件，并将其返回。

#### inflate()

对于DataBinding，我们还有另一种方式的获取：

```java
ItemBcfDashBinding.inflate(......)
```

那么这种方式又是如何获取的呢？`ItemBcfBinding.java`是自动生成的一个类。

```java
\\build\generated\data_binding_base_class_source_out\debug\out\包名\databinding>
// Generated by data binding compiler. Do not edit!
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.databinding.Bindable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import com.honeywell.hch.mobilesubphone.R;
import com.honeywell.hch.mobilesubphone.fragment.home.bcf.BCFDashViewModel;
import java.lang.Deprecated;
import java.lang.Object;

public abstract class ItemBcfDashBinding extends ViewDataBinding {
  @NonNull
  public final ConstraintLayout bcfLayout;

  @NonNull
  public final AppCompatTextView bcfMainText;

  @NonNull
  public final ImageView bcfState;

  @NonNull
  public final AppCompatTextView bcfText;

  @NonNull
  public final AppCompatTextView textView23;

  @NonNull
  public final AppCompatTextView title;

  @NonNull
  public final AppCompatTextView tvLocation;
    
     //bindable注解，说明是需要绑定的model
  @Bindable
  protected BCFDashViewModel mModel;

  protected ItemBcfDashBinding(Object _bindingComponent, View _root, int _localFieldCount,
      ConstraintLayout bcfLayout, AppCompatTextView bcfMainText, ImageView bcfState,
      AppCompatTextView bcfText, AppCompatTextView textView23, AppCompatTextView title,
      AppCompatTextView tvLocation) {
    super(_bindingComponent, _root, _localFieldCount);
    this.bcfLayout = bcfLayout;
    this.bcfMainText = bcfMainText;
    this.bcfState = bcfState;
    this.bcfText = bcfText;
    this.textView23 = textView23;
    this.title = title;
    this.tvLocation = tvLocation;
  }

  public abstract void setModel(@Nullable BCFDashViewModel model);

  @Nullable
  public BCFDashViewModel getModel() {
    return mModel;
  }

  @NonNull
  public static ItemBcfDashBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup root, boolean attachToRoot) {
    return inflate(inflater, root, attachToRoot, DataBindingUtil.getDefaultComponent());
  }

  @NonNull
  @Deprecated
  public static ItemBcfDashBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup root, boolean attachToRoot, @Nullable Object component) {
    return ViewDataBinding.<ItemBcfDashBinding>inflateInternal(inflater, R.layout.item_bcf_dash, root, attachToRoot, component);
  }

  @NonNull
  public static ItemBcfDashBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, DataBindingUtil.getDefaultComponent());
  }

  @NonNull
  @Deprecated
  public static ItemBcfDashBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable Object component) {
    return ViewDataBinding.<ItemBcfDashBinding>inflateInternal(inflater, R.layout.item_bcf_dash, null, false, component);
  }

  public static ItemBcfDashBinding bind(@NonNull View view) {
    return bind(view, DataBindingUtil.getDefaultComponent());
  }

  @Deprecated
  public static ItemBcfDashBinding bind(@NonNull View view, @Nullable Object component) {
    return (ItemBcfDashBinding)bind(component, view, R.layout.item_bcf_dash);
  }
}

```

在生成的Bingding文件中，持有了一些view的变量。但是并不会包含所有的控件。只包含了**根布局+有ID的布局文件+@{}绑定关系的**

所有的bind方法最后都会调用。 `ViewDataBinding.<ItemBcfDashBinding>inflateInternal`

```java
	//androidx\databinding\ViewDataBinding.class
    protected static <T extends ViewDataBinding> T inflateInternal(
            @NonNull LayoutInflater inflater, int layoutId, @Nullable ViewGroup parent,
            boolean attachToParent, @Nullable Object bindingComponent) {
        //最终调用了DataBindingUtil的inflate方法
        return DataBindingUtil.inflate(
                inflater,
                layoutId,
                parent,
                attachToParent,
                checkAndCastToBindingComponent(bindingComponent)
        );
    }

	//DataBindingUtil.java
    public static <T extends ViewDataBinding> T inflate(
            @NonNull LayoutInflater inflater, int layoutId, @Nullable ViewGroup parent,
            boolean attachToParent, @Nullable DataBindingComponent bindingComponent) {
        final boolean useChildren = parent != null && attachToParent;
        final int startChildren = useChildren ? parent.getChildCount() : 0;
        final View view = inflater.inflate(layoutId, parent, attachToParent);
        if (useChildren) {
            return bindToAddedViews(bindingComponent, parent, startChildren, layoutId);
        } else {
            return bind(bindingComponent, view, layoutId);
        }
    }
```

这里最终又回归到了`bindToAddedViews()`方法，和上面的`setContentView`走到了同一个入口。所以就不再重复了。

#### 控件和布局的绑定

继承关系是：DataBindingImpl->DataBinding->ViewDataBinding。

```
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

##### mapBindings进行控件和id的绑定

```
    //androidx.databinding.ViewDataBinding.java
    protected static Object[] mapBindings(DataBindingComponent bindingComponent, View root,
            int numBindings, IncludedLayouts includes, SparseIntArray viewsWithIds) {
        Object[] bindings = new Object[numBindings];
        mapBindings(bindingComponent, root, bindings, includes, viewsWithIds, true);
        return bindings;
    }
    //控件和id的映射
    private static void mapBindings(DataBindingComponent bindingComponent, View view,
            Object[] bindings, IncludedLayouts includes, SparseIntArray viewsWithIds,
            boolean isRoot) {
        final int indexInIncludes;
        //已经存在，直接返回
        final ViewDataBinding existingBinding = getBinding(view);
        if (existingBinding != null) {
            return;
        }
        Object objTag = view.getTag();
        final String tag = (objTag instanceof String) ? (String) objTag : null;
        boolean isBound = false;
        if (isRoot && tag != null && tag.startsWith("layout")) {
            final int underscoreIndex = tag.lastIndexOf('_');
            if (underscoreIndex > 0 && isNumeric(tag, underscoreIndex + 1)) {
                final int index = parseTagInt(tag, underscoreIndex + 1);
                if (bindings[index] == null) {
                    bindings[index] = view;
                }
                indexInIncludes = includes == null ? -1 : index;
                isBound = true;
            } else {
                indexInIncludes = -1;
            }
        } else if (tag != null && tag.startsWith(BINDING_TAG_PREFIX)) {
            int tagIndex = parseTagInt(tag, BINDING_NUMBER_START);
            if (bindings[tagIndex] == null) {
                bindings[tagIndex] = view;
            }
            isBound = true;
            indexInIncludes = includes == null ? -1 : tagIndex;
        } else {
            // Not a bound view
            indexInIncludes = -1;
        }
        if (!isBound) {
            final int id = view.getId();
            if (id > 0) {
                int index;
                if (viewsWithIds != null && (index = viewsWithIds.get(id, -1)) >= 0 &&
                        bindings[index] == null) {
                    bindings[index] = view;
                }
            }
        }

        if (view instanceof  ViewGroup) {
            final ViewGroup viewGroup = (ViewGroup) view;
            final int count = viewGroup.getChildCount();
            int minInclude = 0;
            for (int i = 0; i < count; i++) {
                final View child = viewGroup.getChildAt(i);
                boolean isInclude = false;
                if (indexInIncludes >= 0 && child.getTag() instanceof String) {
                    String childTag = (String) child.getTag();
                    if (childTag.endsWith("_0") &&
                            childTag.startsWith("layout") && childTag.indexOf('/') > 0) {
                        // This *could* be an include. Test against the expected includes.
                        int includeIndex = findIncludeIndex(childTag, minInclude,
                                includes, indexInIncludes);
                        if (includeIndex >= 0) {
                            isInclude = true;
                            minInclude = includeIndex + 1;
                            final int index = includes.indexes[indexInIncludes][includeIndex];
                            final int layoutId = includes.layoutIds[indexInIncludes][includeIndex];
                            int lastMatchingIndex = findLastMatching(viewGroup, i);
                            if (lastMatchingIndex == i) {
                                bindings[index] = DataBindingUtil.bind(bindingComponent, child,
                                        layoutId);
                            } else {
                                final int includeCount =  lastMatchingIndex - i + 1;
                                final View[] included = new View[includeCount];
                                for (int j = 0; j < includeCount; j++) {
                                    included[j] = viewGroup.getChildAt(i + j);
                                }
                                bindings[index] = DataBindingUtil.bind(bindingComponent, included,
                                        layoutId);
                                i += includeCount - 1;
                            }
                        }
                    }
                }
                if (!isInclude) {
                    mapBindings(bindingComponent, child, bindings, includes, viewsWithIds, false);
                }
            }
        }
    }
```

1. 从View的tag中获取缓存，防止多次初始化。
2. 将view储存在`bindings`数组内，分为三种情况：
   1. 根布局，tag以layout开头；
   2. 设置@{}的，tag以binding开头
   3. 设置了id的view
3. 第三部分判断根布局是不是ViewGroup，如果是则遍历根布局，并判断子View是不是include的，如果是的话，则使用`DataBindingUtil.bind`进行递归；如果不是include，则直接使用`mapBindings`进行递归。

这里将对应的id保存到了`mapBindings`中了，然后就可以通过`ItemBcfDashBinding`的构造方法进行view和id的控件绑定。

我们看一下这里的控件是如何进行绑定的获取的情况。

我们知道这里的super肯定是对应了DataBinding类的。

```java
  protected ItemBcfDashBinding(Object _bindingComponent, View _root, int _localFieldCount,
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

#### setModel()

当我们进行关系绑定的时候，是通过`setModel()`方法（这里是需要看你设置的variable的名称了）绑定的。

我们看一下`ActivityLoginPageBindingImpl`的具体代码。我们从`setModel`先来跟踪一下。

```java
    public void setModel(@Nullable LoginViewModel Model) {
        this.mModel = Model;
        synchronized(this) {
            //mDirtyFlags用于说明是哪个数据发生了变化了
            mDirtyFlags |= 0x10L;
        }
        //通知数据属性的变化
        notifyPropertyChanged(BR.model);
        //进行重新绑定
        super.requestRebind();
    }
```

在设置完数据以后，会通知数据属性发生了变化。然后会请求进行重新绑定。

```java
    protected void requestRebind() {
        if (mContainingBinding != null) {
            mContainingBinding.requestRebind();
        } else {
            //获取到对应的生命周期拥有者
            final LifecycleOwner owner = this.mLifecycleOwner;
            if (owner != null) {
                //获取当前的生命周期状态
                Lifecycle.State state = owner.getLifecycle().getCurrentState();
                if (!state.isAtLeast(Lifecycle.State.STARTED)) {
                    //当前未STARTED，不需要进行绑定
                    return; // wait until lifecycle owner is started
                }
            }
            synchronized (this) {
                if (mPendingRebind) {//如果为已经绑定过，则直接返回
                    return;
                }
                mPendingRebind = true;
            }
            if (USE_CHOREOGRAPHER) {//pUSE_CHOREOGRAPHER = SDK_INT >= 16;
                //这里进行了版本的区分，16以上和16以下会调用不同的方法
                mChoreographer.postFrameCallback(mFrameCallback);
            } else {
                mUIThreadHandler.post(mRebindRunnable);
            }
        }
    }
```

##### postCallBack

当版本号>16的时候，会发送一个callback。。

```java
   		mFrameCallback = new Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    mRebindRunnable.run();
                }
            };
```

会执行mRebindRunnable的`run`方法

```java
    private final Runnable mRebindRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (this) {
                mPendingRebind = false;
            }
            processReferenceQueue();

            if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
                // Nested so that we don't get a lint warning in IntelliJ
                if (!mRoot.isAttachedToWindow()) {
                    //如果没有绑定到window的话，会监听绑定。当绑定以后会调用Listener。最后也是会调用到executePendingBindings方法
                    mRoot.removeOnAttachStateChangeListener(ROOT_REATTACHED_LISTENER);
                    mRoot.addOnAttachStateChangeListener(ROOT_REATTACHED_LISTENER);
                    return;
                }
            }
            executePendingBindings();
        }
    };
```

这里会检测root是否绑定到了window上，如果没有绑定的话，会增加一个绑定的监听函数，当root绑定以后，最终仍然会走到`executePendingBindings()`方法中

```java
    public void executePendingBindings() {
        if (mContainingBinding == null) {
            executeBindingsInternal();
        } else {
            mContainingBinding.executePendingBindings();
        }
    }

    /**
     * Evaluates the pending bindings without executing the parent bindings.
     */
    private void executeBindingsInternal() {
        if (mIsExecutingPendingBindings) {
            //如果正在执行，则表示正在执行这段代码，会调用requestRebind。然后返回。
            requestRebind();
            return;
        }
        if (!hasPendingBindings()) {
            //如果当前没有需要绑定的数据，则返回。hasPendingBindings()方法一般是在DataBindingImpl中实现，通过mDirtyFlagss是否为空来进行判断。
            return;
        }
        //将正在执行绑定过程标志位置为true。
        mIsExecutingPendingBindings = true;
        mRebindHalted = false;
        if (mRebindCallbacks != null) {
            //如果存在mRebindCallbacks回调，则调用
            mRebindCallbacks.notifyCallbacks(this, REBIND, null);
            //onRebindListeners会修改mRebindHalted参数。如果导致了重绑定暂停，那么会调用HALTED回调
            if (mRebindHalted) {
                mRebindCallbacks.notifyCallbacks(this, HALTED, null);
            }
        }
        if (!mRebindHalted) {
            //执行绑定过程
            executeBindings();
            if (mRebindCallbacks != null) {
                mRebindCallbacks.notifyCallbacks(this, REBOUND, null);
            }
        }
        mIsExecutingPendingBindings = false;
    }
```

mRebindHalted参数比较特殊

1. 如果设置了mRebindCallbacks，那么就会调用回调，而在执行REBIND的过程中，可能会修改`mRebindHalted`，从而实现对于rebind的暂停执行。
2. 如果`mRebindHalted`被置为了true，那么就会执行`HALTED`，而后面就不会执行`executeBindings()`方法。
3. 如果`mRebindHalted`为false，则执行`executeBindings()`方法。

##### mRebindCallbacks设置

对于这个回调方法，是通过`addOnRebindCallback()`来进行设置的。

```java
    //增加一个数据发生变化时的监听器。允许自动暂停更新页面，但是改功能需要显示的调用executePendingBindings()方法
	public void addOnRebindCallback(@NonNull OnRebindCallback listener) {
        if (mRebindCallbacks == null) {
            mRebindCallbacks = new CallbackRegistry<OnRebindCallback, ViewDataBinding, Void>(REBIND_NOTIFIER);
        }
        mRebindCallbacks.add(listener);
    }

    private static final CallbackRegistry.NotifierCallback<OnRebindCallback, ViewDataBinding, Void>
        REBIND_NOTIFIER = new NotifierCallback<OnRebindCallback, ViewDataBinding, Void>() {
        @Override
        public void onNotifyCallback(OnRebindCallback callback, ViewDataBinding sender, int mode,
                Void arg2) {
            switch (mode) {
                case REBIND:
                    if (!callback.onPreBind(sender)) {
                        //如果onPreBind返回了false，那么就会暂停绑定本次绑定过程。
                        sender.mRebindHalted = true;
                    }
                    break;
                case HALTED:
                    callback.onCanceled(sender);
                    break;
                case REBOUND:
                    callback.onBound(sender);
                    break;
            }
        }
    };
```

##### executeBindings绑定数据

executeBindings是由具体的实现类来处理的。

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
                	//重点方法，更新监听函数
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
		   //重点方法：将View中的值与viewModel中的值做绑定
            androidx.databinding.adapters.TextViewBindingAdapter.setText(this.sendButtonText, modelCountDownGet);
        }
    }
    ...
}
```

##### updateRegistration

当数据发生变化的时候，会调用`updateRegistration()`实现值变化的时候，更新数据。

```java
    private static final CreateWeakListener CREATE_PROPERTY_LISTENER = new CreateWeakListener() {
        @Override
        public WeakListener create(ViewDataBinding viewDataBinding, int localFieldId) {
            return new WeakPropertyListener(viewDataBinding, localFieldId).getListener();
        }
    };

	protected boolean updateRegistration(int localFieldId, Observable observable) {
        return updateRegistration(localFieldId, observable, CREATE_PROPERTY_LISTENER);
    }
```

这里的`CREATE_PROPERTY_LISTENER`是一个具体的实现类，会创建一个`WeakPropertyListener`类

```java
    private boolean updateRegistration(int localFieldId, Object observable,
            CreateWeakListener listenerCreator) {
        if (observable == null) {//
            return unregisterFrom(localFieldId);
        }
        //从已注册的进行查询
        WeakListener listener = mLocalFieldObservers[localFieldId];
        if (listener == null) {//没有查到则进行注册
            registerTo(localFieldId, observable, listenerCreator);
            return true;
        }
        if (listener.getTarget() == observable) {
            return false;//nothing to do, same object
        }
        //证明和原来的不一样，需要解除注册，然后再注册
        unregisterFrom(localFieldId);
        registerTo(localFieldId, observable, listenerCreator);
        return true;
    }
```

##### setText

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



### 总结

1. databinding可以通过addOnRebindCallback增加对于重新绑定的回调通知。

2. #### DataBinderMapperImpl用于生成的布局文件和ViewDataBinding文件的映射关系

3. 每个数据变化的时候，会有对应的dirty字段来标识哪个数据变化了，然后会更新对应的显示数据

4. DataBinding通过apt技术生成对应的文件么？
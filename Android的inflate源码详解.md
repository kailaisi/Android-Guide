### 引言

在之前的[Android布局窗口绘制分析](https://mp.weixin.qq.com/s?__biz=MzUzOTE4MTQzNQ==&mid=2247483756&idx=1&sn=864c8dd5815aa0fa4ff973f9f290831d&chksm=facd2978cdbaa06efd6147f373de477554731a4c5bbc55075614bd1f42987b62161944e73cb1&token=617519148&lang=zh_CN#rd)一文中，我们知道 **setContentView** 最后是通过 LayoutInflater.from(mContext).inflate(resId, contentParent) 来将我们自己的布局文件加载到窗口中的，那么这个 **inflate** 方法到底是如何将我们编写的布局文件一步步解析，然后进行绘制的呢？本文我们就将对这套源码来一次比较深度的剖析。

### 基础知识

 **LayoutInflater** 其本质是一个布局的渲染工具，能够将xml布局文件实例化为相应的View树。获取 **LayoutInflater** 实例的方法也相对来说比较灵活

1. context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
2. LayoutInflater.from(context)
3. 在Activity中通过 getLayoutInflater()方法。

其实这三种方法最终的调用，都会指向第一个。也就是通过IBinder来获取 **LayoutInflater服务** 。

### 测试案例

这里我们也是从最常用的 **inflate(resId, contentParent)** 来进行分析。

### 深度剖析

由于获取 **LayoutInflater** 实例的方法相对来说比较简单(其实内部的IBinder机制是最难懂的)，所以我们跳过这一步，直接分析 **inflate()** 方法。

```java
//LayoutInflater.java
	public View inflate(XmlPullParser parser, @Nullable ViewGroup root) {
		//方法重载
        return inflate(parser, root, root != null);
    }

    public View inflate(@LayoutRes int resource, @Nullable ViewGroup root, boolean attachToRoot) {
        final Resources res = getContext().getResources();
        //如果存在预编译，则可以通过tryInflatePrecompiled获取到View
        View view = tryInflatePrecompiled(resource, res, root, attachToRoot);
        if (view != null) {
            return view;
        }
		//重点方法  获取解析器XmlResourceParser：它包含解析后xml布局信息，通过它，可以获得xml中各种标签的信息，甚至你可以简化的看做是一个包含xml格式字符串的缓存对象
        XmlResourceParser parser = res.getLayout(resource);
        try {
        	//重点方法  调用重载方法
            return inflate(parser, root, attachToRoot);
        } finally {
            parser.close();
        }
    }
```

这里有两个重要的工作需要去做，一个是生成 **XmlResourceParser** 对象，另一个就是我们的重载方法。下面我们逐一对这两个方法进行分析。

#### 解析器的获取

这里生成的 **parser** 是通过 **getLayout** 方法来生成的。我们看一下具体的实现。

```java
//Resouces.java
    public XmlResourceParser getLayout(@LayoutRes int id) throws NotFoundException {
        return loadXmlResourceParser(id, "layout");
    }
    
    //为指定的文件加载XML解析器。
    XmlResourceParser loadXmlResourceParser(@AnyRes int id, @NonNull String type)throws NotFoundException {
        //获取一个typedValue。这里其实是将池(虽然这个池中只有一个)中的TypedValue取出，然后在最后的releaseTempTypedValue时，再将取出的这个typedValue放回池中
        final TypedValue value = obtainTempTypedValue();
        try {
            //这里是个ResourcesImpl类
            final ResourcesImpl impl = mResourcesImpl;
            //根据id，查询layout
            impl.getValue(id, value, true);
            if (value.type == TypedValue.TYPE_STRING) {
                //通过impl加载Parser
                return impl.loadXmlResourceParser(value.string.toString(), id,value.assetCookie, type);
            }
            throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id)+ " type #0x" + Integer.toHexString(value.type) + " is not valid");
        } finally {
            //释放资源
            releaseTempTypedValue(value);
        }
    }
```

这里进行了3个相关操作

1. 池技术获取到一个 **TypedValue** 对象 value，
2. 通过 **getValue** 方法，将相关数据放置到value中。
3. 通过 **impl.loadXmlResourceParser** 方法来获取到了对应的解析器。

##### 获取相关资源getValue()

```java
//ResourcesImpl.java
    void getValue(@AnyRes int id, TypedValue outValue, boolean resolveRefs)throws NotFoundException {
        //mAssets是一个AssetManager对象，
        boolean found = mAssets.getResourceValue(id, 0, outValue, resolveRefs);
        if (found) {
            return;
        }
        throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id));
    }

//AssetManager.java
    //加载当前配置的特定资源标识符关联的数据，然后将数据设置到outValue。
    @UnsupportedAppUsage
    boolean getResourceValue(@AnyRes int resId, int densityDpi, @NonNull TypedValue outValue,boolean resolveRefs) {Preconditions.checkNotNull(outValue, "outValue");
        synchronized (this) {
            ensureValidLocked();
            //进行本地方法的调用，将底层数据赋值给cookie。这里对应的本地方法位置为frameworks\base\core\jni\android_util_AssetManager.cpp
            final int cookie = nativeGetResourceValue(mObject, resId, (short) densityDpi, outValue, resolveRefs);
            if (cookie <= 0) {
                return false;
            }

            // Convert the changing configurations flags populated by native code.
            outValue.changingConfigurations = ActivityInfo.activityInfoConfigNativeToJava(outValue.changingConfigurations);

            if (outValue.type == TypedValue.TYPE_STRING) {
                //如果类型是String，则从全局字符串字符串中获取对应TypeValue中data对应的字符串数据。
                outValue.string = mApkAssets[cookie - 1].getStringFromPool(outValue.data);
            }
            return true;
        }
    }
```

所以这里其实是通过本地的native方法获取到了cookie，从底层将数据复制到了TypedValue中。然后判断如果TypedValue中的数据类型是String类型，从全局字符串字符串中获取对应TypeValue中data对应的字符串数据。

native方法等以后再继续分析（这个outValue中的data会保存layout/*.xml字符串所对应的index信息）。可以参考[Android 重学系列 资源的查找](https://www.jianshu.com/p/b153d63d60b3)

**其实这里属于我们的包资源的解析过程：通过对resources.arsc文件的解析，获得了一个**ResTable**对象，该对象包含了应用程序的全部资源信息，之后，就可以通过ResTable的getResource来获得指定资源，而对于xml布局文件，这里获得的就是一个引用，需要res.resolveReference二次解析，之后就得到了id对应的资源项。这里的xml布局文件对应的资源项的值是一个字符串，其实是一个布局文件路径，它指向一个经过编译的二进制格式保存的Xml资源文件。有了这个Xml资源文件的路径之后，会再次通过loadXmlResourceParser来对该Xml资源文件进行解析，从而得到布局文件解析对象XmlResourceParser。**

##### 文件资源解析器的获取

```java
//Resources.java
XmlResourceParser loadXmlResourceParser(@NonNull String file, @AnyRes int id, int assetCookie,@NonNull String type)
        throws NotFoundException {
    if (id != 0) {
        try {
            synchronized (mCachedXmlBlocks) {
                //mCachedXmlBlockCookies缓存了所有加载的xml文件
                final int[] cachedXmlBlockCookies = mCachedXmlBlockCookies;
                final String[] cachedXmlBlockFiles = mCachedXmlBlockFiles;
                final XmlBlock[] cachedXmlBlocks = mCachedXmlBlocks;
                //先从缓存查找，如果缓存中获取到了，则直接返回
                final int num = cachedXmlBlockFiles.length;
                for (int i = 0; i < num; i++) {
                    if (cachedXmlBlockCookies[i] == assetCookie && cachedXmlBlockFiles[i] != null&& cachedXmlBlockFiles[i].equals(file)) {
                        return cachedXmlBlocks[i].newParser(id);
                    }
                }

                //如果获取不到，则通过openXmlBlockAsset从native查找数据
                final XmlBlock block = mAssets.openXmlBlockAsset(assetCookie, file);
                if (block != null) {
                    final int pos = (mLastCachedXmlBlockIndex + 1) % num;
                    mLastCachedXmlBlockIndex = pos;
                    final XmlBlock oldBlock = cachedXmlBlocks[pos];
                    if (oldBlock != null) {
                        oldBlock.close();
                    }
                    //将数据进行缓存
                    cachedXmlBlockCookies[pos] = assetCookie;
                    cachedXmlBlockFiles[pos] = file;
                    cachedXmlBlocks[pos] = block;
                    //通过block生成解析器
                    return block.newParser(id);
                }
            }
        } catch (Exception e) {
            final NotFoundException rnf = new NotFoundException("File " + file+ " from xml type " + type + " resource ID #0x" + Integer.toHexString(id));
            rnf.initCause(e);
            throw rnf;
        }
    }

    throw new NotFoundException("File " + file + " from xml type " + type + " resource ID #0x"+ Integer.toHexString(id));
}
```

对于解析器的获取，首先从缓存获取，如果获取不到则通过 **openXmlBlockAsset** 来生成 **block** ，然后通过 **newParser()** 生成解析器。

到现在为之，我们已经获取到了我们所需要的 **XmlResourceParser** 对象了，剩下的就是另一个主干的重点方法->inflate

#### 布局绘制

将xml文件进行解析，还原成对应的View，都是在 **inflate(XmlPullParser parser, @Nullable ViewGroup root, boolean attachToRoot)** 来完成的。

```java
    public View inflate(XmlPullParser parser, @Nullable ViewGroup root, boolean attachToRoot) {
        synchronized (mConstructorArgs) {
            Trace.traceBegin(Trace.TRACE_TAG_VIEW, "inflate");
            //mContext是通过LayoutInflater的构造方法传进来的的context，这里一般不调用构造方法，所以基本是null
            final Context inflaterContext = mContext;
            final AttributeSet attrs = Xml.asAttributeSet(parser);
            //mConstructorArgs保存了构造方法的参数值信息，第一个是context，第二个是attr
            //这里先将参数的lastContext临时保存，在最后的位置再进行恢复
            Context lastContext = (Context) mConstructorArgs[0];
            mConstructorArgs[0] = inflaterContext;
            //解析以后最终的View树
            View result = root;

            try {
                //会将parser进行遍历，找到根节点，从根节点开始解析
                advanceToRootNode(parser);
                //name是根节点标签
                final String name = parser.getName();

                //根布局是Merge标签
                if (TAG_MERGE.equals(name)) {
                    //merge标签必须嵌套到对应的父布局中来使用。所以如果root是空或者merge不绑定到布局中，那么就报错
                    if (root == null || !attachToRoot) {
                        throw new InflateException("<merge /> can be used only with a valid "+ "ViewGroup root and attachToRoot=true");
                    }
                    //进行布局文件的绘制
                    rInflate(parser, root, inflaterContext, attrs, false);
                } else {
                    // Temp is the root view that was found in the xml
                    //通过标签来创建View
                    final View temp = createViewFromTag(root, name, inflaterContext, attrs);
                    ViewGroup.LayoutParams params = null;
                    if (root != null) {
                        //根据要创建的布局的属性以及root的信息，生成布局对应的params信息
                        params = root.generateLayoutParams(attrs);
                        if (!attachToRoot) {
                            //如果不添加到父布局，则使用params参数。如果添加到父布局的话，后面会通过addView来
                            temp.setLayoutParams(params);
                        }
                    }
                    //绘制子控件，并添加到temp中
                    rInflateChildren(parser, temp, attrs, true);
                    //如果设置了root，并且要添加到父控件，那么就进行rooot.addView操作
                    if (root != null && attachToRoot) {
                        root.addView(temp, params);
                    }
                    //如果root为空或者不添加到父控件。则直接返回绘制的view
                    if (root == null || !attachToRoot) {
                        result = temp;
                    }
                }

            }
            ...
            return result;
        }
    }
```

**inflate** 的主要作用是根据layout生成对应的布局文件，这里根据入参以及相应的标签进行了不同的处理。

1. merge标签，不能单独使用，必须有root，而且必须绑定。如果符合就调用 **rInflate** 绘制
2. 通过 **createViewFromTag** 创建View，然后如果有root，但是不绑定，则根据root的属性设置创建的view的属性。最后通过 **rInflateChildren** （内部其实调用了 **rInflate** ）进行遍历子控件进行绘制。
3. 如果有root，而且进行绑定，那么将创建的view通过addView方法添加到root中。

这里面有两个方法，是需要我们特别注意的，一个是 **createViewFromTag** 方法，一个是 **rInflate** 方法。这里我们逐个进行分析。

##### 创建最上层的控件

先从createViewFromTag开始，这个方法主要是进行第一个控件的绘制。而且有很多我们可以操作的地方

```java
//LayoutInflater.java
	View createViewFromTag(View parent, String name, Context context, AttributeSet attrs,boolean ignoreThemeAttr) {
        if (name.equals("view")) {
            name = attrs.getAttributeValue(null, "class");
        }

        if (!ignoreThemeAttr) {//进行主题的处理
            final TypedArray ta = context.obtainStyledAttributes(attrs, ATTRS_THEME);
            final int themeResId = ta.getResourceId(0, 0);
            if (themeResId != 0) {
                context = new ContextThemeWrapper(context, themeResId);
            }
            ta.recycle();//进行资源回收
        }
        try {
            //尝试通过Factory来创建View。这里是个hook的点，如果没有设置的话，会返回 null
            View view = tryCreateView(parent, name, context, attrs);
            if (view == null) {
                //如果创建的view为空，那么就尝试从系统的控件中加载
                final Object lastContext = mConstructorArgs[0];
                mConstructorArgs[0] = context;
                try {
                    //根据name是否包含“.”来判断是否是自定义view。一般自定义的View是包含.的，
                    //其实最终都会调用createView方法。系统自带的View，会将prefix设置为“android.view”，然后调用createView方法
                    if (-1 == name.indexOf('.')) {
                        view = onCreateView(context, parent, name, attrs);
                    } else {
                        view = createView(context, name, null, attrs);
                    }
                } finally {
                    mConstructorArgs[0] = lastContext;
                }
            }

            return view;
        }
    }
```

这个方法主要通过 **tryCreateView** 方法进行资源的创建，如果返回为空，则通过 **createView** 方法进行view的创建工作。

这里我们看一下 **tryCreateView** 方法

```java
//LayoutInflater.java
	public final View tryCreateView(@Nullable View parent, @NonNull String name,@NonNull Context context, @NonNull AttributeSet attrs) {
        if (name.equals(TAG_1995)) {
            return new BlinkLayout(context, attrs);
        }
        //通过Factory来创建View。这里是个hook的点吧？
        View view;
        if (mFactory2 != null) {
            view = mFactory2.onCreateView(parent, name, context, attrs);
        } else if (mFactory != null) {
            view = mFactory.onCreateView(name, context, attrs);
        } else {
            view = null;
        }

        if (view == null && mPrivateFactory != null) {
            view = mPrivateFactory.onCreateView(parent, name, context, attrs);
        }

        return view;
    }
```

该方法主要是根据设置的各种Factory，通过其 **onCreateView()** 方法进行View的创建。

在试图通过factory创建view之后，如果返回为空，会调用 **createView** 方法来进行view的绘制工作。

```
public final View createView(@NonNull Context viewContext, @NonNull String name, @Nullable String prefix, @Nullable AttributeSet attrs) throws ClassNotFoundException, InflateException {
    //从缓存中获取构造方法
    Constructor<? extends View> constructor = sConstructorMap.get(name);
    if (constructor != null && !verifyClassLoader(constructor)) {
        //如果缓存的类加载器不是根加载器中的不一致
        constructor = null;
        sConstructorMap.remove(name);
    }
    Class<? extends View> clazz = null;
    try {
        if (constructor == null) {
            //获取clazz文件
            clazz = Class.forName(prefix != null ? (prefix + name) : name, false,mContext.getClassLoader()).asSubclass(View.class);
            //获取构造方法，获取的是参数为(Context.class, AttributeSet)的那个构造方法
            constructor = clazz.getConstructor(mConstructorSignature);
            constructor.setAccessible(true);
            sConstructorMap.put(name, constructor);
        }
        ...
        try {
            //调用构造函数，生成View类，args是两个参数的构造方法，所以也就是我们xml中写的布局，其实最后都会调用两个参数的那个构造方法
            final View view = constructor.newInstance(args);
            if (view instanceof ViewStub) {
                // Use the same context when inflating ViewStub later.
                //如果是ViewStub类
                final ViewStub viewStub = (ViewStub) view;
                viewStub.setLayoutInflater(cloneInContext((Context) args[0]));
            }
            return view;
       ...
   
}
```

在creatView中，主要是通过反射的方法，调用控件的两个参数的构造方法来进行创建。

到这里为止我们的最外层的控件绘制完成了。其主要的流程其实就是两个

1. 通过factory创建，
2. 如果factory创建为空，那么就通过反射来创建View控件

##### 循环创建子View

在之前的源码中我们了解到，外层控件绘制完成以后，会通过 **rInflater** 方法来进行其子控件的绘制，然后通过循环来创建整个布局文件对应的View树。

```java
    //递归方法，用于向下传递xml层次结构并实例化视图、实例化其子视图，然后调用onfinishinfl()
    void rInflate(XmlPullParser parser, View parent, Context context,AttributeSet attrs, boolean finishInflate) throws XmlPullParserException, IOException {

        final int depth = parser.getDepth();
        int type;
        boolean pendingRequestFocus = false;

        while (((type = parser.next()) != XmlPullParser.END_TAG ||parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {

            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            //获取标签名
            final String name = parser.getName();

            if (TAG_REQUEST_FOCUS.equals(name)) {
                pendingRequestFocus = true;
                consumeChildElements(parser);
            } else if (TAG_TAG.equals(name)) {
                //tag标签
                parseViewTag(parser, parent, attrs);
            } else if (TAG_INCLUDE.equals(name)) {
                //include标签不能是根标签
                if (parser.getDepth() == 0) {
                    throw new InflateException("<include /> cannot be the root element");
                }
                parseInclude(parser, context, parent, attrs);
            } else if (TAG_MERGE.equals(name)) {
                //merge标签必须是布局的根元素
                throw new InflateException("<merge /> must be the root element");
            } else {
                //调用createViewFromTag方法，生成对应的view，然后将子view添加到parent布局中
                final View view = createViewFromTag(parent, name, context, attrs);
                final ViewGroup viewGroup = (ViewGroup) parent;
                final ViewGroup.LayoutParams params = viewGroup.generateLayoutParams(attrs);
                //遍历view下面的子控件
                rInflateChildren(parser, view, attrs, true);
                viewGroup.addView(view, params);
            }
        }

        if (pendingRequestFocus) {
            parent.restoreDefaultFocus();
        }

        if (finishInflate) {
            parent.onFinishInflate();
        }
    }
```

所以这里会通过不断的循环调用rInflate方法，将view挂载在父控件下，然后创建子控件。从而完成整个view树的绘制工作。

到这里我们的整个LayoutInflater的工作完成了。

在我们之前讲的一篇[Android布局窗口绘制分析]中讲解过，会将我们的setContextView的布局文件通过inflate方法，绘制到PhoneWindows中的到mContentParent内部。从而实现布局的展示工作。这里算是完成了从窗口的显示到布局的绘制这一条线的工作。

### 强势插入Factory

在进行控件的创建时，我们提到了可以通过factory的 **onCreateView()** 方法进行View的创建工作。其实这里是个经常被hook的地方。这里我们花费一些时间来延伸一下。试想一下，如果我们能够进行Factory的设置，那么我们是不是就可以对页面的布局进行各种操作了。我们把你传过来的TextView变成Button？把整个页面的控件样式、颜色全局改变（咦？我们的最近遇到的应用全局变灰是可以通过这种方式实现呢？）。

这个里面使用的mFactory，mFactory2都是什么呢？

```
//LayoutInflater.java

    private Factory mFactory;

    private Factory2 mFactory2;  
    
    public interface Factory {
        View onCreateView(@NonNull String name, @NonNull Context context,@NonNull AttributeSet attrs);
    }

    public interface Factory2 extends Factory {

        View onCreateView(@Nullable View parent, @NonNull String name,@NonNull Context context, @NonNull AttributeSet attrs);
    }
    
```

所以其实只是两个接口而已。

```
////LayoutInflater.java
public void setFactory(Factory factory) {
    if (mFactorySet) {
        throw new IllegalStateException("A factory has already been set on this LayoutInflater");
    }
    if (factory == null) {
        throw new NullPointerException("Given factory can not be null");
    }
    mFactorySet = true;
    if (mFactory == null) {
        mFactory = factory;
    } else {
        mFactory = new FactoryMerger(factory, null, mFactory, mFactory2);
    }
}


public void setFactory2(Factory2 factory) {
    if (mFactorySet) {
        throw new IllegalStateException("A factory has already been set on this LayoutInflater");
    }
    if (factory == null) {
        throw new NullPointerException("Given factory can not be null");
    }
    mFactorySet = true;
    if (mFactory == null) {
        mFactory = mFactory2 = factory;
    } else {
        mFactory = mFactory2 = new FactoryMerger(factory, factory, mFactory, mFactory2);
    }
}
```

而且设置也比较简单，有对应的public方法来进行设置。唯一的限制就是factory和factory2只能设置一个，而且也不能重复设置。

那么其意义是什么呢？比如说我现在有一个有个需求，想要把某个页面中的TextView全部修改为自定义的NewTextView。那么我们可以创建一个Factory的实现类，然后设置一次即可。

```
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	// 注意需在调用setContentView之前设置，因为setContentView的时候就会使用到Factory
        LayoutInflater.from(this).setFactory2(new LayoutInflater.Factory2() {
            @Override
            public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
                return null;
            }

            @Override
            public View onCreateView(String name, Context context, AttributeSet attrs) {
            	if(TextUtils.equals(name,"TextView")){
                    return new NewTextView(MainActivity.this,attrs);
                }
                return null;
            }
        });

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
```

通过这种方法就可以进行全局替换了。

其实我们经常使用的 **AppCompatActivity** 也是这么来进行处理的。我们跟踪一下源码看看。

```
public class AppCompatActivity extends FragmentActivity implements AppCompatCallback,
    TaskStackBuilder.SupportParentable, ActionBarDrawerToggle.DelegateProvider {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getDelegate().installViewFactory();
        getDelegate().onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);
    }

    public AppCompatDelegate getDelegate() {
        if (mDelegate == null) {
            mDelegate = AppCompatDelegate.create(this, this);
        }
        return mDelegate;
    }
}
```

这里的AppCompatDelegate.create方法进行了不同的版本处理。

```java
public abstract class AppCompatDelegate {
    public static AppCompatDelegate create(Activity activity, AppCompatCallback callback) {
        return create(activity, activity.getWindow(), callback);
    }

    //对于不同的版本做适配
    private static AppCompatDelegate create(Context context, Window window,
        AppCompatCallback callback) {
        final int sdk = Build.VERSION.SDK_INT;
        if (sdk >= 23) {
            return new AppCompatDelegateImplV23(context, window, callback);
        } else if (sdk >= 14) {
            return new AppCompatDelegateImplV14(context, window, callback);
        } else if (sdk >= 11) {
            return new AppCompatDelegateImplV11(context, window, callback);
        } else {
            return new AppCompatDelegateImplV7(context, window, callback);
        }
    }
}
```

当我们创建了对应的DelegateImpl之后，会调用对应的 **installViewFactory** 方法。

```java
    class AppCompatDelegateImplV7 extends AppCompatDelegateImplBase
        implements MenuBuilder.Callback, LayoutInflaterFactory {
        /***部分代码省略****/
        @Override
        public void installViewFactory() {
            LayoutInflater layoutInflater = LayoutInflater.from(mContext);
            if (layoutInflater.getFactory() == null) {
                //这里面会进行setFactory的设置
                LayoutInflaterCompat.setFactory(layoutInflater, this);
            }
            ...
        }
    }

```

也很好奇，为嘛在会设置一个Factory呢？我们跟踪LayoutInflaterCompat.setFactory，最终都会跟踪到 **AppCompatViewInflater** 这个类。

```
//AppCompatViewInflater.java
    class AppCompatViewInflater {
        /***部分代码省略****/
        public final View createView(View parent, final String name, @NonNull Context context,
            @NonNull AttributeSet attrs, boolean inheritContext,
            boolean readAndroidTheme, boolean readAppTheme, boolean wrapContext) {
            ...
            switch (name) {
                case "TextView":
                    view = new AppCompatTextView(context, attrs);
                    break;
                case "ImageView":
                    view = new AppCompatImageView(context, attrs);
                    break;
                ...
            }

            if (view == null && originalContext != context) {
                view = createViewFromTag(context, name, attrs);
            }

            if (view != null) {
                checkOnClickListener(view, attrs);
            }
            return view;
            }
    }
```

明白了么？ **AppCompatActivity** 是通过setFactory方法，将我们使用的 **TextView** 替换为了 **AppCompatTextView** ， **Button** 设置为了 **AppCompatButton** 等，从而做到了兼容的样式。

既然这里是设置了一个Factory了，在刚才的源码中我们看到，其实Factory和Factory2只能设置一次。这时候问题来了，如果我们的类继承了 **AppCompatActivity** 之后，又该如何处理呢？我们会在后面的章节中继续探索。这里就先到此为止。

### 总结

善于总结，才会不断的积累知识：

1. 对于布局文件的获取是通过native方法来进行的。
2. 在通过native方法获取资源时，会完成对 **resources.arsc** 文件的解析，创建一个 **ResTable** 对象，该对象包含了应用程序的全部资源信息。之后通过 **ResTable** 的getResource来获取指定的资源，对于xml布局文件，则只是获取一个引用，需要通过 res.resolveReference进行二次解析，得到id对应的资源项(一个字符串，布局文件的路径，指向经过编译的二进制格式保存的xml资源文件）。然后通过 **loadXmlResourceParser** 对这个路径的xml进行解析，得到不问文件解析对象 **XmlResourceParser** 。
3. 在进行解析的时候，对View的解析，是使用的参数为(Context.class, AttributeSet)的那个构造方法
4. 我们可以通过setFactory的方法来进行全局的控件的替换工作，属于一个能够hook的点。
5. <Merge>标签不能作为顶级标签来使用
6. <merge>标签必须是布局的根元素
7. 有一种BlinkLayout布局，是闪烁的布局

> 本文由 [开了肯](http://www.kailaisii.com/) 发布！ 
>
> 同步公众号[开了肯]

![image-20200404120045271](http://cdn.qiniu.kailaisii.com/typora/20200404120045-194693.png)
##   ViewModel源码解析

### 前言

之前我们发布过 **LiveDate源码解析** 、**Lifecycle源码解析** 两篇文章，这两个一般都是和本章要说的ViewModel一起使用的，最主要的原因就是因为ViewModel会随着Activity的销毁来自动的调用其销毁函数 **onClear** ，从而能让我们做一些相关的数据解绑、请求的取消等操作，非常的方便。只要 **Activity**或**Fragment**是活动的，那么ViewModel就不会进行销毁。也就意味着ViewModel不会因为配置改变(比如旋转)而被销毁。

### 案例

还是从最简单的小例子开始一点点的追踪，一般我们使用时，直接继承 ViewModel即可。

```java
class TestViewModel: ViewModel() {
    override fun onCleared() {
        super.onCleared()
    }
}

class TestActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ...
        ViewModelProviders.of(this).get(TestViewModel::class.java)
    }
}
```

代码很简单，但是为什么几行代码就能实现对其销毁的监听了呢？

### 源码解析

我们看看 **ViewModelProviders.of(this)** 帮我们做了什么

```java
    public static ViewModelProvider of(@NonNull FragmentActivity activity) {
        return of(activity, null);
    }
     
     public static ViewModelProvider of(@NonNull FragmentActivity activity,
            @Nullable Factory factory) {
        Application application = checkApplication(activity);
        if (factory == null) {
        	//如果factory为空，那么使用应用级别的factory,使用的是单例模式
            factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application);
        }
         //根据ViewModelStores.of来创建ViewModelStore对象，然后返回对应的ViewModelProvider
        return new ViewModelProvider(ViewModelStores.of(activity), factory);
    }

```

这里有两个我们需要关注的点，一个是单例factory，一个是ViewModelProvider的获取。

1. 先看一下第一个factory的单例获取

```java
public static class AndroidViewModelFactory extends ViewModelProvider.NewInstanceFactory {
    private static AndroidViewModelFactory sInstance;
	//单例模式，最简单的饿汉模式，非线程安全的。
    public static AndroidViewModelFactory getInstance(@NonNull Application application) {
        if (sInstance == null) {
            sInstance = new AndroidViewModelFactory(application);
        }
        return sInstance;
    }
    private Application mApplication;
    public AndroidViewModelFactory(@NonNull Application application) {
        mApplication = application;
    }

    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (AndroidViewModel.class.isAssignableFrom(modelClass)) {
        	//如果是AndroidViewModel的话直接创建带Application的ViewModel
            try {
                return modelClass.getConstructor(Application.class).newInstance(mApplication);
            } catch (NoSuchMethodException e) {
                .....
            }
        }
        //调用父类方法，创建无构造参数的ViewModel
        return super.create(modelClass);
    }
}


    public static class NewInstanceFactory implements Factory {
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            try {
            	//直接调用无参构造函数
                return modelClass.newInstance();
            } catch (InstantiationException e) {
                throw new RuntimeException("Cannot create an instance of " + modelClass, e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot create an instance of " + modelClass, e);
            }
        }
    }
```

可以看到，当我们使用应用自己创建的Factory单例时，当创建 ViewModel 的实例的时候，先判断是否是 AndroidViewModel 的子类，如果是就创建带 Applictaion 的 ViewModel， 如果不是就走父类的创建函数，创建无参构造函数的 ViewModel 类。

2. 我们继续看一下ViewModelStore的创建过程

```java
    public static ViewModelStore of(@NonNull FragmentActivity activity) {
        if (activity instanceof ViewModelStoreOwner) {
        	//如果activity实现了ViewModelStoreOwner接口，那么直接调用其getViewModelStore()方法，返回其对应的ViewModelStore对象
            return ((ViewModelStoreOwner) activity).getViewModelStore();
        }
        //否则通过创建holderFragment，并将其绑定到activity上，然后返回holderFragmentFor的ViewModelStore对象
        return holderFragmentFor(activity).getViewModelStore();
    }
```

第一种情况我们先放下，我们主要跟踪一下第二种情况。

```java
    private static final HolderFragmentManager sHolderFragmentManager = new HolderFragmentManager();
    //返回一个持有
    public static HolderFragment holderFragmentFor(FragmentActivity activity) {
        return sHolderFragmentManager.holderFragmentFor(activity);
    }
   
        HolderFragment holderFragmentFor(FragmentActivity activity) {
        	//获取activity对应的manager
            FragmentManager fm = activity.getSupportFragmentManager();
            //获取fm中是否有HolderFragment（根据Tag获取）
            HolderFragment holder = findHolderFragment(fm);
            if (holder != null) {//有，直接返回
                return holder;
            }
            //从未提交的holder中获取
            holder = mNotCommittedActivityHolders.get(activity);
            if (holder != null) {//存在，直接返回
                return holder;
            }
            //注册mActivity的销毁监听，在回调中，移除holder中的缓存信息
            if (!mActivityCallbacksIsAdded) {
                mActivityCallbacksIsAdded = true;
                activity.getApplication().registerActivityLifecycleCallbacks(mActivityCallbacks);
            }
            //创建HolderFragment
            holder = createHolderFragment(fm);
            //放入缓存
            mNotCommittedActivityHolders.put(activity, holder);
            return holder;
        }
        //创建HolderFragment
        private static HolderFragment createHolderFragment(FragmentManager fragmentManager) {
            HolderFragment holder = new HolderFragment();
            fragmentManager.beginTransaction().add(holder, HOLDER_TAG).commitAllowingStateLoss();
            return holder;
        }
```

回到主线，通过调用**HolderFragment**的**getViewModelStore()**方法来获取了一个**ViewModelStore**对象。那么这时候通过

```java
public ViewModelProvider(@NonNull ViewModelStore store, @NonNull Factory factory) {
    mFactory = factory;
    this.mViewModelStore = store;
}
```

将ViewModelStore和Factory设置为了ViewModelProvider的内部变量。

3. 当ViewModelProvider创建完以后，我们就可以通过get()方法来进行ViewModel的获取了。我们跟踪一下，看看做了什么处理。

```java
    public <T extends ViewModel> T get(@NonNull Class<T> modelClass) {
        ...
        return get(DEFAULT_KEY + ":" + canonicalName, modelClass);
    }
    
    public <T extends ViewModel> T get(@NonNull String key, @NonNull Class<T> modelClass) {
    	//从ViewModelStore中获取对应的ViewModel
        ViewModel viewModel = mViewModelStore.get(key);
        if (modelClass.isInstance(viewModel)) {//存在并且是就返回
            //noinspection unchecked
            return (T) viewModel;
        } else {
            //noinspection StatementWithEmptyBody
            if (viewModel != null) {
                // TODO: log a warning.
            }
        }
        //通过fatory的create方法创建一个viewModel对象
        viewModel = mFactory.create(modelClass);
        //将对象进行缓存
        mViewModelStore.put(key, viewModel);
        return (T) viewModel;
    }
```

可以看到ViewModelProvider通过持有mViewModelStore和Factory来进行ViewModel的创建以及缓存工作。将创建后的ViewModel对象保存到mViewModelStore中。分工比较明确。

看一下类图。

![image-20200319161740995](http://cdn.qiniu.kailaisii.com/typora/20200319161741-624266.png)

遗留问题

问：那么如何能够实现当Activity销毁的时候自动调用ViewModel的 **onCleared()**的呢？

答：主要就是我们自己无页面的**HolderFragment**来实现的。通过源码分析，我们知道是通过创建无页面的持有**ViewModelStore**对象**HolderFragment**来进行对**Activity**的监听。那么当Activity销毁的时候，就可以通过对HolderFragment的生命周期的**onDestroy()**监听来调用**ViewModelStore**的销毁。

```java
    public void onDestroy() {
        super.onDestroy();
        mViewModelStore.clear();
    }

  public final void clear() {
        for (ViewModel vm : mMap.values()) {
            vm.onCleared();
        }
        mMap.clear();
    }
```

问：如果实现当屏幕横竖屏切换时，ViewModel不销毁的？

答：HolderFragment设置了setRetainInstance(true)属性。当调用了fragment的setRetainInstance(true)方法后，在配置改变时，Fragment不会被重新创建 ，而是会短暂的处于保留状态。如果activity是因操作系统需要回收内存而被销毁，则fragment也会随之销毁。所以能够实现ViewModel不会随着横竖屏的切换而销毁。



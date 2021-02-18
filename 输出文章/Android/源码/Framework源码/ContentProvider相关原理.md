ContentProvider相关原理

#### 使用方式

```kotlin
 val resolver=context.getContentResolver()
 resolver.insert(uri,contentValuse)
```

#### 源码解析

在之前的[Context必知必会]()中我们讲过，一般context的具体实现都是在**ContextImpl**中的。这里的getContectResolver也是在ContextImpl中来实现的。

##### ContentResolver

```java
//frameworks\base\core\java\android\app\ContextImpl.java
	public ContentResolver getContentResolver() {
        return mContentResolver;
    }
```

这里的mContentResolver是呢？

```java
//frameworks\base\core\java\android\app\ContextImpl.java
	private ContextImpl(@Nullable ContextImpl container, @NonNull ActivityThread mainThread,
            @NonNull LoadedApk packageInfo, @Nullable String splitName,
            @Nullable IBinder activityToken, @Nullable UserHandle user, int flags,
            @Nullable ClassLoader classLoader, @Nullable String overrideOpPackageName) {
        mOuterContext = this;
	    ...
        mContentResolver = new ApplicationContentResolver(this, mainThread);
    }
```

##### insert方法源码解析

然后我们看一下insert方法

```java
//frameworks\base\core\java\android\content\ContentResolver.java
    public final @Nullable Uri insert(@RequiresPermission.Write @NonNull Uri url,
                @Nullable ContentValues values) {
		//获取ContentProvider
        IContentProvider provider = acquireProvider(url);
	    //调用insert方法
        Uri createdRow = provider.insert(mPackageName, url, values);
        return createdRow;
    }
```

这里先获取了ContentProvider的**IBinder**对象，然后调用了insert方法。

```java
//frameworks\base\core\java\android\app\ContextImpl.java
		private final ActivityThread mMainThread;

		protected IContentProvider acquireProvider(Context context, String auth) {
            return mMainThread.acquireProvider(...);
        }
```

这里跟踪acquireProvider方法

```java
//frameworks\base\core\java\android\app\ActivityThread.java
	public final IContentProvider acquireProvider(Context c, String auth, int userId, boolean stable) {
        //方法1     查询本地存在的Provider对象
        final IContentProvider provider = acquireExistingProvider(c, auth, userId, stable);
        if (provider != null) {
            return provider;
        }
		//方法2    通过AMS创建一个ContentProvider对象
         holder = ActivityManager.getService().getContentProvider(getApplicationThread(), c.getOpPackageName(), auth, userId, stable);

        //方法3    通过holder安装一个本地Provider对象
        holder = installProvider(c, holder, holder.info,true /*noisy*/, holder.noReleaseNeeded, stable);
        return holder.provider;
    }
```

这个方法主要有3个需要关注的方法：

* 本地查找Provider对象
* 通过AMS创建ContentProvider对象
* 安装本地Provider

![image-20210208171646218](http://cdn.qiniu.kailaisii.com/typora/20210208171647-840850.png)

##### acquireExistingProvider

```java
//frameworks\base\core\java\android\app\ActivityThread.java
	public final IContentProvider acquireExistingProvider(Context c, String auth, int userId, boolean stable) {
        synchronized (mProviderMap) {
			//这里的ProviderKey重写了equals和hashCode方法，如果userid和auth一样，则是同样的对象。这样的话，当从 map中获取的时候，如果二者相同，就能获取到对应的value值
            final ProviderKey key = new ProviderKey(auth, userId);
			//map里面查找
            final ProviderClientRecord pr = mProviderMap.get(key);
            if (pr == null) {
                return null;
            }

            IContentProvider provider = pr.mProvider;
            IBinder jBinder = provider.asBinder();
            if (!jBinder.isBinderAlive()) {//检查provider对应的Binder已经挂掉了，那么就要做一些清理工作
                handleUnstableProviderDiedLocked(jBinder, true);
                return null;
            }

            ProviderRefCount prc = mProviderRefCountMap.get(jBinder);
            if (prc != null) {
                incProviderRefLocked(prc, stable);
            }
            return provider;
        }
    }
```

这里会通过map来查找所对应的provider对象。如果这里没有获取到的话，会返回一个null。

##### ContentProvider对象创建

如果在map中没有找到对应的provider对象，那么就会通过AMS来创建一个对象。

```java
//frameworks\base\services\core\java\com\android\server\am
public final ContentProviderHolder getContentProvider(IApplicationThread caller, ...) {
        return getContentProviderImpl(caller, name, null, callingUid, callingPackage,
                null, stable, userId);
    }
```

getContentProviderImpl方法比较长，我们将其分为几部分来一个个讲解：



```java
           if (providerRunning) {
				//获取对应的providerInfo
                cpi = cpr.info;
                String msg;
                if (r != null && cpr.canRunHere(r)) {
					//canRunHere用来判断cpr可否运行在r所在的线程
					//直接将ContentProviderHolder传给客户端
                    ContentProviderHolder holder = cpr.newHolder(null);
					//provider是holder的Binder对象，
					//如果这里的provider不为空的话，就可以直接使用Binder对象来进行增删改查；如果为空，则自己在本地创建一个IBinder对象，然后去增删改查
					//这里清空provider，由客户端自己去初始化provider的对象。
                    holder.provider = null;
                    return holder;
                }
```

这里判断的是ContentProviderRecord存在的情况下，并且是可以运行在调用者所在的线程。那么这时候直接返回对应的ContentProviderHolder对象。所以这里就存在两种情况。

1. provider启动之后，发布到AMS，然后应用通过AMS拿到对应的Binder对象之后，进行增删改查。
2. provider启动之后，应用直接在当前进程创建一个Provider对象，然后进行对应的增删改查，这时候相当于不再需要进行跨进程通讯了。

这里执行1还是2，就是通过**canRunHere()**方法来判断的。

```java
//frameworks\base\services\core\java\com\android\server\am\ContentProviderRecord.java
public boolean canRunHere(ProcessRecord app) {
	//ContentProvider开启了multiprocess并且进程名称相同或者uid相同。
    return (info.multiprocess || info.processName.equals(app.processName))&& uid == app.info.uid;
}
```
上面的都是

**整个创建流程如下：**

* **如果ContentProviderRecord存在就直接返回**
* **如果ContentProviderRecord不存在就创建一个**
* **如果ContentProviderRecord能跑在调用者进程，就直接返回不再往下走**
* **如果provider所在的进程没有启动，就启动进程，然后等待发布，完成发布的时候返回**
* **如果binder对象还没有发布，就请求发布，然后等待，完成发布的时候返回**

##### installProvider

```java
    private ContentProviderHolder installProvider(Context context,
                                                  ContentProviderHolder holder, ProviderInfo info,
                                                  boolean noisy, boolean noReleaseNeeded, boolean stable) {
        ContentProvider localProvider = null;
        IContentProvider provider;
		//当前客户端没有得到过provider，这时候需要获取provider的远程代理
        if (holder == null || holder.provider == null) {
            Context c = null;
		   //为要创建的provider对象创建对应的content对象
            c = context.createPackageContext(ai.packageName,Context.CONTEXT_INCLUDE_CODE);
           final java.lang.ClassLoader cl = c.getClassLoader();
		  //载入provider的类
            localProvider = packageInfo.getAppFactory().instantiateProvider(cl, info.name);
		   //获得provider对应的IContentProvider对象
            provider = localProvider.getIContentProvider();
            //绑定context，并调用provider的oncreate生命周期函数。
            localProvider.attachInfo(c, info);
        } else {
            //直接使用holder中的provider。也就是AMS返回的IContentProvider。
            provider = holder.provider;
        }
        ContentProviderHolder retHolder;
        synchronized (mProviderMap) {
            IBinder jBinder = provider.asBinder();
            if (localProvider != null) {
                ComponentName cname = new ComponentName(info.packageName, info.name);
				//ProviderClientRecord是一个包装的类，将provider的句柄信息，holder信息，provider信息等等都放入到一个类中。
                ProviderClientRecord pr = mLocalProvidersByName.get(cname);
                if (pr != null) {
                    provider = pr.mProvider;
                } else {
                    holder = new ContentProviderHolder(info);
                    holder.provider = provider;
                    holder.noReleaseNeeded = true;
                    pr = installProviderAuthoritiesLocked(provider, localProvider, holder);
                    mLocalProviders.put(jBinder, pr);
                    mLocalProvidersByName.put(cname, pr);
                }
                retHolder = pr.mHolder;
            } else {
                ProviderRefCount prc = mProviderRefCountMap.get(jBinder);
                if (prc != null) {
                    if (DEBUG_PROVIDER) {
                        Slog.v(TAG, "installProvider: lost the race, updating ref count");
                    }
                    // We need to transfer our new reference to the existing
                    // ref count, releasing the old one...  but only if
                    // release is needed (that is, it is not running in the
                    // system process).
                    if (!noReleaseNeeded) {
                        incProviderRefLocked(prc, stable);
                        try {
                            ActivityManager.getService().removeContentProvider(
                                    holder.connection, stable);
                        } catch (RemoteException e) {
                            //do nothing content provider object is dead any way
                        }
                    }
                } else {
                    ProviderClientRecord client = installProviderAuthoritiesLocked(
                            provider, localProvider, holder);
                    if (noReleaseNeeded) {
                        prc = new ProviderRefCount(holder, client, 1000, 1000);
                    } else {
                        prc = stable
                                ? new ProviderRefCount(holder, client, 1, 0)
                                : new ProviderRefCount(holder, client, 0, 1);
                    }
                    mProviderRefCountMap.put(jBinder, prc);
                }
                retHolder = prc.holder;
            }
        }
        return retHolder;
    }
```



https://blog.csdn.net/carson_ho/article/details/76101093

https://www.jianshu.com/p/5e13d1fec9c9

https://blog.csdn.net/daogepiqian/article/details/50756873

总结：

Provider是可以跑在自己的进程之外，也可以跑在子进程的。
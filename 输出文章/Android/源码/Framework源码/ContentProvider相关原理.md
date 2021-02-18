ContentProvider相关原理

使用方式：

```kotlin
 val resolver=context.getContentResolver()
 resolver.insert(uri,contentValuse)
```

这里得到的resolver是

```java
//frameworks\base\core\java\android\app\ContextImpl.java
	public ContentResolver getContentResolver() {
        return mContentResolver;
    }
```

这里的mContentResolver是什么时候启动的呢？

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

这里先获取了Provider的IBinder对象，然后调用了insert方法。

```java
//frameworks\base\core\java\android\app\ContextImpl.java
		protected IContentProvider acquireProvider(Context context, String auth) {
            return mMainThread.acquireProvider(...);
        }
```

这里跟踪acquireProvider方法

```java
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

#### acquireExistingProvider

```java
    public final IContentProvider acquireExistingProvider(Context c, String auth, int userId, boolean stable) {
        synchronized (mProviderMap) {
            final ProviderKey key = new ProviderKey(auth, userId);
			//map里面查找
            final ProviderClientRecord pr = mProviderMap.get(key);
            if (pr == null) {
                return null;
            }

            IContentProvider provider = pr.mProvider;
            IBinder jBinder = provider.asBinder();
            if (!jBinder.isBinderAlive()) {//检查provider对应的Binder已经挂掉了，那么就要做一些清理工作
                // The hosting process of the provider has died; we can't
                // use this one.
                Log.i(TAG, "Acquiring provider " + auth + " for user " + userId
                        + ": existing object's process dead");
                handleUnstableProviderDiedLocked(jBinder, true);
                return null;
            }

            // Only increment the ref count if we have one.  If we don't then the
            // provider is not reference counted and never needs to be released.
            ProviderRefCount prc = mProviderRefCountMap.get(jBinder);
            if (prc != null) {
                incProviderRefLocked(prc, stable);
            }
            return provider;
        }
    }

```



* 如果ContentProviderRecord存在就直接返回
* 如果ContentProviderRecord不存在就创建一个
* 如果ContentProviderRecord能跑在调用者进程，就直接返回不再往下走
* 如果provider所在的进程没有启动，就启动进程，然后等待发布，完成发布的时候返回
* 如果binder对象还没有发布，就请求发布，然后等待，完成发布的时候返回。

https://blog.csdn.net/carson_ho/article/details/76101093

https://www.jianshu.com/p/5e13d1fec9c9

https://blog.csdn.net/daogepiqian/article/details/50756873
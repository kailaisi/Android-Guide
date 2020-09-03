## SharedPreference剥皮抽筋

SharedPreferences应该属于一个在应用中最常用的东西了，用来存一些最常用的设置信息。

之前在写优化的文章的时候提到过SharedPreferences的性能并不是特别高。这里我们就看看到底是因为啥。

### 使用

sp的使用相对来说还是比较简单的

```java
//获取sp
SharedPreferences sp = context.getSharedPreferences(spName, Context.MODE_PRIVATE);
//保存数据
sp.edit().putInt(key, value).commit();
sp.edit().putInt(key, value).apply();
//读取数据
sp.getInt(key,defaultValue).
```

使用上来说相对来说还是比较简单的。

### 源码

对于源码解析我们按照获取、保存、读取三部分来

#### 获取

这里的context其实是ContextImpl（）。我们看看里面的getSharedPreferences方法。

```java
   public SharedPreferences getSharedPreferences(String name, int mode) {
        File file;
        synchronized (ContextImpl.class) {
            if (mSharedPrefsPaths == null) {
                mSharedPrefsPaths = new ArrayMap<>();
            }
            file = mSharedPrefsPaths.get(name);
            if (file == null) {
                //创建对应的sp文件，并放入到缓存中。路径为data/shared_prefs/name.xml
                file = getSharedPreferencesPath(name);
                //将文件和对应的文件关系进行缓存
                mSharedPrefsPaths.put(name, file);
            }
        }
      	//返回对应的SharedPreferences实例
        return getSharedPreferences(file, mode);
    }
```

这里主要就两个过程。

* 文件不存在，创建文件。然后将name和对应的文件做对应，保存到缓存中。
* 然后根据文件返回对应的SharedPreferences实例

##### 文件创建

我们先看一下文件的创建

```java
    public File getSharedPreferencesPath(String name) {
        return makeFilename(getPreferencesDir(), name + ".xml");
    }
    
    public File getSharedPreferencesPath(String name) {
        return makeFilename(getPreferencesDir(), name + ".xml");
    }

    //获取sp的文件路径
    private File getPreferencesDir() {
        synchronized (mSync) {
            if (mPreferencesDir == null) {
                //data目录下的shared_prefs文件夹
                mPreferencesDir = new File(getDataDir(), "shared_prefs");
            }
            return ensurePrivateDirExists(mPreferencesDir);
        }
    }
```

所以这里会找到 /data/shared_prefs文件夹，然后创建一个以name为名称的xml文件。

##### 生成实例

```java
public SharedPreferences getSharedPreferences(File file, int mode) {
    SharedPreferencesImpl sp;
    synchronized (ContextImpl.class) {
        //获取缓存的SharedPreferencesImpl实例
        final ArrayMap<File, SharedPreferencesImpl> cache = getSharedPreferencesCacheLocked();
        sp = cache.get(file);
        if (sp == null) {
            //权限校验，在android N中  全局可用的mode已经不能使用了，这里会做拦截
            checkMode(mode);
            //创建对应的SharedPreferencesImpl实例
            sp = new SharedPreferencesImpl(file, mode);
            cache.put(file, sp);
            return sp;
        }
    }
    if ((mode & Context.MODE_MULTI_PROCESS) != 0 ||getApplicationInfo().targetSdkVersion < android.os.Build.VERSION_CODES.HONEYCOMB) {
        sp.startReloadIfChangedUnexpectedly();
    }
    return sp;
}
```

这里会获取已经有的实例列表，如果实例列表中不包含要使用的file，那么创建对应的实例，如果存在的话就直接返回。

##### 实例创建

```java
    SharedPreferencesImpl(File file, int mode) {
        mFile = file;
        //生成备份文件
        mBackupFile = makeBackupFile(file);
        mMode = mode;
        mLoaded = false;
        mMap = null;
        mThrowable = null;
        //从磁盘加载数据
        startLoadFromDisk();
    }
```

这里主要的就是2个

* 生成备份文件。
* 磁盘加载保存的数据。

```java
    static File makeBackupFile(File prefsFile) {
        //以sp文件名，创建对应的.bak文件
        return new File(prefsFile.getPath() + ".bak");
    }

    private void startLoadFromDisk() {
        synchronized (mLock) {
            //标识未加载完
            mLoaded = false;
        }
        new Thread("SharedPreferencesImpl-load") {
            public void run() {
                loadFromDisk();
            }
        }.start();
    }
```

加载数据的时候，会通过子线程来加载。在加载之前，将标志位mLoaded置为了false，表示当前未加载完成。

```java
   private void loadFromDisk() {
        synchronized (mLock) {
            if (mLoaded) {
                return;
            }
            //1. 备份文件存在则使用备份文件的数据
            if (mBackupFile.exists()) {
                mFile.delete();
                mBackupFile.renameTo(mFile);
            }
        }
        //读取sp中保存的键值对，保存到hashmap中
        Map<String, Object> map = null;
        try {
            if (mFile.canRead()) {
                BufferedInputStream str = null;
                try {
                    str = new BufferedInputStream(new FileInputStream(mFile), 16 * 1024);
                    //2. 读取对应的键值对
                    map = (Map<String, Object>) XmlUtils.readMapXml(str);
                } catch (Exception e) {
                    Log.w(TAG, "Cannot read " + mFile.getAbsolutePath(), e);
                } finally {
                    IoUtils.closeQuietly(str);
                }
            }
        }catch (Throwable t) {
            thrown = t;
        }
        synchronized (mLock) {
            mLoaded = true;
		   ... 	
                    if (map != null) {
                        //3. 如果读取过程中没有发生异常，则把对应的键值赋值给mMap中。
                        mMap = map;
                        //记录初始化的时间和对应的大小
                        mStatTimestamp = stat.st_mtim;
                        mStatSize = stat.st_size;
                    } else {
                        mMap = new HashMap<>();
                    }
                }
           	...
        }
    }
```

这里也分为3个过程：

* 将.bak文件重命名为sp文件
* 读取sp文件中的键值对，保存到map中。
* 将map的数据复制给mMap（如果读取过程发生异常，则mMap为空列表）。

这里关注一下。在第一步和最后一步的赋值过程，是有锁机制的。而第二部中的读取文件则 没有锁机制。

总结：当我们使用getSharedPreferences方法来获取sp文件的时候，会将对应的xml文件的键值对读取出来，然后保存到对应的缓存中。涉及到了磁盘的读取。

#### 读取

对于对应值的读取很简单，只要使用get**()即可。我们这里只看一个对于字符串的读取

```java
    public String getString(String key, @Nullable String defValue) {
        synchronized (mLock) {
            //等待锁
            awaitLoadedLocked();
            String v = (String) mMap.get(key);
            return v != null ? v : defValue;
        }
    }
```

这里有一个**awaitLoadedLocked()**方法

```java
    /**
     * 等待磁盘的sp键值对加载完成。线程同步方案
     */
    private void awaitLoadedLocked() {
        if (!mLoaded) {
            BlockGuard.getThreadPolicy().onReadFromDisk();
        }
        while (!mLoaded) {
            try {
                //这里会阻塞，等待子Thread读取完所有的键值对以后，这里才会继续执行，返回一个imp了
                mLock.wait();
            } catch (InterruptedException unused) {
            }
        }
    }
```


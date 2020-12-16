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

所以这里会找到 `/data/shared_prefs`文件夹，然后创建一个以name为名称的xml文件。

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

这里有一个锁机制，需要等到所有的键值对都读取完之后再执行数据的读取。

#### 保存

```java
sp.edit().putInt(key, value).commit()
```

对于sp文件的保存，需要获取一个edit对象来进行数据的操作。

```java
    public Editor edit() {
        synchronized (mLock) {
        	//锁机制，等待读取完成
            awaitLoadedLocked();
        }
        return new EditorImpl();
    }
```

这里返回了一个**EditorImpl**对象。该对象里面保存了一个map的列表，来保存所有编辑过的键值对信息。

```java
    public final class EditorImpl implements Editor {
        private final Object mEditorLock = new Object();
        //修改过的键值信息
        private final Map<String, Object> mModified = new HashMap<>();

        @Override
        public Editor putString(String key, @Nullable String value) {
            synchronized (mEditorLock) {
                mModified.put(key, value);
                return this;
            }
        }
        ...
        @Override
        public Editor remove(String key) {
            synchronized (mEditorLock) {
                //这里设置为了this,而不是null
                mModified.put(key, this);
                return this;
            }
        }

```

可以看到，*EditorImpl*会将所有的操作都保存下来，然后最后通过**commit()**或者**apply()**方法来进行提交处理。

我们最后看看提交方法**commit()**。

```java
        public boolean commit() {
            long startTime = 0;
            //重点方法1. 生成对应的提交类
            MemoryCommitResult mcr = commitToMemory();
            //入队
            SharedPreferencesImpl.this.enqueueDiskWrite(mcr, null /* sync write on this thread okay */);
                //等待写入完成
            mcr.writtenToDiskLatch.await();
            notifyListeners(mcr);
            return mcr.writeToDiskResult;
        }
```

主要步骤也是三步：

* 通过**commitToMemory()**来生成对应的提交信息。
* 入队处理
* 等待完成。

```java
        private MemoryCommitResult commitToMemory() {
            long memoryStateGeneration;
            //修改的key列表
            List<String> keysModified = null;
            Set<OnSharedPreferenceChangeListener> listeners = null;
            //将要保存的数据map信息
            Map<String, Object> mapToWriteToDisk;
            synchronized (SharedPreferencesImpl.this.mLock) {
                //如果当前正在写的话，那么这时候不能直接修改，需要弄出来一个备份
                if (mDiskWritesInFlight > 0) {
                    mMap = new HashMap<String, Object>(mMap);
                }
                mapToWriteToDisk = mMap;
                mDiskWritesInFlight++;
                //sp的变化监听
                boolean hasListeners = mListeners.size() > 0;
                if (hasListeners) {
                    keysModified = new ArrayList<String>();
                    listeners = new HashSet<OnSharedPreferenceChangeListener>(mListeners.keySet());
                }
                synchronized (mEditorLock) {
                    //记录是否发生了数据变化
                    boolean changesMade = false;
                    if (mClear) {//清空
                        if (!mapToWriteToDisk.isEmpty()) {
                            changesMade = true;
                            mapToWriteToDisk.clear();
                        }
                        mClear = false;
                    }
                    //逐个检测变更的数据
                    for (Map.Entry<String, Object> e : mModified.entrySet()) {
                        String k = e.getKey();
                        Object v = e.getValue();
                        if (v == this || v == null) {//如果值是null，或者this，那么久移除
                            if (!mapToWriteToDisk.containsKey(k)) {
                                continue;
                            }
                            mapToWriteToDisk.remove(k);
                        } else {
                            if (mapToWriteToDisk.containsKey(k)) {
                                Object existingValue = mapToWriteToDisk.get(k);
                                if (existingValue != null && existingValue.equals(v)) {
                                    //如果相同就不再修改了
                                    continue;
                                }
                            }
                            mapToWriteToDisk.put(k, v);
                        }

                        changesMade = true;
                        if (hasListeners) {
                            keysModified.add(k);
                        }
                    }
                    mModified.clear();
                    if (changesMade) {
                        mCurrentMemoryStateGeneration++;
                    }
                    //记录当前是第几次sp的变更
                    memoryStateGeneration = mCurrentMemoryStateGeneration;
                }
            }
            return new MemoryCommitResult(memoryStateGeneration, keysModified, listeners,
                    mapToWriteToDisk);
        }

```

整个函数就是根据EditorImpl中提交的信息生成需要保存在sp文件中的键值对。最后生成了一个**MemoryCommitResult**实例。

##### 入队操作

```java
    private void enqueueDiskWrite(final MemoryCommitResult mcr, final Runnable postWriteRunnable) {
        //是否是同步
        final boolean isFromSyncCommit = (postWriteRunnable == null);
        //真正执行将数据写入到磁盘的线程
        final Runnable writeToDiskRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (mWritingToDiskLock) {
                    //写入磁盘
                    writeToFile(mcr, isFromSyncCommit);
                }
                synchronized (mLock) {
                    //计数器-1
                    mDiskWritesInFlight--;
                }
                if (postWriteRunnable != null) {
                    postWriteRunnable.run();
                }
            }
        };
        //同步方法
        if (isFromSyncCommit) {
            boolean wasEmpty = false;
            synchronized (mLock) {
                wasEmpty = mDiskWritesInFlight == 1;
            }
            if (wasEmpty) {//如果当前只有一个线程在执行，那么可以直接执行线程，然后返回。如果不是1的话，证明现在有其他线程在进行写入的操作，需要将其放入到队列中
                writeToDiskRunnable.run();
                return;
            }
        }
        //这里会将写入磁盘的runnable放入到队列中进行处理
        QueuedWork.queue(writeToDiskRunnable, !isFromSyncCommit);
    }
```

在入队方法中，会将写入磁盘的任务放到子线程去执行。

如果当前是立即提交，而且当前没有进行磁盘的写入。那么直接执行写入磁盘任务，执行**writeToFile**方法。

其他情况则会放入到队列中，去等待执行。

我们这里直接分析一下文件的写入方法。

```java
    private void writeToFile(MemoryCommitResult mcr, boolean isFromSyncCommit) {
        boolean fileExists = mFile.exists();
        if (fileExists) {//文件必须存在
            //标记是否需要写入
            boolean needsWrite = false;
            //只有当磁盘的状态比本次提交的commit信息老，则进行提交
            if (mDiskStateGeneration < mcr.memoryStateGeneration) {
                if (isFromSyncCommit) {//commit方式
                    needsWrite = true;
                } else {
                    synchronized (mLock) {
                        //保证mcr中保存的编号和当前编号一致，才可执行
                        if (mCurrentMemoryStateGeneration == mcr.memoryStateGeneration) {
                            needsWrite = true;
                        }
                    }
                }
            }

            if (!needsWrite) {//不需要写入
                mcr.setDiskWriteResult(false, true);
                return;
            }

            boolean backupFileExists = mBackupFile.exists();

            if (DEBUG) {
                backupExistsTime = System.currentTimeMillis();
            }

            if (!backupFileExists) {//如果备份文件不存在，则将原来的文件命名为备份文件
                if (!mFile.renameTo(mBackupFile)) {//重命名失败则直接退出
                    Log.e(TAG, "Couldn't rename file " + mFile + " to backup file " + mBackupFile);
                    mcr.setDiskWriteResult(false, false);
                    return;
                }
            } else {//备份文件存在了，原来的文件则直接删除
                mFile.delete();
            }
        }
        //在上面的步骤中，将对应原来的文件，重命名为了备份文件
        //后面会尽量将数据写入到文件中，然后如果成功，则将备份文件删除，然后返回true
        //如果写入过程出现异常了，那么就会删除新创建的文件，让后将备份文件还原回来。
        try {
            //创建对应的sp所对应的文件输出流
            FileOutputStream str = createFileOutputStream(mFile);
            //输出流创建失败，那么这里直接返回false
            if (str == null) {
                mcr.setDiskWriteResult(false, false);
                return;
            }
            //将map数据写入到文件中
            XmlUtils.writeMapXml(mcr.mapToWriteToDisk, str);
            //调用flush,将流中的数据写入磁盘。但是这里不会关闭
            FileUtils.sync(str);
            //关闭输出流
            str.close();
            //设置对应的sp文件的权限
            ContextImpl.setFilePermissionsFromMode(mFile.getPath(), mMode, 0);
            if (DEBUG) {
                setPermTime = System.currentTimeMillis();
            }

            try {
                final StructStat stat = Os.stat(mFile.getPath());
                synchronized (mLock) {
                    //更对对应的时间戳等状态
                    mStatTimestamp = stat.st_mtim;
                    mStatSize = stat.st_size;
                }
            } catch (ErrnoException e) {
                // Do nothing
            }
            //数据已经都存入到sp文件了，那么删除备份文件
            mBackupFile.delete();
            //将当前硬盘文件的编号修改为提交的mcr的编号
            mDiskStateGeneration = mcr.memoryStateGeneration;
            //设置写入成功，然后通知阻塞对象被打开，能够继续进行操作了
            mcr.setDiskWriteResult(true, true);
            ...
            return;
        } catch (XmlPullParserException e) {
            ....
        }
        //如果走到这里，说明磁盘写入发生了异常，但是mFile文件已经写入了一些数据，但是数据存在问题。所以这里需要将损坏的文件删除
        if (mFile.exists()) {
            if (!mFile.delete()) {
                Log.e(TAG, "Couldn't clean up partially-written file " + mFile);
            }
        }
        mcr.setDiskWriteResult(false, false);
    }
```

这里对于磁盘的写入操作涉及到了并发以及写入失败等各种问题的处理。

首先是备份文件的处理：

1. 如果存在备份文件，则删除原来的文件（备份文件存在证明之前的写入出现异常了，文件有问题，所以会删除）。
2. 如果备份文件不存在，则将源文件重命名备份文件。

写入操作：

1. 通过写入流将键值对信息写入到文件
2. 写入成功，则删除备份文件。
3. 更新文件对应的编号信息
4. 设置写入成功，通知阻塞的对象被打开，可以继续执行后续操作。
5. 写入过程发生异常。mFile文件已经写入了一些数据，但是数据存在问题。所以这里需要将损坏的文件删除。

我们看一下是如何进行通知完成的。

```java
       final CountDownLatch writtenToDiskLatch = new CountDownLatch(1);
       void setDiskWriteResult(boolean wasWritten, boolean result) {
            this.wasWritten = wasWritten;
            writeToDiskResult = result;
            writtenToDiskLatch.countDown();
        }


        public boolean commit() {
            ...
            SharedPreferencesImpl.this.enqueueDiskWrite(mcr, null /* sync write on this thread okay */);
                //等待写入完成
                mcr.writtenToDiskLatch.await();
            ...
            return mcr.writeToDiskResult;
        }
```

这里使用了**CountDownLatch**方法来实现线程间的同步问题，有兴趣的小伙伴可以去了解一下。

我们之前所走的流程是**commit**提交之后不需要入队的操作。但是当发生并发的时候，可能会存在commit()的时候，有其他的正在进行写入的操作。这时候需要进行入队操作了。

```java
        //这里会将写入磁盘的runnable放入到队列中进行处理
        QueuedWork.queue(writeToDiskRunnable, !isFromSyncCommit);
```



```java
    public static void queue(Runnable work, boolean shouldDelay) {
        Handler handler = getHandler();

        synchronized (sLock) {
            //放入到队列中
            sWork.add(work);
            //是否需要延迟（commit()方法不会进行延迟）
            if (shouldDelay && sCanDelay) {
                handler.sendEmptyMessageDelayed(QueuedWorkHandler.MSG_RUN, DELAY);
            } else {
                handler.sendEmptyMessage(QueuedWorkHandler.MSG_RUN);
            }
        }
    }
```

这会将我们需要执行的写入操作放入到队列中，然后通过Handler机制去执行。

```java
    private static Handler getHandler() {
        synchronized (sLock) {
            if (sHandler == null) {
                //HandlerThread，一种含有Handler的线程，可以进行handler消息的处理
                HandlerThread handlerThread = new HandlerThread("queued-work-looper", Process.THREAD_PRIORITY_FOREGROUND);
                handlerThread.start();
                sHandler = new QueuedWorkHandler(handlerThread.getLooper());
            }
            return sHandler;
        }
    }
    
    private static class QueuedWorkHandler extends Handler {
        static final int MSG_RUN = 1;

        QueuedWorkHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            if (msg.what == MSG_RUN) {
                processPendingWork();
            }
        }
    }
```

所以这里最终消息的执行，会在**QueuedWorkHandler** 中进行消息的处理。

```java
    private static void processPendingWork() {
        synchronized (sProcessingWork) {//保证同一时间只有一个线程在执行操作
            LinkedList<Runnable> work;
            synchronized (sLock) {
                //复制之后，将原队列清空
                work = (LinkedList<Runnable>) sWork.clone();
                sWork.clear();
                getHandler().removeMessages(QueuedWorkHandler.MSG_RUN);
            }
            if (work.size() > 0) {
                //执行所有的方法
                for (Runnable w : work) {
                    w.run();
                }
            }
        }
    }
```

会将所有的队列消息取出来，然后顺序执行。因为我们之前往队列丢的是**Runable**对象，所以这里就会逐个执行。

我们剩下一个**apply()**需要看看。

```java
        public void apply() {
            final MemoryCommitResult mcr = commitToMemory();
            //子线程，将数据写入到磁盘中
            final Runnable awaitCommit = new Runnable() {
                @Override
                public void run() {
                    try {
                        //等待写入完成
                        mcr.writtenToDiskLatch.await();
                    } catch (InterruptedException ignored) {
                    }

                }
            };
            QueuedWork.addFinisher(awaitCommit);
            //子线程，执行await子线程，并将数据移除
            Runnable postWriteRunnable = new Runnable() {
                @Override
                public void run() {
                    awaitCommit.run();//调用写入磁盘等待的方法
                    QueuedWork.removeFinisher(awaitCommit);
                }
            };
            //入队写数据
            SharedPreferencesImpl.this.enqueueDiskWrite(mcr, postWriteRunnable);

            notifyListeners(mcr);
        }

   private void enqueueDiskWrite(final MemoryCommitResult mcr, final Runnable postWriteRunnable) {
        //是否是同步
        final boolean isFromSyncCommit = (postWriteRunnable == null);
        //真正执行将数据写入到磁盘的线程
        final Runnable writeToDiskRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (mWritingToDiskLock) {
                    //写入磁盘
                    writeToFile(mcr, isFromSyncCommit);
                }
                synchronized (mLock) {
                    //计数器-1
                    mDiskWritesInFlight--;
                }
                if (postWriteRunnable != null) {
                    postWriteRunnable.run();
                }
            }
        };
        //这里会将写入磁盘的runnable放入到队列中进行处理
        QueuedWork.queue(writeToDiskRunnable, !isFromSyncCommit);
    }
```

这里其实分为了3个步骤。

1. 将写入操作入队操作。这样，当队列按照上面的顺序去执行的时候，会调用**writeToDiskRunnable** 方法进行磁盘的写入。
2. 写入完以后，调用**postWriteRunnable**方法。也就是会执行**awaitCommit** 这个线程，而这个线程会等待磁盘的写入完成操作。

### 总结

1. SharedPreference支持使用**registerOnSharedPreferenceChangeListener**方法注册sp文件的变化。
2. 通过apply()方法提交的会延迟100ms后再进行磁盘的写入工作。

#### 疑问

这里一直有一个疑问，也没想明白怎么回事。

在apply或者commit提交的时候，会创建一个**MemoryCommitResult**对象。在创建的时候，如果发现当前正在进行磁盘写入的话，会将原来的mMap进行备份之后再修改。

例如下面的情况：A正在提交文件。这时候，如果B和C都进行了提交。那么B和C就会对原来的mMap进行备份之后，再进行修改。B和C就不知道对方互相修改了哪些资源。在最后进行提交的时候，不会丢失其中的一个修改文件么？

有知道的小伙伴，希望能帮我解惑一下。


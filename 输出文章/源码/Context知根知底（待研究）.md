Context知根知底

Context，肯定都用过，那么Context到底是什么呢？

![image-20201022103816172](http://cdn.qiniu.kailaisii.com/typora/202010/22/103817-938052.png)

Context类本身是个抽象类，具体的子类实现有两个：**ContextImpl****和**ContextWrapper**

#### ContextWrapper

代理类，内部持有Context的具体代理对象，内部的实现方法也都是调用的被代理对象的数据。

```java
public class ContextWrapper extends Context {
    Context mBase;

    public ContextWrapper(Context base) {
        mBase = base;
    }

    protected void attachBaseContext(Context base) {
        if (mBase != null) {
            throw new IllegalStateException("Base context already set");
        }
        mBase = base;
    }

    /**
     * @return the base context as set by the constructor or setBaseContext
     */
    public Context getBaseContext() {
        return mBase;
    }

    @Override
    public AssetManager getAssets() {
        return mBase.getAssets();
    }

    @Override
    public Resources getResources() {
        return mBase.getResources();
    }

    @Override
    public PackageManager getPackageManager() {
        return mBase.getPackageManager();
    }

    @Override
    public ContentResolver getContentResolver() {
        return mBase.getContentResolver();
    }

    @Override
    public Looper getMainLooper() {
        return mBase.getMainLooper();
    }
    
    @Override
    public Context getApplicationContext() {
        return mBase.getApplicationContext();
    }
    
    @Override
    public void setTheme(int resid) {
        mBase.setTheme(resid);
    }

```


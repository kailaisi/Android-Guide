Fragment之startActivityForResult

为什么Activity和Fragment能使用startActivityForResult，并且返回之后能够分发到具体的Fragment中。

```java
//android.support.v4.app.Fragment.java
public void startActivityForResult(Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode, null);
    }
    
    
        public void startActivityForResult(Intent intent, int requestCode, @Nullable Bundle options) {
        if (mHost == null) {
            throw new IllegalStateException("Fragment " + this + " not attached to Activity");
        }
        mHost.onStartActivityFromFragment(this /*fragment*/, intent, requestCode, options);
    }
```

这里的mHost是Fragment的持有者。我们一般继承使用的是FragmentActivity。这里的HostCallbacks是FragmentActivity中的内部类。

```java
    @Override
    public void onStartActivityFromFragment(Fragment fragment, Intent intent, int requestCode) {
        FragmentActivity.this.startActivityFromFragment(fragment, intent, requestCode);
    }


    public void startActivityFromFragment(Fragment fragment, Intent intent,
            int requestCode) {
        startActivityFromFragment(fragment, intent, requestCode, null);
    }


    public void startActivityFromFragment(Fragment fragment, Intent intent,
            int requestCode, @Nullable Bundle options) {
        mStartedActivityFromFragment = true;
        try {
            //一般requestcode不是-1.
            if (requestCode == -1) {
                ActivityCompat.startActivityForResult(this, intent, -1, options);
                return;
            }
            //方法1    检测requestCode是否越界
            checkForValidRequestCode(requestCode);
            //方法2    根据这个requestIndex可以获取到对应Fragment的唯一标识mWho
            int requestIndex = allocateRequestIndex(fragment);
            //方法3    发起startActivityForResult调用，这里requestIndex和requestCode关联起来。
            ActivityCompat.startActivityForResult(
                    this, intent, ((requestIndex + 1) << 16) + (requestCode & 0xffff), options);
        } finally {
            mStartedActivityFromFragment = false;
        }
    }
```

这里我们分3步进行分析处理。

```java
    static void checkForValidRequestCode(int requestCode) {
        if ((requestCode & 0xffff0000) != 0) {
            throw new IllegalArgumentException("Can only use lower 16 bits for requestCode");
        }
    }
```

1. 这里会校验requestCode的合法性。限制了fragment进行请求的时候，对应的requestCode不能大于**0xffff0000**。

```java
   private int allocateRequestIndex(Fragment fragment) {
        ...
       //获取下一个能够使用的index
        while (mPendingFragmentActivityResults.indexOfKey(mNextCandidateRequestIndex) >= 0) {
            mNextCandidateRequestIndex =
                    (mNextCandidateRequestIndex + 1) % MAX_NUM_PENDING_FRAGMENT_ACTIVITY_RESULTS;
        }
        int requestIndex = mNextCandidateRequestIndex;
       //将index和对应的fragment放入到mPendingFragmentActivityResults中
        mPendingFragmentActivityResults.put(requestIndex, fragment.mWho);
       //生成下一个可以使用的参数。
        mNextCandidateRequestIndex =
                (mNextCandidateRequestIndex + 1) % MAX_NUM_PENDING_FRAGMENT_ACTIVITY_RESULTS;

        return requestIndex;
    }
```

2. 这里会生成请求的fragment所使用的唯一的index。然后将index和对应的fragment缓存起来，

```java
ActivityCompat.startIntentSenderForResult(this, intent,
                    ((requestIndex + 1) << 16) + (requestCode & 0xffff), fillInIntent,
                    flagsMask, flagsValues, extraFlags, options);
```

3. 在进行请求的时候，会将唯一的index左移16位，然后将原来的requestCode和0xffff进行相加操作。这个会将对应的fragment和其使用的Activity进行组合。那么返回的时候，我们就可以将这个requestCode进行拆分处理，然后就能够找到对应的fragment以及其实际使用的requestCode信息。

我们看一下实际的onActivityresult方法的返回。这个方法的实现，是在FragmentActivity中的。

```java
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mFragments.noteStateNotSaved();
        //将对应的requestCode右移16位，获取到对应的fragment所使用的index信息
        int requestIndex = requestCode>>16;
        if (requestIndex != 0) {
            //如果是activity发起请求的话，requestIndex是=0的，所以进入这个分支表示是由Fragment发起的请求
            //这里做-1，是因为使用的时候，进行了(requestIndex + 1) << 16的操作。所以需要--的处理
            requestIndex--;
            //获取到对应的调用请求的Fragment类
            String who = mPendingFragmentActivityResults.get(requestIndex);
            //既然已经返回了，那么这里就直接将缓存的请求信息移除
            mPendingFragmentActivityResults.remove(requestIndex);
            if (who == null) {
                Log.w(TAG, "Activity result delivered for unknown Fragment.");
                return;
            }
            //找到fragment
            Fragment targetFragment = mFragments.findFragmentByWho(who);
            if (targetFragment == null) {
                Log.w(TAG, "Activity result no fragment exists for who: " + who);
            } else {
                //调用对应fragment的onActivityResult方法。这里会将requestCode进行一次处理，只取低16位的信息，因为高16位存的是Fragment类的信息
                targetFragment.onActivityResult(requestCode & 0xffff, resultCode, data);
            }
            return;
        }

        ActivityCompat.PermissionCompatDelegate delegate =
                ActivityCompat.getPermissionCompatDelegate();
        //调用activity的onActivityResult方法
        if (delegate != null && delegate.onActivityResult(this, requestCode, resultCode, data)) {
            // Delegate has handled the activity result
            return;
        }
        //
        super.onActivityResult(requestCode, resultCode, data);
    }
```




## Android之Fragment状态管理源码解析

#### 引言

在之前，我们写过一篇[Fragment事务流程分析](https://mp.weixin.qq.com/s?__biz=MzUzOTE4MTQzNQ==&mid=2247483744&idx=1&sn=c5db3f3a1d2f5a5d7e13562d94d2695c&chksm=facd2974cdbaa062668dae6031a8b1ba41ced7338bb8554a0ae5403143c76e0c6a791b97d713&token=1511138431&lang=zh_CN#rd)，在这片文章中，我们了解到，Activity通过通过FragmentManager来对Fragment进行统一的管理。在文章最后，我们讲到**FragmentManager**会通过 **moveToState()** 方法，将Fragment的生命周期和Activity的生命周期进行同步。这一篇文章，就主要分析一下，**FragmentManager**是如何Fragment的状态管理。

### 重要知识点

在进行源码解析之前，我们先讲解一下可能会涉及到的基础知识点

##### Fragment的几种状态

* static final int INVALID_STATE = -1;   //无效的状态或者null值
* static final int INITIALIZING = 0;     //正在初始化
* static final int CREATED = 1;          // 创建完成
* static final int ACTIVITY_CREATED = 2; // 绑定的Activity创建完成
* static final int STOPPED = 3;          // 创建完成，但是没有进行start处理
* static final int STARTED = 4;          // 执行了Created 和started, 但是没有执行resumed.
* static final int RESUMED = 5;          //执行完 Created 、started 和resumed

对于Fragment，在进行创建到显示的过程，其状态会从0逐渐过渡到5。而当其销毁时，状态会从5逐渐回到0。

#### Fragment的创建方式

我们使用Fragment时，主要通过两种方式进行创建

1. 在activity的布局文件中，生命fragment。
2. 通过Java代码，将Fragment添加到已存的**ViewGroup**中

这两种方式其实最终效果是相同的，在进行代码处理中会根据不同的创建方式进行一定的区分处理。比如说第二种，那么必须保证Fragment有对应的container。而且需要有对应的containerId。

### 生命周期源码

现在我们开始进行 **moveToState()** 方法的源码解析。

```java
    void moveToState(Fragment f, int newState, int transit, int transitionStyle, boolean keepActive) {
        //如果fragment没有添加则将状态设置为CREATED
        if ((!f.mAdded || f.mDetached) && newState > Fragment.CREATED) {
            newState = Fragment.CREATED;
        }
        //如果fragment已经移除了
        if (f.mRemoving && newState > f.mState) {
            //当前状态正在创建，并且添加到了堆栈中
            if (f.mState == Fragment.INITIALIZING && f.isInBackStack()) {
                newState = Fragment.CREATED;
            } else {
                //已经是移除的状态了，那么就不能变化为更高的状态了
                newState = f.mState;
            }
        }
        if (f.mDeferStart && f.mState < Fragment.STARTED && newState > Fragment.STOPPED) {
            newState = Fragment.STOPPED;
        }
```

这个里面主要是对还未添加或者已经移除的Fragment进行了状态的处理。

#### 从不显示到显示

```java
        if (f.mState <= newState) {
            ...
            switch (f.mState) {
                case Fragment.INITIALIZING:
                    ...
                case Fragment.CREATED:
                    ...
                case Fragment.STOPPED://这种状态说明Fragment已经创建完成了，但是还没有启动(start)
                    ...
                case Fragment.STARTED://执行了Created 和 started, 但是还没有进行 resumed操作
                    ...
            }
```

这段代码，是fragment由不显示到显示的整个过程。在这个里面根据fragment的当前状态来进行不同的处理。也就是switch里面的操作。我们会发现，其实每个case里面并没有break操作，所以会从符合条件的分支开始，一直往后循环，直到其对应的state和newState相等未知。

这里的分支比较多。我们一个个分析。

##### 初始状态是INITIALIZING

如果我们的fragment当前的状态才刚刚进行初始化。就会走 **case Fragment.INITIALIZING** 分支

```java
                case Fragment.INITIALIZING:
                    if (newState > Fragment.INITIALIZING) {
                        ...
                        //进行生命周期的通知
                        dispatchOnFragmentPreAttached(f, mHost.getContext(), false);
                        f.mCalled = false;
                        //调用onAttach方法，将fragment和activity进行绑定，注意这里使用的是context参数的方法。在安卓24之前，调用的是Activity参数的方法
                        f.onAttach(mHost.getContext());
                        if (!f.mCalled) {
                            throw new SuperNotCalledException("Fragment " + f + " did not call through to super.onAttach()");
                        }
                        //如果不存在父Fragment。
                        if (f.mParentFragment == null) {
                            //最终会调用Activity里面的onAttachFragment方法,我们可以进行绑定的监听，这个方法是在fragment的onAttach方法调用之后，onCreate方法调用之后
                            mHost.onAttachFragment(f);
                        } else {
                            //如果Fragment有父Fragment，会调用父Fragment的onAttachFragment方法
                            f.mParentFragment.onAttachFragment(f);
                        }
                        //进行生命周期的通知
                        dispatchOnFragmentAttached(f, mHost.getContext(), false);
                        //如果Fragment还没有创建
                        if (!f.mIsCreated) {
                            //进行生命周期的通知
                            dispatchOnFragmentPreCreated(f, f.mSavedFragmentState, false);
                            //执行创建过程，调用onCreate方法
                            f.performCreate(f.mSavedFragmentState);
                            //进行生命周期的通知
                            dispatchOnFragmentCreated(f, f.mSavedFragmentState, false);
                        } else {
                            //如果已经创建了，那么直接调用restoreChildFragmentState
                            f.restoreChildFragmentState(f.mSavedFragmentState, true);
                            f.mState = Fragment.CREATED;
                        }
                        f.mRetaining = false;
                    }
```

如果当前状态是INITIALIZING，而新的状态比INITIALIZING高，那么就会执行这段代码。整个流程如下：

1. 通过 **dispatchOnFragmentPreAttached** 方法进行生命周期的通知
2. 调用 **onAttach** 方法
3. 如果当前fragment存在父fragment，那么会调用父Fragment的**onAttachFragment**方法，否则调用Activity里面的**onAttachFragment**方法。
4. 通过 **dispatchOnFragmentAttached** 方法进行生命周期的通知。
5. 如果fragment还未创建，则执行创建过程，调用**performCreate**方法

这里面有多个生命周期的通知功能，我们只找一个分析一下。我们这里分析一下 **dispatchOnFragmentPreAttached** 方法

```java
    final CopyOnWriteArrayList<Pair<FragmentLifecycleCallbacks, Boolean>> mLifecycleCallbacks = new CopyOnWriteArrayList<>();
	//在fragment在进行onAttach之前的分发处理
    void dispatchOnFragmentPreAttached(Fragment f, Context context, boolean onlyRecursive) {
        if (mParent != null) {
            //如果存在父Fragment，会调用其dispatchOnFragmentPreAttached，并递归调用
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager).dispatchOnFragmentPreAttached(f, context, true);
            }
        }
        //进行其注册的生命周期的通知
        for (Pair<FragmentLifecycleCallbacks, Boolean> p : mLifecycleCallbacks) {
            if (!onlyRecursive || p.second) {
                p.first.onFragmentPreAttached(this, f, context);
            }
        }
    }
```

这里也对fragment进行了不同的处理，如果fragment是嵌入在fragment中的话，调用父类的dispatchOnFragmentPreAttached方法。否则就对于绑定的**mLifecycleCallbacks**列表，进行逐一的**onFragmentPreAttached**方法的调用。这里面的**mLifecycleCallbacks**其实是我们注册的fragment的生命周期的监听函数。

在这个状态的转变之后，会将fragment的生命周期状态修改为**CREATED**

```java
    void performCreate(Bundle savedInstanceState) {
        if (mChildFragmentManager != null) {
            mChildFragmentManager.noteStateNotSaved();
        }
        //状态修改~~
        mState = CREATED;
        mCalled = false;
        //调用onCreate方法
        onCreate(savedInstanceState);
        mIsCreated = true;
        ...
    }
```

代码很简单，只是进行了状态的修改，然后调用了**onCreate**方法。

总结：

这里也可以看到fragment的生命周期方法的调用。是先调用**onAttach**方法，然后调用**onCreate**方法。

##### 当前状态是CREATED

当用户当前状态是CREATED的时候，就会走入我们的分支。当然了，还有一种情况就是初始的状态是INITIALIZING，然后顺序执行到了此部分。

```java
                case Fragment.CREATED:
                    // This is outside the if statement below on purpose; we want this to run
                    // even if we do a moveToState from CREATED => *, CREATED => CREATED, and
                    // * => CREATED as part of the case fallthrough above.
                    //如果是通过继承了Fragment方法来创建的fragment。那么这里会调用onCreateView来确保fragment已经进行了View的创建。如果已经创建过了，会有相关的状态信息，不再进行重复创建
                    //如果是通过<fragment>来创建的，这就不会创建View，而是在后面进行创建
                    ensureInflatedFragmentView(f);
                    if (newState > Fragment.CREATED) {
                        if (DEBUG) Log.v(TAG, "moveto ACTIVITY_CREATED: " + f);
                        //如果不是通过继承Fragment进行创建的，也就是通过<fragment>来进行创建的
                        if (!f.mFromLayout) {
                            ViewGroup container = null;
                            if (f.mContainerId != 0) {
                                if (f.mContainerId == View.NO_ID) {
                                    throwException(new IllegalArgumentException("Cannot create fragment " + f + " for a container view with no id"));
                                }
                                container = mContainer.onFindViewById(f.mContainerId);
                                if (container == null && !f.mRestored) {
                                    String resName;
                                    try {
                                        resName = f.getResources().getResourceName(f.mContainerId);
                                    } catch (NotFoundException e) {
                                        resName = "unknown";
                                    }
                                    throwException(new IllegalArgumentException("No view found for id 0x" + Integer.toHexString(f.mContainerId) + " (" + resName + ") for fragment " + f));
                                }
                            }
                            f.mContainer = container;
                            f.mView = f.performCreateView(f.performGetLayoutInflater(f.mSavedFragmentState), container, f.mSavedFragmentState);
                            if (f.mView != null) {
                                f.mView.setSaveFromParentEnabled(false);
                                if (container != null) {
                                    container.addView(f.mView);
                                }
                                if (f.mHidden) {
                                    f.mView.setVisibility(View.GONE);
                                }
                                f.onViewCreated(f.mView, f.mSavedFragmentState);
                                dispatchOnFragmentViewCreated(f, f.mView, f.mSavedFragmentState,
                                        false);
                                // Only animate the view if it is visible. This is done after
                                // dispatchOnFragmentViewCreated in case visibility is changed
                                f.mIsNewlyAdded = (f.mView.getVisibility() == View.VISIBLE) && f.mContainer != null;
                            }
                        }
                        //调用fragment的onActivityCreated方法，并将其状态置为ACTIVITY_CREATED，
                        f.performActivityCreated(f.mSavedFragmentState);
                        //进行相关生命周期的通知
                        dispatchOnFragmentActivityCreated(f, f.mSavedFragmentState, false);
                        if (f.mView != null) {
                            f.restoreViewState(f.mSavedFragmentState);
                        }
                        f.mSavedFragmentState = null;
                    }

```

这个里面就涉及到fragment的创建方式了。对于不同的创建方式，会走不同的分支处理。

这里面有两个比较重要的方法。

1. 调用**ensureInflatedFragmentView** 保证View的创建。
2. 调用**performActivityCreated** 方法

我们先分析第一个方法

```java
    void ensureInflatedFragmentView(Fragment f) {
        //如果fragment是使用Layout来进行布局，而且没有进行View的绘制
        if (f.mFromLayout && !f.mPerformedCreateView) {
            //执行performCreateView方法，这里会调用fragment的onCreateView方法进行绘制工作。
            f.mView = f.performCreateView(f.performGetLayoutInflater(f.mSavedFragmentState), null, f.mSavedFragmentState);
            if (f.mView != null) {
                f.mView.setSaveFromParentEnabled(false);
                if (f.mHidden) f.mView.setVisibility(View.GONE);
                //调用onViewCreated方法
                f.onViewCreated(f.mView, f.mSavedFragmentState);
                //进行生命周期的通知
                dispatchOnFragmentViewCreated(f, f.mView, f.mSavedFragmentState, false);
            }
        }
    }
```

这里面的mFromLayout表明fragment是从layout文件中实例化的。如果fragment还没有进行View的创建。那么这里就会执行fragment的**onCreateView**的方法，进行View的创建工作。如果已经进行过View的创建了，那么这里就不会进行执行了。

如果fragment是不是layout文件中实例化的，这个方法也不会执行。

如果fragment是不是layout文件中实例化的时候，也就是说是通过Java代码，将Fragment添加到已存的**ViewGroup**的方式来进行创建。其也是会通过**onCreateView**的方法，进行View的创建工作。但是在进行创建之前，会先校验ViewGroup的合法性。如果ViewGroup合法，则调用addVIew方法，将创建的View添加到ViewGroup中。

这里面会有三个生命周期函数的调用 **onCreateView** 、 **onViewCreated** 、**onActivityCreated** 。

##### 当前状态是ACTIVITY_CREATED

```java
                case Fragment.ACTIVITY_CREATED://
                    if (newState > Fragment.ACTIVITY_CREATED) {
                        f.mState = Fragment.STOPPED;
                    }
```

这个相对来说比较简单，直接将当前状态设置为了**STOPPED**

##### 当前状态是STOPPED

```java
               case Fragment.STOPPED://这种状态说明Fragment已经创建完成了，但是还没有启动(start)
                    if (newState > Fragment.STOPPED) {
                        if (DEBUG) Log.v(TAG, "moveto STARTED: " + f);
                        //执行onStart()方法
                        f.performStart();
                        //进行生命周期的通知
                        dispatchOnFragmentStarted(f, false);
                    }
```

这里只有一个**onStart** 生命周期函数的调用

##### 当前状态是STARTED

```java
                case Fragment.STARTED://执行了Created 和 started, 但是还没有进行 resumed操作
                    if (newState > Fragment.STARTED) {
                        //执行onResume()方法
                        f.performResume();
                        dispatchOnFragmentResumed(f, false);
                        f.mSavedFragmentState = null;
                        f.mSavedViewState = null;
                    }
```

这里只有一个**onResume** 生命周期函数的调用。

每次进行代码的执行都有一个和newState的判断比较，当Fragment从不显示到显示的过程中，会从当前状态一步步执行，知道其状态和newState一致位置。这既保证了所有生命周期方法的调用，又能够保证和其所附着的Activity的当前生命周期保持同步。

#### 从显示到不显示

当我们的页面从可见状态变化为不可见是，就会走我们后面的分支了

```
		else if (f.mState > newState) {
            switch (f.mState) {
                case Fragment.RESUMED://如果当前状态是resumed，
                    ...
                case Fragment.STARTED:
                    ...
                case Fragment.STOPPED:
                case Fragment.ACTIVITY_CREATED:
                    ...
                case Fragment.CREATED:
                    ...
            }
        }
        
```

从显示到不显示，其状态的变化和之前分析的整好相反，状态会从RESUMED一路变化，最终变化为INITIALIZING。

##### 当前状态值是RESUMED

```java
                case Fragment.RESUMED://如果当前状态是resumed，
                    if (newState < Fragment.RESUMED) {
                        //最新的状态比RESUMED小，会调用onPause(),并将当前状态置为STARTED
                        f.performPause();
                        dispatchOnFragmentPaused(f, false);
                    }

```

这里只有一个**onResume** 生命周期函数的调用。

##### 当前状态值是STARTED

```java
                case Fragment.STARTED:
                    if (newState < Fragment.STARTED) {
                        //最新的状态比STARTED小，会调用onStop(),并将当前状态置为STOPPED
                        f.performStop();
                        dispatchOnFragmentStopped(f, false);
                    }
```

这里只有一个**onResume** 生命周期函数的调用。

##### 当前状态值是STOPPED

```java
                case Fragment.STOPPED:
                case Fragment.ACTIVITY_CREATED:
                    if (newState < Fragment.ACTIVITY_CREATED) {
                        if (f.mView != null) {
                            //如果创建过View，而且设置了状态保存，会进行视图状态的保存
                            if (mHost.onShouldSaveFragmentState(f) && f.mSavedViewState == null) {
                                saveFragmentViewState(f);
                            }
                        }
                        //执行onDestroyView()方法
                        f.performDestroyView();
                        //生命周期的通知
                        dispatchOnFragmentViewDestroyed(f, false);
                        //如果存在mContainer和mView。
                        if (f.mView != null && f.mContainer != null) {
                            //执行动画处理
                            ...
                            //进行View的回收
                            f.mContainer.removeView(f.mView);
                        }
                        f.mContainer = null;
                        f.mView = null;
                        f.mInLayout = false;
                    }
```

这里会进行**onDestroyView()** 生命周期方法的调用，然后进行对象的回收工作。

当前状态值是CREATED

```java
              case Fragment.CREATED:
                    if (newState < Fragment.CREATED) {
                        if (mDestroyed) {
                            //处理动画效果
                            ...
                        }
                        if (f.getAnimatingAway() != null) {
                            f.setStateAfterAnimating(newState);
                            newState = Fragment.CREATED;
                        } else {
                            if (DEBUG) Log.v(TAG, "movefrom CREATED: " + f);
                            if (!f.mRetaining) {
                                f.performDestroy();
                                dispatchOnFragmentDestroyed(f, false);
                            } else {
                                f.mState = Fragment.INITIALIZING;
                            }
                            //调用onDetach()方法
                            f.performDetach();
                            //进行生命周期的通知
                            dispatchOnFragmentDetached(f, false);
                            if (!keepActive) {
                                if (!f.mRetaining) {
                                    makeInactive(f);
                                } else {
                                    f.mHost = null;
                                    f.mParentFragment = null;
                                    f.mFragmentManager = null;
                                }
                            }
                        }
                    }
            }
```

这里会进行**onDetach()** 生命周期方法的调用。

#### 学到的知识点

* 对于Fragment，也可以通过 **registerFragmentLifecycleCallbacks** 方法来注册**Fragment**生命周期的监听功能。
* Fragment的生命周期为
  * onAttached()
  * onCreate()
  * onCreateView()
  * onViewCreated()
  * onActivityCreated()
  * onStart()
  * onResume()
  * onPause()
  * onStop()
  * onDestoryView()
  * onDestory()
  * onDeatch()
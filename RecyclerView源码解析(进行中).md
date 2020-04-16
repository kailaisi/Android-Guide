### 引言

在之前的Android的View绘制机制中我们讲过，对于控件的测量以及布局会通过 **onMeasure()** 和 **onLayout()** 方法来实现。所以这里我们将这两个函数作为入口来研究RecyclerView的整个布局过程。

### 基础

对于RecyclerView这个控件的使用，相对于ListView来说，更加灵活。其所拆分出来的各个类的分工更加明确，很好地体现了我们经常所说的职责单一原则。我们这里先对其中使用到的类进行一下讲解

* LayoutManager：RecyclerView的布局管理者，主要负责对于RecyclerView子View的测量和布局工作。
* RecyclerView.Recycler：缓存的核心类。RecyclerView强大的缓存能力都是基于这个类来实现的。是缓存的核心工具类。
* Adapter：Adapter的基类。负责将ViewHolder中的数据和RecyclerView中的控件进行绑定处理。
* ViewHolder：视图和元数据类。它持有了要显示的数据信息，包括位置、View、ViewType等。

### 源码

无论是View还是ViewGroup的子类，都是通过 **onMeasure() ** 来实现测量工作的，那么我们从onMeasure来当作入口点

#### 自身测量

```java
    protected void onMeasure(int widthSpec, int heightSpec) {
        //dispatchLayoutStep1,dispatchLayoutStep2,dispatchLayoutStep3肯定会执行，但是会根据具体的情况来区分是在onMeasure还是onLayout中执行。
        if (mLayout == null) {//LayoutManager为空，那么就使用默认的测量策略
            defaultOnMeasure(widthSpec, heightSpec);
            return;
        }
        if (mLayout.mAutoMeasure) {
            //有LayoutManager，开启了自动测量
            final int widthMode = MeasureSpec.getMode(widthSpec);
            final int heightMode = MeasureSpec.getMode(heightSpec);
            final boolean skipMeasure = widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY;
            //步骤1  调用LayoutManager的onMeasure方法来进行测量工作
            mLayout.onMeasure(mRecycler, mState, widthSpec, heightSpec);
            //如果width和height都已经是精确值，那么就不用再根据内容进行测量，后面步骤不再处理
            if (skipMeasure || mAdapter == null) {
                return;
            }
            //如果测量过程后的宽或者高都没有精确，那么就需要根据child来进行布局，从而来确定其宽和高。
            // 当前的布局状态是start
            if (mState.mLayoutStep == State.STEP_START) {
                //布局的第一部 主要进行一些初始化的工作
                dispatchLayoutStep1();
            }
            mLayout.setMeasureSpecs(widthSpec, heightSpec);
            mState.mIsMeasuring = true;
            //执行布局第二步。先确认子View的大小与布局
            dispatchLayoutStep2();
            // 布局过程结束，根据Children中的边界信息计算并设置RecyclerView长宽的测量值
            mLayout.setMeasuredDimensionFromChildren(widthSpec, heightSpec);
            //检查是否需要再此测量。如果RecyclerView仍然有非精确的宽和高，或者这里还有至少一个Child还有非精确的宽和高，我们就需要再次测量。
            // 比如父子尺寸属性互相依赖的情况，要改变参数重新进行一次
            if (mLayout.shouldMeasureTwice()) {
                mLayout.setMeasureSpecs(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
                mState.mIsMeasuring = true;
                dispatchLayoutStep2();
                // now we can get the width and height from the children.
                mLayout.setMeasuredDimensionFromChildren(widthSpec, heightSpec);
            }
        } else {
            //有LayoutManager，没有开启自动测量。一般系统的三个LayoutManager都是自动测量，
            // 如果是我们自定义的LayoutManager的话，可以通过setAutoMeasureEnabled关闭自动测量功能
            //RecyclerView已经设置了固定的Size，直接使用固定值即可
            if (mHasFixedSize) {
                mLayout.onMeasure(mRecycler, mState, widthSpec, heightSpec);
                return;
            }
            //如果在测量过程中数据发生变化，需要先对数据进行处理
            ...
            // 处理完新更新的数据，然后执行自定义测量操作。
            if (mAdapter != null) {
                mState.mItemCount = mAdapter.getItemCount();
            } else {
                mState.mItemCount = 0;
            }
            eatRequestLayout();
            //没有设置固定的宽高，则需要进行测量
            mLayout.onMeasure(mRecycler, mState, widthSpec, heightSpec);
            resumeRequestLayout(false);
            mState.mInPreLayout = false;
        }
    }

```

在这个方法里面，根据不同的情况进行了不同的处理。

1. 没有设置LayoutManager的情况下，直接使用默认的测量方法。
2. 当设置了Layoutmanager而且开启了自动测量功能。
3. 设置了LayoutManager但是没有开启自动测量。

我们先从最简单的来分析。

第一种：没有设置LayoutManager。

因为RecyclerView的所有的测量和布局工作都是交给LayoutManager来处理的，如果没有设置的话，只能使用默认的测量方案了。

第三种：有LayoutManager，而且关闭了自动测量功能。

关闭测量的情况下不需要考虑子View的大小和布局。直接按照正常的流程来进行测量即可。如果直接已经设置了固定的宽高，那么直接使用固定值即可。如果没有设置固定宽高，那么就按照正常的控件一样，根据父级的要求与自身的属性进行测量。

第二种：有LayoutManager，开启了自动测量。

这种情况是最复杂的，需要根据子View的布局来调整自身的大小。需要知道子View的大小和布局。所以RecyclerView将布局的过程提前到这里来进行了。

我们简化一下代码再看

```java
if (mLayout.mAutoMeasure) {
    		//调用LayoutManager的onMeasure方法来进行测量工作
            mLayout.onMeasure(mRecycler, mState, widthSpec, heightSpec);
            //如果width和height都已经是精确值，那么就不用再根据内容进行测量，后面步骤不再处理
            if (skipMeasure || mAdapter == null) {
                return;
            }
            if (mState.mLayoutStep == State.STEP_START) {
                //布局的第一部 主要进行一些初始化的工作
                dispatchLayoutStep1();
            }
    		...
            //开启了自动测量，需要先确认子View的大小与布局
            dispatchLayoutStep2();
            ...
            //再根据子View的情况决定自身的大小
            mLayout.setMeasuredDimensionFromChildren(widthSpec, heightSpec);

            if (mLayout.shouldMeasureTwice()) {
                ...
                //如果有父子尺寸属性互相依赖的情况，要改变参数重新进行一次
                dispatchLayoutStep2();
                mLayout.setMeasuredDimensionFromChildren(widthSpec, heightSpec);
            }
        }
```

对于RecyclerView的测量和绘制工作，是需要 **dispatchLayoutStep1** , **dispatchLayoutStep2** , **dispatchLayoutStep3** 这三步来执行的，step1里是进行预布局，主要跟记录数据更新时需要进行的动画所需的信息有关，step2就是实际循环执行了子View的测量布局的一步，而step3主要是用来实际执行动画。而且通过 **mLayoutStep** 记录了当前执行到了哪一步。在开启自动测量的情况下如果没有设置固定宽高，那么会执行setp1和step2。在step2执行完后就可以调用 **setMeasuredDimensionFromChildren** 方法，根据子类的测量布局结果来设置自身的大小。

我们先不进行分析step1，step2和step3的具体功能。直接把 **onLayout** 的代码也贴出来，看一下这3步是如何保证都能够执行的。

```java
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        dispatchLayout();
    }
    
    void dispatchLayout() {
        if (mAdapter == null) {//没有设置adapter，返回
            Log.e(TAG, "No adapter attached; skipping layout");
            // leave the state in START
            return;
        }
        if (mLayout == null) {//没有设置LayoutManager，返回
            Log.e(TAG, "No layout manager attached; skipping layout");
            // leave the state in START
            return;
        }
        mState.mIsMeasuring = false;
        //在onMeasure阶段，如果宽高是固定的，那么mLayoutStep == State.STEP_START 而且dispatchLayoutStep1和dispatchLayoutStep2不会调用
        //所以这里就会调用一下
        if (mState.mLayoutStep == State.STEP_START) {
            dispatchLayoutStep1();
            mLayout.setExactMeasureSpecsFrom(this);
            dispatchLayoutStep2();
        } else if (mAdapterHelper.hasUpdates() || mLayout.getWidth() != getWidth()|| mLayout.getHeight() != getHeight()) {
            //在onMeasure阶段，如果执行了dispatchLayoutStep1，但是没有执行dispatchLayoutStep2,就会执行dispatchLayoutStep2
            mLayout.setExactMeasureSpecsFrom(this);
            dispatchLayoutStep2();
        } else {
            mLayout.setExactMeasureSpecsFrom(this);
        }
        //最终调用dispatchLayoutStep3
        dispatchLayoutStep3();
    }
```

可以看到，其实在 **onLayout** 阶段会根据 **onMeasure**  阶段3个步骤执行到了哪个，然后会在 **onLayout** 中把剩下的步骤执行。

OK，到现在整个流程通了，在这3个步骤中，step2就是执行了子View的测量布局的一步，也是最重要的一环，所以我们将关注的重点放在这个函数。

```java
    private void dispatchLayoutStep2() {
        //禁止布局请求
        eatRequestLayout();
        ...
        mState.mInPreLayout = false;
        //调用LayoutManager的layoutChildren方法来布局
        mLayout.onLayoutChildren(mRecycler, mState);
		...
        resumeRequestLayout(false);
    }
```

这里调用LayoutManager的 **onLayoutChildre** 方法，将对于子View的测量和布局工作交给了LayoutManager。这里我们分析 **LinearLayoutManager** 



待研究

1. Recycler的作用
2. ViewInfoStore类





使用了SparseArray(性能更优？)

总结

1. 在RecyclerView中，内部类Recycler主要负责回收和复用工作。
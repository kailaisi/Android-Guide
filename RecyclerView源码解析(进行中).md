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

这里调用LayoutManager的 **onLayoutChildren** 方法，将对于子View的测量和布局工作交给了LayoutManager。而且我们在自定义LayoutManager的时候也必须要重写这个方法来描述我们的布局错略。这里我们分析最经常使用的 **LinearLayoutManager(后面简称LLM)** 。我们这里只研究垂直方向布局的情况。

```java
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        // layout algorithm:
        // 1) by checking children and other variables, find an anchor coordinate and an anchor
        //  item position.
        // 2) fill towards start, stacking from bottom
        // 3) fill towards end, stacking from top
        // 4) scroll to fulfill requirements like stack from bottom.
        // create layout state
```

在方法的开始位置，直接就仍给了我们一段说明文档，告诉了我们 **LinearLayoutManager** 中的布局策略。简单翻译一下：

1. 通过子控件和其他的变量信息。找到一个锚点和锚点项的位置。
2. 从锚点的位置开始，往上，填充布局子View，直到填满区域
3. 从锚点的位置开始，往下，填充布局子View，直到填满区域
4. 滚动以满足需求，如堆栈从底部

这里有个比较关键的词，就是 **锚点(AnchorInfo)** ，其实 LLM 的布局并不是从上往下一个个进行的。而是很可能从整个布局的中间某个点开始的，然后朝一个方向一个个填充，填满可见区域后，朝另一个方向进行填充。至于先朝哪个方向填充，是根据具体的变量来确定的。

**AnchorInfo** 类需要能够有效的描述一个具体的位置信息。我们看一下内部的属性信息。

```java
    //简单的数据类来保存锚点信息
    class AnchorInfo {
        //锚点参考View在整个数据中的position信息，即它是第几个View
        int mPosition;
        //锚点的具体坐标信息，填充子View的起始坐标。当positon=0的时候，如果只有一半View可见，那么这个数据可能为负数
        int mCoordinate;
        //是否从底部开始布局
        boolean mLayoutFromEnd;
        //是否有效
        boolean mValid;
```

### 锚点的选择

我们首先看一下**AnchorInfo** 类，看一下里面包含了什么有效的信息。

```java
    //简单的数据类来保存锚点信息
    class AnchorInfo {
        //锚点参考View在整个数据中的position信息，即它是第几个View
        int mPosition;
        //锚点的具体坐标信息，填充子View的起始坐标。当positon=0的时候，如果只有一半View可见，那么这个数据可能为负数
        int mCoordinate;
        //是否从底部开始布局
        boolean mLayoutFromEnd;
        //是否有效
        boolean mValid;
```

可以看到，通过 **AnchorInfo** 就可以准确的定位当前的位置信息了。那么在LLM中，这个锚点的位置是如何确定的呢？

我们从源码中去寻找答案。

```java

    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        ...
        //确认LayoutState存在
        ensureLayoutState();
        //禁止回收
        mLayoutState.mRecycle = false;
        //计算是否需要颠倒绘制。是从底部到顶部绘制，还是从顶部到底部绘制（在LLM的构造函数中，其实可以设置反向绘制）
        resolveShouldLayoutReverse();
        //如果当前锚点信息非法，滑动到的位置不可用或者有需要恢复的存储的SaveState
        if (!mAnchorInfo.mValid || mPendingScrollPosition != NO_POSITION || mPendingSavedState != null) {
            //重置锚点信息
            mAnchorInfo.reset();
            //是否从end开始进行布局。因为mShouldReverseLayout和mStackFromEnd默认都是false，那么我们这里可以考虑按照默认的情况来进行分析，也就是mLayoutFromEnd也是false
            mAnchorInfo.mLayoutFromEnd = mShouldReverseLayout ^ mStackFromEnd;
            //计算锚点的位置和坐标
            updateAnchorInfoForLayout(recycler, state, mAnchorInfo);
            //设置锚点有效
            mAnchorInfo.mValid = true;
        }
```

在需要确定锚点的时候，会先将锚点进行初始化，然后通过 **updateAnchorInfoForLayout** 方法来确定锚点的信息。

```java
    private void updateAnchorInfoForLayout(RecyclerView.Recycler recycler, RecyclerView.State state, AnchorInfo anchorInfo) {
        //从挂起的数据更新锚点信息  这个方法一般不会调用到
        if (updateAnchorFromPendingData(state, anchorInfo)) {
            return;
        }
        //**重点方法 从子View来确定锚点信息（这里会尝试从有焦点的子View或者列表第一个位置的View或者最后一个位置的View来确定）
        if (updateAnchorFromChildren(recycler, state, anchorInfo)) {
            return;
        }
        //进入这里说明现在都没有确定锚点（比如设置Data后还没有绘制View的情况下），就直接设置RecyclerView的顶部或者底部位置为锚点(按照默认情况，这里的mPosition=0)。
        anchorInfo.assignCoordinateFromPadding();
        anchorInfo.mPosition = mStackFromEnd ? state.getItemCount() - 1 : 0;
    }
```

锚点的确定方案主要有3个：

1. 从挂起的数据获取锚点信息。一般不会执行。
2. 从子View来确定锚点信息。比如说notifyDataSetChanged方法的时候，屏幕上原来是有View的，那么就会通过这种方式获取
3. 如果上面两种方法都无法确定，则直接使用0位置的View作为锚点参考position。

最后一种什么时候会发生呢？其实就是没有子View让我们作为参考。比如说第一次加载数据的时候，RecyclerView一片空白。这时候肯定没有任何子View能够让我们作为参考。

那么当有子View的时候，我们通过 **updateAnchorFromChildren** 方法来确定锚点位置。

```java
    //从现有子View中确定锚定。大多数情况下，是起始或者末尾的有效子View(一般是未移除，即展示在我们面前的View)。
    private boolean updateAnchorFromChildren(RecyclerView.Recycler recycler, RecyclerView.State state, AnchorInfo anchorInfo) {
        //没有数据，直接返回false
        if (getChildCount() == 0) {
            return false;
        }
        final View focused = getFocusedChild();
        //优先选取获得焦点的子View作为锚点
        if (focused != null && anchorInfo.isViewValidAsAnchor(focused, state)) {
            //保持获取焦点的子view的位置信息
            anchorInfo.assignFromViewAndKeepVisibleRect(focused);
            return true;
        }
        if (mLastStackFromEnd != mStackFromEnd) {
            return false;
        }
        //根据锚点的设置信息，从底部或者顶部获取子View信息
        View referenceChild = anchorInfo.mLayoutFromEnd ? findReferenceChildClosestToEnd(recycler, state) : findReferenceChildClosestToStart(recycler, state);
        if (referenceChild != null) {
            anchorInfo.assignFromView(referenceChild);
            ...
            return true;
        }
        return false;
    }
```

通过子View确定锚点坐标也是进行了3种情况的处理

1. 没有数据，直接返回获取失败
2. 如果某个子View持有焦点，那么直接把持有焦点的子View作为锚点参考点
3. 没有View持有焦点，一般会选择最上（或者最下面）的子View作为锚点参考点

一般情况下，都会使用第三种方案来确定锚点，所以我们这里也主要关注一下这里的方法。按照我们默认的变量信息，这里会通过 **findReferenceChildClosestToStart** 方法获取可见区域中的第一个子View作为锚点的参考View。然后调用 **assignFromView** 方法来确定锚点的几个属性值。

```java
        public void assignFromView(View child) {
            if (mLayoutFromEnd) {
                //如果是从底部布局，那么获取child的底部的位置设置为锚点
                mCoordinate = mOrientationHelper.getDecoratedEnd(child) + mOrientationHelper.getTotalSpaceChange();
            } else {
                //如果是从顶部开始布局，那么获取child的顶部的位置设置为锚点坐标(这里要考虑ItemDecorator的情况)
                mCoordinate = mOrientationHelper.getDecoratedStart(child);
            }
            //mPosition赋值为参考View的position
            mPosition = getPosition(child);
        }
```



待研究

1. Recycler的作用
2. ViewInfoStore类





使用了SparseArray(性能更优？)

总结

1. 在RecyclerView中，内部类Recycler主要负责回收和复用工作。
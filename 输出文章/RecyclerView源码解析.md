### 引言

在之前的[Android的View绘制机制](https://juejin.im/post/5e99d38de51d454706530886)中我们讲过，对于控件的测量以及布局会通过 **onMeasure()** 和 **onLayout()** 方法来实现。所以这里我们将这两个函数作为入口来研究RecyclerView的整个布局过程。

### 基础

RecyclerView相对于以前的ListView来说，更加灵活。其所拆分出来的各个类的分工更加明确，很好地体现了我们经常所说的职责单一原则。我们这里先对其中使用到的类进行一下讲解

* LayoutManager：RecyclerView的布局管理者，主要负责对于RecyclerView子View的测量和布局工作。
* RecyclerView.Recycler：缓存的核心类。RecyclerView强大的缓存能力都是基于这个类来实现的。是缓存的核心工具类。
* Adapter：Adapter的基类。负责将ViewHolder中的数据和RecyclerView中的控件进行绑定处理。
* ViewHolder：视图和元数据类。它持有了要显示的数据信息，包括位置、View、ViewType等。

### 源码

无论是View还是ViewGroup的子类，都是通过 **onMeasure()** 来实现测量工作的，那么我们对于RecyclerView的源码解析就把onMeasure当作我们的切入点

#### 自身测量

```java
    //RecyclerView.java
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
//RecyclerView.java
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
    //RecyclerView.java
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
    //RecyclerView.java
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
    //LinearLayoutManager.java
	public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        // layout algorithm:
        // 1) by checking children and other variables, find an anchor coordinate and an anchor
        //  item position.
        // 2) fill towards start, stacking from bottom
        // 3) fill towards end, stacking from top
        // 4) scroll to fulfill requirements like stack from bottom.
        // create layout state
```

在方法的开始位置，直接就扔给了我们一段说明文档，告诉了我们 **LinearLayoutManager** 中的布局策略。简单翻译一下：

1. 通过子控件和其他的变量信息。找到一个锚点和锚点项的位置。
2. 从锚点的位置开始，往上，填充布局子View，直到填满区域
3. 从锚点的位置开始，往下，填充布局子View，直到填满区域
4. 滚动以满足需求，如堆栈从底部

这里有个比较关键的词，就是 **锚点(AnchorInfo)** ，其实 LLM 的布局并不是从上往下一个个进行的。而是很可能从整个布局的中间某个点开始的，然后朝一个方向一个个填充，填满可见区域后，朝另一个方向进行填充。至于先朝哪个方向填充，是根据具体的变量来确定的。

#### 锚点的选择

**AnchorInfo** 类需要能够有效的描述一个具体的位置信息，我们首先类内部的几个重要的成员变量。

```java
    //LinearLayoutManager.java
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
    //LinearLayoutManager.java
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
    //LinearLayoutManager.java
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
    //LinearLayoutManager.java
	//从现有子View中确定锚定。大多数情况下，是起始或者末尾的有效子View(一般是未移除，展示在我们面前的View)。
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
3. 没有子View持有焦点，一般会选择最上（或者最下面）的子View作为锚点参考点

一般情况下，都会使用第三种方案来确定锚点，所以我们这里也主要关注一下这里的方法。按照我们默认的变量信息，这里会通过 **findReferenceChildClosestToStart** 方法获取可见区域中的第一个子View作为锚点的参考View。然后调用 **assignFromView** 方法来确定锚点的几个属性值。

```java
		//LinearLayoutManager.java
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

mPostion这个变量很好理解，就是子View的位置值，那么 **mCoordinate** 是个什么鬼？我们 **getDecoratedStart** 是怎么处理的就知道了。

```java
        //LinearLayoutManager.java
        //创建mOrientationHelper。我们按照垂直布局来进行分析
        if (mOrientationHelper == null) {
            mOrientationHelper = OrientationHelper.createOrientationHelper(this, mOrientation);
        }
        //OrientationHelper.java
    public static OrientationHelper createVerticalHelper(RecyclerView.LayoutManager layoutManager) {
        return new OrientationHelper(layoutManager) {
            @Override
            @Override
            public int getDecoratedStart(View view) {
                final RecyclerView.LayoutParams params =  (RecyclerView.LayoutParams)view.getLayoutParams();
                //
                return mLayoutManager.getDecoratedTop(view) - params.topMargin;
            }

```

比较难理么，我们上个简陋的图解释一下

![image-20200417130157946](http://cdn.qiniu.kailaisii.com/typora/202004/17/130201-526702.png)

可以看到在使用子控件进行锚点信息确认时，一般会选择屏幕中可见的子View的position为锚点。这里会选取屏幕上第一个可见View，也就是positon=1的子View作为参考点， **mCoordinate** 被赋值为1号子View上面的Decor的顶部位置。

#### 布局的填充

回到主线 **onLayoutChildren** 函数。当我们的锚点信息确认以后，剩下的就是从这个位置开始进行布局的填充。

```java
        if (mAnchorInfo.mLayoutFromEnd) {//从end开始布局
            //倒着绘制的话，先从锚点往上，绘制完再从锚点往下
            //设置绘制方向信息为从锚点往上
            updateLayoutStateToFillStart(mAnchorInfo);
            mLayoutState.mExtra = extraForStart;
            fill(recycler, mLayoutState, state, false);
            startOffset = mLayoutState.mOffset;
            final int firstElement = mLayoutState.mCurrentPosition;
            if (mLayoutState.mAvailable > 0) {
                extraForEnd += mLayoutState.mAvailable;
            }
            //设置绘制方向信息为从锚点往下
            updateLayoutStateToFillEnd(mAnchorInfo);
            mLayoutState.mExtra = extraForEnd;
            mLayoutState.mCurrentPosition += mLayoutState.mItemDirection;
            fill(recycler, mLayoutState, state, false);
            endOffset = mLayoutState.mOffset;

            if (mLayoutState.mAvailable > 0) {
                extraForStart = mLayoutState.mAvailable;
                updateLayoutStateToFillStart(firstElement, startOffset);
                mLayoutState.mExtra = extraForStart;
                fill(recycler, mLayoutState, state, false);
                startOffset = mLayoutState.mOffset;
            }
        } else {//从起始位置开始布局
            // 更新layoutState，设置布局方向朝下
            updateLayoutStateToFillEnd(mAnchorInfo);
            mLayoutState.mExtra = extraForEnd;
            //开始填充布局
            fill(recycler, mLayoutState, state, false);
            //结束偏移
            endOffset = mLayoutState.mOffset;
            //绘制后的最后一个view的position
            final int lastElement = mLayoutState.mCurrentPosition;
            if (mLayoutState.mAvailable > 0) {
                extraForStart += mLayoutState.mAvailable;
            }
            //更新layoutState，设置布局方向朝上
            updateLayoutStateToFillStart(mAnchorInfo);
            mLayoutState.mExtra = extraForStart;
            mLayoutState.mCurrentPosition += mLayoutState.mItemDirection;
            //再次填充布局
            fill(recycler, mLayoutState, state, false);
            //起始位置的偏移
            startOffset = mLayoutState.mOffset;

            if (mLayoutState.mAvailable > 0) {
                extraForEnd = mLayoutState.mAvailable;
                updateLayoutStateToFillEnd(lastElement, endOffset);
                mLayoutState.mExtra = extraForEnd;
                fill(recycler, mLayoutState, state, false);
                endOffset = mLayoutState.mOffset;
            }
        }
```

可以看到，根据不同的绘制方向，这里面做了不同的处理，只是填充的方向相反而已，具体的步骤是相似的。都是从锚点开始往一个方向进行View的填充，填充满以后再朝另一个方向填充。填充子View使用的是 **fill()** 方法。

因为对于绘制方向都按照默认的来处理，所以这里我们看看分析else的代码，而且第一次填充是朝下填充。

```java
    //在LinearLayoutManager中，进行界面重绘和进行滑动两种情况下，往屏幕上填充子View的工作都是调用fill()进行
    int fill(RecyclerView.Recycler recycler, LayoutState layoutState, RecyclerView.State state, boolean stopOnFocusable) {
        //可用区域的像素数
        final int start = layoutState.mAvailable;
        if (layoutState.mScrollingOffset != LayoutState.SCROLLING_OFFSET_NaN) {
            if (layoutState.mAvailable < 0) {
                layoutState.mScrollingOffset += layoutState.mAvailable;
            }
            //将滑出屏幕的View回收掉
            recycleByLayoutState(recycler, layoutState);
        }
        //剩余绘制空间=可用区域+扩展空间。
        int remainingSpace = layoutState.mAvailable + layoutState.mExtra;
        LayoutChunkResult layoutChunkResult = mLayoutChunkResult;
        //循环布局直到没有剩余空间了或者没有剩余数据了
        while ((layoutState.mInfinite || remainingSpace > 0) && layoutState.hasMore(state)) {
            //初始化layoutChunkResult
            layoutChunkResult.resetInternal();
            //**重点方法  添加一个child，然后将绘制的相关信息保存到layoutChunkResult
            layoutChunk(recycler, state, layoutState, layoutChunkResult);
            if (layoutChunkResult.mFinished) {//如果布局结束了(没有view了)，退出循环
                break;
            }
            //根据所添加的child消费的高度更新layoutState的偏移量。mLayoutDirection为+1或者-1，通过乘法来处理是从底部往上布局，还是从上往底部开始布局
            layoutState.mOffset += layoutChunkResult.mConsumed * layoutState.mLayoutDirection;
            if (!layoutChunkResult.mIgnoreConsumed || mLayoutState.mScrapList != null || !state.isPreLayout()) {
                layoutState.mAvailable -= layoutChunkResult.mConsumed;
                //消费剩余可用空间
                remainingSpace -= layoutChunkResult.mConsumed;
            }
            ...
        }
        //返回本次布局所填充的区域
        return start - layoutState.mAvailable;
    }
```

在 **fill** 方法中，会判断当前的是否还有剩余区域可以进行子View的填充。如果没有剩余区域或者没有子View，那么就返回。否则就通过 **layoutChunk** 来进行填充工作，填充完毕以后更新当前的可用区域，然后依次遍历循环，直到不满足条件为止。

循环中的填充是通过 **layoutChunk** 来实现的。

```java
    void layoutChunk(RecyclerView.Recycler recycler, RecyclerView.State state,LayoutState layoutState, LayoutChunkResult result) {
        //通过缓存获取当前position所需要展示的ViewHolder的View
        View view = layoutState.next(recycler);
        if (view == null) {
            //如果我们将视图放置在废弃视图中，这可能会返回null，这意味着没有更多的项需要布局。
            result.mFinished = true;
            return;
        }
        LayoutParams params = (LayoutParams) view.getLayoutParams();
        if (layoutState.mScrapList == null) {
            //根据方向调用addView方法添加子View
            if (mShouldReverseLayout == (layoutState.mLayoutDirection == LayoutState.LAYOUT_START)) {
                addView(view);
            } else {
                addView(view, 0);
            }
        } else {
            if (mShouldReverseLayout == (layoutState.mLayoutDirection == LayoutState.LAYOUT_START)) {
                //这里是即将消失的View，但是需要设置对应的移除动画
                addDisappearingView(view);
            } else {
                addDisappearingView(view, 0);
            }
        }
        //调用measure测量view。这里会考虑到父类的padding
        measureChildWithMargins(view, 0, 0);
        //将本次子View消费的区域设置为子view的高(或者宽)
        result.mConsumed = mOrientationHelper.getDecoratedMeasurement(view);
        //找到view的四个边角位置
        int left, top, right, bottom;
        ...
        //调用child.layout方法进行布局(这里会考虑到view的ItemDecorator等信息)
        layoutDecoratedWithMargins(view, left, top, right, bottom);
        //如果视图未被删除或更改，则使用可用空间
        if (params.isItemRemoved() || params.isItemChanged()) {
            result.mIgnoreConsumed = true;
        }
        result.mFocusable = view.isFocusable();
    }
```

这里主要做了5个处理

1. 通过 **layoutState** 获取要展示的View
2. 通过 **addView** 方法将子View添加到布局中
3. 调用 **measureChildWithMargins** 方法测量子View
4. 调用 **layoutDecoratedWithMargins** 方法布局子View
5. 根据处理的结果，填充LayoutChunkResult的相关信息，以便返回之后，能够进行数据的计算。

如果只是考虑第一次数据加载，那么到目前为止，我们的整个页面通过两次 **fill** 就能够将整个屏幕填充完毕了。



### 复用机制

对于RecyclerView的复用机制，我们就不得不提 **RecyclerView.Recycler** 类了。它的职责就是负责对于View的回收以及复用工作。**Recycler** 依次通过 **Scrap、CacheView、ViewCacheExtension还有RecycledViewPool** 四级缓存机制实现对于RecyclerView的快速绘制工作。

#### Scrap

Scrap是RecyclerView中最轻量的缓存，它不参与滑动时的回收复用，只是作为重新布局时的一种临时缓存。它的目的是，缓存当界面重新布局的前后都出现在屏幕上的ViewHolder，以此省去不必要的重新加载与绑定工作。

在RecyclerView重新布局的时候（不包括RecyclerView初始的那次布局，因为初始化的时候屏幕上本来并没有任何View），先调用**detachAndScrapAttachedViews()**将所有当前屏幕上正在显示的View以ViewHolder为单位标记并记录在列表中，在之后的**fill()**填充屏幕过程中，会优先从Scrap列表里面寻找对应的ViewHolder填充。从Scrap中直接返回的ViewHolder内容没有任何的变化，不会进行重新创建和绑定的过程。

Scrap列表存在于Recycler模块中。

```java
 public final class Recycler {
        final ArrayList<ViewHolder> mAttachedScrap = new ArrayList<>();
        ArrayList<ViewHolder> mChangedScrap = null;
        ...
}
```

可以看到，Scrap实际上包括了两个ViewHolder类型的ArrayList。mAttachedScrap负责保存将会原封不动的ViewHolder，而mChangedScrap负责保存位置会发生移动的ViewHolder，注意只是位置发生移动，内容仍旧是原封不动的。

![img](http://cdn.qiniu.kailaisii.com/7142965-b0656d603b8d445f.png)

上图描述的是我们在一个RecyclerView中删除B项，并且调用了**notifyItemRemoved()**时，mAttachedScrap与mChangedScrap分别会临时存储的View情况。此时，A是在删除前后完全没有变化的，它会被临时放入mAttachedScrap。B是我们要删除的，它也会被放进mAttachedScrap，但是会被额外标记REMOVED，并且在之后会被移除。C和D在删除B后会向上移动位置，因此他们会被临时放入mChangedScrap中。E在此次操作前并没有出现在屏幕中，它不属于Scrap需要管辖的，Scrap只会缓存屏幕上已经加载出来的ViewHolder。在删除时，A,B,C,D都会进入Scrap，而在删除后，A,C,D都会回来，其中C,D只进行了位置上的移动，其内容没有发生变化。

RecyclerView的局部刷新，依赖的就是Scrap的临时缓存，我们需要通过**notifyItemRemoved()、notifyItemChanged**()等系列方法通知RecyclerView哪些位置发生了变化，这样RecyclerView就能在处理这些变化的时候，使用Scrap来缓存其它内容没有发生变化的ViewHolder，于是就完成了局部刷新。需要注意的是，如果我们使用**notifyDataSetChanged()**方法来通知RecyclerView进行更新，其会标记所有屏幕上的View为FLAG_INVALID，从而不会尝试使用Scrap来缓存一会儿还会回来的ViewHolder，而是统统直接扔进RecycledViewPool池子里，回来的时候就要重新走一遍绑定的过程。

Scrap只是作为布局时的临时缓存，它和滑动时的缓存没有任何关系，它的detach和重新attach只临时存在于布局的过程中。布局结束时Scrap列表应该是空的，其成员要么被重新布局出来，要么将被移除，总之在布局过程结束的时候，两个Scrap列表中都不应该再存在任何东西。

#### CacheView是什么？

CacheView是一个以ViewHolder为单位，负责在RecyclerView列表位置产生变动的时候，对刚刚移出屏幕的View进行回收复用的缓存列表。



```java
 public final class Recycler {
        ...
        final ArrayList<ViewHolder> mCachedViews = new ArrayList<ViewHolder>();
        int mViewCacheMax = DEFAULT_CACHE_SIZE;
        ...
}
```

我们可以看到，在Recycler中，mCachedView与前面的Scrap一样，也是一个以ViewHolder为单位存储的ArrayList。这意味着，它也是对于ViewHolder整个进行缓存，在复用时不需要经过创建和绑定过程，内容不发生改变。而且它有个最大缓存个数限制，默认情况下是2个。

![img](http://cdn.qiniu.kailaisii.com/7142965-d2b2e4385d10ed8d.png)

CacheView.png

从上图中可以看出，CacheView将会缓存刚变为不可见的View。这个缓存工作的进行，是发生在**fill()**调用时的，由于布局更新和滑动时都会调用**fill()**来进行填充，因此这个场景在滑动过程中会反复出现，在布局更新时也可能因为位置变动而出现。**fill()**几经周转最终会调用**recycleViewHolderInternal()**，里面将会出现**mCachedViews.add()**。上面提到，CacheView有最大缓存个数限制，那么如果超过了缓存会怎样呢？

```csharp
void recycleViewHolderInternal(ViewHolder holder) {
            ...
            if (forceRecycle || holder.isRecyclable()) {
                if (mViewCacheMax > 0
                        && !holder.hasAnyOfTheFlags(ViewHolder.FLAG_INVALID
                                | ViewHolder.FLAG_REMOVED | ViewHolder.FLAG_UPDATE)) {
                    // Retire oldest cached view 回收并替换最先缓存的那个
                    int cachedViewSize = mCachedViews.size();
                    if (cachedViewSize >= mViewCacheMax && cachedViewSize > 0) {
                        recycleCachedViewAt(0);
                        cachedViewSize--;
                    }
                    mCachedViews.add(targetCacheIndex, holder);
                }
               ...
        }
```

在**recycleViewHolderInternal()**中有这么一段，如果Recycler发现缓存进来一个新ViewHolder时，会超过最大限制，那么它将先调用**recycleCachedViewAt(0)**将最先缓存进来的那个ViewHolder回收进RecycledViewPool池子里，然后再调用**mCachedViews.add()**添加新的缓存。也就是说，我们在滑动RecyclerView的时候，Recycler会不断地缓存刚刚滑过变成不可见的View进入CacheView，在达到CacheView的上限时，又会不断地替换CacheView里的ViewHolder，将它们扔进RecycledViewPool里。如果我们一直朝同一个方向滑动，CacheView其实并没有在效率上产生帮助，它只是不断地在把后面滑过的ViewHolder进行了缓存；而如果我们经常上下来回滑动，那么CacheView中的缓存就会得到很好的利用，毕竟复用CacheView中的缓存的时候，不需要经过创建和绑定的消耗。

#### RecycledViewPool

前面提到，在Srap和CacheView不愿意缓存的时候，都会丢进RecycledViewPool进行回收，因此RecycledViewPool可以说是Recycler中的一个终极回收站。

```java
 public static class RecycledViewPool {
        private SparseArray<ArrayList<ViewHolder>> mScrap =
                new SparseArray<ArrayList<ViewHolder>>();
        private SparseIntArray mMaxScrap = new SparseIntArray();
        private int mAttachCount = 0;

        private static final int DEFAULT_MAX_SCRAP = 5;
```

我们可以在RecyclerView中找到RecycledViewPool，可以看见它的保存形式是和上述的Srap、CacheView都不同的，它是以一个SparseArray嵌套一个ArrayList对ViewHolder进行保存的。原因是RecycledViewPool保存的是以ViewHolder的viewType为区分（我们在重写RecyclerView的**onCreateViewHolder()**时可以发现这里有个viewType参数，可以借助它来实现展示不同类型的列表项）的多个列表。

与前两者不同，RecycledViewPool在进行回收的时候，目标只是回收一个该viewType的ViewHolder对象，并没有保存下原来ViewHolder的内容，在复用时，将会调用**bindViewHolder()** 按照我们在**onBindViewHolder()**描述的绑定步骤进行重新绑定，从而摇身一变变成了一个新的列表项展示出来。

同样，RecycledViewPool也有一个最大数量限制，默认情况下是5。在没有超过最大数量限制的情况下，Recycler会尽量把将被废弃的ViewHolder回收到RecycledViewPool中，以期能被复用。值得一提的是，RecycledViewPool只会按照ViewType进行区分，只要ViewType是相同的，甚至可以在多个RecyclerView中进行通用的复用，只要为它们设置同一个RecycledViewPool就可以了。

总的来看，RecyclerView着重在两个场景使用缓存与回收复用进行了性能上的优化。一是，在数据更新时，利用Scrap实现局部更新，尽可能地减少没有被更改的View进行无用地重新创建与绑定工作。二是，在快速滑动的时候，重复利用已经滑过的ViewHolder对象，以尽可能减少重新创建ViewHolder对象时带来的压力。总体的思路就是：只要没有改变，就直接重用；只要能不创建或重新绑定，就尽可能地偷懒。

### 滑动处理

在研究滑动前，我们先对 **LayoutState** 这个类的几个变量做一下说明。

* mOffset：布局起始位置的偏移量(应该是锚点里面设置的mCoordinate)
* mAvailable：在布局方向上的可以填充的像素值，也就是空闲出来的区域
* mScrollingOffset：不创建新视图的情况下进行滚动的距离。 比如说我某个View上半部分显示了一半，那么这时候我往上滑动一半距离的话以内，是不需要创建新的子View的。这个mScrollingOffset就是我在不创建视图的前提下可以滑动的最大距离。

#### 滑动后的数据处理

当滚动发生时，会触发 **scrollHorizontallyBy** 方法

```java
//LinearLayoutManager.java
		public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler,RecyclerView.State state) {
        if (mOrientation == VERTICAL) {
            return 0;
        }
        return scrollBy(dx, recycler, state);
    }

    int scrollBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getChildCount() == 0 || dy == 0) {
            return 0;
        }
        //标记正在滚动
        mLayoutState.mRecycle = true;
        ensureLayoutState();
        //确认滚动方向
        final int layoutDirection = dy > 0 ? LayoutState.LAYOUT_END : LayoutState.LAYOUT_START;
        final int absDy = Math.abs(dy);
        //更新layoutState，会更新其展示的屏幕区域，偏移量等。比如说当往上滑动的时候，底部会有dy距离的空白区域，这时候，需要调用fill来填充这个dy距离的区域
        updateLayoutState(layoutDirection, absDy, true, state);
        //调用fill进行填充展示在客户面前的view
        final int consumed = mLayoutState.mScrollingOffset + fill(recycler, mLayoutState, state, false);
        ...
        //记录本次滚动的距离
        mLayoutState.mLastScrollDelta = scrolled;
        return scrolled;
    }
```

当滚动时主要进行了两个处理

1. 通过 **updateLayoutState** 方法更新layoutState内部的相关属性的。
2. 调用 **fill** 进行数据的填充。

我们为了更好的理解layoutState内部的属性关系，简单看一下 **updateLayoutState** 内部的实现。

```java
	//LinearLayoutaManager.java
	private void updateLayoutState(int layoutDirection, int requiredSpace,boolean canUseExistingSpace, RecyclerView.State state) {
        int scrollingOffset;
        if (layoutDirection == LayoutState.LAYOUT_END) {
            //获取当前显示的最底部的View
            final View child = getChildClosestToEnd();
            //设置当前显示的子View的底部的偏移量（包括了Decor的高度）
            mLayoutState.mOffset = mOrientationHelper.getDecoratedEnd(child);
            //底部锚点位置减去RecyclerView的高度的话，剩下的就是我们滚动scrollingOffset以内，不会绘制新的View
            //getEndAfterPadding=RecyclerView的高度-padding的高度
            scrollingOffset = mOrientationHelper.getDecoratedEnd(child) - mOrientationHelper.getEndAfterPadding();
        } 
        ....
```

方法里面的注释已经很详细了。

有了复用基础和对这几个变量的理解之后，我们重新回到 **fill** 中，去理解LLM是如何进行缓存处理的。

#### View的回收

首先看一下View的回收。

```java
	//LinearLayoutManager.java
	int fill(RecyclerView.Recycler recycler, LayoutState layoutState, RecyclerView.State state, boolean stopOnFocusable) {
            //重点方法  ** 将滑出屏幕的View回收掉
            recycleByLayoutState(recycler, layoutState);
        }

    private void recycleByLayoutState(RecyclerView.Recycler recycler, LayoutState layoutState) {
        if (!layoutState.mRecycle || layoutState.mInfinite) {
            return;
        }
        if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
            //从End端开始回收视图
            recycleViewsFromEnd(recycler, layoutState.mScrollingOffset);
        } else {
            //从Start端开始回收视图
            recycleViewsFromStart(recycler, layoutState.mScrollingOffset);
        }
    }
```

这里我们考虑手指上滑的情况，也就是 **recycleViewsFromStart** 。另一种情况是相似的，可以自己去理解

```java
    //从头部回收View
    private void recycleViewsFromStart(RecyclerView.Recycler recycler, int dt) {
        //limit表示滑动多少以内不会绘制
        final int limit = dt;
        //返回附加到父视图的当前子View的数量
        final int childCount = getChildCount();
        ...
            //遍历子View
            for (int i = 0; i < childCount; i++) {
                //获取到子View
                View child = getChildAt(i);
                //如果当前的View的底部位置>limit，那么也就是会有View需要绘制，顶部的View也就需要回收了
                //这里有个逻辑，就是如果底部的View不需要绘制，那么顶部的View就不会进行回收
                if (mOrientationHelper.getDecoratedEnd(child) > limit || mOrientationHelper.getTransformedEndWithDecoration(child) > limit) {
                    recycleChildren(recycler, 0, i);
                    return;
                }
            }
        }
    }

```

经过跟踪最后会进入到

```java
    //LinearLayoutManager.java
	private void recycleChildren(RecyclerView.Recycler recycler, int startIndex, int endIndex) {
          ...
                removeAndRecycleViewAt(i, recycler);
           ...
        }
    }
    
    //RecyclerView.java
    public void removeAndRecycleViewAt(int index, Recycler recycler) {
    	final View view = getChildAt(index);
    	removeViewAt(index);
    	recycler.recycleView(view);
    }
    
    //RecyclerView.java
        public void recycleView(View view) {
            recycleViewHolderInternal(holder);
        }
```

最终的回收操作会通过 **recycleViewHolderInternal** 方法来执行。

```java
        void recycleViewHolderInternal(ViewHolder holder) {
            //判断各种无法回收的情况
            ...
            if (forceRecycle || holder.isRecyclable()) {
                //符合回收条件
                if (mViewCacheMax > 0 && !holder.hasAnyOfTheFlags(ViewHolder.FLAG_INVALID | ViewHolder.FLAG_REMOVED | ViewHolder.FLAG_UPDATE | ViewHolder.FLAG_ADAPTER_POSITION_UNKNOWN)) {
                    // Retire oldest cached view
                    //滑动的视图，先保存在mCachedViews中
                    int cachedViewSize = mCachedViews.size();
                    if (cachedViewSize >= mViewCacheMax && cachedViewSize > 0) {
                        //mCachedViews只能缓存mViewCacheMax个，那么需要将最久的那个移到RecycledViewPool
                        recycleCachedViewAt(0);
                        cachedViewSize--;
                    }

                    int targetCacheIndex = cachedViewSize;
                    if (ALLOW_THREAD_GAP_WORK && cachedViewSize > 0  && !mPrefetchRegistry.lastPrefetchIncludedPosition(holder.mPosition)) {
                        // when adding the view, skip past most recently prefetched views
                        int cacheIndex = cachedViewSize - 1;
                        while (cacheIndex >= 0) {
                            int cachedPos = mCachedViews.get(cacheIndex).mPosition;
                            if (!mPrefetchRegistry.lastPrefetchIncludedPosition(cachedPos)) {
                                break;
                            }
                            cacheIndex--;
                        }
                        targetCacheIndex = cacheIndex + 1;
                    }
                    //将本次回收的ViewHolder放到mCachedViews中
                    mCachedViews.add(targetCacheIndex, holder);
                    cached = true;
                }
                if (!cached) {//如果已经缓存了。那么此处不会执行了。
                    addViewHolderToRecycledViewPool(holder, true);
                    recycled = true;
                }
            }
            ...
            mViewInfoStore.removeViewHolder(holder);
            if (!cached && !recycled && transientStatePreventsRecycling) {
                holder.mOwnerRecyclerView = null;
            }
        }
```

这部分代码属于在滑动时的View回收的过程

1. 如果不满足能够回收的条件，则直接抛出异常
2. 如果满足回收条件，会看 **cachedViewSize** 是否已满，如果满了，就移除最早的那个到缓存池 **RecycledViewPool** ，然后将当前需要回收的放入到 **cachedViewSize** 中。如果没满，则直接放入了。
3. 存放线程池 **RecycledViewPool** 会按照ViewType来缓存到不同的队列，每个类型的队列最多缓存5个。如果已经满了，则不再缓存。

#### View的复用

在 **fill** 填充方法中，不仅包含了对于滑出屏幕的View的回收处理，还会将即将展示的界面通过复用ViewHolder来达到快速处理的效果。而对于复用的调用则是在 **layoutChunk** 中的 **layoutState.next(recycler)** 来触发的。

```java
		//LinearLayoutManager.java
        void layoutChunk(RecyclerView.Recycler recycler, RecyclerView.State state,LayoutState layoutState, LayoutChunkResult result) {
            //通过缓存获取当前position所需要展示的ViewHolder的View
            View view = layoutState.next(recycler);
		//LinearLayoutManager.java        
        View next(RecyclerView.Recycler recycler) {
            ...
            final View view = recycler.getViewForPosition(mCurrentPosition);
            ...
        }

		//RecyclerView.java
        public View getViewForPosition(int position) {
            return getViewForPosition(position, false);
        }
		//RecyclerView.java
        View getViewForPosition(int position, boolean dryRun) {
            return tryGetViewHolderForPositionByDeadline(position, dryRun, FOREVER_NS).itemView;
        }
```

最终对于ViewHolder的复用逻辑是由 **tryGetViewHolderForPositionByDeadline** 来处理的。

```java
//RecyclerView.java
	ViewHolder tryGetViewHolderForPositionByDeadline(int position,boolean dryRun, long deadlineNs) {
            boolean fromScrapOrHiddenOrCache = false;
            ViewHolder holder = null;
            //尝试从mChangedScrap中获取。当数据位置发生变化的时候，会走这个逻辑。不如说notifyItemRemove()后，下面的数据会上移，会走这个逻辑
            if (mState.isPreLayout()) {
                holder = getChangedScrapViewForPosition(position);
                fromScrapOrHiddenOrCache = holder != null;
            }
            if (holder == null) {
                //根据position依次尝试从mAttachedScrap、隐藏的列表、一级缓存(mCachedViews)中获取
                holder = getScrapOrHiddenOrCachedHolderForPosition(position, dryRun);
                if (holder != null) {
                    //检验获取的holder是否合法。不合法，就会将holder进行回收。如果合法，则标记fromScrapOrHiddenOrCache为true。表明holder是从这缓存中获取的。
                    if (!validateViewHolderForOffsetPosition(holder)) {
                        ...
                        holder = null;
                    } else {
                        fromScrapOrHiddenOrCache = true;
                    }
                }
            }
            if (holder == null) {
                ...
                if (mAdapter.hasStableIds()) {
                    //根据id依次尝试从mAttachedScrap、一级缓存(mCachedViews)中获取
                    holder = getScrapOrCachedViewForId(mAdapter.getItemId(offsetPosition),type, dryRun);
                    if (holder != null) {
                        holder.mPosition = offsetPosition;
                        fromScrapOrHiddenOrCache = true;
                    }
                }
                //尝试从我们自定义的mViewCacheExtension（二级缓存）中去获取
                if (holder == null && mViewCacheExtension != null) {
                    final View view = mViewCacheExtension .getViewForPositionAndType(this, position, type);
                    if (view != null) {
                        holder = getChildViewHolder(view);
                        if (holder == null) {
                            throw new IllegalArgumentException("getViewForPositionAndType returned" + " a view which does not have a ViewHolder");
                        } else if (holder.shouldIgnore()) {
                            throw new IllegalArgumentException("getViewForPositionAndType returned" + " a view that is ignored. You must call stopIgnoring before" + " returning this view.");
                        }
                    }
                }
                if (holder == null) {
                    //从缓存池里面获取
                    holder = getRecycledViewPool().getRecycledView(type);
                    if (holder != null) {
                        holder.resetInternal();
                        if (FORCE_INVALIDATE_DISPLAY_LIST) {
                            invalidateDisplayListInt(holder);
                        }
                    }
                }
                if (holder == null) {
                    long start = getNanoTime();
                    if (deadlineNs != FOREVER_NS&& !mRecyclerPool.willCreateInTime(type, start, deadlineNs)) {
                        return null;
                    }
                    //如果仍然无法获取的话，调用Adatper的createViewHolder方法创建一个ViewHolder
                    holder = mAdapter.createViewHolder(RecyclerView.this, type);
                    if (ALLOW_THREAD_GAP_WORK) {
                        // only bother finding nested RV if prefetching
                        RecyclerView innerView = findNestedRecyclerView(holder.itemView);
                        if (innerView != null) {
                            holder.mNestedRecyclerView = new WeakReference<>(innerView);
                        }
                    }

                    long end = getNanoTime();
                    mRecyclerPool.factorInCreateTime(type, end - start);
                }
            }
			...
            boolean bound = false;
            if (mState.isPreLayout() && holder.isBound()) {
                // do not update unless we absolutely have to.
                //数据不需要绑定（一般从mChangedScrap，mAttachedScrap中得到的缓存Holder是不需要进行重新绑定的）
                holder.mPreLayoutPosition = position;
            } else if (!holder.isBound() || holder.needsUpdate() || holder.isInvalid()) {
                //holder没有绑定数据，或者需要更新或者holder无效，则需要重新进行数据的绑定
                if (DEBUG && holder.isRemoved()) {
                    throw new IllegalStateException("Removed holder should be bound and it should"+ " come here only in pre-layout. Holder: " + holder);
                }
                final int offsetPosition = mAdapterHelper.findPositionOffset(position);
                //这里会进行数据的绑定
                bound = tryBindViewHolderByDeadline(holder, offsetPosition, position, deadlineNs);
            }
			...
            return holder;
        }
```

这一段的逻辑就是我们的ViewHolder的整个复用的流程。可以汇总一下

1. 从**mChangedScrap**中获取去获取
2. 根据position依次尝试从**mAttachedScrap**、**隐藏的列表**、**一级缓存(mCachedViews)**中获取
3. 根据id依次尝试从**mAttachedScrap**、**一级缓存(mCachedViews)**中获取
4. 尝试从我们自定义的**mViewCacheExtension（二级缓存）**中去获取
5. 根据ViewType从**缓存池**里面获取
6. 如果上面都无法获取的话就通过adapter的 **createViewHolder** 来创建ViewHolder

当我们获取到ViewHolder以后会需要进行绑定，也就是将我们的数据展示在View中。如果获取的ViewHolder缓存已经进行了数据绑定的话，则无需再进行处理，否则就需要通过 **tryBindViewHolderByDeadline** 方法调用 adapter的**bindViewHolder** 来进行数据的绑定。

### 总结

整篇文章到这里结束了，相对来说内容还是比较多的。先从RecyclerView的测量当作入口，在测量的过程中，提到了复用机制。最后通过RecyclerView对滑动的处理方法，从源码层面讲解了Holder的回收和复用的实现机制。

汇总一下本次源码解析学到的新知识：

1. 在RecyclerView中，内部类Recycler主要负责回收和复用工作。
2. 当滑动RecyclerView时，是不断的调用 **fill** 去判断是否需要填充的。
3. LinearLayoutManager可以通过**setReverseLayout**设置反向遍历布局。第一项放置在UI的末尾，第二项放置在它之前。
4. 当View滑动回收的时候，会回调adapter的 **onViewDetachedFromWindow** 方法
5. 深入理解了RecyclerView的缓存机制和原理
6. 对于RecyclerView的子View的布局过程和原理进行了讲解



> 本文由 [开了肯](http://www.kailaisii.com/) 发布！ 
>
> 同步公众号[开了肯]

![image-20200404120045271](https://user-gold-cdn.xitu.io/2020/4/10/17164699ead75494?w=297&h=298&f=png&s=20578)
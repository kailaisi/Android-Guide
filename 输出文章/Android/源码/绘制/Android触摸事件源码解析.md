### 事件分发

![image-20200324185524436](http://cdn.qiniu.kailaisii.com/typora/20200324185529-500051.png)

### Activity 的事件分发处理

```java
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
        	//空方法
            onUserInteraction();
        }
        //getWindow()方法返回的是PhoneWindow。
        if (getWindow().superDispatchTouchEvent(ev)) {
            return true;
        }
        return onTouchEvent(ev);
    }
```

这里如果**superDispatchTouchEvent**分发返回的是true的话，那么直接返回了。否则就会调用**onTouchEvnent**方法。

我们看一下**superDispatchTouchEvent**的实现方法

```java
 	@Override
    public boolean superDispatchTouchEvent(MotionEvent event) {
        return mDecor.superDispatchTouchEvent(event);
    }
```

这里使用的是

```java
public class DecorView extends FrameLayout implements RootViewSurfaceTaker, WindowCallbacks
...
```

所以这里就通过mDecor继承FrameLayout，也就是ViewGroup。即事件从activity传递到了viewgroup。

这个mDecor是getWindow.getDecorView()返回的View，而我们所设置的setContentView设置的View则是它的一个子View。换句话说，是属于我们页面的顶级View。这样就可以通过层层的分发来分发到我们写的布局中。

### ViewGroup事件分发

当从Activity分发到ViewGroup之后，事件交给了ViewGroup的**dispatchTouchEvent**来进行处理

```java
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mInputEventConsistencyVerifier != null) {
            mInputEventConsistencyVerifier.onTouchEvent(ev, 1);
        }

        if (ev.isTargetAccessibilityFocus() && isAccessibilityFocusedViewOrHost()) {
            ev.setTargetAccessibilityFocus(false);
        }

        boolean handled = false;
        if (onFilterTouchEventForSecurity(ev)) {
            final int action = ev.getAction();
            final int actionMasked = action & MotionEvent.ACTION_MASK;
            if (actionMasked == MotionEvent.ACTION_DOWN) {
                //如果当前是down事件，那么清除掉所有的之前的拦截记录信息。
                cancelAndClearTouchTargets(ev);
                resetTouchState();
            }
            //记录是否拦截
            final boolean intercepted;
            //如果是down事件，或者mFirstTouchTarget不为空，那么就进行下面的处理。
            //当有子元素处理时，mFirstTouchTarget不为空。这时候不需要进行拦截
            if (actionMasked == MotionEvent.ACTION_DOWN || mFirstTouchTarget != null) {
                //如果子控件设置了requestdisallowInterceptTouchEvent。那么不会进行拦截的判断处理，也就是不需要调用onInterceptTouchEvent的判断，直接分发交给子控件
                final boolean disallowIntercept = (mGroupFlags & FLAG_DISALLOW_INTERCEPT) != 0;
                if (!disallowIntercept) {
                    //其他的情况，调用onInterceptTouchEvent查看是否需要拦截
                    intercepted = onInterceptTouchEvent(ev);
                    //重新设置action，防止被修改
                    ev.setAction(action); // restore action in case it was changed
                } else {
                    intercepted = false;
                }
            } else {
                //如果说，事件已经初始化过了，并且没有子View被分配处理，那么就说明，这个ViewGroup已经拦截了这个事件
                intercepted = true;
            }
            //如果进行拦截，或者有子控件处理了之前的down事件，进行正常的dispatch
            if (intercepted || mFirstTouchTarget != null) {
                ev.setTargetAccessibilityFocus(false);
            }

            // Check for cancelation.
            //检测当前事件是否取消了，万一有单身20年手速快的呢。。。
            final boolean canceled = resetCancelNextUpFlag(this)|| actionMasked == MotionEvent.ACTION_CANCEL;
 
            final boolean split = (mGroupFlags & FLAG_SPLIT_MOTION_EVENTS) != 0;
            TouchTarget newTouchTarget = null;
            boolean alreadyDispatchedToNewTouchTarget = false;
            //事件不是取消，也没有进行拦截，则进行分发处理
            if (!canceled && !intercepted) {
                //获取当前有焦点的子控件
                View childWithAccessibilityFocus = ev.isTargetAccessibilityFocus()
                        ? findChildWithAccessibilityFocus() : null;
                if (actionMasked == MotionEvent.ACTION_DOWN
                       || (split && actionMasked == MotionEvent.ACTION_POINTER_DOWN)
                        || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                    final int actionIndex = ev.getActionIndex(); // always 0 for down
                    final int idBitsToAssign = split ? 1 << ev.getPointerId(actionIndex)
                            : TouchTarget.ALL_POINTER_IDS;
                    removePointersFromTouchTargets(idBitsToAssign);
                    final int childrenCount = mChildrenCount;
                    if (newTouchTarget == null && childrenCount != 0) {
                        //遍历循环事件区域在哪个控件身上
                        final float x = ev.getX(actionIndex);
                        final float y = ev.getY(actionIndex);
                        final ArrayList<View> preorderedList = buildTouchDispatchChildList();
                        //是否是自定义顺序来进行子控件的绘制，如果设置了的话，那么绘制的View的顺序和mChildren的顺序是不一样的
                        final boolean customOrder = preorderedList == null
                                && isChildrenDrawingOrderEnabled();
                        final View[] children = mChildren;
                        //倒序遍历
                        for (int i = childrenCount - 1; i >= 0; i--) {
                            //获取绘制的index
                            final int childIndex = getAndVerifyPreorderedIndex(
                                    childrenCount, i, customOrder);
                            //根据绘制的index找到对应的子控件
                            final View child = getAndVerifyPreorderedView(
                                    preorderedList, children, childIndex);
                            //如果存在拥有焦点的子控件，优先判断一下这个子控件是否可以处理事件
                            if (childWithAccessibilityFocus != null) {
                                //一直循环，直到找到child和有焦点的子控件相同。这时候，再将i初始化为childrenCount-1。
                                //然后会对这个child进行事件的分发。当下一次for循环时，相当于从头开始。
                                //从而达到了优先判断有焦点的子控件的效果
                                if (childWithAccessibilityFocus != child) {
                                    continue;
                                }
                                childWithAccessibilityFocus = null;
                                i = childrenCount - 1;
                            }
                            //canReceivePointerEvents判断是否可以接收触摸事件
                            //isTransformedTouchPointInView判断当前触摸事件是否在child的范围内
                            if (!child.canReceivePointerEvents()
                                    || !isTransformedTouchPointInView(x, y, child, null)) {
                                ev.setTargetAccessibilityFocus(false);
                                continue;
                            }
                            //getTouchTarget：查找当前子View是否在mFirstTouchTarget所在的触摸目标链表中
                            //若存在则返回这个target，否则返回null
                            //这里的判断主要用于多点触控的情况，例如手指触摸某个子View触发了ACTION_DOWN
                            //这时另一根手指也放在这个视图上触发了ACTION_POINTER_DOWN
                            //此时就需要通过在链表中查找当前子View的结果来判断两根手指触摸的是否为同一个View
                            newTouchTarget = getTouchTarget(child);
                            if (newTouchTarget != null) {
                                // Child is already receiving touch within its bounds.
                                // Give it the new pointer in addition to the ones it is handling.
                                //则将触摸点Id复制给新的TouchTarget对象，并执行break跳出遍历
                                newTouchTarget.pointerIdBits |= idBitsToAssign;
                                break;
                            }

                            resetCancelNextUpFlag(child);
                            //进行事件的分发
                            if (dispatchTransformedTouchEvent(ev, false, child, idBitsToAssign)) {
                                //子控件消费了触摸事件
                                mLastTouchDownTime = ev.getDownTime();
                                if (preorderedList != null) {
                                    // childIndex points into presorted list, find original index
                                    for (int j = 0; j < childrenCount; j++) {
                                        if (children[childIndex] == mChildren[j]) {
                                            mLastTouchDownIndex = j;
                                            break;
                                        }
                                    }
                                } else {
                                    mLastTouchDownIndex = childIndex;
                                }
                                mLastTouchDownX = ev.getX();
                                mLastTouchDownY = ev.getY();
                                //将当前子控件添加到touch链中
                                newTouchTarget = addTouchTarget(child, idBitsToAssign);
                                //设置标志位，证明当前接收到的动作事件已经分发过了，这个标志后续的判断中会用到
                                alreadyDispatchedToNewTouchTarget = true;
                                break;
                            }
                            //如果可访问的焦点没有处理当前的事件，那么清除标志，下一次就不需要先用可访问焦点做一下处理了。
                            ev.setTargetAccessibilityFocus(false);
                        }
                        if (preorderedList != null) preorderedList.clear();
                    }
                    //当mFirstTouchTarget!=null时，newTouchTarget==null
                    //则意味着没有找到新的可以消费事件的子View，那就找最近消费事件的子View来接受事件
                    if (newTouchTarget == null && mFirstTouchTarget != null) {
                        // Did not find a child to receive the event.
                        // Assign the pointer to the least recently added target.
                        newTouchTarget = mFirstTouchTarget;
                        while (newTouchTarget.next != null) {
                            newTouchTarget = newTouchTarget.next;
                        }
                        newTouchTarget.pointerIdBits |= idBitsToAssign;
                    }
                }
            }

            // Dispatch to touch targets.
            if (mFirstTouchTarget == null) {
                // 没有子控件消费事件。那么事件就会由ViewGroup调用super.dispatchTouchEvent自己处理事件
                handled = dispatchTransformedTouchEvent(ev, canceled, null,
                        TouchTarget.ALL_POINTER_IDS);
            } else {
                //多点触控的话，需要对多个子控件进行处理？mFirstTouchTarget其实是一个链，里面保存了处理事件的所有的子控件
                TouchTarget predecessor = null;
                TouchTarget target = mFirstTouchTarget;
                while (target != null) {
                    final TouchTarget next = target.next;
                    if (alreadyDispatchedToNewTouchTarget && target == newTouchTarget) {
                        //如果是已经处理过了触摸事件的View，那么直接设置 handled = true
                        handled = true;
                    } else {
                        final boolean cancelChild = resetCancelNextUpFlag(target.child)
                                || intercepted;
                        if (dispatchTransformedTouchEvent(ev, cancelChild,
                                target.child, target.pointerIdBits)) {
                            handled = true;
                        }
                        if (cancelChild) {
                            if (predecessor == null) {
                                mFirstTouchTarget = next;
                            } else {
                                predecessor.next = next;
                            }
                            target.recycle();
                            target = next;
                            continue;
                        }
                    }
                    predecessor = target;
                    target = next;
                }
            }
            //是ACTION_UP或者取消，那么恢复touch的相关状态
            if (canceled
                    || actionMasked == MotionEvent.ACTION_UP
                    || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                resetTouchState();
            } else if (split && actionMasked == MotionEvent.ACTION_POINTER_UP) {
                final int actionIndex = ev.getActionIndex();
                final int idBitsToRemove = 1 << ev.getPointerId(actionIndex);
                removePointersFromTouchTargets(idBitsToRemove);
            }
        }

        if (!handled && mInputEventConsistencyVerifier != null) {
            mInputEventConsistencyVerifier.onUnhandledEvent(ev, 1);
        }
        return handled;
    }
```

这里对通过触摸事件的区域以及遍历子控件的相应位置，进行逐个的判断。然后通过**dispatchTransformedTouchEvent**进行事件的分发。当没有子控件来处理事件的时候，则直接交给当前**ViewGroup**来进行事件的处理。

这里面有很多特殊的处理，来支持下一次触摸事件的快速处理。比如说当有子控件处理了down事件以后会将当前控件记录下来，如果下一个触摸事件不是down事件，那么就会直接交给子控件进行处理等等。

下面我们看一下**dispatchTransformedTouchEvent**做了什么工作

```java
    private boolean dispatchTransformedTouchEvent(MotionEvent event, boolean cancel,
                                                  View child, int desiredPointerIdBits) {
        	final boolean handled;
        	...
            if (child == null) {
            	//如果当前拦截或者没有子控件进行处理触摸事件，则直接交给上级
                handled = super.dispatchTouchEvent(event);
            } else {
            	//调用子控件的dispatchTouchEvent()方法
                handled = child.dispatchTouchEvent(event);
            }
            return handled;
        }
```

这里的代码进行了简化。可以看到根据当前是否进行了拦截来进行事件的分发。如果当前View没有拦截，那么就调用子控件的**dispatchTouchEvent**方法。

### View的事件分发

现在我们知道了**Activity**以及**ViewGroup**的事件分发机制。那么下一个我们来看看**View**是如何处理的

```java
public boolean dispatchTouchEvent(MotionEvent event) {
    // If the event should be handled by accessibility focus first.
    //首先判断当前事件是否能获得焦点
    if (event.isTargetAccessibilityFocus()) {
        //如果当前控件不能获取焦点，直接返回false
        if (!isAccessibilityFocusedViewOrHost()) {
            return false;
        }
        event.setTargetAccessibilityFocus(false);
    }
    //记录处理结果
    boolean result = false;
    //系统调试用
    if (mInputEventConsistencyVerifier != null) {
        mInputEventConsistencyVerifier.onTouchEvent(event, 0);
    }
    final int actionMasked = event.getActionMasked();
    if (actionMasked == MotionEvent.ACTION_DOWN) {
        //如果是down事件，那么View的滑动操作停止。
        stopNestedScroll();
    }
    //过滤掉不合法的情况，比如被遮挡等
    if (onFilterTouchEventForSecurity(event)) {
        //如果是拖动滚动条，那么直接按照true处理
        if ((mViewFlags & ENABLED_MASK) == ENABLED && handleScrollBarDragging(event)) {
            result = true;
        }
        //ListenerInfo 是view的一个内部类 里面有各种各样的listener,例如OnClickListener，
        // OnLongClickListener，OnTouchListener等等
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnTouchListener != null
                && (mViewFlags & ENABLED_MASK) == ENABLED
                && li.mOnTouchListener.onTouch(this, event)) {
            //如果调用过onTouchListener，按照True处理
            result = true;
        }
        //如果都没有进行处理，onTouchEvent进行处理。那么按照true处理
        //这里可以看到，一旦设置了onClicklistener，onTouchListener就不会再调用onTouchEvent了
        if (!result && onTouchEvent(event)) {
            result = true;
        }
    }
    if (!result && mInputEventConsistencyVerifier != null) {
        mInputEventConsistencyVerifier.onUnhandledEvent(event, 0);
    }

    //如果这是手势的结尾，则在嵌套滚动后清理。所以，滑动后，手指松开就不滚动了？
    if (actionMasked == MotionEvent.ACTION_UP ||
            actionMasked == MotionEvent.ACTION_CANCEL ||
            (actionMasked == MotionEvent.ACTION_DOWN && !result)) {
        stopNestedScroll();
    }
    return result;
}
```

View的事件处理比较简单。

1. 暂停当前控件的滚动
2. 如果设置了**onTouchListener**，那么调用**onTouchListener**方法
3. 如果没有**onTouchListener**，就调用**onTouchEvent**方法。
4. 最后将result返回。

### 总结

只有不断的总结才能不断的成长。对于事件分发机制，平时很少去接触，但是只要遇到了滑动冲突，那么就肯定会需要这部分的知识去解决。

1. 在一次事件发生过程中，Down->Move->Up属于一次触摸事件。每次Down事件触发的时候，会清空之前的一系列拦截记录、触发事件的控件记录等等。
2. 如果子控件设置了 **requestdisallowInterceptTouchEvent(true)** ，那么当前布局就不会再进行 **onInterceptTouchEvent()** 的判断，而是直接分发交给子控件。
3. 如果ViewGroup拦截了 **Down** 事件或者上一次事件没有对应的子控件处理事件，那么后面的一系列事件就不会再进行下发了，直接按照拦截处理。

其实这几点知识也引出来了对于滑动冲突的解决方案。

1. 由父控件来决定是由谁来处理滑动事件(外部拦截法)：在父控件中的 **onIntercepter** 中，对 **Move** 事件判断是当前控件，还是子控件进行处理。如果当前控件处理，则 **onIntercepter** 返回true。否则返回false。这时候父控件的Down事件必须不能拦截(对应总结的第3点)。
2. 由子控件来决定是由谁来处理滑动事件(内部拦截法)：在子控件中的 **dispatchTouchEvent** 方法中，对于 **Move** 事件，判断是由父控件还是子控件来处理。如果当前控件处理，则使用 **requestdisallowInterceptTouchEvent(true)** ，如果是由父控件处理，则使用 **requestdisallowInterceptTouchEvent(false)** 。这时候的父控件需要做一些处理。在 **onIntercepter** 方法中 **Down** 事件不拦截 (理由如总结3)，其他事件需要拦截(拦截才会进行onTouchEvent的调用)。

> 本文由 [开了肯](http://www.kailaisii.com/) 发布！ 
>
> 同步公众号[开了肯]

![image-20200404120045271](http://cdn.qiniu.kailaisii.com/typora/20200404120045-194693.png)


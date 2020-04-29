## Android启动流程源码解析（二）

在之前的源码分析中，我们最后遗留下来一个问题。那就是我们的Activity的启动是啥时候处理的啊？上万行的分析，也没看到我们想要的**onCreate**啥的。其实就是**resumeFocusedStacksTopActivities**方法，所以我们这篇文章就从这个方法开始。

### resumeFocusedStacksTopActivities

```java
   boolean resumeFocusedStacksTopActivities() {
        return resumeFocusedStacksTopActivities(null, null, null);
    }

    boolean resumeFocusedStacksTopActivities(ActivityStack targetStack, ActivityRecord target, ActivityOptions targetOptions) {
        boolean result = false;
        if (targetStack != null && (targetStack.isTopStackOnDisplay()|| getTopDisplayFocusedStack() == targetStack)) {
			//******重点方法******如果当前的activitystack正好处于屏幕的顶部，那么直接调用将target设置到顶部显示
            result = targetStack.resumeTopActivityUncheckedLocked(target, targetOptions);
        }

        for (int displayNdx = mActivityDisplays.size() - 1; displayNdx >= 0; --displayNdx) {
            //标记是否已经显示在屏幕上
            boolean resumedOnDisplay = false;
            final ActivityDisplay display = mActivityDisplays.get(displayNdx);
            for (int stackNdx = display.getChildCount() - 1; stackNdx >= 0; --stackNdx) {
                final ActivityStack stack = display.getChildAt(stackNdx);
                //获取到当前ActivityStack顶部正在运行的Activity
                final ActivityRecord topRunningActivity = stack.topRunningActivityLocked();
                if (!stack.isFocusableAndVisible() || topRunningActivity == null) {
                    continue;
                }
                if (stack == targetStack) {
                    //上面已经做过resume处理了，所以这里我们就不再做处理了
                    resumedOnDisplay |= result;
                    continue;
                }
                if (display.isTopStack(stack) && topRunningActivity.isState(RESUMED)) {
                    stack.executeAppTransition(targetOptions);
                } else {
                    resumedOnDisplay |= topRunningActivity.makeActiveIfNeeded(target);
                }
            }
            //如果仍然没有显示在屏幕上，那么就获取到屏幕当前持有焦点的ActivityStack，然后将activity显示在上面
            if (!resumedOnDisplay) {
                final ActivityStack focusedStack = display.getFocusedStack();
                if (focusedStack != null) {
                    focusedStack.resumeTopActivityUncheckedLocked(target, targetOptions);
                }
            }
        }
        return result;
    }
```

这里我们只需要关注一个方法 **resumeTopActivityUncheckedLocked** ，这个方法也特别长，我们就拆分开，只关注重点方法即可



##### pauseBackStacks遍历暂停

```java
    boolean pauseBackStacks(boolean userLeaving, ActivityRecord resuming, boolean dontWait) {
        boolean someActivityPaused = false;
        for (int stackNdx = mStacks.size() - 1; stackNdx >= 0; --stackNdx) {
            final ActivityStack stack = mStacks.get(stackNdx);
            //获取当前ActivityStack中resume状态的activity，然后startPausingLocked，调用onPause方法暂停Activity
            final ActivityRecord resumedActivity = stack.getResumedActivity();
            if (resumedActivity != null&& (stack.getVisibility(resuming) != STACK_VISIBILITY_VISIBLE|| !stack.isFocusable())) {
                someActivityPaused |= stack.startPausingLocked(userLeaving, false, resuming,dontWait);
            }
        }
        return someActivityPaused;
    }
```

这里面实际上会循环获取ActivityDisplay中的ActivityStack。去调用每个stack的startPausingLocked方法


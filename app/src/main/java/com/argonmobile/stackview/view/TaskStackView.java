package com.argonmobile.stackview.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;

import com.argonmobile.stackview.Constants;
import com.argonmobile.stackview.R;
import com.argonmobile.stackview.RecentsConfiguration;
import com.argonmobile.stackview.model.Profile;
import com.argonmobile.stackview.model.ProfileStack;
import com.argonmobile.stackview.util.Utilities;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by argon on 3/23/15.
 */
public class TaskStackView extends FrameLayout implements ViewPool.ViewPoolConsumer<TaskView, Profile>, TaskStackViewScroller.TaskStackViewScrollerCallbacks {

    LayoutInflater mInflater;
    RecentsConfiguration mConfig;
    ProfileStack mStack;

    TaskStackViewLayoutAlgorithm mLayoutAlgorithm;

    ViewPool<TaskView, Profile> mViewPool;
    ArrayList<TaskViewTransform> mCurrentTaskTransforms = new ArrayList<TaskViewTransform>();
    TaskStackViewScroller mStackScroller;
    TaskStackViewTouchHandler mTouchHandler;

    Rect mTmpRect = new Rect();

    Rect mTaskStackBounds = new Rect();

    int mStackViewsAnimationDuration;
    boolean mStartEnterAnimationRequestedAfterLayout;
    boolean mStartEnterAnimationCompleted;
    ViewAnimation.TaskViewEnterContext mStartEnterAnimationContext;

    boolean mStackViewsClipDirty = true;
    boolean mStackViewsDirty = true;
    boolean mAwaitingFirstLayout = true;

    int[] mTmpVisibleRange = new int[2];
    float[] mTmpCoord = new float[2];
    Matrix mTmpMatrix = new Matrix();

    TaskViewTransform mTmpTransform = new TaskViewTransform();
    HashMap<Profile, TaskView> mTmpTaskViewMap = new HashMap<Profile, TaskView>();

    // A convenience update listener to request updating clipping of tasks
    ValueAnimator.AnimatorUpdateListener mRequestUpdateClippingListener =
            new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    requestUpdateStackViewsClip();
                }
            };

    // A convenience runnable to return all views to the pool
    Runnable mReturnAllViewsToPoolRunnable = new Runnable() {
        @Override
        public void run() {
            int childCount = getChildCount();
            for (int i = childCount - 1; i >= 0; i--) {
                TaskView tv = (TaskView) getChildAt(i);
                mViewPool.returnViewToPool(tv);
                // Also hide the view since we don't need it anymore
                tv.setVisibility(View.INVISIBLE);
            }
        }
    };

    public TaskStackView(Context context, ProfileStack stack) {
        super(context);
        mConfig = RecentsConfiguration.getInstance();
        mStack = stack;
        //mStack.setCallbacks(this);
        mViewPool = new ViewPool<TaskView, Profile>(context, this);
        mInflater = LayoutInflater.from(context);
        mLayoutAlgorithm = new TaskStackViewLayoutAlgorithm(mConfig);
        mStackScroller = new TaskStackViewScroller(context, mConfig, mLayoutAlgorithm);
        mStackScroller.setCallbacks(this);
        mTouchHandler = new TaskStackViewTouchHandler(context, this, mConfig, mStackScroller);
    }

    public boolean isTransformedTouchPointInView(float x, float y, View child) {
        return true;
    }

    /** Requests this task stacks to start it's exit-recents animation. */
    public void startExitToHomeAnimation(ViewAnimation.TaskViewExitContext ctx) {
        // Stop any scrolling
        mStackScroller.stopScroller();
        mStackScroller.stopBoundScrollAnimation();
        // Animate all the task views out of view
        ctx.offscreenTranslationY = mLayoutAlgorithm.mViewRect.bottom -
                (mLayoutAlgorithm.mTaskRect.top - mLayoutAlgorithm.mViewRect.top);
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            TaskView tv = (TaskView) getChildAt(i);
            tv.startExitToHomeAnimation(ctx);
        }

        // Add a runnable to the post animation ref counter to clear all the views
        ctx.postAnimationTrigger.addLastDecrementRunnable(mReturnAllViewsToPoolRunnable);
    }

    /** Requests that the views clipping be updated. */
    void requestUpdateStackViewsClip() {
        if (!mStackViewsClipDirty) {
            invalidate();
            mStackViewsClipDirty = true;
        }
    }

    /** The stack insets to apply to the stack contents */
    public void setStackInsetRect(Rect r) {
        mTaskStackBounds.set(r);
    }

    /**
     * This is called with the full window width and height to allow stack view children to
     * perform the full screen transition down.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        // Compute our stack/task rects
        Rect taskStackBounds = new Rect(mTaskStackBounds);
        taskStackBounds.bottom -= mConfig.systemInsets.bottom;
        computeRects(width, height, taskStackBounds, mConfig.launchedWithAltTab,
                mConfig.launchedFromHome);

        // If this is the first layout, then scroll to the front of the stack and synchronize the
        // stack views immediately to load all the views
        if (mAwaitingFirstLayout) {
            mStackScroller.setStackScrollToInitialState();
            requestSynchronizeStackViewsWithModel();
            synchronizeStackViewsWithModel();
        }

        // Measure each of the TaskViews
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            TaskView tv = (TaskView) getChildAt(i);
            if (tv.isFullScreenView()) {
                tv.measure(widthMeasureSpec, heightMeasureSpec);
            } else {
                if (tv.getBackground() != null) {
                    tv.getBackground().getPadding(mTmpRect);
                } else {
                    mTmpRect.setEmpty();
                }
                tv.measure(
                        MeasureSpec.makeMeasureSpec(
                                mLayoutAlgorithm.mTaskRect.width() + mTmpRect.left + mTmpRect.right,
                                MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(
                                mLayoutAlgorithm.mTaskRect.height() + mTmpRect.top + mTmpRect.bottom, MeasureSpec.EXACTLY));
            }
        }

        setMeasuredDimension(width, height);
    }

    /**
     * This is called with the size of the space not including the top or right insets, or the
     * search bar height in portrait (but including the search bar width in landscape, since we want
     * to draw under it.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // Layout each of the children
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            TaskView tv = (TaskView) getChildAt(i);
            if (tv.isFullScreenView()) {
                tv.layout(left, top, left + tv.getMeasuredWidth(), top + tv.getMeasuredHeight());
            } else {
                if (tv.getBackground() != null) {
                    tv.getBackground().getPadding(mTmpRect);
                } else {
                    mTmpRect.setEmpty();
                }
                tv.layout(mLayoutAlgorithm.mTaskRect.left - mTmpRect.left,
                        mLayoutAlgorithm.mTaskRect.top - mTmpRect.top,
                        mLayoutAlgorithm.mTaskRect.right + mTmpRect.right,
                        mLayoutAlgorithm.mTaskRect.bottom + mTmpRect.bottom);
            }
        }

        if (mAwaitingFirstLayout) {
            mAwaitingFirstLayout = false;
            onFirstLayout();
        }
    }

    /** Handler for the first layout. */
    void onFirstLayout() {
        int offscreenY = mLayoutAlgorithm.mViewRect.bottom -
                (mLayoutAlgorithm.mTaskRect.top - mLayoutAlgorithm.mViewRect.top);

        // Find the launch target task
        Profile launchTargetTask = null;
        int childCount = getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            TaskView tv = (TaskView) getChildAt(i);
            Profile task = tv.getTask();
            if (task.isLaunchTarget) {
                launchTargetTask = task;
                break;
            }
        }

        // Prepare the first view for its enter animation
        for (int i = childCount - 1; i >= 0; i--) {
            TaskView tv = (TaskView) getChildAt(i);
            Profile task = tv.getTask();
            boolean occludesLaunchTarget = (launchTargetTask != null);
            tv.prepareEnterRecentsAnimation(task.isLaunchTarget, occludesLaunchTarget, offscreenY);
        }

        // If the enter animation started already and we haven't completed a layout yet, do the
        // enter animation now
        if (mStartEnterAnimationRequestedAfterLayout) {
            startEnterRecentsAnimation(mStartEnterAnimationContext);
            mStartEnterAnimationRequestedAfterLayout = false;
            mStartEnterAnimationContext = null;
        }

    }

    /** Requests this task stacks to start it's enter-recents animation */
    public void startEnterRecentsAnimation(ViewAnimation.TaskViewEnterContext ctx) {
        // If we are still waiting to layout, then just defer until then
        if (mAwaitingFirstLayout) {
            mStartEnterAnimationRequestedAfterLayout = true;
            mStartEnterAnimationContext = ctx;
            return;
        }

        if (mStack.getTaskCount() > 0) {
            // Find the launch target task
            Profile launchTargetTask = null;
            int childCount = getChildCount();
            for (int i = childCount - 1; i >= 0; i--) {
                TaskView tv = (TaskView) getChildAt(i);
                Profile task = tv.getTask();
                if (task.isLaunchTarget) {
                    launchTargetTask = task;
                    break;
                }
            }

            // Animate all the task views into view
            for (int i = childCount - 1; i >= 0; i--) {
                TaskView tv = (TaskView) getChildAt(i);
                Profile task = tv.getTask();
                ctx.currentTaskTransform = new TaskViewTransform();
                ctx.currentStackViewIndex = i;
                ctx.currentStackViewCount = childCount;
                ctx.currentTaskRect = mLayoutAlgorithm.mTaskRect;
                ctx.currentTaskOccludesLaunchTarget = (launchTargetTask != null);
//                ctx.updateListener = mRequestUpdateClippingListener;
                mLayoutAlgorithm.getStackTransform(task, mStackScroller.getStackScroll(), ctx.currentTaskTransform, null);
                tv.startEnterRecentsAnimation(ctx);
            }

            // Add a runnable to the post animation ref counter to clear all the views
            ctx.postAnimationTrigger.addLastDecrementRunnable(new Runnable() {
                @Override
                public void run() {
                    mStartEnterAnimationCompleted = true;
                }
            });
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mTouchHandler.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mTouchHandler.onTouchEvent(ev);
    }

    @Override
    public void computeScroll() {
        mStackScroller.computeScroll();
        // Synchronize the views
        synchronizeStackViewsWithModel();
        clipTaskViews();
        // Notify accessibility
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED);
    }

    /** Updates the clip for each of the task views. */
    void clipTaskViews() {
        // Update the clip on each task child
        if (Constants.DebugFlags.App.EnableTaskStackClipping) {
            int childCount = getChildCount();
            for (int i = 0; i < childCount - 1; i++) {
                TaskView tv = (TaskView) getChildAt(i);
                TaskView nextTv = null;
                TaskView tmpTv = null;
                int clipBottom = 0;
                if (tv.shouldClipViewInStack()) {
                    // Find the next view to clip against
                    int nextIndex = i;
                    while (nextIndex < getChildCount()) {
                        tmpTv = (TaskView) getChildAt(++nextIndex);
                        if (tmpTv != null && tmpTv.shouldClipViewInStack()) {
                            nextTv = tmpTv;
                            break;
                        }
                    }

                    // Clip against the next view, this is just an approximation since we are
                    // stacked and we can make assumptions about the visibility of the this
                    // task relative to the ones in front of it.
                    if (nextTv != null) {
                        // Map the top edge of next task view into the local space of the current
                        // task view to find the clip amount in local space
                        mTmpCoord[0] = mTmpCoord[1] = 0;
                        Utilities.mapCoordInDescendentToSelf(nextTv, this, mTmpCoord, false);
                        Utilities.mapCoordInSelfToDescendent(tv, this, mTmpCoord, mTmpMatrix);
                        clipBottom = (int) Math.floor(tv.getMeasuredHeight() - mTmpCoord[1]
                                - nextTv.getPaddingTop() - 1);
                    }
                }
                //tv.getViewBounds().setClipBottom(clipBottom);
            }
            if (getChildCount() > 0) {
                // The front most task should never be clipped
                TaskView tv = (TaskView) getChildAt(getChildCount() - 1);
                //tv.getViewBounds().setClipBottom(0);
            }
        }
        mStackViewsClipDirty = false;
    }


    /** Computes the stack and task rects */
    public void computeRects(int windowWidth, int windowHeight, Rect taskStackBounds,
                             boolean launchedWithAltTab, boolean launchedFromHome) {
        // Compute the rects in the stack algorithm
        mLayoutAlgorithm.computeRects(windowWidth, windowHeight, taskStackBounds);

        // Update the scroll bounds
        updateMinMaxScroll(false, launchedWithAltTab, launchedFromHome);
    }

    /** Updates the min and max virtual scroll bounds */
    void updateMinMaxScroll(boolean boundScrollToNewMinMax, boolean launchedWithAltTab,
                            boolean launchedFromHome) {
        // Compute the min and max scroll values
        mLayoutAlgorithm.computeMinMaxScroll(mStack.getTasks(), launchedWithAltTab, launchedFromHome);

        // Debug logging
        if (boundScrollToNewMinMax) {
            mStackScroller.boundScroll();
        }
    }

    /** Requests that the views be synchronized with the model */
    void requestSynchronizeStackViewsWithModel() {
        requestSynchronizeStackViewsWithModel(0);
    }
    void requestSynchronizeStackViewsWithModel(int duration) {
        if (!mStackViewsDirty) {
            invalidate();
            mStackViewsDirty = true;
        }
        if (mAwaitingFirstLayout) {
            // Skip the animation if we are awaiting first layout
            mStackViewsAnimationDuration = 0;
        } else {
            mStackViewsAnimationDuration = Math.max(mStackViewsAnimationDuration, duration);
        }
    }

    /** Synchronizes the views with the model */
    boolean synchronizeStackViewsWithModel() {
        if (mStackViewsDirty) {

            // Get all the task transforms
            ArrayList<Profile> tasks = mStack.getTasks();
            float stackScroll = mStackScroller.getStackScroll();
            int[] visibleRange = mTmpVisibleRange;
            boolean isValidVisibleRange = updateStackTransforms(mCurrentTaskTransforms, tasks,
                    stackScroll, visibleRange, false);

            // Return all the invisible children to the pool
            mTmpTaskViewMap.clear();
            int childCount = getChildCount();
            for (int i = childCount - 1; i >= 0; i--) {
                TaskView tv = (TaskView) getChildAt(i);
                Profile task = tv.getTask();
                int taskIndex = mStack.indexOfTask(task);
                if (visibleRange[1] <= taskIndex && taskIndex <= visibleRange[0]) {
                    mTmpTaskViewMap.put(task, tv);
                } else {
                    mViewPool.returnViewToPool(tv);
                }
            }

            // Pick up all the newly visible children and update all the existing children
            for (int i = visibleRange[0]; isValidVisibleRange && i >= visibleRange[1]; i--) {
                Profile task = tasks.get(i);
                TaskViewTransform transform = mCurrentTaskTransforms.get(i);
                TaskView tv = mTmpTaskViewMap.get(task);
                int taskIndex = mStack.indexOfTask(task);

                if (tv == null) {
                    tv = mViewPool.pickUpViewFromPool(task, task);

                    if (mStackViewsAnimationDuration > 0) {
                        // For items in the list, put them in start animating them from the
                        // approriate ends of the list where they are expected to appear
                        if (Float.compare(transform.p, 0f) <= 0) {
                            mLayoutAlgorithm.getStackTransform(0f, 0f, mTmpTransform, null);
                        } else {
                            mLayoutAlgorithm.getStackTransform(1f, 0f, mTmpTransform, null);
                        }
                        tv.updateViewPropertiesToTaskTransform(mTmpTransform, 0);
                    }
                }

                // Animate the task into place
                tv.updateViewPropertiesToTaskTransform(mCurrentTaskTransforms.get(taskIndex),
                        mStackViewsAnimationDuration, mRequestUpdateClippingListener);

            }

            // Reset the request-synchronize params
            mStackViewsAnimationDuration = 0;
            mStackViewsDirty = false;
            mStackViewsClipDirty = true;
            return true;
        }
        return false;
    }

    /**
     * Gets the stack transforms of a list of tasks, and returns the visible range of tasks.
     */
    private boolean updateStackTransforms(ArrayList<TaskViewTransform> taskTransforms,
                                          ArrayList<Profile> tasks,
                                          float stackScroll,
                                          int[] visibleRangeOut,
                                          boolean boundTranslationsToRect) {
        // XXX: We should be intelligent about where to look for the visible stack range using the
        //      current stack scroll.
        // XXX: We should log extra cases like the ones below where we don't expect to hit very often
        // XXX: Print out approximately how many indices we have to go through to find the first visible transform

        int taskTransformCount = taskTransforms.size();
        int taskCount = tasks.size();
        int frontMostVisibleIndex = -1;
        int backMostVisibleIndex = -1;

        // We can reuse the task transforms where possible to reduce object allocation
        if (taskTransformCount < taskCount) {
            // If there are less transforms than tasks, then add as many transforms as necessary
            for (int i = taskTransformCount; i < taskCount; i++) {
                taskTransforms.add(new TaskViewTransform());
            }
        } else if (taskTransformCount > taskCount) {
            // If there are more transforms than tasks, then just subset the transform list
            taskTransforms.subList(0, taskCount);
        }

        // Update the stack transforms
        TaskViewTransform prevTransform = null;
        for (int i = taskCount - 1; i >= 0; i--) {
            TaskViewTransform transform = mLayoutAlgorithm.getStackTransform(tasks.get(i),
                    stackScroll, taskTransforms.get(i), prevTransform);
            if (transform.visible) {
                if (frontMostVisibleIndex < 0) {
                    frontMostVisibleIndex = i;
                }
                backMostVisibleIndex = i;
            } else {
                if (backMostVisibleIndex != -1) {
                    // We've reached the end of the visible range, so going down the rest of the
                    // stack, we can just reset the transforms accordingly
                    while (i >= 0) {
                        taskTransforms.get(i).reset();
                        i--;
                    }
                    break;
                }
            }

            if (boundTranslationsToRect) {
                transform.translationY = Math.min(transform.translationY,
                        mLayoutAlgorithm.mViewRect.bottom);
            }
            prevTransform = transform;
        }
        if (visibleRangeOut != null) {
            visibleRangeOut[0] = frontMostVisibleIndex;
            visibleRangeOut[1] = backMostVisibleIndex;
        }
        return frontMostVisibleIndex != -1 && backMostVisibleIndex != -1;
    }

    /**** ViewPoolConsumer Implementation ****/

    @Override
    public TaskView createView(Context context) {
        return (TaskView) mInflater.inflate(R.layout.recent_profile_view, this, false);
    }

    @Override
    public void prepareViewToEnterPool(TaskView tv) {
        Profile task = tv.getTask();

        // Detach the view from the hierarchy
        detachViewFromParent(tv);

        // Reset the view properties
        tv.resetViewProperties();
    }

    @Override
    public void prepareViewToLeavePool(TaskView tv, Profile task, boolean isNewView) {
        // Rebind the task and request that this task's data be filled into the TaskView
        tv.onTaskBound(task);

        // Mark the launch task as fullscreen
        if (Constants.DebugFlags.App.EnableScreenshotAppTransition && mAwaitingFirstLayout) {
            if (task.isLaunchTarget) {
                tv.setIsFullScreen(true);
            }
        }

        // Sanity check, the task view should always be clipping against the stack at this point,
        // but just in case, re-enable it here
        tv.setClipViewInStack(true);

        // Find the index where this task should be placed in the stack
        int insertIndex = -1;
        int taskIndex = mStack.indexOfTask(task);
        if (taskIndex != -1) {
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                Profile tvTask = ((TaskView) getChildAt(i)).getTask();
                if (taskIndex < mStack.indexOfTask(tvTask)) {
                    insertIndex = i;
                    break;
                }
            }
        }

        // Add/attach the view to the hierarchy
        if (isNewView) {
            addView(tv, insertIndex);

            // Set the callbacks and listeners for this new view
            // tv.setTouchEnabled(true);
            // tv.setCallbacks(this);
        } else {
            attachViewToParent(tv, insertIndex, tv.getLayoutParams());
        }
    }

    @Override
    public boolean hasPreferredData(TaskView taskView, Profile preferredData) {
        return false;
    }

    @Override
    public void onScrollChanged(float p) {
        requestSynchronizeStackViewsWithModel();
        postInvalidateOnAnimation();
    }
}

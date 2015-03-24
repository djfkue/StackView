package com.argonmobile.stackview.view;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.argonmobile.stackview.Constants;
import com.argonmobile.stackview.R;
import com.argonmobile.stackview.RecentsConfiguration;
import com.argonmobile.stackview.model.Profile;
import com.argonmobile.stackview.util.Utilities;

/* The task bar view */
public class TaskViewHeader extends FrameLayout {

    RecentsConfiguration mConfig;

    ImageView mApplicationIcon;
    TextView mActivityDescription;

    RippleDrawable mBackground;
    GradientDrawable mBackgroundColorDrawable;
    int mBackgroundColor;
    Drawable mLightDismissDrawable;
    Drawable mDarkDismissDrawable;
    AnimatorSet mFocusAnimator;
    ValueAnimator backgroundColorAnimator;
    PorterDuffColorFilter mDimFilter = new PorterDuffColorFilter(0, PorterDuff.Mode.SRC_ATOP);

    boolean mIsFullscreen;
    boolean mCurrentPrimaryColorIsDark;
    int mCurrentPrimaryColor;

    static Paint sHighlightPaint;
    private Paint mDimPaint = new Paint();

    public TaskViewHeader(Context context) {
        this(context, null);
    }

    public TaskViewHeader(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskViewHeader(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mConfig = RecentsConfiguration.getInstance();
        setWillNotDraw(false);
        // Configure the highlight paint
        if (sHighlightPaint == null) {
            sHighlightPaint = new Paint();
            sHighlightPaint.setStyle(Paint.Style.STROKE);
            sHighlightPaint.setStrokeWidth(mConfig.taskViewHighlightPx);
            sHighlightPaint.setColor(mConfig.taskBarViewHighlightColor);
            sHighlightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            sHighlightPaint.setAntiAlias(true);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // We ignore taps on the task bar except on the filter and dismiss buttons
        if (!Constants.DebugFlags.App.EnableTaskBarTouchEvents) return true;

        return super.onTouchEvent(event);
    }

    @Override
    protected void onFinishInflate() {

        // Initialize the icon and description views
        mApplicationIcon = (ImageView) findViewById(R.id.application_icon);
        mActivityDescription = (TextView) findViewById(R.id.activity_description);

        // Hide the backgrounds if they are ripple drawables
//        if (!Constants.DebugFlags.App.EnableTaskFiltering) {
//            if (mApplicationIcon.getBackground() instanceof RippleDrawable) {
//                mApplicationIcon.setBackground(null);
//            }
//        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mIsFullscreen) {
            // Draw the highlight at the top edge (but put the bottom edge just out of view)
            float offset = (float) Math.ceil(mConfig.taskViewHighlightPx / 2f);
            float radius = mConfig.taskViewRoundedCornerRadiusPx;
            int count = canvas.save(Canvas.CLIP_SAVE_FLAG);
            canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRect(-offset, 0f, (float) getMeasuredWidth() + offset,
                    getMeasuredHeight() + radius, sHighlightPaint);
            canvas.restoreToCount(count);
        }
    }

    /** Sets whether the current task is full screen or not. */
    void setIsFullscreen(boolean isFullscreen) {
        mIsFullscreen = isFullscreen;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** Returns the secondary color for a primary color. */
    int getSecondaryColor(int primaryColor, boolean useLightOverlayColor) {
        int overlayColor = useLightOverlayColor ? Color.WHITE : Color.BLACK;
        return Utilities.getColorWithOverlay(primaryColor, overlayColor, 0.8f);
    }

    /** Binds the bar view to the task */
    public void rebindToTask(Profile t) {

        if (!mActivityDescription.getText().toString().equals(t.activityLabel)) {
            mActivityDescription.setText(t.activityLabel);
        }
    }

    /** Unbinds the bar view from the task */
    void unbindFromTask() {
        mApplicationIcon.setImageDrawable(null);
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {

        // Don't forward our state to the drawable - we do it manually in onTaskViewFocusChanged.
        // This is to prevent layer trashing when the view is pressed.
        return new int[] {};
    }

    public void setDimAlpha(int alpha) {
        int color = Color.argb(alpha, 0, 0, 0);
        mDimFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        mDimPaint.setColorFilter(mDimFilter);
        setLayerType(LAYER_TYPE_HARDWARE, mDimPaint);
    }
}

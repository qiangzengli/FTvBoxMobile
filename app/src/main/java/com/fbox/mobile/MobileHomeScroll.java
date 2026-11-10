package com.fbox.mobile;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;

/** A collapsing phone header whose motion never changes the list's measured viewport. */
public final class MobileHomeScroll extends ViewGroup implements NestedScrollingParent3 {
    private final NestedScrollingParentHelper nested = new NestedScrollingParentHelper(this);
    private final LinearLayout header;
    private final FrameLayout categories;
    private final View pager;
    private final int categoryHeight;
    private final int actionsHeight;
    private View actions;
    private View scrollingTarget;
    private int offset;
    private boolean homePage = true;

    public MobileHomeScroll(Context context, View categoryList, View pager) {
        super(context);
        this.pager = pager;
        categoryHeight = MobileSheets.dp(context, 56);
        actionsHeight = MobileSheets.dp(context, 84);
        setClipChildren(true);
        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        categories = new FrameLayout(context);
        categories.addView(categoryList, new FrameLayout.LayoutParams(-1, categoryHeight));
        header.addView(categories, new LinearLayout.LayoutParams(-1, categoryHeight));
        addView(header, new LayoutParams(-1, -2));
        addView(pager, new LayoutParams(-1, -1));
    }

    public void bindActions(View clip) {
        if (clip == null || actions == clip) return;
        if (actions != null) header.removeView(actions);
        if (clip.getParent() instanceof ViewGroup) ((ViewGroup) clip.getParent()).removeView(clip);
        actions = clip;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, actionsHeight);
        params.leftMargin = params.rightMargin = MobileSheets.dp(getContext(), 8);
        header.addView(actions, params);
        actions.setVisibility(homePage ? VISIBLE : GONE);
        requestLayout();
    }

    public void selectPage(int position) {
        if (scrollingTarget instanceof androidx.recyclerview.widget.RecyclerView) {
            ((androidx.recyclerview.widget.RecyclerView) scrollingTarget).stopScroll();
        }
        scrollingTarget = null;
        homePage = position == 0;
        if (actions != null) actions.setVisibility(homePage ? VISIBLE : GONE);
        expand();
        requestLayout();
    }

    public void expand() { offset = 0; applyOffset(); }

    private int range() { return categoryHeight + (homePage && actions != null ? actionsHeight : 0); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        header.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(range(), MeasureSpec.EXACTLY));
        // The list always measures against the fully expanded content viewport. Its lower edge
        // is clipped while the header is visible, just like a scrolling app bar.
        pager.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        offset = Math.max(0, Math.min(range(), offset));
        header.layout(0, -offset, getWidth(), range() - offset);
        pager.layout(0, range() - offset, getWidth(), range() - offset + getHeight());
        updateAccessibility();
    }

    private int move(int dy) {
        int previous = offset;
        offset = Math.max(0, Math.min(range(), offset + dy));
        if (offset != previous) applyOffset();
        return offset - previous;
    }

    private void applyOffset() {
        // Offset both siblings together. Resizing two nested containers here causes RecyclerView
        // relayout and changes its touch coordinates on every frame, which visibly flickers.
        ViewCompat.offsetTopAndBottom(header, -offset - header.getTop());
        ViewCompat.offsetTopAndBottom(pager, range() - offset - pager.getTop());
        updateAccessibility();
    }

    private void updateAccessibility() {
        categories.setImportantForAccessibility(offset >= categoryHeight
                ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if (actions != null) actions.setImportantForAccessibility(!homePage || offset >= range()
                ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    @Override public boolean requestChildRectangleOnScreen(View child, android.graphics.Rect rectangle, boolean immediate) {
        android.graphics.Rect visible = new android.graphics.Rect(rectangle);
        visible.offset(child.getLeft(), child.getTop());
        if (child == pager && visible.bottom > getHeight() && offset < range()) {
            move(range() - offset);
            return true;
        }
        return super.requestChildRectangleOnScreen(child, rectangle, immediate);
    }

    @Override public boolean onStartNestedScroll(View child, View target, int axes, int type) {
        return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }
    @Override public void onNestedScrollAccepted(View child, View target, int axes, int type) {
        scrollingTarget = target;
        nested.onNestedScrollAccepted(child, target, axes, type);
    }
    @Override public void onStopNestedScroll(View target, int type) { nested.onStopNestedScroll(target, type); }
    @Override public void onNestedPreScroll(View target, int dx, int dy, int[] consumed, int type) {
        consumed[1] += move(dy);
    }
    @Override public void onNestedScroll(View target, int dx, int dy, int ux, int uy, int type, int[] consumed) {
        consumed[1] += move(uy);
    }
    @Override public void onNestedScroll(View target, int dx, int dy, int ux, int uy, int type) { move(uy); }
    @Override public int getNestedScrollAxes() { return nested.getNestedScrollAxes(); }
    @Override public boolean onNestedPreFling(View target, float vx, float vy) { return false; }
    @Override public boolean onNestedFling(View target, float vx, float vy, boolean consumed) { return false; }
    @Override public boolean onStartNestedScroll(View child, View target, int axes) { return onStartNestedScroll(child, target, axes, ViewCompat.TYPE_TOUCH); }
    @Override public void onNestedScrollAccepted(View child, View target, int axes) { onNestedScrollAccepted(child, target, axes, ViewCompat.TYPE_TOUCH); }
    @Override public void onStopNestedScroll(View target) { onStopNestedScroll(target, ViewCompat.TYPE_TOUCH); }
    @Override public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) { onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH); }
    @Override public void onNestedScroll(View target, int dx, int dy, int ux, int uy) { onNestedScroll(target, dx, dy, ux, uy, ViewCompat.TYPE_TOUCH); }
}

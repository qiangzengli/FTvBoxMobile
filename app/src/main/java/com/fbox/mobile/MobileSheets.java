package com.fbox.mobile;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import androidx.core.widget.NestedScrollView;
import android.widget.TextView;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.dialog.BaseDialog;

/** A bounded, scrollable phone sheet shared by every native feature dialog. */
public final class MobileSheets {
    private MobileSheets() {}
    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    public static void install(BaseDialog dialog) {
        Context context = dialog.getContext();
        Window window = dialog.getWindow();
        ViewGroup host = dialog.findViewById(android.R.id.content);
        if (host == null || host.getChildCount() == 0) return;
        View body = host.getChildAt(0);
        // Resolve and hide the original title while the body still belongs to the dialog.
        CharSequence sheetTitle = dialog.getSheetTitle();
        android.widget.EditText input = body.findViewById(R.id.input);
        View submit = body.findViewById(R.id.inputSubmit);
        if (input != null && submit != null && body.findViewById(R.id.mobileApiActions) == null) {
            input.setOnEditorActionListener((view, action, event) -> {
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                        || action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                        || action == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                    hideKeyboard(input);
                    submit.performClick();
                    return true;
                }
                return false;
            });
        }
        View footer = body.findViewById(R.id.mobileApiActions);
        if (footer != null) ((ViewGroup) footer.getParent()).removeView(footer);
        host.removeView(body);
        LinearLayout sheet = new LinearLayout(context) {
            @Override protected void onMeasure(int width, int height) {
                int available = (int) (getResources().getDisplayMetrics().heightPixels * .85f);
                if (MeasureSpec.getMode(height) != MeasureSpec.UNSPECIFIED) {
                    available = Math.min(available, MeasureSpec.getSize(height));
                }
                super.onMeasure(width, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST));
            }
        };
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 16));
        sheet.setBackgroundResource(R.drawable.mobile_sheet);
        android.widget.FrameLayout handleArea = new android.widget.FrameLayout(context);
        handleArea.setContentDescription("下滑关闭弹窗");
        View handle = new View(context);
        android.graphics.drawable.GradientDrawable handleShape = new android.graphics.drawable.GradientDrawable();
        handleShape.setColor(0xFF687080); handleShape.setCornerRadius(dp(context, 2));
        handle.setBackground(handleShape);
        handleArea.addView(handle, new android.widget.FrameLayout.LayoutParams(dp(context, 36), dp(context, 4), Gravity.CENTER));
        sheet.addView(handleArea, new LinearLayout.LayoutParams(-1, dp(context, 20)));
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText(sheetTitle); title.setTextSize(18); title.setTextColor(Color.WHITE);
        title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = new TextView(context);
        close.setText("关闭"); close.setTextSize(14); close.setTextColor(0xFFAEC6FF);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭弹窗");
        close.setBackgroundResource(R.drawable.mobile_surface);
        close.setOnClickListener(v -> dialog.cancel());
        header.addView(close, new LinearLayout.LayoutParams(dp(context, 56), dp(context, 48)));
        sheet.addView(header, new LinearLayout.LayoutParams(-1, -2));
        installDragDismiss(handleArea, sheet, dialog);
        installDragDismiss(header, sheet, dialog);
        NestedScrollView scroll = new NestedScrollView(context);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        body.setBackgroundColor(Color.TRANSPARENT);
        scroll.addView(body, new NestedScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2, 1);
        bodyParams.topMargin = dp(context, 12);
        sheet.addView(scroll, bodyParams);
        if (footer != null) {
            LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
            footerParams.topMargin = dp(context, 12);
            sheet.addView(footer, footerParams);
        }
        host.addView(sheet, new ViewGroup.LayoutParams(-1, -2));
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.dimAmount = .48f;
        window.setAttributes(attrs);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.setCanceledOnTouchOutside(true);
    }

    private static void installDragDismiss(View touchArea, View sheet, BaseDialog dialog) {
        touchArea.setOnTouchListener(new View.OnTouchListener() {
            float startY;
            @Override public boolean onTouch(View view, android.view.MotionEvent event) {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    startY = event.getRawY(); return true;
                }
                float distance = Math.max(0, event.getRawY() - startY);
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                    sheet.setTranslationY(distance); return true;
                }
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                    if (distance >= dp(view.getContext(), 64)) dialog.cancel();
                    else sheet.animate().translationY(0).setDuration(150).start();
                    return true;
                }
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) sheet.setTranslationY(0);
                return true;
            }
        });
    }

    public static TextView action(Context context, String label) {
        TextView button = new TextView(context);
        button.setText(label); button.setTextColor(Color.WHITE); button.setTextSize(14);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        button.setMinHeight(dp(context, 48));
        button.setBackgroundResource(R.drawable.mobile_surface);
        return button;
    }

    public static void hideKeyboard(View input) {
        android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager)
                input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }
}

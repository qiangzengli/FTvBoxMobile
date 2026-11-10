package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import xyz.doikki.videoplayer.util.CutoutUtil;

public class BaseDialog extends Dialog {
    private boolean phoneSheetInstalled;
    private String sheetLayout = "";
    private CharSequence sheetTitle;

    @Override public void setContentView(int layoutResID) {
        sheetLayout = getContext().getResources().getResourceEntryName(layoutResID);
        super.setContentView(layoutResID);
    }

    @Override public void setTitle(CharSequence title) {
        sheetTitle = title;
        super.setTitle(title);
    }

    public CharSequence getSheetTitle() {
        if (sheetTitle != null) return sheetTitle;
        for (int id : new int[]{R.id.title, R.id.episode_title}) {
            android.widget.TextView label = findViewById(id);
            if (label != null && label.getText().length() > 0) {
                label.setVisibility(View.GONE);
                return label.getText();
            }
        }
        switch (sheetLayout) {
            case "dialog_api": return "订阅配置";
            case "dialog_api_history": return "配置历史";
            case "dialog_backup": return "备份与恢复";
            case "dialog_cast": return "选择投屏设备";
            case "dialog_checkbox_search": return "搜索范围";
            case "dialog_confirm": return "清空记录";
            case "dialog_danmu_api": return "弹幕地址";
            case "dialog_danmu_setting": return "弹幕设置";
            case "dialog_desc": return "影片资料";
            case "dialog_episode": return "选集";
            case "dialog_grid_filter": return "筛选";
            case "dialog_live_password": return "频道密码";
            case "dialog_quick_search": return "快速搜索";
            case "dialog_remote": return "远程连接";
            case "dialog_search_danmu": return "搜索弹幕";
            case "dialog_search_remotetv": return "搜索设备";
            case "dialog_search_subtitle": return "搜索字幕";
            case "dialog_subtitle": return "字幕设置";
            case "dialog_about": return "关于 FBox";
            case "dialog_xwalk": return "安装播放组件";
            case "dialog_tip": return "提示";
            default: return "请选择";
        }
    }
    public BaseDialog(@NonNull Context context) {
        super(context, R.style.CustomDialogStyle);
    }

    public BaseDialog(Context context, int customDialogStyle) {
        super(context, customDialogStyle);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        CutoutUtil.adaptCutoutAboveAndroidP(this, true);//设置刘海
        super.onCreate(savedInstanceState);
    }

    @Override
    public void show() {
        if (isContextInvalid()) {
            return;
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        super.show();
        hideSysBar();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        if (!phoneSheetInstalled) {
            com.fbox.mobile.MobileSheets.install(this);
            phoneSheetInstalled = true;
        }
        android.util.DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        boolean portrait = metrics.heightPixels > metrics.widthPixels;
        getWindow().setGravity(portrait ? android.view.Gravity.BOTTOM : android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
        getWindow().setLayout(portrait ? metrics.widthPixels : Math.min(metrics.widthPixels, com.fbox.mobile.MobileSheets.dp(getContext(), 420)), WindowManager.LayoutParams.WRAP_CONTENT);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private boolean isContextInvalid() {
        Context context = getContext();
        if (!(context instanceof Activity)) {
            return false;
        }
        Activity activity = (Activity) context;
        return activity.isFinishing()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed());
    }

    private void hideSysBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        }
    }
}

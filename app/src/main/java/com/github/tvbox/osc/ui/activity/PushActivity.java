package com.github.tvbox.osc.ui.activity;

import android.content.ClipboardManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.Toast;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.receiver.PushReceiver;

/** Phone entry point for direct links and links handled by the subscription's push source. */
public class PushActivity extends BaseActivity {
    @Override protected int getLayoutResID() { return R.layout.activity_push; }
    @Override protected void init() {
        EditText input = findViewById(R.id.mobileLinkInput);
        findViewById(R.id.pushLocal).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null) {
                Toast.makeText(this, "剪贴板中没有内容", Toast.LENGTH_SHORT).show();
                return;
            }
            CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
            input.setText(value == null ? "" : value.toString().trim());
            input.setSelection(input.length());
        });
        findViewById(R.id.mobilePlayLink).setOnClickListener(v -> play(input));
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_GO) { play(input); return true; }
            return false;
        });
    }
    private void play(EditText input) {
        String url = input.getText().toString().trim();
        if (url.isEmpty()) { input.setError("请输入播放链接"); return; }
        PushReceiver.send(this, url);
    }
}

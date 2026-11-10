package com.fbox.mobile;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.dialog.BaseDialog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Keeps common playback actions in reach; advanced controls open in a phone sheet. */
public final class MobilePlayerControls {
    private MobilePlayerControls() {}
    public static void install(View root) {
        LinearLayout group = root.findViewById(R.id.play_btn_group);
        if (group == null) return;
        List<Integer> primary = Arrays.asList(R.id.play_label, R.id.play_pre, R.id.play_next, R.id.play_speed);
        List<TextView> advanced = new ArrayList<>();
        LinearLayout hidden = new LinearLayout(root.getContext());
        hidden.setVisibility(View.GONE);
        ((android.view.ViewGroup) root).addView(hidden);
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            if (child instanceof TextView && !primary.contains(child.getId())) {
                group.removeViewAt(i); hidden.addView(child); advanced.add(0, (TextView) child);
            }
        }
        TextView episodes = MobileSheets.action(root.getContext(), "选集");
        episodes.setOnClickListener(v -> root.findViewById(R.id.play_next).performLongClick());
        group.addView(episodes);
        TextView more = MobileSheets.action(root.getContext(), "更多");
        more.setContentDescription("播放设置、字幕、弹幕和投屏");
        group.addView(more);
        more.setOnClickListener(v -> {
            BaseDialog dialog = new BaseDialog(root.getContext());
            dialog.setTitle("播放设置");
            LinearLayout list = new LinearLayout(root.getContext()); list.setOrientation(LinearLayout.VERTICAL);
            for (TextView source : advanced) {
                if (source.getVisibility() != View.VISIBLE) continue;
                LinearLayout row = new LinearLayout(root.getContext());
                TextView action = MobileSheets.action(root.getContext(), source.getText().toString());
                action.setOnClickListener(clicked -> { dialog.dismiss(); source.performClick(); });
                row.addView(action, new LinearLayout.LayoutParams(0, -2, 1));
                if (source.isLongClickable()) {
                    TextView options = MobileSheets.action(root.getContext(), "选项");
                    options.setContentDescription(source.getText() + "更多选项");
                    options.setOnClickListener(clicked -> { dialog.dismiss(); source.performLongClick(); });
                    row.addView(options);
                }
                list.addView(row);
            }
            dialog.setContentView(list); dialog.show();
        });
    }
}

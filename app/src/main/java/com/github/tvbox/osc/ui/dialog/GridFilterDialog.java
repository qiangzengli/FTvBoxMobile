package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.ui.adapter.GridFilterKVAdapter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class GridFilterDialog extends BaseDialog {
    public LinearLayout filterRoot;

    public GridFilterDialog(@NonNull @NotNull Context context) {
        super(context);
        setCanceledOnTouchOutside(false);
        setCancelable(true);
        setContentView(R.layout.dialog_grid_filter);
        filterRoot = findViewById(R.id.filterRoot);


    }

    public interface Callback {
        void change();
    }

    public void setOnDismiss(Callback callback) {
        setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                if (selectChange) {
                    callback.change();
                }
            }
        });
    }

    private Callback onChanged;

    public void setOnChanged(Callback callback) { onChanged = callback; }

    public void setData(MovieSort.SortData sortData) {
        filterRoot.removeAllViews();
        for (MovieSort.SortFilter filter : sortData.filters) {
            View line = LayoutInflater.from(getContext()).inflate(R.layout.item_grid_filter, filterRoot, false);
            ((TextView) line.findViewById(R.id.filterName)).setText(filter.name);
            TvRecyclerView gridView = line.findViewById(R.id.mFilterKv);
            float widthDp = getContext().getResources().getDisplayMetrics().widthPixels
                    / getContext().getResources().getDisplayMetrics().density;
            float fontScale = getContext().getResources().getConfiguration().fontScale;
            int columns = Math.max(1, Math.min(3, (int) (widthDp / (112 * Math.max(1, fontScale)))));
            gridView.setHasFixedSize(false);
            gridView.setNestedScrollingEnabled(false);
            gridView.setLayoutManager(new com.owen.tvrecyclerview.widget.V7GridLayoutManager(getContext(), columns));
            GridFilterKVAdapter adapter = new GridFilterKVAdapter();
            ArrayList<String> labels = new ArrayList<>(filter.values.keySet());
            ArrayList<String> values = new ArrayList<>(filter.values.values());
            adapter.setSelectedPosition(values.indexOf(sortData.filterSelect.get(filter.key)));
            gridView.setAdapter(adapter);
            adapter.setOnItemClickListener((ignored, view, position) -> {
                String value = values.get(position);
                boolean deselect = value.equals(sortData.filterSelect.get(filter.key));
                if (deselect) sortData.filterSelect.remove(filter.key);
                else sortData.filterSelect.put(filter.key, value);
                adapter.setSelectedPosition(deselect ? -1 : position);
                selectChange = true;
                if (onChanged != null) onChanged.change();
            });
            adapter.setNewData(labels);
            filterRoot.addView(line);
        }
    }

    private boolean selectChange = false;

    public void show() {
        selectChange = false;
        super.show();
    }

}

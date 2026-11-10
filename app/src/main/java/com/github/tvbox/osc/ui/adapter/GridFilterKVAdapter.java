package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.util.ArrayList;

public class GridFilterKVAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    private int selectedPosition = -1;

    public void setSelectedPosition(int position) {
        selectedPosition = position;
        notifyDataSetChanged();
    }
    public GridFilterKVAdapter() {
        super(R.layout.item_grid_filter_value, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.filterValue, item);
        boolean selected = helper.getLayoutPosition() == selectedPosition;
        android.widget.TextView label = helper.getView(R.id.filterValue);
        label.setSelected(selected);
        label.getPaint().setFakeBoldText(selected);
        label.setTextColor(selected ? 0xFF02F8E1 : android.graphics.Color.WHITE);
    }
}

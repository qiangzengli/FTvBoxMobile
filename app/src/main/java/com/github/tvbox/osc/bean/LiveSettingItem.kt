package com.github.tvbox.osc.bean

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
class LiveSettingItem {
    private var itemIndex: Int = 0
    private var itemName: String? = null
    private var itemSelected: Boolean = false

    fun getItemIndex(): Int {
        return itemIndex
    }

    fun setItemIndex(itemIndex: Int) {
        this.itemIndex = itemIndex
    }

    fun getItemName(): String? {
        return itemName
    }

    fun setItemName(itemName: String?) {
        this.itemName = itemName
    }

    fun isItemSelected(): Boolean {
        return itemSelected
    }

    fun setItemSelected(itemSelected: Boolean) {
        this.itemSelected = itemSelected
    }
}

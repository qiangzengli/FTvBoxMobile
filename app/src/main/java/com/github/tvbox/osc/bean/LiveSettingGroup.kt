package com.github.tvbox.osc.bean

import java.util.ArrayList

class LiveSettingGroup {
    private var groupIndex: Int = 0
    private var groupName: String? = null
    private var liveSettingItems: ArrayList<LiveSettingItem>? = null

    fun getGroupIndex(): Int {
        return groupIndex
    }

    fun setGroupIndex(groupIndex: Int) {
        this.groupIndex = groupIndex
    }

    fun getGroupName(): String? {
        return groupName
    }

    fun setGroupName(groupName: String?) {
        this.groupName = groupName
    }

    fun getLiveSettingItems(): ArrayList<LiveSettingItem>? {
        return liveSettingItems
    }

    fun setLiveSettingItems(liveSettingItems: ArrayList<LiveSettingItem>?) {
        this.liveSettingItems = liveSettingItems
    }
}

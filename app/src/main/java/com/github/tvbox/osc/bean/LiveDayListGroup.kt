package com.github.tvbox.osc.bean

class LiveDayListGroup {
    private var groupIndex: Int = 0
    private var groupName: String? = null

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
}

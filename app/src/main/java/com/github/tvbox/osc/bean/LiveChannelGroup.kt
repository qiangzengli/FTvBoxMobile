package com.github.tvbox.osc.bean

import java.util.ArrayList

class LiveChannelGroup {
    /**
     * groupIndex : 分组索引号
     * groupName : 分组名称
     * password : 分组密码
     */
    private var groupIndex: Int = 0
    private var groupName: String? = null
    private var groupPassword: String? = null
    private var liveChannelItems: ArrayList<LiveChannelItem>? = null

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

    fun getLiveChannels(): ArrayList<LiveChannelItem>? {
        return liveChannelItems
    }

    fun setLiveChannels(liveChannelItems: ArrayList<LiveChannelItem>?) {
        this.liveChannelItems = liveChannelItems
    }

    fun getGroupPassword(): String? {
        return groupPassword
    }

    fun setGroupPassword(groupPassword: String?) {
        this.groupPassword = groupPassword
    }
}

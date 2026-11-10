package com.github.tvbox.osc.event

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
class TopStateEvent(
    @JvmField
    var type: Int
) {
    companion object {
        const val TYPE_TOP = 0
    }
}

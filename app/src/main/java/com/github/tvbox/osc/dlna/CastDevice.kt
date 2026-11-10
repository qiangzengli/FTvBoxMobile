package com.github.tvbox.osc.dlna

import android.text.TextUtils
import org.fourthline.cling.model.meta.RemoteDevice

class CastDevice(
    val type: Int,
    val id: String,
    val name: String
) {
    companion object {
        const val TYPE_TVBOX = 1
        const val TYPE_DLNA = 2

        @JvmStatic
        fun tvbox(host: String): CastDevice {
            return CastDevice(TYPE_TVBOX, host, "TVBox $host")
        }

        @JvmStatic
        fun dlna(device: RemoteDevice): CastDevice {
            val details = device.details
            val name = if (details == null) "DLNA" else details.friendlyName
            val uuid = device.identity.udn.identifierString
            return CastDevice(TYPE_DLNA, uuid, if (TextUtils.isEmpty(name)) "DLNA" else name)
        }
    }

    val displayName: String
        get() = (if (type == TYPE_DLNA) "DLNA  " else "TVBox  ") + name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CastDevice) return false
        return type == other.type && TextUtils.equals(id, other.id)
    }

    override fun hashCode(): Int {
        return (type * 31) + id.hashCode()
    }
}

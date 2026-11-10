package com.github.tvbox.osc.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
@Entity(tableName = "cache")
class Cache : Serializable {
    @PrimaryKey(autoGenerate = false)
    @JvmField
    var key: String = ""

    @JvmField
    var data: ByteArray? = null
}

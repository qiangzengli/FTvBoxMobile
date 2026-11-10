package com.github.tvbox.osc.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

/**
 * @author pj567
 * @date :2021/1/7
 * @description:
 */
@Entity(tableName = "vodRecord")
class VodRecord : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

    @JvmField
    @ColumnInfo(name = "vodId")
    var vodId: String? = null

    @JvmField
    @ColumnInfo(name = "updateTime")
    var updateTime: Long = 0

    @JvmField
    @ColumnInfo(name = "sourceKey")
    var sourceKey: String? = null

    @JvmField
    var dataJson: String? = null
}

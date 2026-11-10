package com.github.tvbox.osc.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "vodCollect")
class VodCollect : Serializable {
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
    @ColumnInfo(name = "name")
    var name: String? = null

    @JvmField
    @ColumnInfo(name = "pic")
    var pic: String? = null
}

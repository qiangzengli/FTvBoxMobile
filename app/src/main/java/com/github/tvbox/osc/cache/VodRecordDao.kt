package com.github.tvbox.osc.cache

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * @author pj567
 * @date :2021/1/7
 * @description:
 */
@Dao
interface VodRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(record: VodRecord): Long

    @Query("select * from vodRecord order by updateTime desc, id desc limit :size")
    fun getAll(size: Int): List<VodRecord>

    @Query("select * from vodRecord where `sourceKey`=:sourceKey and `vodId`=:vodId")
    fun getVodRecord(sourceKey: String?, vodId: String?): VodRecord?

    @Delete
    fun delete(record: VodRecord): Int

    @Query("select count(*) from vodRecord")
    fun getCount(): Int

    @Query("DELETE FROM vodRecord")
    fun deleteAll()

    /**
     * 保留最新指定条数, 其他删除.
     * @param size 保留条数
     * @return
     */
    @Query("DELETE FROM vodRecord where id NOT IN (SELECT id FROM vodRecord ORDER BY updateTime desc, id desc LIMIT :size)")
    fun reserver(size: Int): Int
}

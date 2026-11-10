package com.github.tvbox.osc.cache

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(cache: Cache): Long

    @Query("select *from cache where `key`=:key")
    fun getCache(key: String): Cache?

    @Delete
    fun delete(cache: Cache): Int

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(cache: Cache): Int
}

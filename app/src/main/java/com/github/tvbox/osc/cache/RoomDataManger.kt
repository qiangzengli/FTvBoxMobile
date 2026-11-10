package com.github.tvbox.osc.cache

import android.text.TextUtils
import com.github.tvbox.osc.bean.VodInfo
import com.github.tvbox.osc.data.AppDataManager
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.orhanobut.hawk.Hawk
import java.util.ArrayList
import java.util.HashSet

/**
 * @author pj567
 * @date :2021/1/7
 * @description:
 */
object RoomDataManger {
    private val vodInfoStrategy = object : ExclusionStrategy {
        override fun shouldSkipField(field: FieldAttributes): Boolean {
            if (field.declaringClass == VodInfo::class.java && field.name == "seriesFlags") {
                return true
            }
            if (field.declaringClass == VodInfo::class.java && field.name == "seriesMap") {
                return true
            }
            return false
        }

        override fun shouldSkipClass(clazz: Class<*>?): Boolean {
            return false
        }
    }

    private fun getVodInfoGson(): Gson {
        return GsonBuilder().addSerializationExclusionStrategy(vodInfoStrategy).create()
    }

    @JvmStatic
    fun insertVodRecord(sourceKey: String?, vodInfo: VodInfo) {
        val dao = AppDataManager.get().getVodRecordDao()
        if (Hawk.get(HawkConfig.HISTORY_MERGE, false)) {
            removeSameNameVodRecords(dao, sourceKey, vodInfo)
        }
        var record = dao.getVodRecord(sourceKey, vodInfo.id)
        if (record == null) {
            record = VodRecord()
        }
        record.sourceKey = sourceKey
        record.vodId = vodInfo.id
        record.updateTime = System.currentTimeMillis()
        record.dataJson = getVodInfoGson().toJson(vodInfo)
        dao.insert(record)
    }

    @JvmStatic
    fun getVodInfo(sourceKey: String?, vodId: String?): VodInfo? {
        val record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodId)
        try {
            if (record != null && !record.dataJson.isNullOrEmpty()) {
                val vodInfo: VodInfo = getVodInfoGson().fromJson(
                    record.dataJson,
                    object : TypeToken<VodInfo>() {}.type
                )
                if (vodInfo.name == null) return null
                return vodInfo
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    @JvmStatic
    fun deleteVodRecord(sourceKey: String?, vodInfo: VodInfo) {
        val record = AppDataManager.get().getVodRecordDao().getVodRecord(sourceKey, vodInfo.id)
        if (record != null) {
            AppDataManager.get().getVodRecordDao().delete(record)
        }
    }

    @JvmStatic
    fun getAllVodRecord(limit: Int): List<VodInfo> {
        val dao = AppDataManager.get().getVodRecordDao()
        val index = Hawk.get(HawkConfig.HISTORY_NUM, 0)
        val hisNum = HistoryHelper.getHisNum(index)
        val recordList = dao.getAll(Int.MAX_VALUE)
        val vodInfoList = ArrayList<VodInfo>()
        val historyMerge = Hawk.get(HawkConfig.HISTORY_MERGE, false)
        val historyNames = if (historyMerge) HashSet<String>() else null
        if (recordList != null) {
            for (record in recordList) {
                var info: VodInfo? = null
                try {
                    if (!record.dataJson.isNullOrEmpty()) {
                        info = getVodInfoGson().fromJson(
                            record.dataJson,
                            object : TypeToken<VodInfo>() {}.type
                        )
                        info?.sourceKey = record.sourceKey
                        if (info?.name == null) info = null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val name = getVodRecordName(info)
                if (info != null) {
                    if (!historyMerge || TextUtils.isEmpty(name) || historyNames!!.add(name)) {
                        vodInfoList.add(info)
                    }
                }
            }
        }
        if (dao.getCount() > hisNum) {
            dao.reserver(hisNum)
        }
        val size = minOf(vodInfoList.size, minOf(limit, hisNum))
        return ArrayList(vodInfoList.subList(0, size))
    }

    private fun removeSameNameVodRecords(dao: VodRecordDao, sourceKey: String?, vodInfo: VodInfo) {
        val name = getVodRecordName(vodInfo)
        if (TextUtils.isEmpty(name)) return
        for (record in dao.getAll(Int.MAX_VALUE)) {
            if (TextUtils.equals(sourceKey, record.sourceKey) && TextUtils.equals(vodInfo.id, record.vodId)) {
                continue
            }
            try {
                val history: VodInfo = getVodInfoGson().fromJson(
                    record.dataJson,
                    object : TypeToken<VodInfo>() {}.type
                )
                if (TextUtils.equals(name, getVodRecordName(history))) {
                    dao.delete(record)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun getVodRecordName(vodInfo: VodInfo?): String {
        val n = vodInfo?.name
        return if (n.isNullOrEmpty()) "" else n.trim()
    }

    @JvmStatic
    fun insertVodCollect(sourceKey: String?, vodInfo: VodInfo) {
        val record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id)
        if (record != null) {
            return
        }
        val newRecord = VodCollect()
        newRecord.sourceKey = sourceKey
        newRecord.vodId = vodInfo.id
        newRecord.updateTime = System.currentTimeMillis()
        newRecord.name = vodInfo.name
        newRecord.pic = vodInfo.pic
        AppDataManager.get().getVodCollectDao().insert(newRecord)
    }

    @JvmStatic
    fun deleteVodCollect(id: Int) {
        AppDataManager.get().getVodCollectDao().delete(id)
    }

    @JvmStatic
    fun deleteVodCollect(sourceKey: String?, vodInfo: VodInfo) {
        val record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodInfo.id)
        if (record != null) {
            AppDataManager.get().getVodCollectDao().delete(record)
        }
    }

    @JvmStatic
    fun deleteVodCollectAll() {
        AppDataManager.get().getVodCollectDao().deleteAll()
    }

    @JvmStatic
    fun deleteVodRecordAll() {
        AppDataManager.get().getVodRecordDao().deleteAll()
    }

    @JvmStatic
    fun isVodCollect(sourceKey: String?, vodId: String?): Boolean {
        val record = AppDataManager.get().getVodCollectDao().getVodCollect(sourceKey, vodId)
        return record != null
    }

    @JvmStatic
    fun getAllVodCollect(): List<VodCollect> {
        return AppDataManager.get().getVodCollectDao().getAll()
    }
}

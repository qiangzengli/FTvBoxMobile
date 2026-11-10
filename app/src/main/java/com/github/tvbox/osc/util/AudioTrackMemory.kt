package com.github.tvbox.osc.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Pair

/**
 * 音轨记忆
 */
class AudioTrackMemory private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(playKey: String, groupIndex: Int, trackIndex: Int) {
        LOG.i("echo-AudioTrackMemory save playKey:$playKey")
        val key = playKey + "_exo"
        prefs.edit()
            .putInt(key + KEY_GROUP_SUFFIX, groupIndex)
            .putInt(key + KEY_TRACK_SUFFIX, trackIndex)
            .apply()
    }

    fun save(playKey: String, trackIndex: Int) {
        LOG.i("echo-AudioTrackMemory save playKey:$playKey")
        prefs.edit().putInt(playKey + "_ijk" + KEY_TRACK_SUFFIX, trackIndex).apply()
    }

    fun exoLoad(playKey: String): Pair<Int, Int>? {
        val key = playKey + "_exo"
        val group = prefs.getInt(key + KEY_GROUP_SUFFIX, -1)
        val track = prefs.getInt(key + KEY_TRACK_SUFFIX, -1)
        if (group >= 0 && track >= 0) {
            return Pair.create(group, track)
        }
        return null
    }

    fun ijkLoad(playKey: String): Int {
        val key = playKey + "_ijk"
        return prefs.getInt(key + KEY_TRACK_SUFFIX, -1)
    }

    companion object {
        private var instance: AudioTrackMemory? = null
        private const val PREFS_NAME = "audio_track_prefs"
        private const val KEY_GROUP_SUFFIX = "_group"
        private const val KEY_TRACK_SUFFIX = "_track"

        @Synchronized
        @JvmStatic
        fun getInstance(context: Context): AudioTrackMemory {
            if (instance == null) {
                instance = AudioTrackMemory(context)
            }
            return instance!!
        }
    }
}

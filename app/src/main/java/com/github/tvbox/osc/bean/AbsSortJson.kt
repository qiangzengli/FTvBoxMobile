package com.github.tvbox.osc.bean

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.util.ArrayList

class AbsSortJson : Serializable {

    @SerializedName(value = "class")
    @JvmField
    var classes: ArrayList<AbsJsonClass>? = null

    @SerializedName(value = "list")
    @JvmField
    var list: ArrayList<AbsJson.AbsJsonVod>? = null

    fun toAbsSortXml(): AbsSortXml {
        val absSortXml = AbsSortXml()
        val movieSort = MovieSort()
        movieSort.sortList = ArrayList()
        if (classes == null) {
            classes = ArrayList()
        }
        for (cls in classes!!) {
            if (cls == null || cls.type_id == null || cls.type_name == null) {
                continue
            }
            val sortData = MovieSort.SortData()
            sortData.id = cls.type_id
            sortData.name = cls.type_name
            sortData.flag = cls.type_flag
            movieSort.sortList?.add(sortData)
        }
        if (list != null && list!!.isNotEmpty()) {
            val movie = Movie()
            val videos = ArrayList<Movie.Video>()
            for (vod in list!!) {
                videos.add(vod.toXmlVideo())
            }
            movie.videoList = videos
            absSortXml.list = movie
        } else {
            absSortXml.list = null
        }
        absSortXml.classes = movieSort
        return absSortXml
    }

    class AbsJsonClass : Serializable {
        @SerializedName(value = "type_id", alternate = ["id"])
        @JvmField
        var type_id: String? = null

        @SerializedName(value = "type_name", alternate = ["name"])
        @JvmField
        var type_name: String? = null

        @JvmField
        var type_flag: String? = null
    }
}

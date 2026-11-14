package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.BounceInterpolator
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.chad.library.adapter.base.BaseQuickAdapter
import com.fbox.mobile.LocalVideos
import com.fbox.mobile.MobileSheets
import com.fbox.mobile.auth.MobileConfigAuth
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.BaseLazyFragment
import com.github.tvbox.osc.bean.Movie
import com.github.tvbox.osc.cache.RoomDataManger
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.event.ServerEvent
import com.github.tvbox.osc.ui.activity.CollectActivity
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.activity.HistoryActivity
import com.github.tvbox.osc.ui.activity.HomeActivity
import com.github.tvbox.osc.ui.activity.LivePlayActivity
import com.github.tvbox.osc.ui.activity.PushActivity
import com.github.tvbox.osc.ui.activity.SearchActivity
import com.github.tvbox.osc.ui.activity.SettingActivity
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapter
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.ImgUtil
import com.github.tvbox.osc.util.UA
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.AbsCallback
import com.lzy.okgo.model.Response
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.ArrayList
import java.util.Calendar

/**
 * @author pj567
 * @date :2021/3/9
 * @description:
 */
class UserFragment : BaseLazyFragment(), View.OnClickListener {
    private val localVideoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (ignored: SecurityException) {
        }
        startActivity(LocalVideos.intent(requireContext(), uri))
    }

    private var tvLive: LinearLayout? = null
    private var tvSearch: LinearLayout? = null
    private var tvSetting: LinearLayout? = null
    private var tvHistory: LinearLayout? = null
    private var tvCollect: LinearLayout? = null
    private var tvPush: LinearLayout? = null
    private var homeSourceRec: List<Movie.Video>? = null
    private var sourceViewModel: SourceViewModel? = null
    private var style: ImgUtil.Style? = null

    companion object {
        @JvmField
        var homeHotVodAdapter: HomeHotVodAdapter? = null

        @JvmField
        var tvHotList: TvRecyclerView? = null

        @JvmStatic
        fun newInstance(): UserFragment {
            return UserFragment()
        }

        @JvmStatic
        fun newInstance(recVod: List<Movie.Video>?): UserFragment {
            return UserFragment().setArguments(recVod)
        }
    }

    fun setArguments(recVod: List<Movie.Video>?): UserFragment {
        this.homeSourceRec = recVod
        return this
    }

    override fun onFragmentResume() {
        super.onFragmentResume()
        val ctx = context ?: requireContext()
        val multiColumn = Hawk.get(HawkConfig.HOME_REC_STYLE, false)
        var spanCount = if (multiColumn) 3 else 1
        if (multiColumn && style != null && Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1) {
            spanCount = Math.max(1, Math.min(3, ImgUtil.spanCountByStyle(style, spanCount)))
        }
        tvHotList?.visibility = View.VISIBLE
        tvHotList?.setHasFixedSize(false)
        tvHotList?.layoutManager = V7GridLayoutManager(ctx, spanCount)
        val padding = MobileSheets.dp(ctx, 8f)
        tvHotList?.setPadding(padding, padding, padding, padding)
        homeHotVodAdapter?.setPhoneListMode(spanCount == 1)
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2) {
            val allVodRecord = RoomDataManger.getAllVodRecord(20)
            val vodList = ArrayList<Movie.Video>()
            for (vodInfo in allVodRecord) {
                val vod = Movie.Video().apply {
                    id = vodInfo.id
                    sourceKey = vodInfo.sourceKey
                    name = vodInfo.name
                    pic = vodInfo.pic
                    if (vodInfo.playNote != null && vodInfo.playNote.isNotEmpty()) {
                        note = "上次看到" + vodInfo.playNote
                    }
                }
                vodList.add(vod)
            }
            homeHotVodAdapter?.setNewData(vodList)
        }
    }

    override fun getLayoutResID(): Int {
        return R.layout.fragment_user
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val hotList: TvRecyclerView? = view.findViewById(R.id.tvHotList)
        if (hotList != null && hotList.layoutManager == null) {
            val ctx = context ?: return
            hotList.layoutManager = V7LinearLayoutManager(ctx, V7LinearLayoutManager.VERTICAL, false)
        }
    }

    private fun jumpSearch(vod: Movie.Video) {
        val ctx = context ?: return
        val newIntent = if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
            Intent(ctx, FastSearchActivity::class.java)
        } else {
            Intent(ctx, SearchActivity::class.java)
        }
        newIntent.putExtra("title", vod.name)
        newIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        mActivity?.startActivity(newIntent)
    }

    override fun init() {
        EventBus.getDefault().register(this)
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        tvLive = findViewById(R.id.tvLive)
        tvSearch = findViewById(R.id.tvSearch)
        tvSetting = findViewById(R.id.tvSetting)
        tvCollect = findViewById(R.id.tvFavorite)
        tvHistory = findViewById(R.id.tvHistory)
        tvPush = findViewById(R.id.tvPush)
        tvLive?.setOnClickListener(this)
        tvSearch?.setOnClickListener(this)
        tvSetting?.setOnClickListener(this)
        tvHistory?.setOnClickListener(this)
        tvPush?.setOnClickListener(this)
        tvCollect?.setOnClickListener(this)
        tvLive?.onFocusChangeListener = focusChangeListener
        tvSearch?.onFocusChangeListener = focusChangeListener
        tvSetting?.onFocusChangeListener = focusChangeListener
        tvHistory?.onFocusChangeListener = focusChangeListener
        tvPush?.onFocusChangeListener = focusChangeListener
        tvCollect?.onFocusChangeListener = focusChangeListener
        val localFiles: View? = findViewById(R.id.mobileLocal)
        localFiles?.setOnClickListener { localVideoPicker.launch(arrayOf("video/*")) }
        tvHotList = findViewById(R.id.tvHotList)
        tvHotList?.isNestedScrollingEnabled = true
        if (mActivity is HomeActivity) {
            (mActivity as HomeActivity).bindMobileHomeActions(findViewById(R.id.mobileHomeActionsClip))
        }
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1 && homeSourceRec != null) {
            style = ImgUtil.initStyle()
        }
        var tvRate = ""
        val recMode = Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC)
        if (recMode == 0) {
            tvRate = "豆瓣热播"
        } else if (recMode == 1) {
            tvRate = if (homeSourceRec != null) "站点推荐" else "豆瓣热播"
        }
        val adapter = HomeHotVodAdapter(style, tvRate)
        homeHotVodAdapter = adapter
        adapter.setOnItemClickListener { _, _, position ->
            if (ApiConfig.get().getSourceBeanList().isEmpty()) return@setOnItemClickListener
            val vod = adapter.getItem(position) ?: return@setOnItemClickListener
            val vid = vod.id
            val vaction = vod.action

            if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1 && homeSourceRec != null && vaction != null) {
                if (MobileConfigAuth.handle(this@UserFragment, vod) {
                        EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_CONFIG_AUTH_CHANGED))
                    }) return@setOnItemClickListener
                sourceViewModel?.action(vod.sourceKey ?: "", vaction)
                return@setOnItemClickListener
            }

            if (!vid.isNullOrEmpty() && (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2) && HawkConfig.hotVodDelete) {
                adapter.remove(position)
                val vodInfo = RoomDataManger.getVodInfo(vod.sourceKey, vid)
                if (vodInfo != null) {
                    RoomDataManger.deleteVodRecord(vod.sourceKey, vodInfo)
                }
                Toast.makeText(context, "已删除当前记录", Toast.LENGTH_SHORT).show()
            } else if (vid != null && vid.startsWith("msearch:")) {
                jumpSearch(vod)
            } else {
                val bundle = Bundle().apply {
                    putString("id", vid)
                    putString("sourceKey", vod.sourceKey)
                    putString("title", vod.name)
                    putString("picture", vod.pic)
                }
                jumpActivity(DetailActivity::class.java, bundle)
            }
        }

        adapter.setOnItemLongClickListener(BaseQuickAdapter.OnItemLongClickListener { _, _, position ->
            if (ApiConfig.get().getSourceBeanList().isEmpty()) return@OnItemLongClickListener false
            val vod = adapter.getItem(position) ?: return@OnItemLongClickListener false
            val vid = vod.id
            if (vid != null && vid.startsWith("msearch:")) {
                val bundle = Bundle().apply {
                    putString("id", vid)
                    putString("sourceKey", vod.sourceKey)
                    putString("title", vod.name)
                    putString("picture", vod.pic)
                }
                jumpActivity(DetailActivity::class.java, bundle)
            } else if (!vid.isNullOrEmpty() && (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2)) {
                HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete
                adapter.notifyDataSetChanged()
            } else {
                val bundle = Bundle().apply {
                    putString("title", vod.name)
                }
                jumpActivity(FastSearchActivity::class.java, bundle)
            }
            true
        })

        tvHotList?.setOnItemListener(object : TvRecyclerView.OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(BounceInterpolator()).start()
            }

            override fun onItemSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(BounceInterpolator()).start()
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View, position: Int) {
            }
        })
        tvHotList?.adapter = adapter

        initHomeHotVod(adapter)
        sourceViewModel?.actionResult?.observe(viewLifecycleOwner, Observer { jsonObject ->
            if (jsonObject == null) return@Observer
            val msg = jsonObject.optString("msg")
            if (msg.isNotEmpty()) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        })
    }

    private fun initHomeHotVod(adapter: HomeHotVodAdapter) {
        val rec = Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC)
        if (rec == 1) {
            val sourceRec = homeSourceRec
            if (sourceRec != null) {
                adapter.setNewData(sourceRec)
                return
            }
        } else if (rec == 2) {
            return
        }
        setDouBanData(adapter)
    }

    private fun setDouBanData(adapter: HomeHotVodAdapter) {
        try {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DATE)
            val today = String.format("%d%d%d", year, month, day)
            val requestDay = Hawk.get("home_hot_day", "")
            if (requestDay == today) {
                val json = Hawk.get("home_hot", "")
                if (json.isNotEmpty()) {
                    val hotMovies = loadHots(json)
                    if (hotMovies.isNotEmpty()) {
                        adapter.setNewData(hotMovies)
                        return
                    }
                }
            }
            val doubanUrl = "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=$year,$year"
            OkGo.get<String>(doubanUrl)
                .headers("User-Agent", UA.randomOne())
                .execute(object : AbsCallback<String>() {
                    override fun onSuccess(response: Response<String>) {
                        val netJson = response.body() ?: return
                        Hawk.put("home_hot_day", today)
                        Hawk.put("home_hot", netJson)
                        mActivity?.runOnUiThread {
                            adapter.setNewData(loadHots(netJson))
                        }
                    }

                    @Throws(Throwable::class)
                    override fun convertResponse(response: okhttp3.Response): String {
                        return response.body()?.string() ?: ""
                    }
                })
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    private fun loadHots(json: String): ArrayList<Movie.Video> {
        val result = ArrayList<Movie.Video>()
        try {
            val infoJson = Gson().fromJson(json, JsonObject::class.java)
            val array = infoJson.getAsJsonArray("data") ?: return result
            val limit = Math.min(array.size(), 25)
            for (i in 0 until limit) {
                val ele = array.get(i)
                val obj = ele.asJsonObject
                val vod = Movie.Video().apply {
                    name = obj.get("title").asString
                    note = obj.get("rate").asString
                    if (!note.isNullOrEmpty()) note += " 分"
                    pic = obj.get("cover").asString + "@User-Agent=" + UA.randomOne() + "@Referer=https://www.douban.com/"
                }
                result.add(vod)
            }
        } catch (th: Throwable) {
        }
        return result
    }

    private val focusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
        if (hasFocus) {
            v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(BounceInterpolator()).start()
        } else {
            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(BounceInterpolator()).start()
        }
    }

    override fun onClick(v: View) {
        // takagen99: Remove Delete Mode
        HawkConfig.hotVodDelete = false
        FastClickCheckUtil.check(v)
        when (v.id) {
            R.id.tvLive -> jumpActivity(LivePlayActivity::class.java)
            R.id.tvSearch -> jumpActivity(SearchActivity::class.java)
            R.id.tvSetting -> jumpActivity(SettingActivity::class.java)
            R.id.tvHistory -> jumpActivity(HistoryActivity::class.java)
            R.id.tvPush -> jumpActivity(PushActivity::class.java)
            R.id.tvFavorite -> jumpActivity(CollectActivity::class.java)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun server(event: ServerEvent) {
        if (event.type == ServerEvent.SERVER_CONNECTION) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }
}

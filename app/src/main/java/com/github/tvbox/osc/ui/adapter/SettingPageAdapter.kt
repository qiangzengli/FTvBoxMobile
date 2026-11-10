package com.github.tvbox.osc.ui.adapter

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.github.tvbox.osc.base.BaseLazyFragment

class SettingPageAdapter : FragmentPagerAdapter {
    @JvmField
    var fragmentManager: FragmentManager? = null
    @JvmField
    var list: MutableList<BaseLazyFragment>? = null

    constructor(fm: FragmentManager) : super(fm) {
        this.fragmentManager = fm
    }

    constructor(fm: FragmentManager, list: MutableList<BaseLazyFragment>?) : super(fm) {
        this.fragmentManager = fm
        this.list = list
    }

    override fun getItem(position: Int): Fragment {
        return list!![position]
    }

    override fun getCount(): Int {
        return list?.size ?: 0
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val fragment = super.instantiateItem(container, position) as Fragment
        fragmentManager?.beginTransaction()?.show(fragment)?.commitAllowingStateLoss()
        return fragment
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        val fragment = list?.get(position) ?: return
        fragmentManager?.beginTransaction()?.hide(fragment)?.commitAllowingStateLoss()
    }
}

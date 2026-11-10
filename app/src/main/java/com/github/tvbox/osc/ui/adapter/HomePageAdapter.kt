package com.github.tvbox.osc.ui.adapter

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.github.tvbox.osc.base.BaseLazyFragment

class HomePageAdapter : FragmentPagerAdapter {
    @JvmField
    var fragmentManager: FragmentManager? = null
    @JvmField
    var list: MutableList<BaseLazyFragment>? = null
    private val adapterId = System.nanoTime()

    constructor(fm: FragmentManager) : super(fm) {
        this.fragmentManager = fm
    }

    constructor(fm: FragmentManager, list: MutableList<BaseLazyFragment>?) : super(fm) {
        this.fragmentManager = fm
        this.list = list
    }

    fun clear() {
        list?.clear()
        notifyDataSetChanged()
    }

    fun removeAll() {
        val currentList = list ?: return
        val fm = fragmentManager ?: return
        val transaction = fm.beginTransaction()
        for (fragment in currentList) {
            if (fragment.isAdded) {
                transaction.remove(fragment)
            }
        }
        transaction.commitAllowingStateLoss()
        fm.executePendingTransactions()
        currentList.clear()
        notifyDataSetChanged()
    }

    override fun getItem(position: Int): Fragment {
        return list!![position]
    }

    override fun getItemId(position: Int): Long {
        return adapterId + position
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
        val fragment = `object` as Fragment
        fragmentManager?.beginTransaction()?.hide(fragment)?.commitAllowingStateLoss()
    }
}

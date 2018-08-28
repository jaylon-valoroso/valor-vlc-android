package org.videolan.vlc.gui

import android.support.annotation.MainThread
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.withContext

abstract class DiffUtilAdapter<D, VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {

    protected var dataset: List<D> = listOf()
    private set
    private val diffCallback by lazy(LazyThreadSafetyMode.NONE) { createCB() }
    private val updateActor = actor<List<D>>(UI, Channel.CONFLATED) {
        for (list in channel) internalUpdate(list)
    }
    protected abstract fun onUpdateFinished()

    @MainThread
    fun update (list: List<D>) {
        updateActor.offer(list)
    }

    @MainThread
    private suspend fun internalUpdate(list: List<D>) {
        val (finalList, result) = withContext(CommonPool) {
            val finalList = prepareList(list)
            val result = DiffUtil.calculateDiff(diffCallback.apply { update(dataset, finalList) }, detectMoves())
            Pair(finalList, result)
        }
        dataset = finalList
        result.dispatchUpdatesTo(this@DiffUtilAdapter)
        onUpdateFinished()
    }

    protected open fun prepareList(list: List<D>) : List<D> = list.toList()

    @MainThread
    fun isEmpty() = dataset.isEmpty()

    open fun getItem(position: Int) = dataset[position]

    protected open fun detectMoves() = false

    protected open fun createCB() = DiffCallback<D>()

    open class DiffCallback<D> : DiffUtil.Callback() {
        lateinit var oldList: List<D>
        lateinit var newList: List<D>

        fun update(oldList: List<D>, newList: List<D>) {
            this.oldList = oldList
            this.newList = newList
        }

        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areContentsTheSame(oldItemPosition : Int, newItemPosition : Int) = true

        override fun areItemsTheSame(oldItemPosition : Int, newItemPosition : Int) = oldList[oldItemPosition] == newList[newItemPosition]
    }
}
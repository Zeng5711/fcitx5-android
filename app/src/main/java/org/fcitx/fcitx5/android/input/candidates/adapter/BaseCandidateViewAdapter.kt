package org.fcitx.fcitx5.android.input.candidates.adapter

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.CandidateItemUi

abstract class BaseCandidateViewAdapter :
    RecyclerView.Adapter<BaseCandidateViewAdapter.ViewHolder>() {
    inner class ViewHolder(val ui: CandidateItemUi) : RecyclerView.ViewHolder(ui.root) {
        var idx = -1
    }

    var candidates: Array<String> = arrayOf()
        private set

    var offset = 0
        private set

    fun getCandidateAt(position: Int) = candidates[offset + position]

    @SuppressLint("NotifyDataSetChanged")
    fun updateCandidates(data: Array<String>) {
        candidates = data
        notifyDataSetChanged()
    }

    fun updateCandidatesWithOffset(data: Array<String>, offset: Int) {
        this.offset = offset
        updateCandidates(data)
    }

    @CallSuper
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(CandidateItemUi(parent.context, theme)).apply {
            itemView.setOnClickListener { onSelect(this.idx + offset) }
        }
    }

    @CallSuper
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.ui.text.text = getCandidateAt(position)
        holder.ui.index.text = "${position+1}"
        holder.idx = position
    }

    abstract val theme: Theme

    override fun getItemCount() = candidates.size - offset

    abstract fun onSelect(idx: Int)
}

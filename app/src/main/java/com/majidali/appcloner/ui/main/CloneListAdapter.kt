package com.majidali.appcloner.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.majidali.appcloner.databinding.ItemCloneBinding
import com.majidali.appcloner.domain.model.CloneInfo

/**
 * One row per clone: custom name, original package, frozen state,
 * and three big actions — Open, Freeze/Unfreeze, and details.
 * The briefcase badge is drawn by Android on the profile app's own
 * launcher icon; here the row shows a small badge text instead so
 * the list stays readable at a glance.
 */
class CloneListAdapter(
    private val onOpen: (CloneInfo) -> Unit,
    private val onFreezeToggle: (CloneInfo) -> Unit,
    private val onDetails: (CloneInfo) -> Unit
) : ListAdapter<CloneInfo, CloneListAdapter.CloneViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CloneViewHolder {
        val binding = ItemCloneBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CloneViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CloneViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CloneViewHolder(
        private val binding: ItemCloneBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(clone: CloneInfo) {
            binding.cloneName.text = clone.customName
            binding.clonePackage.text = clone.packageName
            binding.frozenBadge.visibility =
                if (clone.isFrozen) android.view.View.VISIBLE
                else android.view.View.GONE
            binding.freezeButton.text =
                if (clone.isFrozen) "Unfreeze" else "Freeze"
            binding.openButton.setOnClickListener { onOpen(clone) }
            binding.freezeButton.setOnClickListener { onFreezeToggle(clone) }
            binding.root.setOnClickListener { onDetails(clone) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CloneInfo>() {
            override fun areItemsTheSame(oldItem: CloneInfo, newItem: CloneInfo): Boolean =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: CloneInfo, newItem: CloneInfo): Boolean =
                oldItem == newItem
        }
    }
}

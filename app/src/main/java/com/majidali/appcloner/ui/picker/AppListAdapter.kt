package com.majidali.appcloner.ui.picker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.majidali.appcloner.databinding.ItemAppBinding
import com.majidali.appcloner.domain.model.AppInfo

/**
 * One row per launchable app: name, package, and a "split APK" note
 * when the app was installed as base + splits (most Play apps are).
 * Already-cloned apps show "Cloned" and cannot be tapped again.
 */
class AppListAdapter(
    private val apps: List<AppInfo>,
    private val clonedPackages: Set<String>,
    private val onPick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size

    inner class AppViewHolder(
        private val binding: ItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            val alreadyCloned = clonedPackages.contains(app.packageName)
            binding.appName.text = app.label
            binding.appPackage.text = app.packageName
            binding.splitNote.visibility =
                if (app.hasSplits) View.VISIBLE else View.GONE
            binding.clonedBadge.visibility =
                if (alreadyCloned) View.VISIBLE else View.GONE
            binding.root.isEnabled = !alreadyCloned
            binding.root.alpha = if (alreadyCloned) 0.5f else 1.0f
            binding.root.setOnClickListener {
                if (!alreadyCloned) onPick(app)
            }
        }
    }
}

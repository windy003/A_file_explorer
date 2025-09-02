package com.example.fileexplorer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BreadcrumbAdapter(
    private val breadcrumbs: MutableList<BreadcrumbItem>,
    private val onBreadcrumbClick: (BreadcrumbItem) -> Unit
) : RecyclerView.Adapter<BreadcrumbAdapter.BreadcrumbViewHolder>() {

    class BreadcrumbViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.tvBreadcrumbName)
        val separatorTextView: TextView = itemView.findViewById(R.id.tvSeparator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BreadcrumbViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_breadcrumb, parent, false)
        return BreadcrumbViewHolder(view)
    }

    override fun onBindViewHolder(holder: BreadcrumbViewHolder, position: Int) {
        val breadcrumb = breadcrumbs[position]
        
        holder.nameTextView.text = breadcrumb.name
        
        // 隐藏最后一个项目的分隔符
        holder.separatorTextView.visibility = if (breadcrumb.isLast) View.GONE else View.VISIBLE
        
        // 设置点击事件
        holder.nameTextView.setOnClickListener {
            onBreadcrumbClick(breadcrumb)
        }
    }

    override fun getItemCount(): Int = breadcrumbs.size

    fun updateBreadcrumbs(newBreadcrumbs: List<BreadcrumbItem>) {
        breadcrumbs.clear()
        breadcrumbs.addAll(newBreadcrumbs)
        notifyDataSetChanged()
    }
}
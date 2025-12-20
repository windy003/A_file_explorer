package com.example.fileexplorer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class FileAdapter(
    private val fileItems: MutableList<FileItem>,
    private val onItemClick: (FileItem) -> Unit,
    private val onItemLongClick: (FileItem) -> Boolean,  // 新增：长按事件
    private val onMoreClick: (FileItem, View) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private var isMultiSelectMode = false  // 多选模式标志

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView as CardView
        val checkbox: CheckBox = itemView.findViewById(R.id.cbFileSelect)
        val fileIcon: ImageView = itemView.findViewById(R.id.ivFileIcon)
        val fileName: TextView = itemView.findViewById(R.id.tvFileName)
        val fileInfo: TextView = itemView.findViewById(R.id.tvFileInfo)
        val moreButton: ImageView = itemView.findViewById(R.id.ivMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val fileItem = fileItems[position]

        holder.fileName.text = fileItem.name

        // 设置图标
        if (fileItem.isDirectory) {
            holder.fileIcon.setImageResource(R.drawable.ic_folder)
        } else {
            holder.fileIcon.setImageResource(R.drawable.ic_file)
        }

        // 设置文件信息
        val info = if (fileItem.isDirectory) {
            "${fileItem.getDisplayDate()}"
        } else {
            "${fileItem.getDisplaySize()} • ${fileItem.getDisplayDate()}"
        }
        holder.fileInfo.text = info

        // 多选模式UI更新
        if (isMultiSelectMode) {
            holder.checkbox.visibility = View.VISIBLE
            holder.checkbox.isChecked = fileItem.isSelected
            holder.moreButton.visibility = View.GONE  // 隐藏菜单按钮

            // 选中状态视觉反馈
            if (fileItem.isSelected) {
                holder.cardView.setCardBackgroundColor(
                    holder.itemView.context.getColor(android.R.color.holo_blue_light)
                )
                holder.cardView.alpha = 0.7f
            } else {
                holder.cardView.setCardBackgroundColor(
                    holder.itemView.context.getColor(android.R.color.white)
                )
                holder.cardView.alpha = 1.0f
            }
        } else {
            holder.checkbox.visibility = View.GONE
            holder.moreButton.visibility = View.VISIBLE
            holder.cardView.setCardBackgroundColor(
                holder.itemView.context.getColor(android.R.color.white)
            )
            holder.cardView.alpha = 1.0f
        }

        // 点击事件
        holder.itemView.setOnClickListener {
            if (isMultiSelectMode) {
                // 多选模式：切换选中状态
                fileItem.isSelected = !fileItem.isSelected
                notifyItemChanged(position)
            } else {
                // 普通模式：执行打开操作
                onItemClick(fileItem)
            }
        }

        // 长按事件
        holder.itemView.setOnLongClickListener {
            onItemLongClick(fileItem)
        }

        // 菜单按钮点击
        holder.moreButton.setOnClickListener {
            onMoreClick(fileItem, it)
        }
    }

    override fun getItemCount(): Int = fileItems.size

    fun updateFiles(newFiles: List<FileItem>) {
        fileItems.clear()
        fileItems.addAll(newFiles)
        notifyDataSetChanged()
    }

    // 进入多选模式
    fun enterMultiSelectMode() {
        isMultiSelectMode = true
        notifyDataSetChanged()
    }

    // 退出多选模式
    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        // 清除所有选中状态
        fileItems.forEach { it.isSelected = false }
        notifyDataSetChanged()
    }

    // 获取选中的文件项
    fun getSelectedItems(): List<FileItem> {
        return fileItems.filter { it.isSelected }
    }

    // 全选
    fun selectAll() {
        fileItems.forEach { it.isSelected = true }
        notifyDataSetChanged()
    }

    // 取消全选
    fun deselectAll() {
        fileItems.forEach { it.isSelected = false }
        notifyDataSetChanged()
    }

    // 检查是否为多选模式
    fun isInMultiSelectMode(): Boolean = isMultiSelectMode

    // 获取选中数量
    fun getSelectedCount(): Int = fileItems.count { it.isSelected }
}

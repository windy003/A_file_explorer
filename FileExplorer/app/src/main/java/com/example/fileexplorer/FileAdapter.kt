package com.example.fileexplorer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FileAdapter(
    private val fileItems: MutableList<FileItem>,
    private val onItemClick: (FileItem) -> Unit,
    private val onMoreClick: (FileItem, View) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            onItemClick(fileItem)
        }
        
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
}
package com.UIN.Tool.ui.dev;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.UIN.Tool.R;

import java.util.ArrayList;
import java.util.List;

public class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.ViewHolder> {

    private List<String> fileList = new ArrayList<>();
    private String currentFile = "";
    private OnFileSelectedListener listener;

    public interface OnFileSelectedListener {
        void onFileSelected(String fileName);
        void onFileLongClick(String fileName);
    }

    public void setOnFileSelectedListener(OnFileSelectedListener listener) {
        this.listener = listener;
    }

    public void setFileList(List<String> fileList) {
        this.fileList = fileList;
        notifyDataSetChanged();
    }

    public void setCurrentFile(String currentFile) {
        this.currentFile = currentFile;
        notifyDataSetChanged();
    }

    public void addFile(String fileName) {
        if (!fileList.contains(fileName)) {
            fileList.add(fileName);
            notifyItemInserted(fileList.size() - 1);
        }
    }

    public void removeFile(String fileName) {
        int position = fileList.indexOf(fileName);
        if (position != -1) {
            fileList.remove(position);
            notifyItemRemoved(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file_tree, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String fileName = fileList.get(position);
        holder.tvFileName.setText(fileName);
        holder.ivFileIcon.setImageResource(getFileIcon(fileName));
        
        // 高亮当前选中的文件
        if (fileName.equals(currentFile)) {
            holder.itemView.setBackgroundColor(holder.itemView.getContext()
                    .getColor(R.color.surface_variant));
            holder.tvFileName.setTextColor(holder.itemView.getContext()
                    .getColor(R.color.primary));
        } else {
            holder.itemView.setBackgroundColor(0);
            holder.tvFileName.setTextColor(holder.itemView.getContext()
                    .getColor(R.color.text_primary));
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFileSelected(fileName);
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onFileLongClick(fileName);
            }
            return true;
        });
    }

    private int getFileIcon(String fileName) {
        if (fileName.endsWith(".java")) {
            return R.drawable.ic_java;
        } else if (fileName.endsWith(".html")) {
            return R.drawable.ic_html;
        } else if (fileName.endsWith(".css")) {
            return R.drawable.ic_css;
        } else if (fileName.endsWith(".js")) {
            return R.drawable.ic_javascript;
        } else if (fileName.endsWith(".xml")) {
            return R.drawable.ic_xml;
        } else if (fileName.endsWith(".json")) {
            return R.drawable.ic_json;
        } else {
            return R.drawable.ic_file;
        }
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivFileIcon;
        TextView tvFileName;
        ImageView ivFileStatus;

        ViewHolder(View itemView) {
            super(itemView);
            ivFileIcon = itemView.findViewById(R.id.iv_file_icon);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            ivFileStatus = itemView.findViewById(R.id.iv_file_status);
        }
    }
}
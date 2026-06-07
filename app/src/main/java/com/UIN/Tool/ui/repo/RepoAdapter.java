package com.UIN.Tool.ui.repo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.UIN.Tool.R;
import com.UIN.Tool.utils.LogUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件仓库列表适配器
 */
public class RepoAdapter extends RecyclerView.Adapter<RepoAdapter.ViewHolder> {

    private static final String TAG = "RepoAdapter";
    private Context context;
    private List<RepoPluginInfo> plugins = new ArrayList<>();
    private OnPluginActionListener listener;
    private List<String> installedPluginIds = new ArrayList<>();
    private int loadingPosition = -1;

    public interface OnPluginActionListener {
        void onInstall(RepoPluginInfo plugin, int position);
        void onOpen(RepoPluginInfo plugin);
    }

    public RepoAdapter(Context context, OnPluginActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setPlugins(List<RepoPluginInfo> plugins) {
        this.plugins = plugins;
        notifyDataSetChanged();
        LogUtils.d(TAG, "设置插件列表，共 " + plugins.size() + " 个");
    }

    public void setInstalledPlugins(List<String> pluginIds) {
        this.installedPluginIds = pluginIds;
        notifyDataSetChanged();
        LogUtils.d(TAG, "更新已安装插件列表，共 " + pluginIds.size() + " 个");
    }

    public void setButtonLoading(int position, boolean loading) {
        if (loading) {
            loadingPosition = position;
        } else {
            loadingPosition = -1;
        }
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_repo_plugin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RepoPluginInfo plugin = plugins.get(position);
        boolean isInstalled = installedPluginIds.contains(plugin.getPluginId());
        boolean isLoading = (loadingPosition == position);

        // 插件名称
        holder.tvName.setText(plugin.getName());
        
        // 插件 ID
        holder.tvPluginId.setText(plugin.getPluginId());
        
        // 版本信息
        String versionText = "版本: " + plugin.getVersionName();
        holder.tvVersion.setText(versionText);
        
        // 文件大小
        String sizeText;
        if (plugin.getSize() > 0) {
            sizeText = "大小: " + plugin.getFormattedSize();
        } else {
            sizeText = "大小: 未知";
        }
        holder.tvSize.setText(sizeText);
        
        // 更新日期
        String dateText = "更新: " + plugin.getFormattedDate();
        holder.tvDate.setText(dateText);

        // 作者信息
        if (plugin.getAuthor() != null && !plugin.getAuthor().isEmpty()) {
            holder.tvAuthor.setText(plugin.getAuthor());
            holder.tvAuthor.setVisibility(View.VISIBLE);
        } else {
            holder.tvAuthor.setVisibility(View.GONE);
        }

        // 描述信息
        if (plugin.getDescription() != null && !plugin.getDescription().isEmpty()) {
            holder.tvDescription.setText(plugin.getDescription());
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }

        // 设置按钮状态
        if (isLoading) {
            holder.btnAction.setVisibility(View.INVISIBLE);
            holder.btnProgress.setVisibility(View.VISIBLE);
            holder.btnAction.setEnabled(false);
        } else {
            holder.btnAction.setVisibility(View.VISIBLE);
            holder.btnProgress.setVisibility(View.GONE);
            holder.btnAction.setEnabled(true);
            
            if (isInstalled) {
                holder.btnAction.setText("打开");
                holder.btnAction.setBackgroundTintList(
                    androidx.core.content.ContextCompat.getColorStateList(context, R.color.success));
            } else {
                holder.btnAction.setText("安装");
                holder.btnAction.setBackgroundTintList(
                    androidx.core.content.ContextCompat.getColorStateList(context, R.color.primary));
            }
        }

        // 点击安装/打开按钮
        holder.btnAction.setOnClickListener(v -> {
            if (isInstalled) {
                if (listener != null) {
                    LogUtils.action(TAG, "打开插件", plugin.getName());
                    listener.onOpen(plugin);
                }
            } else {
                if (!isLoading && listener != null) {
                    LogUtils.action(TAG, "安装插件", plugin.getName());
                    listener.onInstall(plugin, position);
                }
            }
        });

        // 点击卡片显示简单提示
        holder.itemView.setOnClickListener(v -> {
            Toast.makeText(context, plugin.getName() + "\n" + plugin.getPluginId(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return plugins.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvPluginId;
        TextView tvAuthor;
        TextView tvDescription;
        TextView tvVersion;
        TextView tvSize;
        TextView tvDate;
        Button btnAction;
        ProgressBar btnProgress;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_plugin_name);
            tvPluginId = itemView.findViewById(R.id.tv_plugin_id);
            tvAuthor = itemView.findViewById(R.id.tv_author);
            tvDescription = itemView.findViewById(R.id.tv_description);
            tvVersion = itemView.findViewById(R.id.tv_version);
            tvSize = itemView.findViewById(R.id.tv_size);
            tvDate = itemView.findViewById(R.id.tv_date);
            btnAction = itemView.findViewById(R.id.btn_action);
            btnProgress = itemView.findViewById(R.id.btn_progress);
        }
    }
}
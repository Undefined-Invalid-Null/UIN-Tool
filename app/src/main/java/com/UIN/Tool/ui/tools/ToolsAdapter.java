package com.UIN.Tool.ui.tools;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.UIN.Tool.R;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.plugin.PluginManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ToolsAdapter extends RecyclerView.Adapter<ToolsAdapter.ViewHolder> {

    private List<PluginInfo> plugins = new ArrayList<>();
    private final OnPluginClickListener listener;

    public interface OnPluginClickListener {
        void onPluginClick(PluginInfo plugin);
    }

    public ToolsAdapter(OnPluginClickListener listener) {
        this.listener = listener;
    }

    public void setPlugins(List<PluginInfo> plugins) {
        this.plugins = plugins;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plugin_detail, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PluginInfo plugin = plugins.get(position);
        holder.tvName.setText(plugin.name);
        holder.tvDescription.setText(plugin.description != null && !plugin.description.isEmpty() ? plugin.description : "无描述");

        // 加载图标
        loadIcon(holder.ivIcon, plugin);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPluginClick(plugin);
            }
        });
    }

    private void loadIcon(ImageView imageView, PluginInfo plugin) {
        try {
            PluginManager pluginManager = PluginManager.getInstance(imageView.getContext());
            File pluginDir = pluginManager.getPluginDirFile(plugin.pluginId);
            
            if (pluginDir != null && pluginDir.exists()) {
                String iconPath = plugin.icon != null && !plugin.icon.isEmpty() ? plugin.icon : "icon.png";
                File iconFile = new File(pluginDir, iconPath);
                
                if (iconFile.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                        return;
                    }
                }
            }
            // 默认图标
            imageView.setImageResource(android.R.drawable.ic_menu_info_details);
        } catch (Exception e) {
            e.printStackTrace();
            imageView.setImageResource(android.R.drawable.ic_menu_info_details);
        }
    }

    @Override
    public int getItemCount() {
        return plugins.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        TextView tvDescription;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvName = itemView.findViewById(R.id.tv_name);
            tvDescription = itemView.findViewById(R.id.tv_description);
        }
    }
}
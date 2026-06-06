package com.UIN.Tool.ui.tools;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.UIN.Tool.R;
import com.UIN.Tool.plugin.PluginInfo;
import com.UIN.Tool.ui.common.IconHelper;

import java.util.List;
import java.util.Map;

public class CategoryAdapter extends BaseExpandableListAdapter {

    private Context context;
    private List<String> categories;
    private Map<String, List<PluginInfo>> categoryMap;
    private OnPluginClickListener listener;

    public interface OnPluginClickListener {
        void onPluginClick(PluginInfo plugin);
    }

    public CategoryAdapter(Context context, List<String> categories,
                           Map<String, List<PluginInfo>> categoryMap,
                           OnPluginClickListener listener) {
        this.context = context;
        this.categories = categories;
        this.categoryMap = categoryMap;
        this.listener = listener;
    }

    @Override
    public int getGroupCount() {
        return categories.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        String category = categories.get(groupPosition);
        List<PluginInfo> plugins = categoryMap.get(category);
        return plugins != null ? plugins.size() : 0;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return categories.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        String category = categories.get(groupPosition);
        List<PluginInfo> plugins = categoryMap.get(category);
        return plugins != null ? plugins.get(childPosition) : null;
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        ViewHolderGroup holder;
        
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_category_group, parent, false);
            holder = new ViewHolderGroup();
            holder.tvTitle = convertView.findViewById(R.id.tv_category_title);
            holder.ivIndicator = convertView.findViewById(R.id.iv_indicator);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolderGroup) convertView.getTag();
        }
        
        String category = categories.get(groupPosition);
        holder.tvTitle.setText(category);
        holder.ivIndicator.setImageResource(isExpanded ? R.drawable.ic_chevron_down : R.drawable.ic_chevron_right);
        holder.ivIndicator.setColorFilter(context.getColor(R.color.text_secondary));
        
        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                             View convertView, ViewGroup parent) {
        ViewHolderChild holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_plugin_detail, parent, false);
            holder = new ViewHolderChild();
            holder.ivIcon = convertView.findViewById(R.id.iv_icon);
            holder.tvName = convertView.findViewById(R.id.tv_name);
            holder.tvDescription = convertView.findViewById(R.id.tv_description);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolderChild) convertView.getTag();
        }

        PluginInfo plugin = (PluginInfo) getChild(groupPosition, childPosition);
        if (plugin != null) {
            holder.tvName.setText(plugin.name);
            holder.tvDescription.setText(plugin.description != null && !plugin.description.isEmpty()
                    ? plugin.description : context.getString(R.string.plugin_no_description));
            IconHelper.loadPluginIcon(holder.ivIcon, plugin);

            convertView.setOnClickListener(v -> {
                if (listener != null) listener.onPluginClick(plugin);
            });
        }

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    static class ViewHolderGroup {
        TextView tvTitle;
        ImageView ivIndicator;
    }

    static class ViewHolderChild {
        ImageView ivIcon;
        TextView tvName;
        TextView tvDescription;
    }
}
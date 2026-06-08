package com.UIN.Tool.ui.docs;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.UIN.Tool.R;
import com.UIN.Tool.databinding.ActivityDocBrowserBinding;
import com.UIN.Tool.ui.common.BaseActivity;

import java.util.ArrayList;
import java.util.List;

public class DocBrowserActivity extends BaseActivity {

    private ActivityDocBrowserBinding binding;
    private DocAdapter adapter;
    private List<DocItem> docItems = new ArrayList<>();

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_doc_browser;
    }

    @Override
    protected void initViews() {
        binding = ActivityDocBrowserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupToolbar(binding.toolbar, getString(R.string.doc_center_title), true);
        
        adapter = new DocAdapter();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    @Override
    protected void initData() {
        loadDocItems();
    }

    private void loadDocItems() {
        // 使用帮助 - 使用 ic_help 不存在，改用 ic_info
        DocItem helpDoc = new DocItem(
            R.drawable.ic_info,
            getString(R.string.doc_type_help),
            getString(R.string.doc_help_desc),
            "help"
        );
        docItems.add(helpDoc);

        // 开发文档
        DocItem devDoc = new DocItem(
            R.drawable.ic_developer_mode,
            getString(R.string.doc_type_dev),
            getString(R.string.doc_dev_desc),
            "dev"
        );
        docItems.add(devDoc);

        // 更新日志
        DocItem changelogDoc = new DocItem(
            R.drawable.ic_update,
            getString(R.string.doc_type_changelog),
            getString(R.string.doc_changelog_desc),
            "changelog"
        );
        docItems.add(changelogDoc);

        // 关于
        DocItem aboutDoc = new DocItem(
            R.drawable.ic_info,
            getString(R.string.doc_type_about),
            getString(R.string.doc_about_desc),
            "about"
        );
        docItems.add(aboutDoc);

        // 贡献者
        DocItem contributorsDoc = new DocItem(
            R.drawable.ic_contacts,
            getString(R.string.doc_type_contributors),
            getString(R.string.doc_contributors_desc),
            "contributors"
        );
        docItems.add(contributorsDoc);

        adapter.notifyDataSetChanged();
    }

    private class DocAdapter extends RecyclerView.Adapter<DocAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_doc_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocItem item = docItems.get(position);
            holder.ivIcon.setImageResource(item.iconRes);
            holder.ivIcon.setColorFilter(getColor(R.color.primary));
            holder.tvTitle.setText(item.title);
            holder.tvDescription.setText(item.description);
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(DocBrowserActivity.this, DocViewerActivity.class);
                intent.putExtra("doc_type", item.docType);
                intent.putExtra("title", item.title);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return docItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvTitle;
            TextView tvDescription;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_icon);
                tvTitle = itemView.findViewById(R.id.tv_title);
                tvDescription = itemView.findViewById(R.id.tv_description);
            }
        }
    }

    private static class DocItem {
        int iconRes;
        String title;
        String description;
        String docType;

        DocItem(int iconRes, String title, String description, String docType) {
            this.iconRes = iconRes;
            this.title = title;
            this.description = description;
            this.docType = docType;
        }
    }
}
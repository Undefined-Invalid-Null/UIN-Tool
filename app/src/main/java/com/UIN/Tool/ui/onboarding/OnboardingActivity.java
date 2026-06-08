package com.UIN.Tool.ui.onboarding;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.UIN.Tool.MainActivity;
import com.UIN.Tool.R;
import com.UIN.Tool.ui.docs.DocBrowserActivity;
import com.UIN.Tool.ui.permission.PermissionExplainActivity;
import com.UIN.Tool.utils.LogUtils;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private static final String TAG = "OnboardingActivity";
    private static final String PREF_NAME = "app_prefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    
    private ViewPager2 viewPager;
    private LinearLayout indicatorContainer;
    private Button btnSkip;
    private Button btnNext;
    private Button btnStart;
    private TextView tvDocLink;
    private TextView tvPermissionLink;
    private OnboardingAdapter adapter;
    private List<OnboardingItem> items = new ArrayList<>();
    private List<ImageView> indicators = new ArrayList<>();
    private boolean isVersionUpdate = false;
    private boolean isFinishing = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        
        isVersionUpdate = getIntent().getBooleanExtra("is_version_update", false);
        String versionName = getIntent().getStringExtra("version_name");
        
        initViews();
        initData(versionName);
        setupViewPager();
        setupIndicators();
        setupListeners();
        setupLinks();
    }
    
    private void initViews() {
        viewPager = findViewById(R.id.view_pager);
        indicatorContainer = findViewById(R.id.indicator_container);
        btnSkip = findViewById(R.id.btn_skip);
        btnNext = findViewById(R.id.btn_next);
        btnStart = findViewById(R.id.btn_start);
        tvDocLink = findViewById(R.id.tv_doc_link);
        tvPermissionLink = findViewById(R.id.tv_permission_link);
        
        if (isVersionUpdate && btnSkip != null) {
            btnSkip.setText(R.string.onboarding_close);
        }
    }
    
    private void setupLinks() {
        if (tvDocLink != null) {
            tvDocLink.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(OnboardingActivity.this, DocBrowserActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                } catch (Exception e) {
                    LogUtils.e(TAG, "打开文档中心失败", e);
                }
            });
        }
        
        if (tvPermissionLink != null) {
            tvPermissionLink.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(OnboardingActivity.this, PermissionExplainActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                } catch (Exception e) {
                    LogUtils.e(TAG, "打开权限说明失败", e);
                }
            });
        }
    }
    
    private void initData(String versionName) {
        items.add(new OnboardingItem(
            isVersionUpdate ? getString(R.string.onboarding_update_title) : getString(R.string.onboarding_welcome_title),
            isVersionUpdate ? String.format(getString(R.string.onboarding_update_desc), versionName) 
                            : getString(R.string.onboarding_welcome_desc)
        ));
        
        items.add(new OnboardingItem(
            getString(R.string.onboarding_plugin_title),
            getString(R.string.onboarding_plugin_desc)
        ));
        
        items.add(new OnboardingItem(
            getString(R.string.onboarding_dev_title),
            getString(R.string.onboarding_dev_desc)
        ));
        
        items.add(new OnboardingItem(
            getString(R.string.onboarding_web_title),
            getString(R.string.onboarding_web_desc)
        ));
        
        items.add(new OnboardingItem(
            getString(R.string.onboarding_ready_title),
            getString(R.string.onboarding_ready_desc)
        ));
    }
    
    private void setupViewPager() {
        adapter = new OnboardingAdapter(items);
        viewPager.setAdapter(adapter);
        
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateButtons(position);
                updateIndicators(position);
            }
        });
        
        updateButtons(0);
    }
    
    private void setupIndicators() {
        if (items.size() <= 1) {
            indicatorContainer.setVisibility(View.GONE);
            return;
        }
        
        indicatorContainer.setVisibility(View.VISIBLE);
        indicatorContainer.removeAllViews();
        indicators.clear();
        
        for (int i = 0; i < items.size(); i++) {
            ImageView dot = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            dot.setLayoutParams(params);
            
            if (i == 0) {
                dot.setImageResource(R.drawable.indicator_dot_selected);
            } else {
                dot.setImageResource(R.drawable.indicator_dot_normal);
            }
            
            indicatorContainer.addView(dot);
            indicators.add(dot);
        }
    }
    
    private void updateIndicators(int position) {
        for (int i = 0; i < indicators.size(); i++) {
            if (i == position) {
                indicators.get(i).setImageResource(R.drawable.indicator_dot_selected);
            } else {
                indicators.get(i).setImageResource(R.drawable.indicator_dot_normal);
            }
        }
    }
    
    private void setupListeners() {
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> finishOnboarding());
        }
        
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                int currentItem = viewPager.getCurrentItem();
                if (currentItem < items.size() - 1) {
                    viewPager.setCurrentItem(currentItem + 1, true);
                } else {
                    finishOnboarding();
                }
            });
        }
        
        if (btnStart != null) {
            btnStart.setOnClickListener(v -> finishOnboarding());
        }
    }
    
    private void updateButtons(int position) {
        boolean isLastPage = position == items.size() - 1;
        
        if (btnNext != null) {
            btnNext.setVisibility(isLastPage ? View.GONE : View.VISIBLE);
        }
        if (btnStart != null) {
            btnStart.setVisibility(isLastPage ? View.VISIBLE : View.GONE);
        }
        
        if (btnSkip != null) {
            if (position == 0 && !isVersionUpdate) {
                btnSkip.setVisibility(View.GONE);
            } else {
                btnSkip.setVisibility(View.VISIBLE);
            }
        }
    }
    
    private void finishOnboarding() {
        if (isFinishing) return;
        isFinishing = true;
        
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(OnboardingActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 50);
    }
    
    private static class OnboardingItem {
        String title;
        String description;
        
        OnboardingItem(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }
    
    private static class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.ViewHolder> {
        
        private List<OnboardingItem> items;
        
        OnboardingAdapter(List<OnboardingItem> items) {
            this.items = items;
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OnboardingItem item = items.get(position);
            holder.tvTitle.setText(item.title);
            holder.tvDescription.setText(item.description);
        }
        
        @Override
        public int getItemCount() {
            return items.size();
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle;
            TextView tvDescription;
            
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tv_title);
                tvDescription = itemView.findViewById(R.id.tv_description);
            }
        }
    }
}
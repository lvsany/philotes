package com.example.philotes;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.philotes.data.api.OpenAIService;
import com.example.philotes.domain.RuleEngine;
import com.example.philotes.utils.AiSettingsManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SettingsFragment extends Fragment {

    private MaterialCardView cardApiConfig;

    private Spinner spinnerProvider;
    private TextInputEditText etApiKey;
    private TextInputEditText etBaseUrl;
    private TextInputEditText etModelName;
    private TextInputEditText etTriggerKeyword;
    private Button btnTestConnection;
    private Button btnAddKeyword;
    private Button btnOpenAccessibilitySettings;
    private Button btnOpenAppPermissionSettings;
    private TextView tvStatus;
    private TextView tvPermissionStatus;
    private RadioGroup rgRoutingPolicy;
    private LinearLayout layoutKeywordList;

    private AiSettingsManager settingsManager;
    private final List<String> customKeywords = new ArrayList<>();

    private static class ApiProvider {
        String name;
        String baseUrl;
        String defaultModel;

        ApiProvider(String name, String baseUrl, String defaultModel) {
            this.name = name;
            this.baseUrl = baseUrl;
            this.defaultModel = defaultModel;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final ApiProvider[] providers = {
            new ApiProvider("OpenAI", "https://api.openai.com/v1", "gpt-3.5-turbo"),
            new ApiProvider("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
            new ApiProvider("自定义", "", "")
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        settingsManager = new AiSettingsManager(requireContext());

        initViews(view);
        loadSettings();
        setupListeners();
        updateUiState();
    }

    private void initViews(View view) {
        cardApiConfig = view.findViewById(R.id.cardApiConfig);

        spinnerProvider = view.findViewById(R.id.spinnerProvider);
        etApiKey = view.findViewById(R.id.etApiKey);
        etBaseUrl = view.findViewById(R.id.etBaseUrl);
        etModelName = view.findViewById(R.id.etModelName);
        etTriggerKeyword = view.findViewById(R.id.etTriggerKeyword);
        btnTestConnection = view.findViewById(R.id.btnTestConnection);
        btnAddKeyword = view.findViewById(R.id.btnAddKeyword);
        btnOpenAccessibilitySettings = view.findViewById(R.id.btnOpenAccessibilitySettings);
        btnOpenAppPermissionSettings = view.findViewById(R.id.btnOpenAppPermissionSettings);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvPermissionStatus = view.findViewById(R.id.tvPermissionStatus);
        rgRoutingPolicy = view.findViewById(R.id.rgRoutingPolicy);
        layoutKeywordList = view.findViewById(R.id.layoutKeywordList);

        ArrayAdapter<ApiProvider> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                providers);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProvider.setAdapter(adapter);
    }

    private void loadSettings() {
        isLoadingSettings = true;
        String apiKey = settingsManager.getApiKey();
        String baseUrl = settingsManager.getBaseUrl();
        String modelName = settingsManager.getModelName();
        String provider = settingsManager.getApiProvider();

        if (apiKey != null) {
            etApiKey.setText(apiKey);
        }
        if (baseUrl != null) {
            etBaseUrl.setText(baseUrl);
        }
        if (modelName != null) {
            etModelName.setText(modelName);
        }

        int providerIndex = 0;
        if (AiSettingsManager.PROVIDER_DEEPSEEK.equals(provider)) {
            providerIndex = 1;
        } else if (AiSettingsManager.PROVIDER_CUSTOM.equals(provider)) {
            providerIndex = 2;
        }
        spinnerProvider.setSelection(providerIndex);

        String routingPolicy = settingsManager.getRoutingPolicy();
        if (AiSettingsManager.ROUTING_LOCAL_ONLY.equals(routingPolicy)) {
            rgRoutingPolicy.check(R.id.rbRoutingLocalOnly);
        } else if (AiSettingsManager.ROUTING_CLOUD_ONLY.equals(routingPolicy)) {
            rgRoutingPolicy.check(R.id.rbRoutingCloudOnly);
        } else {
            rgRoutingPolicy.check(R.id.rbRoutingSmart);
        }

        updateUiState();
        customKeywords.clear();
        customKeywords.addAll(settingsManager.getCustomTriggerKeywords());
        renderKeywordList();
        applyRuleEngineRealtime();

        if (!settingsManager.isKeywordsSeeded()) {
            customKeywords.clear();
            customKeywords.addAll(Arrays.asList("开会", "导航"));
            settingsManager.setCustomTriggerKeywords(customKeywords);
            settingsManager.markKeywordsSeeded();
            renderKeywordList();
            applyRuleEngineRealtime();
        }

        isLoadingSettings = false;
        updateStatusText();
    }

    private boolean isLoadingSettings = false;

    private final TextWatcher apiAutoSaveWatcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override
        public void afterTextChanged(Editable s) {
            if (!isLoadingSettings) {
                autoSaveApiConfig();
            }
        }
    };

    private void setupListeners() {
        spinnerProvider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                ApiProvider provider = providers[position];
                if (!provider.baseUrl.isEmpty()) {
                    etBaseUrl.setText(provider.baseUrl);
                    etModelName.setText(provider.defaultModel);
                }
                if (!isLoadingSettings) {
                    autoSaveApiConfig();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        etApiKey.addTextChangedListener(apiAutoSaveWatcher);
        etBaseUrl.addTextChangedListener(apiAutoSaveWatcher);
        etModelName.addTextChangedListener(apiAutoSaveWatcher);

        btnTestConnection.setOnClickListener(v -> testConnection());
        btnAddKeyword.setOnClickListener(v -> {
            String keyword = etTriggerKeyword.getText() == null
                    ? ""
                    : etTriggerKeyword.getText().toString().trim();
            if (keyword.isEmpty()) {
                Toast.makeText(requireContext(), "请输入关键词", Toast.LENGTH_SHORT).show();
                return;
            }
            addKeyword(keyword);
            etTriggerKeyword.setText("");
        });

        rgRoutingPolicy.setOnCheckedChangeListener((group, checkedId) -> {
            String policy = AiSettingsManager.ROUTING_SMART;
            if (checkedId == R.id.rbRoutingLocalOnly) {
                policy = AiSettingsManager.ROUTING_LOCAL_ONLY;
            } else if (checkedId == R.id.rbRoutingCloudOnly) {
                policy = AiSettingsManager.ROUTING_CLOUD_ONLY;
            }
            settingsManager.setRoutingPolicy(policy);
            updateUiState();
            updateStatusText();
        });

        btnOpenAccessibilitySettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        btnOpenAppPermissionSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
            startActivity(intent);
        });
    }

    private void updateUiState() {
        boolean showCloud = settingsManager.needsCloudConfig();
        cardApiConfig.setVisibility(showCloud ? View.VISIBLE : View.GONE);
    }

    private void testConnection() {
        String apiKey = etApiKey.getText() == null ? "" : etApiKey.getText().toString().trim();
        String baseUrl = etBaseUrl.getText() == null ? "" : etBaseUrl.getText().toString().trim();
        String modelName = etModelName.getText() == null ? "" : etModelName.getText().toString().trim();

        if (apiKey.isEmpty()) {
            Toast.makeText(requireContext(), "请输入 API Key", Toast.LENGTH_SHORT).show();
            return;
        }

        if (baseUrl.isEmpty()) {
            Toast.makeText(requireContext(), "请输入 Base URL", Toast.LENGTH_SHORT).show();
            return;
        }

        if (modelName.isEmpty()) {
            Toast.makeText(requireContext(), "请输入模型名称", Toast.LENGTH_SHORT).show();
            return;
        }

        btnTestConnection.setEnabled(false);
        btnTestConnection.setText("测试中...");
        tvStatus.setText("正在测试连接...");

        new Thread(() -> {
            try {
                OpenAIService service = new OpenAIService(apiKey, baseUrl, modelName);
                String response = service.chatCompletion(
                        "You are a helpful assistant. Reply in one word.",
                        "Say 'OK' if you can receive this message.");

                requireActivity().runOnUiThread(() -> {
                    if (response != null && !response.isEmpty()) {
                        tvStatus.setText("✅ 连接成功！\n响应: " + response);
                        Toast.makeText(requireContext(), "连接测试成功！", Toast.LENGTH_SHORT).show();
                    } else {
                        tvStatus.setText("❌ 连接失败：无响应");
                        Toast.makeText(requireContext(), "连接失败：无响应", Toast.LENGTH_SHORT).show();
                    }
                    btnTestConnection.setEnabled(true);
                    btnTestConnection.setText("测试连接");
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    tvStatus.setText("❌ 连接失败：" + e.getMessage());
                    Toast.makeText(requireContext(), "连接失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnTestConnection.setEnabled(true);
                    btnTestConnection.setText("测试连接");
                });
            }
        }).start();
    }

    private void autoSaveApiConfig() {
        if (!settingsManager.needsCloudConfig()) {
            return;
        }
        String apiKey = etApiKey.getText() == null ? "" : etApiKey.getText().toString().trim();
        String baseUrl = etBaseUrl.getText() == null ? "" : etBaseUrl.getText().toString().trim();
        String modelName = etModelName.getText() == null ? "" : etModelName.getText().toString().trim();

        settingsManager.setApiKey(apiKey);
        settingsManager.setBaseUrl(baseUrl);
        settingsManager.setModelName(modelName);

        int selectedPosition = spinnerProvider.getSelectedItemPosition();
        String providerType;
        if (selectedPosition == 0) {
            providerType = AiSettingsManager.PROVIDER_OPENAI;
        } else if (selectedPosition == 1) {
            providerType = AiSettingsManager.PROVIDER_DEEPSEEK;
        } else {
            providerType = AiSettingsManager.PROVIDER_CUSTOM;
        }
        settingsManager.setApiProvider(providerType);
        settingsManager.applyToLlmConfig();
        updateStatusText();
    }

    private void addKeyword(String keyword) {
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        for (String existing : customKeywords) {
            if (existing.toLowerCase(Locale.ROOT).equals(normalized)) {
                Toast.makeText(requireContext(), "关键词已存在", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        customKeywords.add(keyword.trim());
        settingsManager.setCustomTriggerKeywords(customKeywords);
        renderKeywordList();
        applyRuleEngineRealtime();
    }

    private void removeKeyword(String keyword) {
        customKeywords.remove(keyword);
        settingsManager.setCustomTriggerKeywords(customKeywords);
        renderKeywordList();
        applyRuleEngineRealtime();
    }

    private void renderKeywordList() {
        layoutKeywordList.removeAllViews();

        if (customKeywords.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("当前没有关键词，添加后才会触发主动分析");
            empty.setTextSize(12f);
            empty.setTextColor(0xFF5A6F8F);
            layoutKeywordList.addView(empty);
            return;
        }

        for (String keyword : customKeywords) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.setBackgroundResource(R.drawable.bg_card_surface);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(6);
            row.setLayoutParams(rowLp);

            TextView tv = new TextView(requireContext());
            tv.setText(keyword);
            tv.setTextSize(14f);
            tv.setTextColor(0xFF102445);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(tvLp);

            TextView btnDelete = new TextView(requireContext());
            btnDelete.setText("删除");
            btnDelete.setTextSize(12f);
            btnDelete.setTextColor(0xFFB62828);
            btnDelete.setPadding(dp(8), dp(2), dp(8), dp(2));
            btnDelete.setOnClickListener(v -> removeKeyword(keyword));

            row.addView(tv);
            row.addView(btnDelete);
            layoutKeywordList.addView(row);
        }
    }

    private void applyRuleEngineRealtime() {
        List<String> keywords = new ArrayList<>();
        for (String keyword : customKeywords) {
            if (!TextUtils.isEmpty(keyword)) {
                keywords.add(keyword.trim().toLowerCase(Locale.ROOT));
            }
        }
        RuleEngine.getInstance().updateRules(keywords, Collections.emptyList());
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (density * value);
    }

    private void updatePermissionStatus() {
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();

        StringBuilder status = new StringBuilder();
        status.append("无障碍服务: ").append(accessibilityEnabled ? "已开启" : "未开启");
        tvPermissionStatus.setText(status.toString());
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                requireContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) {
            return false;
        }

        ComponentName serviceComponent = new ComponentName(requireContext(), FloatingButtonService.class);
        String serviceName = serviceComponent.flattenToString();
        return enabledServices.contains(serviceName);
    }

    private void updateStatusText() {
        StringBuilder status = new StringBuilder();

        String policy = settingsManager.getRoutingPolicy();
        if (AiSettingsManager.ROUTING_LOCAL_ONLY.equals(policy)) {
            status.append("路由: 强制端侧（隐私优先）\n");
            status.append("状态: 仅使用本地模型，需要下载模型文件");
        } else if (AiSettingsManager.ROUTING_CLOUD_ONLY.equals(policy)) {
            status.append("路由: 强制云端（高精度）\n");
            if (settingsManager.isApiConfigured()) {
                status.append("状态: 已配置\n");
                status.append("模型: ").append(settingsManager.getModelName());
            } else {
                status.append("状态: 未配置 API Key");
            }
        } else {
            status.append("路由: 智能路由（推荐）\n");
            if (settingsManager.isApiConfigured()) {
                status.append("状态: 本地优先，云端兜底已就绪\n");
                status.append("云端模型: ").append(settingsManager.getModelName());
            } else {
                status.append("状态: 本地优先，未配置云端兜底");
            }
        }

        status.append("\n自定义关键词: ").append(customKeywords.size()).append(" 个");

        tvStatus.setText(status.toString());
        updatePermissionStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionStatus();
    }
}

package com.example.philotes;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.philotes.data.api.OpenAIService;
import com.example.philotes.utils.AiSettingsManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    private RadioGroup rgAiMode;
    private RadioButton rbOnDevice;
    private RadioButton rbCloudApi;
    private MaterialCardView cardApiConfig;

    private Spinner spinnerProvider;
    private TextInputEditText etApiKey;
    private TextInputEditText etBaseUrl;
    private TextInputEditText etModelName;
    private Button btnTestConnection;
    private Button btnSave;
    private TextView tvStatus;

    private AiSettingsManager settingsManager;

    // API 提供商配置
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

    private ApiProvider[] providers = {
        new ApiProvider("OpenAI", "https://api.openai.com/v1", "gpt-3.5-turbo"),
        new ApiProvider("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        new ApiProvider("自定义", "", "")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 启用返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("AI 设置");
        }

        settingsManager = new AiSettingsManager(this);

        initViews();
        loadSettings();
        setupListeners();
        updateUiState();
    }

    private void initViews() {
        rgAiMode = findViewById(R.id.rgAiMode);
        rbOnDevice = findViewById(R.id.rbOnDevice);
        rbCloudApi = findViewById(R.id.rbCloudApi);
        cardApiConfig = findViewById(R.id.cardApiConfig);

        spinnerProvider = findViewById(R.id.spinnerProvider);
        etApiKey = findViewById(R.id.etApiKey);
        etBaseUrl = findViewById(R.id.etBaseUrl);
        etModelName = findViewById(R.id.etModelName);
        btnTestConnection = findViewById(R.id.btnTestConnection);
        btnSave = findViewById(R.id.btnSave);
        tvStatus = findViewById(R.id.tvStatus);

        // 设置 Spinner 适配器
        ArrayAdapter<ApiProvider> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            providers
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProvider.setAdapter(adapter);
    }

    private void loadSettings() {
        // 加载 AI 模式
        String mode = settingsManager.getAiMode();
        if (AiSettingsManager.MODE_CLOUD_API.equals(mode)) {
            rbCloudApi.setChecked(true);
        } else {
            rbOnDevice.setChecked(true);
        }

        // 加载 API 配置
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

        // 根据保存的提供商类型选择 Spinner
        int providerIndex = 0; // 默认 OpenAI
        if (AiSettingsManager.PROVIDER_DEEPSEEK.equals(provider)) {
            providerIndex = 1;
        } else if (AiSettingsManager.PROVIDER_CUSTOM.equals(provider)) {
            providerIndex = 2;
        }
        spinnerProvider.setSelection(providerIndex);

        updateStatusText();
    }

    private void setupListeners() {
        // AI 模式切换
        rgAiMode.setOnCheckedChangeListener((group, checkedId) -> updateUiState());

        // API 提供商选择
        spinnerProvider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                ApiProvider provider = providers[position];
                if (!provider.baseUrl.isEmpty()) {
                    etBaseUrl.setText(provider.baseUrl);
                    etModelName.setText(provider.defaultModel);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // 测试连接
        btnTestConnection.setOnClickListener(v -> testConnection());

        // 保存设置
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void updateUiState() {
        boolean isCloudMode = rbCloudApi.isChecked();
        cardApiConfig.setVisibility(isCloudMode ? View.VISIBLE : View.GONE);
    }

    private void testConnection() {
        String apiKey = etApiKey.getText().toString().trim();
        String baseUrl = etBaseUrl.getText().toString().trim();
        String modelName = etModelName.getText().toString().trim();

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show();
            return;
        }

        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "请输入 Base URL", Toast.LENGTH_SHORT).show();
            return;
        }

        if (modelName.isEmpty()) {
            Toast.makeText(this, "请输入模型名称", Toast.LENGTH_SHORT).show();
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
                    "Say 'OK' if you can receive this message."
                );

                runOnUiThread(() -> {
                    if (response != null && !response.isEmpty()) {
                        tvStatus.setText("✅ 连接成功！\n响应: " + response);
                        Toast.makeText(this, "连接测试成功！", Toast.LENGTH_SHORT).show();
                    } else {
                        tvStatus.setText("❌ 连接失败：无响应");
                        Toast.makeText(this, "连接失败：无响应", Toast.LENGTH_SHORT).show();
                    }
                    btnTestConnection.setEnabled(true);
                    btnTestConnection.setText("测试连接");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvStatus.setText("❌ 连接失败：" + e.getMessage());
                    Toast.makeText(this, "连接失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnTestConnection.setEnabled(true);
                    btnTestConnection.setText("测试连接");
                });
            }
        }).start();
    }

    private void saveSettings() {
        // 保存 AI 模式
        String mode = rbCloudApi.isChecked() ?
            AiSettingsManager.MODE_CLOUD_API :
            AiSettingsManager.MODE_ON_DEVICE;
        settingsManager.setAiMode(mode);

        // 如果是云端模式，保存 API 配置
        if (rbCloudApi.isChecked()) {
            String apiKey = etApiKey.getText().toString().trim();
            String baseUrl = etBaseUrl.getText().toString().trim();
            String modelName = etModelName.getText().toString().trim();

            if (apiKey.isEmpty()) {
                Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show();
                return;
            }

            settingsManager.setApiKey(apiKey);
            settingsManager.setBaseUrl(baseUrl);
            settingsManager.setModelName(modelName);

            // 保存提供商类型
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
        }

        // 应用设置
        settingsManager.applyToLlmConfig();

        updateStatusText();
        Toast.makeText(this, "设置已保存！请重启应用使设置生效", Toast.LENGTH_LONG).show();

        // 返回主页面
        finish();
    }

    private void updateStatusText() {
        String mode = settingsManager.getAiMode();
        StringBuilder status = new StringBuilder();

        if (AiSettingsManager.MODE_ON_DEVICE.equals(mode)) {
            status.append("模式: 端侧模型（本地推理）\n");
            status.append("状态: 需要下载模型文件");
        } else {
            status.append("模式: 云端 API（联网模式）\n");
            if (settingsManager.isApiConfigured()) {
                status.append("状态: 已配置\n");
                status.append("模型: ").append(settingsManager.getModelName());
            } else {
                status.append("状态: 未配置 API Key");
            }
        }

        tvStatus.setText(status.toString());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

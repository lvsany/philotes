package com.example.philotes;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.domain.ActionParser;
import com.example.philotes.utils.ModelUtils;
import com.google.gson.GsonBuilder;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private EditText etInput;
    private Button btnParse;
    private TextView tvResult;

    // Download UI
    private LinearLayout layoutDownload;
    private ProgressBar progressBar;
    private Button btnDownload;
    private TextView tvDownloadStatus;

    private ActionParser actionParser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etInput = findViewById(R.id.etInput);
        btnParse = findViewById(R.id.btnParse);
        tvResult = findViewById(R.id.tvResult);

        layoutDownload = findViewById(R.id.layoutDownload);
        progressBar = findViewById(R.id.progressBar);
        btnDownload = findViewById(R.id.btnDownload);
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus);

        // Check Model
        File modelFile = ModelUtils.getModelFile(this);

        if (modelFile.exists()) {
             initModel(modelFile);
        } else {
             showDownloadUI();
        }

        btnDownload.setOnClickListener(v -> startDownload(modelFile));

        btnParse.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show();
                return;
            }
            performParse(text);
        });
    }

    private void showDownloadUI() {
        layoutDownload.setVisibility(View.VISIBLE);
        btnParse.setEnabled(false);
        etInput.setEnabled(false);
        tvResult.setText("Model file missing. Please download to continue.");
    }

    private void startDownload(File targetFile) {
        btnDownload.setEnabled(false);
        tvDownloadStatus.setText("Downloading... (This may take a while)");

        ModelUtils.downloadModel(this, ModelUtils.MODEL_URL, targetFile, new ModelUtils.DownloadListener() {
            @Override
            public void onProgress(int percentage) {
                runOnUiThread(() -> progressBar.setProgress(percentage));
            }

            @Override
            public void onCompleted(File file) {
                runOnUiThread(() -> {
                     layoutDownload.setVisibility(View.GONE);
                     initModel(file);
                     Toast.makeText(MainActivity.this, "Download Complete!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                     String msg = "Download Failed.\n" +
                             "Check the 'MODEL_URL' in ModelUtils.java.\n" +
                             "Error: " + e.getMessage();
                     tvDownloadStatus.setText(msg);
                     tvDownloadStatus.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                     btnDownload.setEnabled(true);
                     btnDownload.setText("Retry Download");
                });
            }
        });
    }

    private void initModel(File modelFile) {
        // Initialize ActionParser with OnDeviceLlmService
        actionParser = new ActionParser(new com.example.philotes.data.api.OnDeviceLlmService(this, modelFile.getAbsolutePath()));

        btnParse.setEnabled(true);
        etInput.setEnabled(true);
        tvResult.setText("Model Ready: " + modelFile.getName());
    }

    private void performParse(String text) {
        tvResult.setText("Loading model and parsing (this may take a moment)...");
        btnParse.setEnabled(false);

        new Thread(() -> {
            try {
                // Ensure actionParser is initialized (should be if btn is enabled)
                if (actionParser == null) return;

                ActionPlan plan = actionParser.parse(text);
                // Pretty print the result
                String jsonResult = plan != null
                    ? new GsonBuilder().setPrettyPrinting().create().toJson(plan)
                    : "Error: Model returned null or invalid JSON.";

                runOnUiThread(() -> {
                    tvResult.setText(jsonResult);
                    btnParse.setEnabled(true);
                });
            } catch (Exception e) {
                e.printStackTrace();
                String errorMsg = "Error parsing: " + e.getMessage();
                runOnUiThread(() -> {
                    tvResult.setText(errorMsg);
                    btnParse.setEnabled(true);
                });
            }
        }).start();
    }
}

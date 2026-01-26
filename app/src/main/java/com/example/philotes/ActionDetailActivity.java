package com.example.philotes;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.domain.ActionExecutor;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
public class ActionDetailActivity extends AppCompatActivity {
    private EditText etTitle, etTime, etLocation, etDescription;
    private Button btnExecute;
    private ActionPlan actionPlan;
    private ActionExecutor actionExecutor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_action_detail);
        initViews();
        loadData();
        actionExecutor = new ActionExecutor(this);
        btnExecute.setOnClickListener(v -> executeAction());
    }
    private void initViews() {
        etTitle = findViewById(R.id.etTitle);
        etTime = findViewById(R.id.etTime);
        etLocation = findViewById(R.id.etLocation);
        etDescription = findViewById(R.id.etDescription);
        btnExecute = findViewById(R.id.btnExecute);
    }
    private void loadData() {
        String planJson = getIntent().getStringExtra("action_plan");
        if (planJson != null) {
            actionPlan = new Gson().fromJson(planJson, ActionPlan.class);
            Map<String, String> slots = actionPlan.getSlots();
            if (slots != null) {
                etTitle.setText(slots.getOrDefault("title", ""));
                etTime.setText(slots.getOrDefault("time", ""));
                etLocation.setText(slots.getOrDefault("location", ""));
                etDescription.setText(slots.getOrDefault("description", ""));
            }
        }
    }
    private void executeAction() {
        // Update slots from UI
        Map<String, String> slots = actionPlan.getSlots();
        if (slots == null) {
            slots = new HashMap<>();
            // actionPlan doesn't have a setSlots, but it has a slots field.
            // Since we deserialized it with Gson, we might need to be careful.
            // But let's assume getSlots() returns the reference if it's not null.
        }
        slots.put("title", etTitle.getText().toString());
        slots.put("time", etTime.getText().toString());
        slots.put("location", etLocation.getText().toString());
        slots.put("description", etDescription.getText().toString());

        Toast.makeText(this, "正在确认执行: " + slots.get("title"), Toast.LENGTH_SHORT).show();

        // Use ActionExecutor
        new Thread(() -> {
            ActionExecutor.ExecutionResult result = actionExecutor.execute(actionPlan);
            runOnUiThread(() -> {
                Toast.makeText(ActionDetailActivity.this, result.message, Toast.LENGTH_LONG).show();
                if (result.success) {
                    finish();
                }
            });
        }).start();
    }
}

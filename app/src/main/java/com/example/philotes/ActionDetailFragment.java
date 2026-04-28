package com.example.philotes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.domain.ActionExecutor;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class ActionDetailFragment extends Fragment {

    private static final String ARG_ACTION_PLAN_JSON = "arg_action_plan_json";

    private EditText etTitle;
    private EditText etTime;
    private EditText etLocation;
    private EditText etDescription;
    private Button btnExecute;

    private ActionPlan actionPlan;
    private ActionExecutor actionExecutor;

    public static ActionDetailFragment newInstance(String actionPlanJson) {
        ActionDetailFragment fragment = new ActionDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ACTION_PLAN_JSON, actionPlanJson);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_action_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        actionExecutor = new ActionExecutor(requireContext());

        etTitle = view.findViewById(R.id.etTitle);
        etTime = view.findViewById(R.id.etTime);
        etLocation = view.findViewById(R.id.etLocation);
        etDescription = view.findViewById(R.id.etDescription);
        btnExecute = view.findViewById(R.id.btnExecute);

        loadData();
        btnExecute.setOnClickListener(v -> executeAction());
    }

    private void loadData() {
        String planJson = null;
        if (getArguments() != null) {
            planJson = getArguments().getString(ARG_ACTION_PLAN_JSON);
        }

        if (planJson == null || planJson.isEmpty()) {
            Toast.makeText(requireContext(), "动作数据为空", Toast.LENGTH_SHORT).show();
            return;
        }

        actionPlan = new Gson().fromJson(planJson, ActionPlan.class);
        if (actionPlan == null) {
            Toast.makeText(requireContext(), "动作数据解析失败", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> slots = actionPlan.getSlots();
        if (slots != null) {
            etTitle.setText(slots.getOrDefault("title", ""));
            etTime.setText(slots.getOrDefault("time", ""));
            etLocation.setText(slots.getOrDefault("location", ""));
            etDescription.setText(slots.getOrDefault("description", ""));
        }
    }

    private void executeAction() {
        if (actionPlan == null) {
            Toast.makeText(requireContext(), "动作数据为空", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> slots = actionPlan.getSlots();
        if (slots == null) {
            slots = new HashMap<>();
        }

        slots.put("title", etTitle.getText().toString());
        slots.put("time", etTime.getText().toString());
        slots.put("location", etLocation.getText().toString());
        slots.put("description", etDescription.getText().toString());

        Toast.makeText(requireContext(), "正在确认执行: " + slots.get("title"), Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            ActionExecutor.ExecutionResult result = actionExecutor.execute(actionPlan);
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show();
                if (result.success && requireActivity() instanceof MainActivity) {
                    ((MainActivity) requireActivity()).navigateToHomeTab();
                }
            });
        }).start();
    }
}

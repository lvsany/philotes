package com.example.philotes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.philotes.data.model.ActionPlan;

import java.util.List;
import java.util.Map;

public class ActionCardAdapter extends RecyclerView.Adapter<ActionCardAdapter.ViewHolder> {

    private List<ActionPlan> planList;
    private OnActionClickListener listener;

    public interface OnActionClickListener {
        void onExecute(ActionPlan plan);
        void onEdit(ActionPlan plan);
    }

    public ActionCardAdapter(List<ActionPlan> planList, OnActionClickListener listener) {
        this.planList = planList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_action_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActionPlan plan = planList.get(position);

        // Bind Title & Confidence
        String title = "未知动作";
        if (plan.getType() != null) {
            title = plan.getType().toString();
        }
        if (plan.getSlots() != null && plan.getSlots().containsKey("title")) {
            title += ": " + plan.getSlots().get("title");
        }
        holder.tvActionTitle.setText(title);

        holder.tvConfidence.setText(String.format(java.util.Locale.getDefault(), "%.0f%%", plan.getConfidence() * 100));

        // Bind Details
        StringBuilder details = new StringBuilder();
        if (plan.getSlots() != null) {
            for (Map.Entry<String, String> entry : plan.getSlots().entrySet()) {
                if (!entry.getKey().equals("title")) {
                    details.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }
        }
        if (details.length() == 0) {
            details.append(plan.getOriginalText());
        }
        holder.tvActionDetails.setText(details.toString().trim());

        // Bind Buttons
        holder.btnExecute.setOnClickListener(v -> {
            if (listener != null) listener.onExecute(plan);
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(plan);
        });
    }

    @Override
    public int getItemCount() {
        return planList.size();
    }

    public void addCard(ActionPlan plan) {
        planList.add(0, plan);
        notifyItemInserted(0);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvActionTitle, tvActionDetails, tvConfidence;
        Button btnEdit, btnExecute;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvActionTitle = itemView.findViewById(R.id.tvActionTitle);
            tvActionDetails = itemView.findViewById(R.id.tvActionDetails);
            tvConfidence = itemView.findViewById(R.id.tvConfidence);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnExecute = itemView.findViewById(R.id.btnExecute);
        }
    }
}

package com.example.philotes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.philotes.data.model.ActionPlan;
import java.util.List;
import java.util.Map;
public class ActionCardAdapter extends RecyclerView.Adapter<ActionCardAdapter.ViewHolder> {
    private List<ActionPlan> actionPlans;
    private OnActionClickListener listener;
    public interface OnActionClickListener {
        void onExecute(ActionPlan plan);
        void onEdit(ActionPlan plan);
    }
    public ActionCardAdapter(List<ActionPlan> actionPlans, OnActionClickListener listener) {
        this.actionPlans = actionPlans;
        this.listener = listener;
    }
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_action_card, parent, false);
        return new ViewHolder(view);
    }
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActionPlan plan = actionPlans.get(position);
        holder.tvActionTitle.setText(plan.getType().name());
        StringBuilder details = new StringBuilder();
        if (plan.getSlots() != null) {
            for (Map.Entry<String, String> entry : plan.getSlots().entrySet()) {
                details.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        holder.tvActionDetails.setText(details.toString().trim());
        holder.tvConfidence.setText((int)(plan.getConfidence() * 100) + "%");
        holder.btnExecute.setOnClickListener(v -> listener.onExecute(plan));
        holder.btnEdit.setOnClickListener(v -> listener.onEdit(plan));
    }
    @Override
    public int getItemCount() {
        return actionPlans.size();
    }
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvActionTitle;
        TextView tvActionDetails;
        TextView tvConfidence;
        Button btnExecute;
        Button btnEdit;
        ImageView ivActionIcon;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvActionTitle = itemView.findViewById(R.id.tvActionTitle);
            tvActionDetails = itemView.findViewById(R.id.tvActionDetails);
            tvConfidence = itemView.findViewById(R.id.tvConfidence);
            btnExecute = itemView.findViewById(R.id.btnExecute);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            ivActionIcon = itemView.findViewById(R.id.ivActionIcon);
        }
    }
}

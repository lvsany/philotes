package com.example.philotes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.philotes.data.model.ActionPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ActionCardAdapter extends RecyclerView.Adapter<ActionCardAdapter.ViewHolder> {

    private final List<ActionCardItem> cardList;
    private final OnActionClickListener listener;

    public interface OnActionClickListener {
        void onExecute(ActionPlan plan);
        void onEdit(ActionPlan plan);
    }

    public ActionCardAdapter(List<ActionCardItem> cardList, OnActionClickListener listener) {
        this.cardList = cardList == null ? new ArrayList<>() : cardList;
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
        ActionCardItem card = cardList.get(position);

        if (card.isStreaming()) {
            holder.tvActionTitle.setText("实时解析中...");
            holder.tvConfidence.setText("...");
            String text = card.getStreamingText();
            holder.tvActionDetails.setText(text == null || text.trim().isEmpty() ? "模型推理中，请稍候" : text);
            holder.btnExecute.setVisibility(View.GONE);
            holder.btnEdit.setVisibility(View.GONE);
            holder.btnExecute.setOnClickListener(null);
            holder.btnEdit.setOnClickListener(null);
            return;
        }

        ActionPlan plan = card.getPlan();
        if (plan == null) {
            holder.tvActionTitle.setText("解析异常");
            holder.tvConfidence.setText("0%");
            holder.tvActionDetails.setText("未收到有效动作结果");
            holder.btnExecute.setVisibility(View.GONE);
            holder.btnEdit.setVisibility(View.GONE);
            return;
        }

        holder.btnExecute.setVisibility(View.VISIBLE);
        holder.btnEdit.setVisibility(View.VISIBLE);

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
        return cardList.size();
    }

    public void showStreamingCard(String requestId) {
        int index = findCardIndex(requestId);
        if (index >= 0) {
            cardList.set(index, ActionCardItem.streaming(requestId, ""));
            notifyItemChanged(index);
            return;
        }
        cardList.add(0, ActionCardItem.streaming(requestId, ""));
        notifyItemInserted(0);
    }

    public void updateStreamingCard(String requestId, String streamingText) {
        int index = findCardIndex(requestId);
        if (index < 0) {
            return;
        }
        cardList.set(index, ActionCardItem.streaming(requestId, streamingText));
        notifyItemChanged(index);
    }

    public int replaceStreamingCard(String requestId, ActionPlan plan) {
        int index = findCardIndex(requestId);
        if (index >= 0) {
            cardList.set(index, ActionCardItem.result(requestId, plan));
            notifyItemChanged(index);
            return index;
        }
        cardList.add(0, ActionCardItem.result(requestId, plan));
        notifyItemInserted(0);
        return 0;
    }

    public void removeCardByRequestId(String requestId) {
        int index = findCardIndex(requestId);
        if (index >= 0) {
            cardList.remove(index);
            notifyItemRemoved(index);
        }
    }

    private int findCardIndex(String requestId) {
        for (int i = 0; i < cardList.size(); i++) {
            if (requestId.equals(cardList.get(i).getRequestId())) {
                return i;
            }
        }
        return -1;
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

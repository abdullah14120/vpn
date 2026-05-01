package com.vpn.ab.core;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private final List<String> logEntries;

    public LogAdapter(List<String> logEntries) {
        this.logEntries = logEntries;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // نستخدم TextView بسيط لكل سطر
        TextView textView = new TextView(parent.getContext());
        textView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setPadding(8, 4, 8, 4);
        textView.setTextColor(android.graphics.Color.parseColor("#00FF41")); // لون أخضر ماتريكس
        textView.setTextSize(11f);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE); // خط Terminal
        return new LogViewHolder(textView);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.textView.setText(logEntries.get(position));
    }

    @Override
    public int getItemCount() {
        return logEntries.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        LogViewHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView;
        }
    }
}

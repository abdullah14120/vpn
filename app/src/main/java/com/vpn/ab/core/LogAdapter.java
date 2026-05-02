package com.vpn.ab.core;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private List<String> logs;

    public LogAdapter(List<String> logs) {
        this.logs = logs;
    }

    // دالة احترافية لتحديث القائمة من الـ MainActivity
    public void updateLogs(List<String> newLogs) {
        this.logs = newLogs;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        tv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setPadding(16, 8, 16, 8); // زيادة الحواف قليلاً لسهولة القراءة
        tv.setTextColor(android.graphics.Color.parseColor("#00FF41")); 
        tv.setTextSize(12);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE); 
        return new LogViewHolder(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        String logLine = logs.get(position);
        holder.textView.setText(logLine);

        // إضافة ميزة نسخ السطر عند الضغط المطول (مفيدة جداً للمطورين)
        holder.textView.setOnLongClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Shield Log", logLine);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(v.getContext(), "تم نسخ السجل", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return logs != null ? logs.size() : 0;
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        LogViewHolder(View v) {
            super(v);
            textView = (TextView) v;
        }
    }
}

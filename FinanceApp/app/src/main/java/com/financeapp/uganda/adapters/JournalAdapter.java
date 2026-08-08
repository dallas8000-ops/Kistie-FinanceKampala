package com.financeapp.uganda.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.R;
import com.financeapp.uganda.models.JournalEntry;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JournalAdapter extends RecyclerView.Adapter<JournalAdapter.Holder> {
    private List<JournalEntry> entries = new ArrayList<>();
    private OnJournalClickListener listener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public interface OnJournalClickListener {
        void onJournalClick(JournalEntry entry);
    }

    public void setListener(OnJournalClickListener listener) { this.listener = listener; }

    public void setEntries(List<JournalEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_journal, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        JournalEntry e = entries.get(position);
        h.tvDate.setText(e.getDate() != null ? fmt.format(e.getDate()) : "");
        h.tvDesc.setText(e.getDescription());
        h.tvRef.setText(e.getReference() + " · " + e.getSourceType());
    }

    @Override
    public int getItemCount() { return entries.size(); }

    class Holder extends RecyclerView.ViewHolder {
        TextView tvDate, tvDesc, tvRef;
        Holder(View v) {
            super(v);
            tvDate = v.findViewById(R.id.tvDate);
            tvDesc = v.findViewById(R.id.tvDescription);
            tvRef = v.findViewById(R.id.tvReference);
            v.setOnClickListener(x -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION)
                    listener.onJournalClick(entries.get(getAdapterPosition()));
            });
        }
    }
}

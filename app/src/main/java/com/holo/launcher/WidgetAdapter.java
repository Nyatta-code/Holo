package com.holo.launcher;

import android.appwidget.AppWidgetProviderInfo;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** The "Widgets" tab of the drawer: a list of installed widget providers. */
public class WidgetAdapter extends RecyclerView.Adapter<WidgetAdapter.VH> {

    public static class Entry {
        public AppWidgetProviderInfo info;
        public String label;
        public String sizeText;
        public Bitmap preview;
    }

    public interface OnPick {
        void onPick(AppWidgetProviderInfo info);
    }

    private final List<Entry> entries = new ArrayList<>();
    private final OnPick onPick;

    public WidgetAdapter(OnPick onPick) {
        this.onPick = onPick;
    }

    public void setEntries(List<Entry> list) {
        entries.clear();
        entries.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_widget, parent, false));
    }

    @Override
    public void onBindViewHolder(VH h, int position) {
        final Entry e = entries.get(position);
        h.preview.setImageBitmap(e.preview);
        h.label.setText(e.label);
        h.size.setText(e.sizeText);
        h.itemView.setOnClickListener(v -> onPick.onPick(e.info));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView preview;
        final TextView label;
        final TextView size;

        VH(View v) {
            super(v);
            preview = v.findViewById(R.id.preview);
            label = v.findViewById(R.id.label);
            size = v.findViewById(R.id.size);
        }
    }
}

package com.holo.launcher;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Drawer grid. Only draws pre-rendered bitmaps, so scrolling stays smooth. */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {

    public interface Actions {
        void onClick(AppInfo app);

        void onLongClick(View anchor, AppInfo app);
    }

    private final List<AppInfo> source = new ArrayList<>();
    private final List<AppInfo> shown = new ArrayList<>();
    private final Actions actions;
    private final int iconPx;
    private final int labelSp;
    private final boolean showLabels;
    private String query = "";

    public AppAdapter(Actions actions, int iconPx, int labelSp, boolean showLabels) {
        this.actions = actions;
        this.iconPx = iconPx;
        this.labelSp = labelSp;
        this.showLabels = showLabels;
        setHasStableIds(true);
    }

    public void setApps(List<AppInfo> apps) {
        source.clear();
        source.addAll(apps);
        refilter();
    }

    public void filter(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.getDefault());
        refilter();
    }

    public AppInfo first() {
        return shown.isEmpty() ? null : shown.get(0);
    }

    private void refilter() {
        shown.clear();
        for (AppInfo app : source) {
            if (query.isEmpty() || app.labelLower.contains(query)) {
                shown.add(app);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return shown.get(position).id;
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        VH vh = new VH(v);
        ViewGroup.LayoutParams lp = vh.icon.getLayoutParams();
        lp.width = iconPx;
        lp.height = iconPx;
        vh.icon.setLayoutParams(lp);
        vh.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSp);
        vh.label.setVisibility(showLabels ? View.VISIBLE : View.GONE);
        return vh;
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        final AppInfo app = shown.get(position);
        holder.icon.setImageBitmap(app.icon);
        holder.label.setText(app.label);
        holder.itemView.setOnClickListener(v -> actions.onClick(app));
        holder.itemView.setOnLongClickListener(v -> {
            actions.onLongClick(v, app);
            return true;
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
        }
    }
}

package com.holo.launcher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Grid adapter for the app drawer, with simple substring filtering. */
public class AppAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<AppInfo> source = new ArrayList<>();
    private final List<AppInfo> shown = new ArrayList<>();
    private String query = "";

    public AppAdapter(Context context) {
        inflater = LayoutInflater.from(context);
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

    private void refilter() {
        shown.clear();
        for (AppInfo app : source) {
            if (query.isEmpty()
                    || app.label.toString().toLowerCase(Locale.getDefault()).contains(query)) {
                shown.add(app);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return shown.size();
    }

    @Override
    public AppInfo getItem(int position) {
        return shown.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static class Holder {
        ImageView icon;
        TextView label;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        Holder holder;
        if (view == null) {
            view = inflater.inflate(R.layout.item_app, parent, false);
            holder = new Holder();
            holder.icon = view.findViewById(R.id.icon);
            holder.label = view.findViewById(R.id.label);
            view.setTag(holder);
        } else {
            holder = (Holder) view.getTag();
        }
        AppInfo app = shown.get(position);
        holder.icon.setImageDrawable(app.icon);
        holder.label.setText(app.label);
        return view;
    }
}

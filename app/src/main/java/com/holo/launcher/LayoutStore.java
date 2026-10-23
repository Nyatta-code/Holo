package com.holo.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Persists what sits on the home screen pages (JSON in a private preferences file). */
public class LayoutStore {

    private final SharedPreferences sp;
    private final List<ItemInfo> items = new ArrayList<>();
    private int nextId = 1;

    public LayoutStore(Context c) {
        sp = c.getSharedPreferences("layout", Context.MODE_PRIVATE);
        load();
    }

    public List<ItemInfo> items() {
        return items;
    }

    private void load() {
        items.clear();
        String s = sp.getString("items", null);
        if (s == null) {
            return;
        }
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                ItemInfo it = new ItemInfo();
                it.id = o.getInt("id");
                it.type = o.getInt("type");
                it.page = o.getInt("page");
                it.cellX = o.getInt("x");
                it.cellY = o.getInt("y");
                it.spanX = o.getInt("sx");
                it.spanY = o.getInt("sy");
                it.pkg = o.optString("pkg", null);
                it.cls = o.optString("cls", null);
                it.widgetId = o.optInt("wid", -1);
                items.add(it);
                nextId = Math.max(nextId, it.id + 1);
            }
        } catch (JSONException e) {
            DebugLog.d("layout load failed: " + e);
        }
    }

    public void save() {
        try {
            JSONArray a = new JSONArray();
            for (ItemInfo it : items) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("type", it.type);
                o.put("page", it.page);
                o.put("x", it.cellX);
                o.put("y", it.cellY);
                o.put("sx", it.spanX);
                o.put("sy", it.spanY);
                if (it.pkg != null) {
                    o.put("pkg", it.pkg);
                }
                if (it.cls != null) {
                    o.put("cls", it.cls);
                }
                o.put("wid", it.widgetId);
                a.put(o);
            }
            sp.edit().putString("items", a.toString()).apply();
        } catch (JSONException e) {
            DebugLog.d("layout save failed: " + e);
        }
    }

    public void add(ItemInfo it) {
        it.id = nextId++;
        items.add(it);
        save();
    }

    public void remove(ItemInfo it) {
        items.remove(it);
        save();
    }

    public List<ItemInfo> onPage(int page) {
        List<ItemInfo> out = new ArrayList<>();
        for (ItemInfo it : items) {
            if (it.page == page) {
                out.add(it);
            }
        }
        return out;
    }

    public List<Integer> widgetIds() {
        List<Integer> out = new ArrayList<>();
        for (ItemInfo it : items) {
            if (it.type == ItemInfo.WIDGET && it.widgetId >= 0) {
                out.add(it.widgetId);
            }
        }
        return out;
    }

    /** First launch (or after a reset): put the clock on the home page. */
    public void ensureDefaults(int homePage, int cols) {
        if (sp.getBoolean("init", false)) {
            return;
        }
        ItemInfo clock = new ItemInfo();
        clock.type = ItemInfo.CLOCK;
        clock.page = homePage;
        clock.cellX = 0;
        clock.cellY = 0;
        clock.spanX = cols;
        clock.spanY = 2;
        add(clock);
        sp.edit().putBoolean("init", true).apply();
    }

    public void reset() {
        items.clear();
        sp.edit().remove("items").remove("init").apply();
    }

    public boolean fits(int page, int x, int y, int sx, int sy, int cols, int rows,
                        ItemInfo ignore) {
        if (x < 0 || y < 0 || x + sx > cols || y + sy > rows) {
            return false;
        }
        for (ItemInfo o : items) {
            if (o == ignore || o.page != page) {
                continue;
            }
            boolean overlap = x < o.cellX + o.spanX && o.cellX < x + sx
                    && y < o.cellY + o.spanY && o.cellY < y + sy;
            if (overlap) {
                return false;
            }
        }
        return true;
    }

    /** Returns {page, x, y} of the first free area starting at startPage, or null. */
    public int[] findFree(int startPage, int pages, int cols, int rows, int sx, int sy) {
        for (int k = 0; k < pages; k++) {
            int p = (startPage + k) % pages;
            for (int y = 0; y + sy <= rows; y++) {
                for (int x = 0; x + sx <= cols; x++) {
                    if (fits(p, x, y, sx, sy, cols, rows, null)) {
                        return new int[]{p, x, y};
                    }
                }
            }
        }
        return null;
    }

    /**
     * After the grid size changed: move items that no longer fit. Returns widget ids
     * of items that had to be dropped so the caller can free them.
     */
    public List<Integer> reflow(int pages, int cols, int rows) {
        List<Integer> dropped = new ArrayList<>();
        List<ItemInfo> copy = new ArrayList<>(items);
        boolean changed = false;
        for (ItemInfo it : copy) {
            if (it.page >= pages) {
                continue; // hidden page, kept for later
            }
            if (fits(it.page, it.cellX, it.cellY, it.spanX, it.spanY, cols, rows, it)) {
                continue;
            }
            it.spanX = Math.min(it.spanX, cols);
            it.spanY = Math.min(it.spanY, rows);
            int[] slot = findFreeIgnoring(it, pages, cols, rows);
            if (slot == null) {
                items.remove(it);
                if (it.type == ItemInfo.WIDGET && it.widgetId >= 0) {
                    dropped.add(it.widgetId);
                }
            } else {
                it.page = slot[0];
                it.cellX = slot[1];
                it.cellY = slot[2];
            }
            changed = true;
        }
        if (changed) {
            save();
        }
        return dropped;
    }

    private int[] findFreeIgnoring(ItemInfo it, int pages, int cols, int rows) {
        for (int p = 0; p < pages; p++) {
            for (int y = 0; y + it.spanY <= rows; y++) {
                for (int x = 0; x + it.spanX <= cols; x++) {
                    if (fits(p, x, y, it.spanX, it.spanY, cols, rows, it)) {
                        return new int[]{p, x, y};
                    }
                }
            }
        }
        return null;
    }

    /** Removes app shortcuts whose app is gone. Call only when the repository is loaded. */
    public boolean pruneMissing(AppRepository repo) {
        boolean changed = false;
        List<ItemInfo> copy = new ArrayList<>(items);
        for (ItemInfo it : copy) {
            if (it.type == ItemInfo.APP && repo.find(it.component()) == null) {
                items.remove(it);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
        return changed;
    }
}

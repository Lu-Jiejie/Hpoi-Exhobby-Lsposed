package com.kelvin.hpoi.exhobby.hook;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONObject;

import java.lang.reflect.Proxy;

/**
 * Reflection helpers for the host app. Field and method names here come from the real
 * dex of Hpoi 2.43, not from decompiler output, so they survive obfuscation.
 */
public final class HostBridge {

    public static final String PKG = "net.hpoi";
    public static final String ACT_HOBBY = "net.hpoi.ui.hobby.HobbyDetailActivity";
    public static final String VIEW_LIST = "net.hpoi.ui.part.comment.HeaderCommentListView";

    /** HobbyDetailActivity field holding the item id / item type. */
    private static final String FIELD_ITEM_ID = "u";
    private static final String FIELD_ITEM_TYPE = "v";

    private static final String EXTRA_ITEM_ID = "itemId";
    private static final String EXTRA_ITEM_TYPE = "itemType";
    private static final String EXTRA_ITEM_DATA = "itemData";

    private static final String IFACE_VIEW_BINDING = "androidx.viewbinding.ViewBinding";
    private static final String CLS_RECYCLER_LP =
            "androidx.recyclerview.widget.RecyclerView$LayoutParams";

    private HostBridge() {
    }

    public static boolean isHobbyPage(Activity act) {
        return act != null && ACT_HOBBY.equals(act.getClass().getName());
    }

    public static int itemId(Activity act) {
        Object v = field(act, FIELD_ITEM_ID);
        if (v instanceof Integer && (Integer) v > 0) {
            return (Integer) v;
        }
        return intentItemId(act);
    }

    public static String itemType(Activity act) {
        Object v = field(act, FIELD_ITEM_TYPE);
        if (v instanceof String && !((String) v).isEmpty()) {
            return (String) v;
        }
        return intentItemType(act);
    }

    private static Object field(Object target, String name) {
        try {
            return de.robv.android.xposed.XposedHelpers.getObjectField(target, name);
        } catch (Throwable t) {
            return null;
        }
    }

    public static int intentItemId(Activity act) {
        try {
            Intent it = act.getIntent();
            if (it == null) {
                return 0;
            }
            int id = it.getIntExtra(EXTRA_ITEM_ID, 0);
            if (id > 0) {
                return id;
            }
            JSONObject data = itemData(it);
            return data == null ? 0 : data.optInt(EXTRA_ITEM_ID, 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static String intentItemType(Activity act) {
        try {
            Intent it = act.getIntent();
            if (it != null) {
                String type = it.getStringExtra(EXTRA_ITEM_TYPE);
                if (type != null && !type.isEmpty()) {
                    return type;
                }
                JSONObject data = itemData(it);
                if (data != null) {
                    String fromData = data.optString(EXTRA_ITEM_TYPE, "");
                    if (!fromData.isEmpty()) {
                        return fromData;
                    }
                }
            }
        } catch (Throwable t) {
            // fall through
        }
        return "hobby";
    }

    private static JSONObject itemData(Intent it) {
        try {
            String raw = it.getStringExtra(EXTRA_ITEM_DATA);
            return raw == null ? null : new JSONObject(raw);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The host's list expects {@code androidx.viewbinding.ViewBinding}, which only exists in
     * its dex; a proxy keeps the module free of a compile-time dependency on the app.
     */
    public static Object viewBinding(Context ctx, View root) {
        try {
            ClassLoader cl = ctx.getClassLoader();
            Class<?> iface = Class.forName(IFACE_VIEW_BINDING, false, cl);
            return Proxy.newProxyInstance(cl, new Class<?>[]{iface},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getRoot".equals(name)) {
                            return root;
                        }
                        if ("toString".equals(name)) {
                            return "ExhobbyHeaderBinding";
                        }
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(root);
                        }
                        if ("equals".equals(name)) {
                            return proxy == (args == null ? null : args[0]);
                        }
                        return null;
                    });
        } catch (Throwable t) {
            XLog.w("ViewBinding proxy failed", t);
            return null;
        }
    }

    /** RecyclerView casts child layout params to its own type. */
    public static void applyRecyclerLayoutParams(View v) {
        try {
            Class<?> lp = Class.forName(CLS_RECYCLER_LP, false, v.getContext().getClassLoader());
            Object params = lp.getConstructor(int.class, int.class).newInstance(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            v.setLayoutParams((ViewGroup.LayoutParams) params);
        } catch (Throwable t) {
            XLog.w("RecyclerView LayoutParams failed", t);
        }
    }
}

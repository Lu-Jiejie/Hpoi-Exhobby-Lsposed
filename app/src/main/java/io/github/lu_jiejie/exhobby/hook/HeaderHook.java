package io.github.lu_jiejie.exhobby.hook;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import io.github.lu_jiejie.exhobby.ui.ExhobbyCard;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Adds the ExHobby card as the second header of the hobby page.
 *
 * <p>Hpoi builds its header list in {@code HobbyDetailActivity.x1()} and hands it to
 * {@code HeaderCommentListView.setHeader(List)}. Editing that list before the call is the
 * least invasive insertion point: the adapter rebuilds itself from the list it receives.
 */
public final class HeaderHook {

    /** Below the information card, above the timeline. */
    private static final int INSERT_AT = 1;

    private HeaderHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> cls = XposedHelpers.findClass(HostBridge.VIEW_LIST, lp.classLoader);
        XposedHelpers.findAndHookMethod(cls, "setHeader", List.class, new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    insert(param);
                } catch (Throwable t) {
                    XLog.w("header insert failed", t);
                }
            }
        });
        XLog.d("header hook installed");
    }

    @SuppressWarnings("unchecked")
    private static void insert(XC_MethodHook.MethodHookParam param) {
        if (!(param.thisObject instanceof View) || !(param.args[0] instanceof List)) {
            return;
        }
        Context ctx = ((View) param.thisObject).getContext();
        Activity act = ActivityFinder.of(ctx);
        if (!HostBridge.isHobbyPage(act)) {
            return;
        }
        List<Object> headers = (List<Object>) param.args[0];

        ExhobbyCard card = CardRegistry.get(act);
        boolean fresh = card == null;
        if (fresh) {
            card = new ExhobbyCard(act);
        }
        if (card.getBinding() == null) {
            card.setBinding(HostBridge.viewBinding(ctx, card.getView()));
        }
        Object binding = card.getBinding();
        if (binding == null) {
            return;
        }
        HostBridge.applyRecyclerLayoutParams(card.getView());
        if (card.getView().getParent() instanceof ViewGroup) {
            ((ViewGroup) card.getView().getParent()).removeView(card.getView());
        }
        // Re-entering the page reuses the same list object, so never add twice.
        if (headers.contains(binding)) {
            return;
        }
        headers.add(headers.isEmpty() ? 0 : Math.min(INSERT_AT, headers.size()), binding);

        if (fresh) {
            CardRegistry.put(act, card);
            // The list page usually passes the id in the intent; start before the API replies.
            int id = HostBridge.intentItemId(act);
            if (id > 0) {
                card.load(id, HostBridge.intentItemType(act));
            }
        }
    }
}

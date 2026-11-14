package io.github.lu_jiejie.exhobby.hook;

import android.app.Activity;

import io.github.lu_jiejie.exhobby.ui.ExhobbyCard;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Watches the detail response arriving. {@code i2(activity, ApiResult)} is where Hpoi
 * stores the item id on the activity, so it is the first moment the real id is known.
 */
public final class DetailHook {

    private static final String METHOD_ITEM_LOADED = "i2";

    private DetailHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> cls = XposedHelpers.findClass(HostBridge.ACT_HOBBY, lp.classLoader);
        XposedBridge.hookAllMethods(cls, METHOD_ITEM_LOADED, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    handle(param);
                } catch (Throwable t) {
                    XLog.w("item callback failed", t);
                }
            }
        });
        XLog.d("detail hook installed");
    }

    private static void handle(XC_MethodHook.MethodHookParam param) {
        // Static in 2.43 (the activity is the first argument) but tolerate an instance method.
        Object target = param.thisObject instanceof Activity
                ? param.thisObject
                : (param.args.length > 0 ? param.args[0] : null);
        if (!(target instanceof Activity)) {
            return;
        }
        Activity act = (Activity) target;
        ExhobbyCard card = CardRegistry.get(act);
        if (card == null) {
            return;
        }
        int id = HostBridge.itemId(act);
        if (id > 0) {
            card.load(id, HostBridge.itemType(act));
        }
    }
}

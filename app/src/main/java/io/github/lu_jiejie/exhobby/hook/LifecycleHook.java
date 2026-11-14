package io.github.lu_jiejie.exhobby.hook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Drops the cached card so a recreated activity builds a fresh one. */
public final class LifecycleHook {

    private static final String METHOD_DESTROY = "onDestroy";

    private LifecycleHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> cls = XposedHelpers.findClass(HostBridge.ACT_HOBBY, lp.classLoader);
        XposedBridge.hookAllMethods(cls, METHOD_DESTROY, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                CardRegistry.remove(param.thisObject);
            }
        });
        XLog.d("lifecycle hook installed");
    }
}

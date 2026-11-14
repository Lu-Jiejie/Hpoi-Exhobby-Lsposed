package io.github.lu_jiejie.exhobby;

import io.github.lu_jiejie.exhobby.hook.DetailHook;
import io.github.lu_jiejie.exhobby.hook.HeaderHook;
import io.github.lu_jiejie.exhobby.hook.HostBridge;
import io.github.lu_jiejie.exhobby.hook.LifecycleHook;
import io.github.lu_jiejie.exhobby.hook.XLog;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Entry point named in assets/xposed_init. */
public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!HostBridge.PKG.equals(lp.packageName)) {
            return;
        }
        install("header", () -> HeaderHook.install(lp));
        install("detail", () -> DetailHook.install(lp));
        install("lifecycle", () -> LifecycleHook.install(lp));
    }

    private interface Installation {
        void run();
    }

    /** One failing hook must not stop the others from installing. */
    private static void install(String name, Installation step) {
        try {
            step.run();
        } catch (Throwable t) {
            XLog.w("hook " + name + " failed", t);
        }
    }
}

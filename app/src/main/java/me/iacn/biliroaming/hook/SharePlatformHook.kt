package me.iacn.biliroaming.hook

import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log
import me.iacn.biliroaming.logic.Applet
import me.iacn.biliroaming.mirror.BiliBiliPackage

/**
 * Created by Meolunr on 2020/8/8
 * Email meolunr@gmail.com
 */
class SharePlatformHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!ConfigManager.instance.disableAppletShare()) return
        log("Start hook: Applet")

        val biliPackage = BiliBiliPackage.instance
        findAndHookMethod(biliPackage.sharePlatformDispatch, mClassLoader, biliPackage.shareHandleBundle, String::class.java, Bundle::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                Applet.onShare(param.args[0] as String, param.args[1] as Bundle)
            }
        })
    }
}
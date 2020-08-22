package me.iacn.biliroaming.hook

import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log
import me.iacn.biliroaming.mirror.BiliBiliPackage

/**
 * Created by Meolunr on 2020/8/8
 * Email meolunr@gmail.com
 */
class AppletHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!ConfigManager.instance.disableAppletShare()) return
        log("Start hook: Applet")

        val biliPackage = BiliBiliPackage.instance
        findAndHookMethod(biliPackage.sharePlatformDispatch, mClassLoader, biliPackage.shareHandleBundle, String::class.java, Bundle::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val platform = param.args[0]
                if (platform == "WEIXIN" || platform == "QQ") {
                    with(param.args[1] as Bundle) {
                        putString("params_program_id", null)
                        putString("params_program_path", null)
                        putString("params_type", "type_video")
                    }
                }
            }
        })
    }
}
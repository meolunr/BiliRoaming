package me.iacn.biliroaming.hook

import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.log

/**
 * Created by Meolunr on 2020/8/8
 * Email meolunr@gmail.com
 */
class AppletHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        log("Start hook: Applet")

        findAndHookMethod("com.bilibili.lib.sharewrapper.basic.ThirdPartyShareInterceptorV2", mClassLoader, "b",
                String::class.java, Bundle::class.java, "com.bilibili.lib.sharewrapper.h\$b", object : XC_MethodHook() {
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
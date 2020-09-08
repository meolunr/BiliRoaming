package me.iacn.biliroaming.hook

import android.app.Activity
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log

/**
 * Created by Meolunr on 2019/12/15
 * Email meolunr@gmail.com
 */
class TeenagersModeHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        if (!ConfigManager.instance.disableTeenagersModeDialog()) return
        log("Start hook: TeenagersMode")

        findAndHookMethod("com.bilibili.teenagersmode.ui.TeenagersModeDialogActivity",
                mClassLoader, "onCreate", Bundle::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                activity.finish()
                log("Teenagers mode dialog has been closed")
            }
        })
    }
}
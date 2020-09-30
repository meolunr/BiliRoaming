package me.iacn.biliroaming.hook

import android.app.Activity
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.logic.TeenagersModeDialog

/**
 * Created by Meolunr on 2019/12/15
 * Email meolunr@gmail.com
 */
class TeenagersModeHook : BaseHook() {

    override fun isEnable() = ConfigManager.instance.disableTeenagersModeDialog()

    override fun startHook(classLoader: ClassLoader) {
        findAndHookMethod("com.bilibili.teenagersmode.ui.TeenagersModeDialogActivity",
                classLoader, "onCreate", Bundle::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                TeenagersModeDialog.onPostTeenagersDialogCreate(param.thisObject as Activity)
            }
        })
    }
}
package me.iacn.biliroaming.hook

import android.content.Context
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getObjectField
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log
import me.iacn.biliroaming.logic.CustomTheme
import me.iacn.biliroaming.mirror.BiliBiliPackage
import tv.danmaku.bili.ui.theme.api.BiliSkin

/**
 * Created by Meolunr on 2019/7/14
 * Email meolunr@gmail.com
 */
class ThemeHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        if (!ConfigManager.instance.enableCustomTheme()) return
        log("Start hook: CustomTheme")

        CustomTheme.onStartHook(mClassLoader)

        val biliPackage = BiliBiliPackage.instance

        findAndHookMethod("tv.danmaku.bili.ui.theme.ThemeStoreActivity", mClassLoader, biliPackage.skinLoaded,
                "tv.danmaku.bili.ui.theme.api.BiliSkinList", Boolean::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val biliSkinList = param.args[0]
                val mList = getObjectField(biliSkinList, "mList") as MutableList<Any>

                CustomTheme.onLoadSkinList(mList)
            }
        })

        findAndHookMethod(biliPackage.themeListClickListener, mClassLoader, "onClick", View::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val view = param.args[0] as View
                val idName = view.resources.getResourceEntryName(view.id)

                // list_item is resource id of the skin recyclerview
                if ("list_item" != idName) return

                val biliSkin = view.tag ?: return
                if (CustomTheme.onSkinItemClick(param, view.context, biliSkin as BiliSkin)) {
                    param.result = null
                }
            }
        })

        findAndHookMethod(biliPackage.themeHelper, biliPackage.saveThemeKey, Context::class.java, Int::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val newId = CustomTheme.onSaveThemeId(param.args[1] as Int)
                param.args[1] = newId
            }
        })

        // Make sure that not invalidate when user not logging in
        val mainActivityClass = findClass("tv.danmaku.bili.MainActivityV2", mClassLoader)
        biliPackage.themeErrorImpls?.split("|")?.forEach {
            findAndHookMethod(mainActivityClass, it, XC_MethodReplacement.DO_NOTHING)
        }
    }
}
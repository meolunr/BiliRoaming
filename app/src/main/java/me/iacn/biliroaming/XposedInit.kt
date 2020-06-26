package me.iacn.biliroaming

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import me.iacn.biliroaming.hook.BangumiPlayUrlHook
import me.iacn.biliroaming.hook.BangumiSeasonHook
import me.iacn.biliroaming.hook.CommentHook
import me.iacn.biliroaming.hook.CustomThemeHook
import me.iacn.biliroaming.hook.TeenagersModeHook

/**
 * Created by iAcn on 2019/3/24
 * Email i@iacn.me
 */
class XposedInit : IXposedHookLoadPackage {

    companion object {
        lateinit var sPrefs: XSharedPreferences
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (BuildConfig.APPLICATION_ID == lpparam.packageName) {
            findAndHookMethod(MainActivity::class.java.name, lpparam.classLoader,
                    "isModuleActive", XC_MethodReplacement.returnConstant(true))
        }

        if (BILIBILI_PACKAGENAME != lpparam.packageName) return

        sPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID)

        findAndHookMethod(Instrumentation::class.java, "callApplicationOnCreate",
                Application::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                // Hook main process and download process
                when (lpparam.processName) {
                    "tv.danmaku.bili" -> {
                        log("BiliBili process launched...")
                        BiliBiliPackage.getInstance().init(lpparam.classLoader, param.args[0] as Context)
                        BangumiSeasonHook(lpparam.classLoader).startHook()
                        BangumiPlayUrlHook(lpparam.classLoader).startHook()
                        CustomThemeHook(lpparam.classLoader).startHook()
                        TeenagersModeHook(lpparam.classLoader).startHook()
                        CommentHook(lpparam.classLoader).startHook()
                    }
                    "tv.danmaku.bili:web" -> {
                        CustomThemeHook(lpparam.classLoader).insertColorForWebProcess()
                    }
                }
            }
        })
    }
}
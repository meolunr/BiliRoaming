package me.iacn.biliroaming

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import me.iacn.biliroaming.hook.BangumiDownloadHook
import me.iacn.biliroaming.hook.BeanApiHook
import me.iacn.biliroaming.hook.CloudConfigHook
import me.iacn.biliroaming.hook.CommentConfigHook
import me.iacn.biliroaming.hook.GrpcApiHook
import me.iacn.biliroaming.hook.SeasonRelatedHook
import me.iacn.biliroaming.hook.SharePlatformHook
import me.iacn.biliroaming.hook.TeenagersModeHook
import me.iacn.biliroaming.hook.ThemeRelatedHook
import me.iacn.biliroaming.inject.ClassLoaderInjector
import me.iacn.biliroaming.mirror.BiliBiliPackage

/**
 * Created by Meolunr on 2019/3/24
 * Email meolunr@gmail.com
 */
class XposedInit : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (BuildConfig.APPLICATION_ID == lpparam.packageName) {
            findAndHookMethod(MainActivity.Companion::class.java.name, lpparam.classLoader, "isModuleActive", XC_MethodReplacement.returnConstant(true))
        }

        if (BILIBILI_PACKAGENAME != lpparam.packageName) return

        findAndHookMethod(Instrumentation::class.java, "callApplicationOnCreate", Application::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                // Hook main process and download process
                when (lpparam.processName) {
                    "tv.danmaku.bili" -> {
                        log("BiliBili process launched...")
                        initialize(lpparam.classLoader, param.args[0] as Context)

                        arrayOf(
                                GrpcApiHook(),
                                BeanApiHook(),
                                BangumiDownloadHook(),
                                SeasonRelatedHook(),
                                ThemeRelatedHook(),
                                TeenagersModeHook(),
                                CommentConfigHook(),
                                SharePlatformHook(),
                                CloudConfigHook(),
                        ).filter {
                            it.isEnable()
                        }.forEach {
                            it.startHook(lpparam.classLoader)
                            log("Start hook: ${it::class.java.simpleName.removeSuffix("Hook")}")
                        }
                    }
                }
            }
        })
    }

    private fun initialize(hostClassLoader: ClassLoader, hostContext: Context) {
        ClassLoaderInjector.setupWithHost(hostClassLoader)
        BiliBiliPackage.instance.init(hostClassLoader, hostContext)
    }
}
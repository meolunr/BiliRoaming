package me.iacn.hotxposed

import android.content.Context
import dalvik.system.PathClassLoader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import me.iacn.biliroaming.BuildConfig
import java.io.File

/**
 * Created by iAcn on 2019/3/25
 * Email i@iacn.me
 */
class HotXposedInit : IXposedHookLoadPackage {

    companion object {
        private val HOST_PACKAGES: Array<String> = arrayOf(
                "tv.danmaku.bili",
                BuildConfig.APPLICATION_ID
        )
        private const val REAL_XPOSED_INIT = "me.iacn.biliroaming.XposedInit"
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        disableModulesUpdatedNotification(lpparam)
        if (lpparam.packageName !in HOST_PACKAGES) return

        val moduleApkFile = getModuleApkFile()
        if (!moduleApkFile.exists()) return

        val classLoader = PathClassLoader(moduleApkFile.absolutePath, lpparam::class.java.classLoader)
        classLoader.loadClass(REAL_XPOSED_INIT)?.let {
            callMethod(it.newInstance(), "handleLoadPackage", lpparam)
        }
    }

    private fun disableModulesUpdatedNotification(lpparam: LoadPackageParam) {
        if ("de.robv.android.xposed.installer" == lpparam.packageName) {
            findAndHookMethod("de.robv.android.xposed.installer.util.NotificationUtil", lpparam.classLoader,
                    "showModulesUpdatedNotification", XC_MethodReplacement.DO_NOTHING)
        }
    }

    private fun getModuleApkFile(): File {
        val activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread")
        val context = callMethod(activityThread, "getSystemContext") as Context
        val applicationInfo = context.packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0)
        return File(applicationInfo.sourceDir)
    }
}
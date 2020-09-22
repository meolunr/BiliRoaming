package me.iacn.biliroaming.hook

import com.bilibili.okretro.call.rxjava.RxGeneralResponse
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findClass
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log
import me.iacn.biliroaming.logic.BangumiSeason
import me.iacn.biliroaming.mirror.BiliBiliPackage

/**
 * Created by Meolunr on 2019/3/27
 * Email meolunr@gmail.com
 */
class RetrofitResponseHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        if (!ConfigManager.instance.enableMainFunc()) return
        log("Start hook: BangumiSeason")

        XposedBridge.hookAllConstructors(findClass(BiliBiliPackage.instance.retrofitResponse, mClassLoader), object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                when (val body = param.args[1]) {
                    is RxGeneralResponse -> BangumiSeason.onBangumiResponse(body)
                }
            }
        })
    }
}
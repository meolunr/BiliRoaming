package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log

/**
 * Created by iAcn on 2019/3/29
 * Email i@iacn.me
 */
class BangumiPlayUrlHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        if (!ConfigManager.instance.enableMainFunc()) return
        log("startHook: BangumiPlayUrl")

        findAndHookMethod("com.bapis.bilibili.pgc.gateway.player.v1.PlayURLMoss", mClassLoader, "playView",
                "com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                println("result = ${param.result}")
                val queryString = constructQueryString()
                println(queryString)
            }
        })
    }

    private fun constructQueryString(): String {
        return ""
    }
}
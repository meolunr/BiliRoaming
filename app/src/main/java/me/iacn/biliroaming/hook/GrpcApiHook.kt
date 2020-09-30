package me.iacn.biliroaming.hook

import com.bapis.bilibili.community.service.dm.v1.DmPlayerConfigReq
import com.bapis.bilibili.community.service.dm.v1.DmViewReply
import com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReply
import com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.logic.BangumiPlayUrl
import me.iacn.biliroaming.logic.PlayerKovConfig

/**
 * Created by Meolunr on 2019/3/29
 * Email meolunr@gmail.com
 */
class GrpcApiHook : BaseHook() {

    override fun isEnable() = ConfigManager.instance.enableMainFunc() || ConfigManager.instance.disableDanmakuTextSizeSync()

    override fun startHook(classLoader: ClassLoader) {
        if (ConfigManager.instance.enableMainFunc()) {
            findAndHookMethod("com.bapis.bilibili.pgc.gateway.player.v1.PlayURLMoss", classLoader, "playView",
                    "com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val response = param.result as PlayViewReply
                    // Filter normal play url
                    if (response.hasVideoInfo()) return

                    BangumiPlayUrl.onLimitedPlayViewResponse(param.args[0] as PlayViewReq)?.let {
                        param.result = it
                    }
                }
            })
        }

        if (ConfigManager.instance.disableDanmakuTextSizeSync()) {
            val dmMossClass = findClass("com.bapis.bilibili.community.service.dm.v1.DMMoss", classLoader)
            findAndHookMethod(dmMossClass, "dmView", "com.bapis.bilibili.community.service.dm.v1.DmViewReq", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.result = PlayerKovConfig.onPlayerConfigPull(param.result as DmViewReply)
                }
            })

            findAndHookMethod(dmMossClass, "dmPlayerConfig", "com.bapis.bilibili.community.service.dm.v1.DmPlayerConfigReq",
                    "com.bilibili.lib.moss.api.MossResponseHandler", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (PlayerKovConfig.onPlayerConfigPush(param.args[0] as DmPlayerConfigReq))
                        param.result = null
                }
            })
        }
    }
}
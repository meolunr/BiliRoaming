package me.iacn.biliroaming.hook

import android.net.Uri
import com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log
import me.iacn.biliroaming.toIntString

/**
 * Created by iAcn on 2019/3/29
 * Email i@iacn.me
 */
class BangumiPlayUrlHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        if (!ConfigManager.instance.enableMainFunc()) return
        log("Start hook: BangumiPlayUrl")

        findAndHookMethod("com.bapis.bilibili.pgc.gateway.player.v1.PlayURLMoss", mClassLoader, "playView",
                "com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                println("result = ${param.result}")
                val queryString = constructQueryString(param.args[0] as PlayViewReq)
                println(queryString)
//                println(BiliRoamingApi.getPlayUrl(queryString))
            }
        })
    }

    private fun constructQueryString(request: PlayViewReq): String {
        return with(Uri.Builder()) {
            val accessKey = BiliBiliPackage.instance.accessKey
            if (accessKey.isNotEmpty()) {
                appendQueryParameter("access_key", accessKey)
            }
            appendQueryParameter("cid", request.cid.toString())
            appendQueryParameter("ep_id", request.epId.toString())
            appendQueryParameter("fnval", request.fnval.toString())
            appendQueryParameter("fnver", request.fnver.toString())
            appendQueryParameter("force_host", request.forceHost.toString())
            appendQueryParameter("fourk", request.fourk.toIntString())
            appendQueryParameter("qn", request.qn.toString())
            toString()
        }
    }
}
package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.log
import me.iacn.biliroaming.network.BiliRoamingApi
import me.iacn.biliroaming.network.StreamUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection

/**
 * Created by iAcn on 2019/3/29
 * Email i@iacn.me
 */
class BangumiPlayUrlHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("main_func", false)) return
        log("startHook: BangumiPlayUrl")

        findAndHookMethod("com.bilibili.lib.okhttp.huc.OkHttpURLConnection", mClassLoader, "getInputStream", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                // Found from "b.ecy" in version 5.39.1
                val connection = param.thisObject as HttpURLConnection
                val urlString = connection.url.toString()

                if (!urlString.startsWith("https://api.bilibili.com/pgc/player/api/playurl")) return

                val queryString = urlString.substring(urlString.indexOf("?") + 1)
                if (queryString.contains("ep_id=")) {
                    val inputStream = param.result as InputStream
                    var content = StreamUtils.getContent(inputStream)

                    if (isLimitWatchingArea(content)) {
                        content = BiliRoamingApi.getPlayUrl(queryString)
                        log("Has replaced play url with proxy server")
                    }
                    param.result = ByteArrayInputStream(content.toByteArray())
                }
            }
        })
    }

    private fun isLimitWatchingArea(jsonText: String): Boolean {
        return try {
            val json = JSONObject(jsonText)
            val code = json.optInt("code")
            log("Loading play url: code = $code")
            code == -10403
        } catch (e: JSONException) {
            e.printStackTrace()
            false
        }
    }
}
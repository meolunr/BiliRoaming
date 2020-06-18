package me.iacn.biliroaming.hook

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.Constant.TAG
import me.iacn.biliroaming.XposedInit
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
class BangumiPlayUrlHook(classLoader: ClassLoader?) : BaseHook(classLoader) {

    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("main_func", false)) return
        Log.d(TAG, "startHook: BangumiPlayUrl")

        findAndHookMethod("com.bilibili.lib.okhttp.huc.OkHttpURLConnection", mClassLoader, "getInputStream", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                // Found from "b.ecy" in version 5.39.1
                val connection = param.thisObject as HttpURLConnection
                val urlString = connection.url.toString()

                if (!urlString.startsWith("https://api.bilibili.com/pgc/player/api/playurl")) return

                val queryString = urlString.substring(urlString.indexOf("?") + 1)
                if (queryString.contains("ep_id=")) {
                    val inputStream = param.result as InputStream
                    val encoding = connection.contentEncoding
                    var content = StreamUtils.getContent(inputStream, encoding)

                    if (isLimitWatchingArea(content)) {
                        content = BiliRoamingApi.getPlayUrl(queryString)
                        Log.d(TAG, "Has replaced play url with proxy server")
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
            Log.d(TAG, "Loading play url: code = $code")
            code == -10403
        } catch (e: JSONException) {
            e.printStackTrace()
            false
        }
    }
}
package me.iacn.biliroaming.logic

import android.util.ArrayMap
import com.alibaba.fastjson.JSONObject
import com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason
import com.bilibili.okretro.call.rxjava.RxGeneralResponse
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log
import me.iacn.biliroaming.mirror.BiliBiliPackage
import me.iacn.biliroaming.network.BiliRoamingApi

/**
 * Created by Meolunr on 2020/9/22
 * Email meolunr@gmail.com
 */
object BangumiSeason {

    private val lastSeasonInfo: MutableMap<String, Any?> by lazy { ArrayMap<String, Any?>() }

    fun onBuildSeasonParams(seasonId: String, episodeId: String, paramMap: MutableMap<*, *>) {
        val id = when {
            seasonId != "0" -> paramMap["season_id"]
            episodeId != "0" -> "ep${paramMap["ep_id"]}"
            else -> return
        }

        log("Bangumi watching: Id = $id")
        lastSeasonInfo["id"] = id
        lastSeasonInfo["access_key"] = paramMap["access_key"]
    }

    fun onBangumiResponse(body: RxGeneralResponse) {
        // Filter out illegal responses
        // If it isn't bangumi, the type variable will not exist in this map
        if ("id" !in lastSeasonInfo) return

        // Filter normal bangumi and other responses
        if (isBangumiWithWatchPermission(body)) {
            setBangumiDownload(body.result as BangumiUniformSeason)
        } else {
            log("Found a restricted bangumi: $lastSeasonInfo")
            onLimitedBangumiResponse(body)
        }

        lastSeasonInfo.clear()
    }

    /**
     * @return new jump url
     */
    fun onGetJumpUrl(videoCard: Any): Any? {
        val redirectUrl = getObjectField(videoCard, "redirectUrl") as String
        if (redirectUrl.isNotEmpty()) {
            return callMethod(videoCard, "getUrl", redirectUrl)
        }
        return null
    }

    private fun onLimitedBangumiResponse(body: RxGeneralResponse) {
        val biliPackage = BiliBiliPackage.instance
        val useCache = body.result != null

        val contentJson = org.json.JSONObject(getSeasonFromProxyServer(useCache))
        val code = contentJson.optInt("code")
        log("Get new season information from proxy server: code = $code, useCache = $useCache")

        if (code == 0) {
            val resultJson = contentJson.optJSONObject("result")
            val newResult = callStaticMethod(biliPackage.fastJson, biliPackage.fastJsonParse, resultJson!!.toString(), BangumiUniformSeason::class.java) as BangumiUniformSeason

            if (useCache) {
                // Replace only episodes and rights
                // Remain user information, such as follow status, watch progress, etc.
                if (!newResult.rights.areaLimit) {
                    val result = body.result as BangumiUniformSeason
                    result.rights = newResult.rights
                    result.modules = newResult.modules
                    result.seasonLimit = null
                }
            } else {
                body.code = 0
                body.result = newResult
            }
            setBangumiDownload(body.result as BangumiUniformSeason)
        }
    }

    private fun setBangumiDownload(result: BangumiUniformSeason) {
        if (!ConfigManager.instance.enableBangumiDownload()) return

        result.rights.allowDownload = true

        for (module in result.modules) {
            val data = getObjectField(module, "data") as JSONObject?
            data?.getJSONArray("episodes")?.let {
                // It's positive
                for (i in 0 until it.size()) {
                    val rights = it.getJSONObject(i).getJSONObject("rights")
                    rights.put("allow_download", 1)
                }
            }
        }
    }

    private fun isBangumiWithWatchPermission(body: RxGeneralResponse): Boolean {
        log("BangumiApiResponse: code = ${body.code}, result = ${body.result}")
        if (body.result is BangumiUniformSeason) {
            return !(body.result as BangumiUniformSeason).rights.areaLimit
        }
        return body.code != -404
    }

    private fun getSeasonFromProxyServer(useCache: Boolean): String {
        val id = lastSeasonInfo["id"] as String
        val accessKey = lastSeasonInfo["access_key"] as String
        return BiliRoamingApi.getSeason(id, accessKey, useCache)
    }
}
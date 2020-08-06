package me.iacn.biliroaming.hook

import android.util.ArrayMap
import com.bilibili.bangumi.data.page.detail.entity.BangumiUniformSeason
import com.bilibili.okretro.call.rxjava.RxGeneralResponse
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getObjectField
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log
import me.iacn.biliroaming.network.BiliRoamingApi
import org.json.JSONObject
import com.alibaba.fastjson.JSONObject as FastJsonObject

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
class BangumiSeasonHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val lastSeasonInfo: MutableMap<String, Any?> by lazy { ArrayMap<String, Any?>() }

    override fun startHook() {
        if (!ConfigManager.instance.enableMainFunc()) return
        log("Start hook: BangumiSeason")

        val paramsMapClass = findClass("com.bilibili.bangumi.data.page.detail.BangumiDetailApiService\$UniformSeasonParamsMap", mClassLoader)
        XposedBridge.hookAllConstructors(paramsMapClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val paramMap = param.thisObject as MutableMap<*, *>

                val id = when {
                    param.args[2] != "0" -> paramMap["season_id"]     // Arg2: season id
                    param.args[3] != "0" -> "ep${paramMap["ep_id"]}"  // Arg3: episode id
                    else -> return
                }

                log("Bangumi watching: Id = $id")
                lastSeasonInfo["id"] = id
                lastSeasonInfo["access_key"] = paramMap["access_key"]
            }
        })

        XposedBridge.hookAllConstructors(findClass(BiliBiliPackage.instance.retrofitResponse, mClassLoader), object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val body = param.args[1]
                // Filter non-bangumi responses
                // If it isn't bangumi, the type variable will not exist in this map
                if ((body !is RxGeneralResponse) or ("id" !in lastSeasonInfo)) return
                body as RxGeneralResponse

                // Filter normal bangumi and other responses
                if (isBangumiWithWatchPermission(body)) {
                    setBangumiDownload(body.result as BangumiUniformSeason)
                } else {
                    log("Found a restricted bangumi: $lastSeasonInfo")
                    onLimitedBangumiResponse(body)
                }
                lastSeasonInfo.clear()
            }
        })

        // Fix issue that video can't be found by clicking on the video card from dynamic tab
        val urlHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val redirectUrl = getObjectField(param.thisObject, "redirectUrl") as String
                if (redirectUrl.isNotEmpty()) {
                    param.result = callMethod(param.thisObject, "getUrl", redirectUrl)
                }
            }
        }
        val videoCardClass = findClass("com.bilibili.bplus.followingcard.api.entity.cardBean.VideoCard", mClassLoader)
        findAndHookMethod(videoCardClass, "getJumpUrl", urlHook)
        findAndHookMethod(videoCardClass, "getCommentJumpUrl", urlHook)
    }

    private fun onLimitedBangumiResponse(body: RxGeneralResponse) {
        val biliPackage = BiliBiliPackage.instance
        val useCache = body.result != null

        val contentJson = JSONObject(getSeasonFromProxyServer(useCache))
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
                    result.episodes = newResult.episodes
                    result.seasonLimit = null

                    if (biliPackage.hasModulesInResult) {
                        result.modules = newResult.modules
                    }
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

        if (!BiliBiliPackage.instance.hasModulesInResult) return

        for (module in result.modules) {
            val data = getObjectField(module, "data") as FastJsonObject?
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
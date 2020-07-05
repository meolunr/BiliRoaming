package me.iacn.biliroaming.hook

import android.util.ArrayMap
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getBooleanField
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setIntField
import de.robv.android.xposed.XposedHelpers.setObjectField
import me.iacn.biliroaming.BiliBiliPackage
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.log
import me.iacn.biliroaming.network.BiliRoamingApi
import org.json.JSONObject

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
class BangumiSeasonHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private companion object {
        private const val ID_TYPE_SEASON = 0
        private const val ID_TYPE_EPISODE = 2
    }

    private val lastSeasonInfo: MutableMap<String, Any?> by lazy { ArrayMap<String, Any?>() }

    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("main_func", false)) return
        log("startHook: BangumiSeason")

        val paramsMapClass = findClass("com.bilibili.bangumi.data.page.detail." +
                "BangumiDetailApiService\$UniformSeasonParamsMap", mClassLoader)
        XposedBridge.hookAllConstructors(paramsMapClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val paramMap = param.thisObject as MutableMap<*, *>

                val id = when (param.args[1] as Int) {
                    ID_TYPE_SEASON -> paramMap["season_id"]
                    ID_TYPE_EPISODE -> "ep${paramMap["ep_id"]}"
                    else -> return
                }

                log("Bangumi watching: Id = $id")
                lastSeasonInfo["id"] = id
                lastSeasonInfo["access_key"] = paramMap["access_key"]
            }
        })

        val responseClass = findClass(BiliBiliPackage.instance.retrofitResponse, mClassLoader)
        XposedBridge.hookAllConstructors(responseClass, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val biliPackage = BiliBiliPackage.instance
                val body = param.args[1]
                val bangumiApiResponse = biliPackage.bangumiApiResponse

                // Filter non-bangumi responses
                // If it isn't bangumi, the type variable will not exist in this map
                if (!bangumiApiResponse.isInstance(body) or !lastSeasonInfo.containsKey("id")) return

                val result = getObjectField(body, "result")
                // Filter normal bangumi and other responses
                if (isBangumiWithWatchPermission(getIntField(body, "code"), result)) {
                    lastSeasonInfo.clear()
                    return
                }

                val useCache = result != null
                val content = getSeasonFromProxyServer(useCache)
                val contentJson = JSONObject(content)
                val code = contentJson.optInt("code")
                log("Get new season information from proxy server: code = $code, useCache = $useCache")

                if (code == 0) {
                    val resultJson = contentJson.optJSONObject("result")
                    val beanClass = biliPackage.bangumiUniformSeason
                    val newResult = callStaticMethod(biliPackage.fastJson, biliPackage.fastJsonParse, resultJson!!.toString(), beanClass)

                    if (useCache) {
                        // Replace only episodes and rights
                        // Remain user information, such as follow status, watch progress, etc.
                        val newRights = getObjectField(newResult, "rights")
                        if (!getBooleanField(newRights, "areaLimit")) {
                            val newEpisodes = getObjectField(newResult, "episodes")
                            setObjectField(result, "rights", newRights)
                            setObjectField(result, "episodes", newEpisodes)
                            setObjectField(result, "seasonLimit", null)

                            if (biliPackage.hasModulesInResult) {
                                val newModules = getObjectField(newResult, "modules")
                                setObjectField(result, "modules", newModules)
                            }
                        }
                    } else {
                        setIntField(body, "code", 0)
                        setObjectField(body, "result", newResult)
                    }
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

    private fun isBangumiWithWatchPermission(code: Int, result: Any?): Boolean {
        log("BangumiApiResponse: code = $code, result = $result")
        result?.let {
            val bangumiSeasonClass = BiliBiliPackage.instance.bangumiUniformSeason
            if (bangumiSeasonClass.isInstance(it)) {
                val rights = getObjectField(result, "rights")
                val areaLimit = getBooleanField(rights, "areaLimit")
                return !areaLimit
            }
        }
        return code != -404
    }

    private fun getSeasonFromProxyServer(useCache: Boolean): String {
        log("Found a restricted bangumi: $lastSeasonInfo")
        val id = lastSeasonInfo["id"] as String
        val accessKey = lastSeasonInfo["access_key"] as String
        return BiliRoamingApi.getSeason(id, accessKey, useCache)
    }
}
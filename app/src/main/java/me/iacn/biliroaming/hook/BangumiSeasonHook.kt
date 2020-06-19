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
import me.iacn.biliroaming.Constant.TYPE_EPISODE_ID
import me.iacn.biliroaming.Constant.TYPE_SEASON_ID
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.log
import me.iacn.biliroaming.network.BiliRoamingApi.getSeason
import org.json.JSONObject

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
class BangumiSeasonHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val lastSeasonInfo: MutableMap<String, String?> by lazy { ArrayMap<String, String?>() }

    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("main_func", false)) return
        log("startHook: BangumiSeason")

        val paramsMapClass = findClass("com.bilibili.bangumi.data.page.detail." +
                "BangumiDetailApiService\$UniformSeasonParamsMap", mClassLoader)
        XposedBridge.hookAllConstructors(paramsMapClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val paramMap = param.thisObject as MutableMap<String, String>
                val type = param.args[1] as Int

                when (type) {
                    TYPE_SEASON_ID -> {
                        val seasonId = paramMap["season_id"]
                        lastSeasonInfo["season_id"] = "seasonId"
                        log("Bangumi watching: seasonId = $seasonId")
                    }
                    TYPE_EPISODE_ID -> {
                        val episodeId = paramMap["ep_id"]
                        lastSeasonInfo["episode_id"] = episodeId
                        log("Bangumi watching: episodeId = $episodeId")
                    }
                    else -> return
                }

                lastSeasonInfo["type"] = type.toString()
                lastSeasonInfo["access_key"] = paramMap["access_key"]
            }
        })

        val responseClass = findClass(BiliBiliPackage.getInstance().retrofitResponse(), mClassLoader)
        XposedBridge.hookAllConstructors(responseClass, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val body = param.args[1]
                val bangumiApiResponse = BiliBiliPackage.getInstance().bangumiApiResponse()

                // Filter non-bangumi responses
                // If it isn't bangumi, the type variable will not exist in this map
                if (!bangumiApiResponse.isInstance(body) || !lastSeasonInfo.containsKey("type")) return

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
                    val biliPackage = BiliBiliPackage.getInstance()

                    val resultJson = contentJson.optJSONObject("result")
                    val beanClass = BiliBiliPackage.getInstance().bangumiUniformSeason()
                    val newResult = callStaticMethod(biliPackage.fastJson(), biliPackage.fastJsonParse(), resultJson!!.toString(), beanClass)

                    if (useCache) {
                        // Replace only episodes and rights
                        // Remain user information, such as follow status, watch progress, etc.
                        val newRights = getObjectField(newResult, "rights")
                        if (!getBooleanField(newRights, "areaLimit")) {
                            val newEpisodes = getObjectField(newResult, "episodes")
                            setObjectField(result, "rights", newRights)
                            setObjectField(result, "episodes", newEpisodes)
                            setObjectField(result, "seasonLimit", null)

                            if (biliPackage.hasModulesInResult()) {
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

        // ===========================================
        try {
            val urlHook: Any = object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val redirectUrl =
                            getObjectField(param.thisObject, "redirectUrl") as String
                    if (redirectUrl.isEmpty()) return
                    param.result =
                            callMethod(param.thisObject, "getUrl", redirectUrl)
                }
            }

            findAndHookMethod("com.bilibili.bplus.followingcard.api.entity.cardBean.VideoCard",
                    mClassLoader, "getJumpUrl", urlHook)

            findAndHookMethod("com.bilibili.bplus.followingcard.api.entity.cardBean.VideoCard",
                    mClassLoader, "getCommentJumpUrl", urlHook)
        } catch (ignored: Throwable) {
        }
    }
    // ===========================================

    private fun isBangumiWithWatchPermission(code: Int, result: Any?): Boolean {
        log("BangumiApiResponse: code = $code, result = $result")
        result?.let {
            val bangumiSeasonClass = BiliBiliPackage.getInstance().bangumiUniformSeason()
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
        var id: String? = null
        val accessKey = lastSeasonInfo["access_key"] as String?
        when (lastSeasonInfo["type"]!!.toInt()) {
            TYPE_SEASON_ID -> id = lastSeasonInfo["season_id"] as String?
            TYPE_EPISODE_ID -> id = "ep" + lastSeasonInfo["episode_id"]
        }

        println("--------------------")
        println(lastSeasonInfo)
        println(accessKey)
        println(useCache)
        val season = getSeason(id!!, accessKey!!, useCache)
        println(season)
        return season
    }
}
package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import me.iacn.biliroaming.logic.BangumiSeason

/**
 * Created by Meolunr on 2020/9/22
 * Email meolunr@gmail.com
 */
class SeasonRelatedHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        val paramsMapClass = findClass("com.bilibili.bangumi.data.page.detail.BangumiDetailApiService\$UniformSeasonParamsMap", mClassLoader)
        XposedBridge.hookAllConstructors(paramsMapClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                // Arg2: season id; Arg3: episode id
                BangumiSeason.onBuildSeasonParams(param.args[2] as String, param.args[3] as String, param.thisObject as MutableMap<*, *>)
            }
        })

        // Fix issue that video can't be found by clicking on the video card from dynamic tab
        val urlHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                BangumiSeason.onGetJumpUrl(param.thisObject)?.let {
                    param.result = it
                }
            }
        }
        val videoCardClass = findClass("com.bilibili.bplus.followingcard.api.entity.cardBean.VideoCard", mClassLoader)
        findAndHookMethod(videoCardClass, "getJumpUrl", urlHook)
        findAndHookMethod(videoCardClass, "getCommentJumpUrl", urlHook)
    }
}
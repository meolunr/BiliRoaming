package me.iacn.biliroaming.hook

import com.bapis.bilibili.app.playurl.v1.CloudConf
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log
import me.iacn.biliroaming.logic.PlayerRecommend

/**
 * Created by Meolunr on 2020/9/10
 * Email meolunr@gmail.com
 */

class CloudConfigHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!ConfigManager.instance.disablePlayerRecommend()) return
        log("Start hook: BiliSettings")

        findAndHookMethod("com.bilibili.lib.deviceconfig.generated.internal.PlayAbilityConfImpl\$recommendConf\$1", mClassLoader, "invoke", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.result = PlayerRecommend.onGetRecommendConfig(param.result as CloudConf)
            }
        })
    }
}
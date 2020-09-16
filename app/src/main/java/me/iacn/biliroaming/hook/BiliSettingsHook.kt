package me.iacn.biliroaming.hook

import com.bapis.bilibili.app.playurl.v1.CloudConf
import com.bapis.bilibili.app.playurl.v1.FieldValue
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log

/**
 * Created by Meolunr on 2020/9/10
 * Email meolunr@gmail.com
 */

class BiliSettingsHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!ConfigManager.instance.disablePlayerRecommend()) return
        log("Start hook: BiliSettings")

        findAndHookMethod("com.bilibili.lib.deviceconfig.generated.internal.PlayAbilityConfImpl\$recommendConf\$1", mClassLoader, "invoke", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val result = param.result as CloudConf
                param.result = CloudConf.newBuilder().apply {
                    setConfType(result.confType)
                    setFieldValue(FieldValue.newBuilder().apply {
                        setSwitch(false)
                    })
                }.build()
            }
        })
    }
}
package me.iacn.biliroaming.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.logic.CommentFloor

/**
 * Created by Meolunr on 2020/2/27
 * Email meolunr@gmail.com
 */
class CommentConfigHook : BaseHook() {

    override fun isEnable() = ConfigManager.instance.enableCommentFloor()

    override fun startHook(classLoader: ClassLoader) {
        val floorHook: XC_MethodHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val config = getObjectField(param.thisObject, "config")
                config?.let {
                    CommentFloor.onApplyCommentConfig(it)
                }
            }
        }

        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentCursorList", classLoader, "isShowFloor", floorHook)
        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentDialogue", classLoader, "isShowFloor", floorHook)
        findAndHookMethod("com.bilibili.app.comm.comment2.model.BiliCommentDetail", classLoader, "isShowFloor", floorHook)
    }
}
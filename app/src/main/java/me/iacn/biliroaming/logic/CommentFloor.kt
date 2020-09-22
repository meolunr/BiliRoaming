package me.iacn.biliroaming.logic

import de.robv.android.xposed.XposedHelpers.setIntField

/**
 * Created by Meolunr on 2020/9/22
 * Email meolunr@gmail.com
 */
object CommentFloor {

    fun onApplyCommentConfig(config: Any) {
        setIntField(config, "mShowFloor", 1)
    }
}
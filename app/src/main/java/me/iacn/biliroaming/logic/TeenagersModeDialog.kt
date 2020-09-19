package me.iacn.biliroaming.logic

import android.app.Activity
import me.iacn.biliroaming.log

/**
 * Created by Meolunr on 2020/9/19
 * Email meolunr@gmail.com
 */
object TeenagersModeDialog {

    fun onPostTeenagersDialogCreate(activity: Activity) {
        activity.finish()
        log("Teenagers mode dialog has been closed")
    }
}
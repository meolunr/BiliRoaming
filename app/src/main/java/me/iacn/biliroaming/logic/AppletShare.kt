package me.iacn.biliroaming.logic

import android.os.Bundle

/**
 * Created by Meolunr on 2020/9/17
 * Email meolunr@gmail.com
 */
object AppletShare {

    fun onShare(platform: String, params: Bundle) {
        if (platform == "WEIXIN" || platform == "QQ") {
            params.putString("params_program_id", null)
            params.putString("params_program_path", null)
            params.putString("params_type", "type_video")
        }
    }
}
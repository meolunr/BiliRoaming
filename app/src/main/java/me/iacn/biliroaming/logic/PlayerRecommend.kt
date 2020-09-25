package me.iacn.biliroaming.logic

import com.bapis.bilibili.app.playurl.v1.CloudConf
import com.bapis.bilibili.app.playurl.v1.FieldValue

/**
 * Created by Meolunr on 2020/9/25
 * Email meolunr@gmail.com
 */
object PlayerRecommend {

    /**
     * @return new player recommend config
     */
    fun onGetRecommendConfig(config: CloudConf): CloudConf {
        return CloudConf.newBuilder().apply {
            setConfType(config.confType)
            setFieldValue(FieldValue.newBuilder().apply {
                setSwitch(false)
            })
        }.build()
    }
}
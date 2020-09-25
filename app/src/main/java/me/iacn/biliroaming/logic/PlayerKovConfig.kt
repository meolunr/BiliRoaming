package me.iacn.biliroaming.logic

import com.bapis.bilibili.community.service.dm.v1.DmPlayerConfigReq
import com.bapis.bilibili.community.service.dm.v1.DmViewReply

/**
 * Created by Meolunr on 2020/9/25
 * Email meolunr@gmail.com
 */
object PlayerKovConfig {

    /**
     * @return new player configuration
     */
    fun onPlayerConfigPull(dmViewReply: DmViewReply): DmViewReply {
        return dmViewReply.toBuilder().apply {
            setPlayerConfig(dmViewReply.playerConfig.toBuilder().apply {
                setDanmukuPlayerConfig(dmViewReply.playerConfig.danmukuPlayerConfig.toBuilder().apply {
                    // Given an invalid value let the player use local configuration
                    setPlayerDanmakuScalingfactor(-1f)
                })
            })
        }.build()
    }

    /**
     * @return Whether to stop executing the original method
     */
    fun onPlayerConfigPush(playerConfig: DmPlayerConfigReq): Boolean {
        return playerConfig.hasScalingfactor()
    }
}
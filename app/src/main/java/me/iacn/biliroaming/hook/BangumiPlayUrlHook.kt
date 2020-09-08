package me.iacn.biliroaming.hook

import android.net.Uri
import com.bapis.bilibili.app.playurl.v1.DashItem
import com.bapis.bilibili.app.playurl.v1.DashVideo
import com.bapis.bilibili.app.playurl.v1.Stream
import com.bapis.bilibili.app.playurl.v1.StreamInfo
import com.bapis.bilibili.app.playurl.v1.VideoInfo
import com.bapis.bilibili.pgc.gateway.player.v1.PlayAbilityConf
import com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReply
import com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import me.iacn.biliroaming.ConfigManager
import me.iacn.biliroaming.log
import me.iacn.biliroaming.mirror.BiliBiliPackage
import me.iacn.biliroaming.network.BiliRoamingApi
import me.iacn.biliroaming.toIntString
import org.json.JSONObject

/**
 * Created by Meolunr on 2019/3/29
 * Email meolunr@gmail.com
 */
class BangumiPlayUrlHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        if (!ConfigManager.instance.enableMainFunc()) return
        log("Start hook: BangumiPlayUrl")

        findAndHookMethod("com.bapis.bilibili.pgc.gateway.player.v1.PlayURLMoss", mClassLoader, "playView",
                "com.bapis.bilibili.pgc.gateway.player.v1.PlayViewReq", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val response = param.result as PlayViewReply
                // Filter normal play url
                if (response.hasVideoInfo()) return

                val queryString = constructQueryString(param.args[0] as PlayViewReq)
                val contentJson = JSONObject(BiliRoamingApi.getPlayUrl(queryString))

                if (contentJson.optInt("code") == 0) {
                    val newResponse = constructProtoBufResponse(contentJson)
                    param.result = newResponse
                    log("The play url was replaced with proxy server, type: protobuf")
                }
            }
        })
    }

    private fun constructQueryString(request: PlayViewReq): String {
        return with(Uri.Builder()) {
            val accessKey = BiliBiliPackage.instance.accessKey
            if (accessKey.isNotEmpty()) {
                appendQueryParameter("access_key", accessKey)
            }
            appendQueryParameter("cid", request.cid.toString())
            appendQueryParameter("ep_id", request.epId.toString())
            appendQueryParameter("fnval", request.fnval.toString())
            appendQueryParameter("fnver", request.fnver.toString())
            appendQueryParameter("force_host", request.forceHost.toString())
            appendQueryParameter("fourk", request.fourk.toIntString())
            appendQueryParameter("qn", request.qn.toString())
        }.toString()
    }

    private fun constructProtoBufResponse(contentJson: JSONObject): PlayViewReply {
        return PlayViewReply.newBuilder().apply {
            setPlayConf(PlayAbilityConf.newBuilder().apply {
                setDislikeDisable(true)
                setElecDisable(true)
                setShakeDisable(true)
            })

            setVideoInfo(VideoInfo.newBuilder().apply {
                setFormat(contentJson.optString("format"))
                setQuality(contentJson.optInt("quality"))
                setTimelength(contentJson.optLong("timelength"))
                setVideoCodecid(contentJson.optInt("video_codecid"))

                when (contentJson.optString("type")) {
                    "DASH" -> buildInDash(this, contentJson)
                    "FLV" -> buildInSegment(this, contentJson)
                }
            })
        }.build()
    }

    private fun buildInDash(videoInfoBuilder: VideoInfo.Builder, contentJson: JSONObject) {
        videoInfoBuilder.run {
            val dash = contentJson.getJSONObject("dash")

            val audios = dash.getJSONArray("audio")
            val audioIds = mutableListOf<Int>()
            for (i in 0 until audios.length()) {
                val audio = audios.getJSONObject(i)
                val id = audio.optInt("id")
                audioIds.add(id)

                addDashAudio(DashItem.newBuilder().apply {
                    setBaseUrl(audio.optString("base_url"))
                    setBandwidth(audio.optInt("bandwidth"))
                    setCodecid(audio.optInt("codecid"))
                    setMd5(audio.optString("md5"))
                    setSize(audio.optLong("size"))
                    setId(id)

                    val urls = audio.getJSONArray("backup_url")
                    for (j in 0 until urls.length())
                        addBackupUrl(urls.getString(j))
                })
            }

            var audioIndex = audioIds.size
            val noRexcode = contentJson.optInt("no_rexcode") != 0
            val formatMap = generateFormatMap(contentJson)

            val videos = dash.getJSONArray("video")
            for (i in 0 until videos.length()) {
                val video = videos.getJSONObject(i)
                // Unused video info
                if (video.optInt("codecid") != contentJson.optInt("video_codecid")) continue

                addStreamList(Stream.newBuilder().apply {
                    setDashVideo(DashVideo.newBuilder().apply {
                        setBaseUrl(video.optString("base_url"))
                        setBandwidth(video.optInt("bandwidth"))
                        setCodecid(video.optInt("codecid"))
                        setMd5(video.optString("md5"))
                        setSize(video.optLong("size"))
                        setNoRexcode(noRexcode)

                        // Audio Bandwidth: narrow --> broad
                        // Video Bandwidth: broad  --> narrow
                        // Use the narrowest bandwidth audio when audio and video streams are not a pair. (Official logic)
                        audioIndex--
                        setAudioId(audioIds[if (audioIndex >= 0) audioIndex else 0])

                        val urls = video.getJSONArray("backup_url")
                        for (j in 0 until urls.length())
                            addBackupUrl(urls.getString(j))
                    })

                    setStreamInfo(StreamInfo.newBuilder().apply {
                        val quality = video.optInt("id")
                        formatMap[quality]?.run {
                            setDescription(optString("description"))
                            setDisplayDesc(optString("display_desc"))
                            setFormat(optString("format"))
                            setNeedLogin(optBoolean("need_login"))
                            setNeedVip(optBoolean("need_vip"))
                            setNewDescription(optString("new_description"))
                            setSuperscript(optString("superscript"))
                        }
                        setAttribute(0)
                        setIntact(true)
                        setQuality(quality)
                        setNoRexcode(noRexcode)
                    })
                })
            }
        }
    }

    private fun buildInSegment(videoInfoBuilder: VideoInfo.Builder, contentJson: JSONObject) {
        // TODO: Build probuf of type flv
        println("Flv Json => $contentJson")
    }

    private fun generateFormatMap(contentJson: JSONObject): MutableMap<Int, JSONObject> {
        val formatMap = mutableMapOf<Int, JSONObject>()
        val formatJsonArray = contentJson.getJSONArray("support_formats")

        for (i in 0 until formatJsonArray.length()) {
            val format = formatJsonArray.getJSONObject(i)
            val quality = format.optInt("quality")
            formatMap[quality] = format
        }

        return formatMap
    }
}
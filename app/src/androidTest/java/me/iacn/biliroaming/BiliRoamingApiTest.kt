package me.iacn.biliroaming

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.iacn.biliroaming.network.BiliRoamingApi
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiliRoamingApiTest {

    @Test
    fun testGetSeason() {
        val content = BiliRoamingApi.getSeason("21680", "", true)
        val contentJson = JSONObject(content)
        assertEquals(0, contentJson.optInt("code"))

        val result = contentJson.optJSONObject("result")
        assertNotNull(result)

        val modules = result!!.optJSONArray("modules")
        assertNotNull(modules)

        var episodes: JSONArray? = null
        for (i in 0 until modules!!.length()) {
            val module = modules.optJSONObject(i)
            val data = module.optJSONObject("data")
            assertNotNull(data)

            data!!.keys().forEach {
                if ("episodes" == it) {
                    episodes = data.optJSONArray("episodes")
                    return@forEach
                }
            }

            assertNotNull(episodes)
            assert(episodes!!.length() > 0)
        }

        println(content)
    }
}
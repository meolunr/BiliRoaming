package me.iacn.biliroaming.network

import java.io.InputStream

/**
 * Created by Meolunr on 2019/3/26
 * Email meolunr@gmail.com
 */
object StreamUtils {

    fun getContent(inputStream: InputStream): String {
        inputStream.bufferedReader().use {
            return it.readText()
        }
    }
}
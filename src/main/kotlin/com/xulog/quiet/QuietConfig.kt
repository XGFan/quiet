package com.xulog.quiet

import java.util.*

class QuietConfig {
    var siteName: String = "Quiet"
    var siteUrl: String = "/"
    var port: Int = 4567
    var theme: String = "moricolor"
    var debug: Boolean = false
    var contentDir: String? = null
    //这个路径下的文件不对外开放访问
    var hiddenDir: String? = null

    fun getHiddenDirs() = hiddenDir?.split(",") ?: emptyList()

    fun init(props: Properties) {
        this.javaClass.declaredFields.forEach {
            it.isAccessible = true
            val v = props[it.name]?.toString()
            if (v != null) {
                when (it.type) {
                    String::class.java -> it.set(this, v)
                    Int::class.java -> it.set(this, v.toInt())
                    Boolean::class.java -> it.set(this, v.toBoolean())
                }
            }
        }
    }


    override fun toString(): String {
        return "QuietConfig(siteName='$siteName', siteUrl='$siteUrl', port=$port, theme='$theme', debug=$debug, contentDir=$contentDir, hiddenDirs=${getHiddenDirs()})"
    }
}
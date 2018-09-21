package com.xulog.quite

import java.util.*

class QuietConfig {
    lateinit var siteName: String
    var siteUrl: String = "/"
    var port: Int = 4567
    var theme: String = "moricolor"
    var debug: Boolean = false
    var markdownDir: String? = null
    var hiddenDir: String? = null
    var hiddenDirs: List<String> = emptyList()

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
        hiddenDirs = hiddenDir?.split(",") ?: emptyList()
    }


    override fun toString(): String {
        return "QuietConfig(siteName='$siteName', siteUrl='$siteUrl', port=$port, theme='$theme', debug=$debug, markdownDir=$markdownDir, hiddenDirs=$hiddenDirs)"
    }
}
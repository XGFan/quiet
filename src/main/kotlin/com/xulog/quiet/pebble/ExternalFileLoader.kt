package com.xulog.quiet.pebble

import com.mitchellbosecke.pebble.loader.Loader
import com.mitchellbosecke.pebble.utils.PathUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.Reader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

class ExternalFileLoader(private val path: Path) : Loader<String> {

    private var prefix: String? = null

    private var suffix: String? = null

    private var charset = "UTF-8"

    private val logger = LoggerFactory.getLogger(ExternalFileLoader::class.java)

    override fun getReader(templateName: String): Reader? {
        val templatePath = (prefix ?: "") + templateName + (suffix ?: "")
        logger.debug("Looking for template in {}{}.", path.toString(), templatePath)
        val path = path.resolve(templatePath)
        return if (Files.exists(path) && Files.isReadable(path)) {
            Files.newBufferedReader(path, Charset.forName(charset))
        } else {
            null
        }
    }


    override fun setPrefix(prefix: String?) {
        this.prefix = prefix
    }

    override fun setSuffix(suffix: String?) {
        this.suffix = suffix
    }

    override fun resolveRelativePath(relativePath: String?, anchorPath: String?): String {
        return PathUtils.resolveRelativePath(relativePath, anchorPath, File.separatorChar) //todo
    }


    override fun setCharset(charset: String) {
        this.charset = charset
    }

    override fun createCacheKey(templateName: String): String {
        return templateName
    }
}
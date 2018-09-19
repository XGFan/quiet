package com.xulog.quite

import com.mitchellbosecke.pebble.PebbleEngine
import com.mitchellbosecke.pebble.loader.ClasspathLoader
import com.mitchellbosecke.pebble.loader.Loader
import com.xulog.quite.QuietApplication.Constants.MD_DIR
import com.xulog.quite.QuietApplication.Constants.STATIC_DIR
import com.xulog.quite.QuietApplication.Constants.THEME_DIR
import com.xulog.quite.pebble.ExternalFileLoader
import com.xulog.quite.pebble.PebbleTemplateEngine
import org.slf4j.LoggerFactory
import org.slf4j.impl.StaticLoggerBinder
import spark.ModelAndView
import spark.Spark
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.DateTimeFormatter
import java.util.*


class QuietApplication(clazz: Class<*>, args: Array<String>) {


    object Constants {
        const val STATIC_DIR = "public"
        const val THEME_DIR = "theme"
        const val MD_DIR = "markdown"

    }

    private val logger = LoggerFactory.getLogger("Quiet")

    val quietConfig: QuietConfig = QuietConfig()
    val postService: PostService

    val runtimePath: Path
    val markdownDirectory: Path

    var externalTheme: Path

    val pebbleTemplateEngine: PebbleTemplateEngine

    private var externalMode = true

    init {
        logger.info(StaticLoggerBinder.getSingleton().loggerFactoryClassStr)

        //初始化运行位置
        val location = clazz.protectionDomain.codeSource.location.toURI()
        runtimePath = if (location.path.endsWith(".jar")) {
            //如果是打包运行，我们则将其父目录作为保存文件的目录
            Paths.get(location).parent
        } else {
            //如果是直接运行，我们将其父目录的父目录作为保存文件的目录，方便开发调试
            //Project/quite/target/classes/
            Paths.get(location).parent.parent
        }
        logger.info("run at {}", runtimePath)

        //加载配置
        try {
            val reader = Files.newBufferedReader(runtimePath.resolve("quiet.properties"))
            val properties = Properties()
            properties.load(reader)
            quietConfig.init(properties)
            logger.info("config is {}", quietConfig)
        } catch (e: Exception) {
            logger.error("Load config failed", e)
            throw RuntimeException()
        }
        //创建文件夹
        markdownDirectory = runtimePath.resolveOrCreate(MD_DIR)
        logger.info("markdown directory is {}", markdownDirectory)


        //先找外部theme
        externalTheme = runtimePath.resolveOrCreate(THEME_DIR).resolve(quietConfig.theme)

        val templateLoader: Loader<String>

        if (Files.exists(externalTheme) && Files.isReadable(externalTheme)) {
            logger.info("theme directory is {} ,external Mode", externalTheme)
            templateLoader = ExternalFileLoader(externalTheme)
        } else {
            externalMode = false
            templateLoader = ClasspathLoader()
            templateLoader.prefix = "$THEME_DIR/${quietConfig.theme}"
            if (ClassLoader.getSystemResource("$THEME_DIR/${quietConfig.theme}") != null) {
                logger.info("theme directory is {}", quietConfig.theme)
            } else {
                logger.error("theme {} does not exists", quietConfig.theme)
                throw RuntimeException()
            }
        }

        postService = PostService(markdownDirectory)

        //创建TemplateEngine

        val pebbleEngine = PebbleEngine.Builder()
                .cacheActive(!quietConfig.debug)
                .loader(templateLoader)
                .build()
        pebbleTemplateEngine = PebbleTemplateEngine(pebbleEngine).addGlobalModel("config", quietConfig)
    }


    fun startWeb() {
        Spark.port(quietConfig.port)
        if (externalMode) {
            Spark.externalStaticFileLocation(externalTheme.resolve(STATIC_DIR).toString())
        } else {
            Spark.staticFileLocation("$THEME_DIR/${quietConfig.theme}/$STATIC_DIR")
        }

        Spark.redirect.get("", "page/1")
        Spark.redirect.get("/", "page/1")
        Spark.redirect.get("index", "page/1")

        Spark.get("page/:index", { request, response ->
            val page = request.params(":index").toInt()
            val attributes = HashMap<String, Any>()
            val metas = postService.posts.sortedByDescending { it.create }
                    .drop(10 * (page - 1))
                    .take(10).map {
                        mapOf(
                                "name" to it.name,
                                "date" to it.create,
                                "dateStr" to it.create.toLocalDate().format(DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)),
                                "url" to it.dateUri
                        )
                    }
            attributes["metas"] = metas
            attributes["current"] = page
            attributes["total"] = postService.posts.size / 10 + 1
            ModelAndView(attributes, "index.peb")
        }, pebbleTemplateEngine)


        Spark.get("*", "text/html", { req, res ->
            val uri = URLDecoder.decode(req.uri(), "UTF-8")
            val splits = uri.split("/").filter { it.isNotBlank() }
            var first =
                    if (splits.isEmpty()) {
                        "page"
                    } else if (splits.size == 1 && (splits.first() == "index" || splits.first() == "index.html")) {
                        "page"
                    } else {
                        splits.first()
                    }
            if (first == "page") {
                val page = splits.getOrNull(1)?.toIntOrNull() ?: 1
                val attributes = HashMap<String, Any>()
                val metas = postService.posts.sortedByDescending { it.create }
                        .drop(10 * (page - 1))
                        .take(10).map {
                            mapOf(
                                    "name" to it.name,
                                    "date" to it.create,
                                    "dateStr" to it.create.toLocalDate().format(DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)),
                                    "url" to it.dateUri
                            )
                        }
                attributes["metas"] = metas
                attributes["current"] = page
                attributes["total"] = postService.posts.size / 10 + 1
                ModelAndView(attributes, "index.peb")
            } else if (first == "category") {
                val cats = splits.drop(1)
                val attributes = HashMap<String, Any>()
                attributes["current"] = cats
                val metas = postService.posts.filter { it.matchCat(cats) }.map {
                    mapOf(
                            "name" to it.name,
                            "date" to it.create,
                            "dateStr" to it.create.toLocalDate().format(DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)),
                            "url" to it.dateUri
                    )
                }
                attributes["metas"] = metas
                val children = postService.posts.sortedByDescending { it.create }
                        .filter { it.isGrandPa(cats) }
                        .map { it.categories.take(cats.size + 1) }
                        .distinct()
                        .map { it.last() to it.joinToString("/", prefix = "/category/") }

                attributes["children"] = children
                ModelAndView(attributes, "archive.peb")
                //目录
            } else if (splits.last().endsWith("html")) {
                //文章
                val attributes = HashMap<String, Any>()
                val post = postService.posts
                        .find { it.matchUri(uri) }
                if (post == null) {
                    Spark.halt(404, "NOT FOUND")
                }
                val url = StringBuilder("/category")
                attributes["markdown"] = post!!.content
                attributes["title"] = post.title
                attributes["create"] = post.create
                attributes["categories"] = post.categories.map {
                    it to url.append("/").append(it).toString()
                }
                ModelAndView(attributes, "post.peb")
            } else {
                Spark.halt(404, "NOT FOUND")
                ModelAndView(null, null)
            }
        }, pebbleTemplateEngine)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val quiteApplication = QuietApplication(QuietApplication::class.java, args)
            quiteApplication.startWeb()
        }
    }


}

//todo
fun Path.resolveOrCreate(name: String): Path {
    val target = this.resolve(name)
    return if (!Files.exists(target)) {
        Files.createDirectory(target)
        return target
    } else {
        if (!Files.isDirectory(target) || !Files.isWritable(target)) {
            //已存在同名文件
            throw RuntimeException("已存在同名文件或文件夹不可写")
        } else {
            target
        }
    }

}

class QuietConfig {
    lateinit var siteName: String
    lateinit var siteUrl: String
    var port: Int = 4567
    var theme: String = "moricolor"
    var debug: Boolean = false


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
        return "QuiteConfig(siteName='$siteName', siteUrl='$siteUrl', port=$port, theme='$theme', debug=$debug)"
    }


}

val isInt: Any = object : Any() {
    override fun equals(other: Any?): Boolean {
        return if (other is String) {
            other.toIntOrNull() != null
        } else {
            false
        }
    }
}
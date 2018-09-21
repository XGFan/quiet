package com.xulog.quite

import com.mitchellbosecke.pebble.PebbleEngine
import com.mitchellbosecke.pebble.loader.ClasspathLoader
import com.mitchellbosecke.pebble.loader.Loader
import com.xulog.quite.Constants.MD_DIR
import com.xulog.quite.Constants.STATIC_DIR
import com.xulog.quite.Constants.THEME_DIR
import com.xulog.quite.pebble.ExternalFileLoader
import com.xulog.quite.pebble.PebbleTemplateEngine
import org.slf4j.LoggerFactory
import org.slf4j.impl.StaticLoggerBinder
import spark.ModelAndView
import spark.Spark
import java.io.File
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.DateTimeFormatter
import java.util.*

class QuietApplication(clazz: Class<*>, args: Array<String>) {

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
            //Project/quiet/target/classes/
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
        markdownDirectory = if (quietConfig.markdownDir == null) {
            runtimePath.resolveOrCreate(MD_DIR)
        } else {
            File(quietConfig.markdownDir).toPath()
        }
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

        postService = PostService(markdownDirectory, quietConfig)

        //创建TemplateEngine

        val pebbleEngine = PebbleEngine.Builder()
                .cacheActive(!quietConfig.debug)
                .loader(templateLoader)
                .build()
        pebbleTemplateEngine = PebbleTemplateEngine(pebbleEngine).addGlobalModel("config", quietConfig)
    }


    val postToMeta: (Post) -> Map<String, Any> = {
        mapOf(
                "name" to it.name,
                "date" to it.create,
                "dateStr" to it.create.toLocalDate().format(DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)),
                "url" to it.dateUri
        )
    }


    private val page: API = { req, res ->
        val uri = URLDecoder.decode(req.uri(), "UTF-8")
        val splits = uri.split("/").filter { it.isNotBlank() }
        val first =
                if (splits.isEmpty()) {
                    "page"
                } else if (splits.size == 1 && (splits.first() == "index" || splits.first() == "index.html")) {
                    "page"
                } else {
                    splits.first()
                }
        when {
            first == "page" -> {//首页
                val pageIndex = splits.getOrNull(1)?.toIntOrNull() ?: 1
                val attributes = HashMap<String, Any>()
                val page = postService.findPost(10 * (pageIndex - 1), 10)
                attributes["metas"] = page.content.map(postToMeta).toList() //当前页文章信息
                attributes["current"] = pageIndex //当前页码
                attributes["total"] = page.total / 10 + 1 //总目录
                ModelAndView(attributes, "index.peb")
            }
            first == "category" -> {//分类目录
                val cats = splits.drop(1)
                val attributes = HashMap<String, Any>()
                attributes["metas"] = postService.findPost(cats).asSequence().map(postToMeta).toList() //当前页文章信息
                attributes["current"] = cats.toCategories() //当前目录
                attributes["children"] = postService.findChildren(cats) //子目录
                ModelAndView(attributes, "archive.peb")
            }
            splits.last().endsWith("html") -> { //文章
                val attributes = HashMap<String, Any>()
                val post = postService.findPost(uri)
                if (post == null) {
                    Spark.halt(404, "NOT FOUND")
                }
                attributes["markdown"] = post!!.content
                attributes["title"] = post.title
                attributes["create"] = post.create
                attributes["categories"] = post.categories.toCategories()
                ModelAndView(attributes, "post.peb")
            }
            else -> {
                Spark.halt(404, "NOT FOUND")
                ModelAndView(null, null)
            }
        }
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
        Spark.get("*", "text/html", page, pebbleTemplateEngine)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val quiteApplication = QuietApplication(QuietApplication::class.java, args)
            quiteApplication.startWeb()
        }
    }
}
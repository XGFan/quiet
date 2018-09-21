package com.xulog.quite

import com.vladsch.flexmark.ext.footnotes.FootnoteExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.toc.TocExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import com.xulog.quite.Constants.MARKDOWN_EXTS
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap


class PostService
constructor(private val quiet: Path, private val quietConfig: QuietConfig) {

    private val logger = LoggerFactory.getLogger("[${quiet.fileName}] Worker")

    private val posts: LinkedList<Post> = LinkedList()

    private val tasks = ConcurrentHashMap.newKeySet<Path>()

    private val parser: Parser
    private val renderer: HtmlRenderer

    private val dateParser = DateTimeFormatter.ofPattern("[yyyy-MM-dd HH:mm:ss][yyyy-MM-dd HH:mm][yyyy-MM-dd]")

    //todo 略微尴尬的一些操作
    init {
        //创建markdown parse和renderer
        val options = MutableDataSet()
                .set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(),
                        FootnoteExtension.create(),
                        TocExtension.create(),
                        StrikethroughExtension.create()))
                .set(HtmlRenderer.SOFT_BREAK, "<br/>")
        parser = Parser.builder(options).build()
        renderer = HtmlRenderer.builder(options).build()

        object : Watcher(quiet) {
            override fun onUpdate(file: Path) {
                tasks.add(file)
            }
        }.init()

        Thread(Runnable {
            while (true) {
                tasks.removeAll { taskPath ->
                    posts.removeAll { it.path == taskPath || it.path.startsWith(taskPath) }
                    if (Files.exists(taskPath)) {
                        if (MARKDOWN_EXTS.any { taskPath.fileName.toString().endsWith(it) }) {
                            logger.info("process {}", taskPath)
                            try {
                                val post = parseFile(quiet, taskPath)
                                posts.add(post)
                                posts.sortByDescending { it.create }
                            } catch (e: Exception) {
                                logger.error("process {} error", taskPath, e)
                            }
                        }
                    }
                    true
                }
                Thread.sleep(200L)
            }
        }, "Worker").start()
    }


    fun findPost(offset: Int, limit: Int): Page<Post> {
        val filter = posts.asSequence().filter { it.showOnPage }
                .filter { it.categories.intersect(quietConfig.hiddenDirs).isEmpty() }
        return filter.drop(offset).take(limit) to filter.count()
    }

    fun findPost(uri: String): Post? {
        return posts.find { it.matchUri(uri) }
    }

    fun findPost(cats: List<String>): List<Post> {
        return posts.filter { it.matchCat(cats) }
    }

    fun findChildren(cats: List<String>): List<Category> {
        return posts.asSequence().filter { it.isGrandPa(cats) }
                .map { it.categories.take(cats.size + 1) }
                .distinct()
                .map { it.last() to it.joinToString("/", prefix = "/category/") }.toList()
    }

    /**
     * 解析markdown并预渲染文件
     */
    private fun parseFile(root: Path, markdown: Path): Post {
        val filename = markdown.fileName.toString().substringBeforeLast(".")
        val lines = ArrayList<String>()
        val newBufferedReader = Files.newBufferedReader(markdown)
        val content = newBufferedReader.use {
            val firstLine = it.readLine()
            if (firstLine.contains("---")) {
                while (true) {
                    val readLine = it.readLine()
                    if (readLine.endsWith("---")) {
                        break
                    } else {
                        lines.add(readLine)
                    }
                }
            }
            val document = parser.parseReader(it)
            renderer.render(document)
        }
        val map = lines.map {
            val index = it.indexOf(":")
            it.substring(0, index).trim() to it.substring(index + 1).trim()
        }.toMap()
        val attr = Files.readAttributes(markdown, BasicFileAttributes::class.java)
        val creationTime = LocalDateTime.ofInstant(attr.creationTime().toInstant(), ZoneId.systemDefault())
        val lastModifiedTime = LocalDateTime.ofInstant(attr.lastModifiedTime().toInstant(), ZoneId.systemDefault())
        val (create, update) = getDate(creationTime, lastModifiedTime, map)
        return Post(markdown,
                filename,
                map["title"]?.cleanQuotes() ?: filename,
                map["slug"]?.cleanQuotes(),
                markdown.categoriesOf(root),
                emptyList(),
                create,
                update,
                map["showOnPage"]?.toBoolean() ?: true,
                content
        )
    }

    private fun getDate(fileCreation: LocalDateTime, fileModify: LocalDateTime, header: Map<String, String>): Pair<LocalDateTime, LocalDateTime> {
        var create: LocalDateTime = (header["create"]?.let { LocalDateTime.parse(it, dateParser) } //TODO
                ?: min(fileCreation, fileModify))
        var update = (header["date"]?.let { LocalDateTime.parse(it, dateParser) } //TODO
                ?: max(fileCreation, fileModify))
        create = min(create, update)
        update = max(create, update)
        return create to update
    }
}

data class Post(val path: Path,
                val name: String, //文件名,不带后缀
                val title: String,
                val slug: String? = null, //url？
                val categories: List<String> = emptyList(),//目录
                val tags: List<String> = emptyList(),
                val create: LocalDateTime, //创建时间
                val update: LocalDateTime,//最后修改时间
                var showOnPage: Boolean = true,
                var content: String) {
    val dateUri = "/${create.format(Constants.DATE_IN_URL)}/${slug ?: title}.html"

    val categoryUri = "${categories.joinToString("/", prefix = "/", postfix = "/")}${slug ?: title}.html"

    fun matchUri(url: String): Boolean {
        return dateUri == url || categoryUri == url
    }

    fun matchCat(cats: List<String>): Boolean {
        return cats.size == categories.size && cats == categories
    }

    fun isGrandPa(cats: List<String>): Boolean {
        return cats.size <= categories.size - 1 && cats == categories.take(cats.size)
    }
}
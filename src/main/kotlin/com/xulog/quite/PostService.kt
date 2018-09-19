package com.xulog.quite

import com.vladsch.flexmark.ext.footnotes.FootnoteExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.toc.TocExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.WatchKey
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque


class PostService
constructor(val quite: Path) {

    val logger = LoggerFactory.getLogger("${quite.fileName} Worker")

    val extList = arrayOf("md", "markdown", "mmd", "mdown")
    val keys = HashMap<WatchKey, Path>()

    val posts = ConcurrentLinkedDeque<Post>()

    val tasks = ConcurrentHashMap.newKeySet<Path>()

    val parser: Parser
    val renderer: HtmlRenderer

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

        object : Watcher(quite) {
            override fun onUpdate(file: Path) {
                tasks.add(file)
            }
        }.init()

        Thread(Runnable {
            while (true) {
                tasks.removeAll { taskPath ->
                    posts.removeAll { it.path == taskPath || it.path.startsWith(taskPath) }
                    if (Files.exists(taskPath)) {
                        if (extList.any { taskPath.fileName.toString().endsWith(it) }) {
                            logger.info("process {}", taskPath)
                            val post = parseFile(quite, taskPath)
                            posts.add(post)
                        }
                    }
                    true
                }
                Thread.sleep(200L)
            }
        }, "Worker").start()
    }


    /**
     * 解析markdown并预渲染文件
     */
    fun parseFile(root: Path, markdown: Path): Post {
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
        return Post(markdown,
                filename,
                map["title"]?.cleanQuotes() ?: filename,
                map["slug"]?.cleanQuotes(),
                markdown.categoriesOf(root),
                emptyList(),
                map["create"]?.let { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }
                        ?: creationTime,
                map["date"]?.let { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }
                        ?: lastModifiedTime,
                content
        )
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
                var content: String) {

    val dateUri = "/${create.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}/${slug ?: title}.html"

    val categoryUri = "/${categories.joinToString("/")}/${slug ?: title}.html"

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


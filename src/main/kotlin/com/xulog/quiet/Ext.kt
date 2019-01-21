package com.xulog.quiet

import spark.ModelAndView
import spark.Request
import spark.Response
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 清除引号
 */
fun String.cleanQuotes(): String = if (this.startsWith("\"") && this.endsWith("\"")) {
    this.drop(1).dropLast(1)
} else {
    this
}

/**
 * 找出中间目录
 */
fun Path.categoriesOf(root: Path): List<String> {
    if (!this.startsWith(root)) {
        throw RuntimeException("非${root}下文件")
    }
    return (root.nameCount until this.nameCount - 1).map {
        this.subpath(it, it + 1).toString()
    }
}


fun min(a: LocalDateTime, b: LocalDateTime) = if (a.isAfter(b)) b else a
fun max(a: LocalDateTime, b: LocalDateTime) = if (a.isAfter(b)) a else b


typealias Page<T> = Pair<Sequence<T>, Int>

val <T> Page<T>.content
    get() = first

val <T> Page<T>.total
    get() = second

typealias Category = Pair<String, String>

val Category.name
    get() = first
val Category.url
    get() = second

object Constants {
    const val STATIC_DIR = "public"
    const val THEME_DIR = "theme"
    const val MD_DIR = "markdown"
    val MARKDOWN_EXTS = arrayOf("md", "markdown", "mmd", "mdown")
    val DATE_IN_URL = DateTimeFormatter.ofPattern("yyyy/MM/dd")!!
}

typealias API = (Request, Response) -> ModelAndView?

//todo
fun Path.resolveOrCreate(name: String): Path {
    val target = this.resolve(name)
    return if (!Files.exists(target)) {
        Files.createDirectory(target)
        return target
    } else {
        if (!Files.isDirectory(target) || !Files.isReadable(target)) {
            //已存在同名文件
            throw RuntimeException("已存在同名文件或文件夹不可读")
        } else {
            target
        }
    }

}

fun List<String>.toCategories(): List<Category> {
    val url = StringBuilder("/category")
    return this.map {
        it to url.append("/").append(it).toString()
    }
}
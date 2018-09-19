package com.xulog.quite

import java.nio.file.Path

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
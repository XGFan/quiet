package com.xulog.quiet

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.attribute.BasicFileAttributes


abstract class Watcher(val root: Path) {
    val logger: Logger = LoggerFactory.getLogger("[${root.fileName}] Watcher")
    val keys = HashMap<WatchKey, Path>()
    val watcher: WatchService = FileSystems.getDefault().newWatchService()

    val watchEvents = arrayOf(OVERFLOW, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)

    private val fileVisitor = object : SimpleFileVisitor<Path>() {
        @Throws(IOException::class)
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (canAddToWatchList(dir)) {
                logger.debug("Add new directory {}", dir)
                val key = dir.register(watcher, watchEvents)
                keys[key] = dir
            }
            return FileVisitResult.CONTINUE
        }


        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            try {
                onUpdate(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return FileVisitResult.CONTINUE

        }
    }

    fun init() {
        Thread(Runnable {
            Files.walkFileTree(root, fileVisitor)
            while (true) {
                val key: WatchKey
                try {
                    key = watcher.take()
                } catch (x: InterruptedException) {
                    logger.debug("Thread Interrupted")
                    break
                }
                val pollEvents = key.pollEvents()
                for (event in pollEvents) {
                    val ev = event as WatchEvent<Path>
                    val eventKind = ev.kind()
                    val file = keys[key]?.resolve(event.context()) ?: continue //todo
                    if (file.fileName.toString().startsWith(".")) {
                        logger.trace("Skip {}", file)
                        continue
                    }
                    when (eventKind) {
                        ENTRY_DELETE -> {
                            val oldKeys = keys.filterValues { it == file || it.startsWith(file) }.keys
                            if (oldKeys.isNotEmpty()) {
                                logger.debug("Remove old directory {} {}", file, oldKeys.size)
                                oldKeys.forEach { keys.remove(it) }
                                onUpdate(file)
                            } else {
                                logger.debug("Delete {}", file)
                                onUpdate(file)
                            }
                        }
                        ENTRY_CREATE -> if (Files.isDirectory(file)) {
                            Files.walkFileTree(file, fileVisitor)
                        } else {
                            logger.debug("Create {}", file)
                            onUpdate(file)
                        }
                        ENTRY_MODIFY -> {
                            if (!Files.isDirectory(file)) {
                                logger.debug("Modify {}", file)
                                onUpdate(file)
                            }
                        }
                    }
                }
                val valid = key.reset()
                if (!valid) {
                    keys.remove(key)
                    if (keys.isEmpty()) {
                        break
                    }
                }
            }

        }, "Watcher").start()

    }

    open fun canAddToWatchList(directory: Path): Boolean {
        return !Files.isHidden(directory) && !directory.fileName.toString().startsWith(".")
    }

    abstract fun onUpdate(file: Path)
}
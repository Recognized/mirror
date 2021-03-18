package com.github.recognized.mirror

import com.google.protobuf.ByteString
import com.google.protobuf.TextFormat
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import mirror.FileWatcher
import mirror.Update
import mirror.Utils
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

class IdeaProjectFileWatcher(
        private val project: Project,
        private val queue: BlockingQueue<Update>
) : FileWatcher, Disposable {
    private val basePath = project.basePath ?: error("Project is required to have base path")
    private val log = LoggerFactory.getLogger(IdeaProjectFileWatcher::class.java)

    init {
        Disposer.register(project, this)
    }

    override fun dispose() {}

    private val listener = object : BulkFileListener {
        override fun after(events: List<VFileEvent>) {
            events.forEach {
                when (it) {
                    is VFileMoveEvent -> {
                        val newPath = Paths.get(it.newPath)
                        val oldPath = Paths.get(it.oldPath)
                        putFile(
                                path = oldPath,
                                exists = false,
                                mtime = System.currentTimeMillis(),
                                isDirectory = newPath.isDirectory(),
                                isExecutable = false,
                                isSymlink = false
                        )
                    }
                }
                val vFile = it.file
                val path = Paths.get(it.path)
                putFile(
                        path = Paths.get(it.path),
                        exists = vFile?.exists() ?: path.exists(),
                        mtime = System.currentTimeMillis(),
                        isDirectory = vFile?.isDirectory ?: path.isDirectory(),
                        isExecutable = path.toFile().canExecute(),
                        isSymlink = vFile?.`is`(VFileProperty.SYMLINK) ?: path.basicAttributesIfExists()?.isSymbolicLink
                        ?: false,
                        content = vFile?.contentsToByteArray()?.let { content -> ByteString.copyFrom(content) }
                )
            }
        }
    }

    override fun runOneLoop(): Duration? {
        return Duration.ofSeconds(1)
    }

    override fun onStart() {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, listener)
    }

    override fun onStop() {
        Disposer.dispose(this)
    }

    override fun performInitialScan(): List<Update> {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Performing initial scan", false) {
            override fun run(indicator: ProgressIndicator) {
                Files.walkFileTree(Paths.get(basePath), object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        putFile(
                                dir,
                                exists = dir.exists(),
                                mtime = attrs.lastModifiedTime().toMillis(),
                                isDirectory = true,
                                isSymlink = attrs.isSymbolicLink,
                                isExecutable = false
                        )
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        putFile(
                                file,
                                exists = file.exists(),
                                mtime = attrs.lastModifiedTime().toMillis(),
                                isDirectory = attrs.isDirectory,
                                isSymlink = attrs.isSymbolicLink,
                                isExecutable = file.toFile().canExecute()
                        )
                        return FileVisitResult.CONTINUE
                    }
                })
            }
        })
        val updates = ArrayList<Update>(queue.size)
        queue.drainTo(updates)
        return updates
    }

    private fun putFile(path: Path,
                        exists: Boolean,
                        mtime: Long,
                        isDirectory: Boolean,
                        isExecutable: Boolean,
                        isSymlink: Boolean,
                        content: ByteString? = null
    ) {
        val name = path.fileName.toString()
        Utils.resetIfInterrupted {
            val ub = Update
                    .newBuilder()
                    .setPath(name)
                    .setDelete(!exists)
                    .setModTime(mtime)
                    .setDirectory(isDirectory)
                    .setExecutable(isExecutable)
                    .setLocal(true)
            if (content != null) {
                ub.data = content
            }
            readSymlinkTargetIfNeeded(ub, isSymlink)
            setIgnoreStringIfNeeded(ub)
            clearModTimeIfADelete(ub)
            val u = ub.build()
            if (log.isTraceEnabled) {
                log.trace("Queueing: " + TextFormat.shortDebugString(u))
            }
            queue.put(u)
        }
    }

    private fun clearModTimeIfADelete(ub: Update.Builder) {
        if (ub.delete) {
            ub.clearModTime()
        }
    }

    private fun setIgnoreStringIfNeeded(ub: Update.Builder) {
        if (ub.path.endsWith(".gitignore")) {
            try {
                ub.ignoreString = FileUtils.readFileToString(File(ub.path), StandardCharsets.UTF_8)
            } catch (e: IOException) {
                // ignore as the file probably disappeared
                log.debug("Exception reading .gitignore, assumed stale", e)
            }
        }
    }

    private fun readSymlinkTargetIfNeeded(ub: Update.Builder, isSymlink: Boolean) {
        if (isSymlink) {
            readSymlinkTarget(ub)
        }
    }

    private fun readSymlinkTarget(ub: Update.Builder) {
        try {
            val path: Path = Paths.get(ub.path)
            val symlink = Files.readSymbolicLink(path)
            val targetPath = if (symlink.isAbsolute) {
                path.parent.normalize().relativize(symlink.normalize()).toString()
            } else {
                symlink.toString()
            }
            ub.symlink = targetPath
            ub.modTime = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis()
        } catch (e: IOException) {
            log.debug("Exception reading symlink, assumed stale", e)
        }
    }
}
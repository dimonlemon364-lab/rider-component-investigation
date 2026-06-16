package com.componentinvestigation

import com.jetbrains.rd.ide.model.RelationEntry
import com.jetbrains.rd.ide.model.RelationsResult
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Renders a [RelationsResult] to a Markdown file and opens it. Links use `relativePath` and
 * `relativePath:line`, which [RelationMarkdownLinkOpener] turns into jump-to-line navigation.
 */
object RelationMarkdownExporter {

    fun export(project: Project, result: RelationsResult) {
        // File write + VFS refresh are disk operations — keep them off the EDT; open on the UI thread.
        ApplicationManager.getApplication().executeOnPooledThread {
            val markdown = render(result)
            val base = project.basePath ?: System.getProperty("java.io.tmpdir")
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val outDir = File(base, ".relations").apply { mkdirs() }
            val outFile = File(outDir, "Relations-${sanitize(result.targetName)}-$stamp.md")
            outFile.writeText(markdown)
            val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outFile)

            ApplicationManager.getApplication().invokeLater {
                if (vFile != null) {
                    FileEditorManager.getInstance(project).openFile(vFile, true)
                }
                notify(project, "Exported ${result.entries.size} relations to ${outFile.name}")
            }
        }
    }

    /** Public for unit testing the rendering against a golden string. */
    fun render(result: RelationsResult): String = buildString {
        val entries = result.entries
        val fileCount = entries.map { it.relativePath }.distinct().size
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        appendLine("# Relations of ${result.targetName}")
        appendLine()
        appendLine("_Generated $now · ${entries.size} usages across $fileCount files_")
        appendLine()

        appendLine("## List of usage")
        appendLine()
        entries.groupBy { it.relativePath }
            .toSortedMap()
            .forEach { (path, group) ->
                val fileName = path.substringAfterLast('/')
                appendLine("### [$fileName (${group.size} items found)](${link(group.first().filePath)})")
                group.sortedBy { it.line }.forEach { appendLine(renderUsageBullet(it)) }
                appendLine()
            }

        appendLine("## By member / access")
        appendLine()
        entries.groupBy { RelationUsage.memberKindLabel(it.memberKind) + ": " + it.memberName }
            .toSortedMap()
            .forEach { (member, memberGroup) ->
                appendLine("### $member")
                memberGroup.groupBy { RelationUsage.accessKindLabel(it.accessKind) }
                    .toSortedMap()
                    .forEach { (access, accessGroup) ->
                        appendLine("- **$access** (${accessGroup.size})")
                        accessGroup.sortedBy { it.relativePath }.forEach {
                            val target = "${link(it.filePath)}:${it.line}"
                            appendLine("  - [${it.relativePath}:${it.line}]($target) ${inlineSnippet(it)}")
                        }
                    }
                appendLine()
            }
    }

    private fun renderUsageBullet(entry: RelationEntry): String {
        val target = "${link(entry.filePath)}:${entry.line}"
        val access = RelationUsage.accessKindLabel(entry.accessKind)
        return if (entry.usageSnippet.contains('\n')) {
            // Multi-line snippet -> fenced block under the bullet.
            buildString {
                appendLine("- _${access}_ [link]($target)")
                appendLine()
                appendLine("  ```")
                entry.usageSnippet.lineSequence().forEach { appendLine("  $it") }
                append("  ```")
            }
        } else {
            "- `${entry.usageSnippet}` — _${access}_ [link]($target)"
        }
    }

    private fun inlineSnippet(entry: RelationEntry): String =
        if (entry.usageSnippet.contains('\n')) "" else "`${entry.usageSnippet.trim()}`"

    // Percent-encode spaces only; keep '/' and the ':line' suffix intact for the link opener.
    private fun link(path: String): String = path.replace(" ", "%20")

    private fun sanitize(name: String): String =
        name.ifBlank { "Component" }.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun notify(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Component Investigation")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }
}

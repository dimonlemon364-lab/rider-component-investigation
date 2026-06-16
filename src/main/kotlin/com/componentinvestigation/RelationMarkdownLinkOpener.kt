package com.componentinvestigation

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private val LOG = logger<RelationMarkdownLinkOpener>()

/**
 * Overrides the Markdown preview's link opener (an application service in 2025.3 — there is no
 * link-opener extension point) so that `path` / `path:line` links in our exported reports — and any
 * local-file link — navigate to the file (at the line) in the editor. External links still browse.
 */
class RelationMarkdownLinkOpener : MarkdownLinkOpener {

    override fun isSafeLink(project: Project?, link: String): Boolean =
        (project != null && resolve(project, link) != null) || isExternal(link)

    override fun openLink(project: Project?, link: String) = handle(project, link)

    override fun openLink(project: Project?, link: String, sourceFile: VirtualFile?) = handle(project, link)

    private fun handle(project: Project?, link: String) {
        if (project != null) {
            val target = resolve(project, link)
            LOG.debug("Markdown link '$link' -> ${target?.first?.path}:${target?.second}")
            if (target != null) {
                val (file, line) = target
                ApplicationManager.getApplication().invokeLater {
                    OpenFileDescriptor(project, file, (line - 1).coerceAtLeast(0), 0).navigate(true)
                }
                return
            }
        }
        if (isExternal(link)) BrowserUtil.browse(link)
    }

    private fun isExternal(link: String): Boolean =
        link.startsWith("http://") || link.startsWith("https://") || link.startsWith("mailto:")

    /** Parses `<path>[:<line>]`, resolves the file inside the project; null if it isn't one. */
    private fun resolve(project: Project, rawLink: String): Pair<VirtualFile, Int>? {
        if (isExternal(rawLink) || rawLink.startsWith("#")) return null

        var decoded = URLDecoder.decode(rawLink.substringBefore('#'), StandardCharsets.UTF_8)
        // The Markdown preview turns local hrefs into file: URLs — strip the scheme.
        decoded = decoded.removePrefix("file://").removePrefix("file:")

        // Split a trailing :<digits> as the line (keeps Windows C:\ paths intact).
        val colon = decoded.lastIndexOf(':')
        val (path, line) =
            if (colon > 1 && decoded.length > colon + 1 && decoded.substring(colon + 1).all { it.isDigit() }) {
                decoded.substring(0, colon) to decoded.substring(colon + 1).toInt()
            } else {
                decoded to 1
            }

        val file = resolveFile(project, path) ?: return null
        return file to line
    }

    private fun resolveFile(project: Project, path: String): VirtualFile? {
        val lfs = LocalFileSystem.getInstance()
        // Absolute path first, then relative to the project/solution base dir.
        lfs.findFileByPath(path)?.let { return it }
        val base = project.basePath ?: return null
        return lfs.findFileByPath("$base/${path.trimStart('/')}")
    }
}

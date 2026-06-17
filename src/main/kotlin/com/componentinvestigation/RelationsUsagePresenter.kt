package com.componentinvestigation

import com.jetbrains.rd.ide.model.RelationEntry
import com.jetbrains.rd.ide.model.RelationsResult
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewPresentation
import com.intellij.util.concurrency.AppExecutorUtil

/** Turns the backend [RelationsResult] into IntelliJ usages and shows the native Find Usages view. */
class RelationsUsagePresenter(private val project: Project) {

    fun show(result: RelationsResult) {
        // Building UsageInfo creates PSI smart pointers (workspace-index access) — must run off the
        // EDT under a read action; the view itself is then shown back on the UI thread.
        ReadAction.nonBlocking<Array<Usage>> {
            result.entries.mapNotNull(::toUsage).toTypedArray()
        }
            .inSmartMode(project)
            .expireWith(project)
            .finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState()) { usages ->
                showView(result, usages)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun showView(result: RelationsResult, usages: Array<Usage>) {
        val presentation = UsageViewPresentation().apply {
            tabText = "Relations of ${result.targetName}"
            toolwindowTitle = "Relations of ${result.targetName}"
            searchString = "Relations of ${result.targetName}"
            isOpenInNewTab = true
            isCodeUsages = true
        }

        val view = UsageViewManager.getInstance(project)
            .showUsages(UsageTarget.EMPTY_ARRAY, usages, presentation)

        // Export button in the results' lower toolbar.
        view.addButtonToLowerPane(
            { RelationMarkdownExporter.export(project, result) },
            "Export to Markdown",
        )

        // Switch the results between member-first (member → access → file, the default) and
        // file-first (file → member → access). A custom grouping-toolbar toggle would need internal
        // API to force a regroup, so we flip the session flag and re-show: the fresh view re-reads
        // MemberUsageGroupingRule.getRank() and groups accordingly.
        val toggleLabel = if (RelationGroupingState.groupByFileFirst) "Group by Member" else "Group by File"
        view.addButtonToLowerPane(
            {
                RelationGroupingState.groupByFileFirst = !RelationGroupingState.groupByFileFirst
                view.close()
                showView(result, usages)
            },
            toggleLabel,
        )
    }

    private fun toUsage(entry: RelationEntry): Usage? {
        val file = LocalFileSystem.getInstance().findFileByPath(entry.filePath) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null

        // Rider's IntelliJ frontend has no real PSI for C#/VB/ASP files, so findElementAt(offset) can
        // return a whole-file element (or null), which makes the UsageInfo span the file and navigate
        // to line 1 (seen for *.ascx.vb). Anchor navigation on entry.line — the value the Markdown
        // export uses and which is reliably correct — and only trust the precise offset when it
        // actually lands on that line.
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            ?: FileDocumentManager.getInstance().getDocument(file)
            ?: return RelationUsage(UsageInfo(psiFile), entry)

        val (start, end) = resolveRange(document, entry)
        return RelationUsage(UsageInfo(psiFile, start, end), entry)
    }

    /** Maps a backend [RelationEntry] to an in-file text range, preferring its line over its offset. */
    private fun resolveRange(document: Document, entry: RelationEntry): Pair<Int, Int> {
        val lineIndex = (entry.line - 1).coerceIn(0, (document.lineCount - 1).coerceAtLeast(0))
        val lineStart = document.getLineStartOffset(lineIndex)
        val lineEnd = document.getLineEndOffset(lineIndex)

        val offsetTrustworthy = entry.offset in 0 until document.textLength &&
            document.getLineNumber(entry.offset) == lineIndex
        val start = if (offsetTrustworthy) entry.offset else lineStart
        val end = (start + entry.length).coerceIn(start, lineEnd)
        return start to end
    }
}

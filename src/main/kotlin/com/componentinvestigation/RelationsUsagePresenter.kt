package com.componentinvestigation

import com.jetbrains.rd.ide.model.RelationEntry
import com.jetbrains.rd.ide.model.RelationsResult
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
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
    }

    private fun toUsage(entry: RelationEntry): Usage? {
        val file = LocalFileSystem.getInstance().findFileByPath(entry.filePath) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        val element = psiFile.findElementAt(entry.offset) ?: psiFile
        val usageInfo = UsageInfo(element)
        return RelationUsage(usageInfo, entry)
    }
}

package com.componentinvestigation

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.rd.util.lifetime
import com.jetbrains.rd.ide.model.componentInvestigationModel
import com.jetbrains.rider.projectView.solution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Subscribes to the backend's `showRelations` signal (fired when the "Relations" Code Vision lens
 * is clicked) and shows the result in the native Find Usages view.
 */
class RelationsProtocolListener : ProjectActivity {
    override suspend fun execute(project: Project) {
        // RdSignal.advise must run on the protocol dispatcher (the UI thread); ProjectActivity.execute
        // runs on a background coroutine, so switch to the EDT before subscribing.
        withContext(Dispatchers.EDT) {
            val model = project.solution.componentInvestigationModel
            model.showRelations.advise(project.lifetime) { result ->
                // show() builds usages off-EDT and shows the view on the UI thread itself.
                RelationsUsagePresenter(project).show(result)
            }
        }
    }
}

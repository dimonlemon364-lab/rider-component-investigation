package model

import com.jetbrains.rd.generator.nova.*
import com.jetbrains.rd.generator.nova.PredefinedType.*
import com.jetbrains.rider.model.nova.ide.SolutionModel

/**
 * Protocol model for "Show Relations for Component/Class".
 *
 * Extends the Rider Solution model, so rdgen generates:
 *  - frontend (Kotlin):  `Solution.componentInvestigationModel`
 *  - backend  (C#):      `Solution.GetComponentInvestigationModel()`
 */
@Suppress("unused")
object ComponentInvestigationModel : Ext(SolutionModel.Solution) {

    private val UsageAccessKind = enum {
        +"Read"
        +"Write"
        +"ReadWrite"
        +"Invocation"
        +"Instantiation"
        +"TypeUsage"
        +"Other"
    }

    private val MemberKind = enum {
        +"Class"
        +"Method"
        +"Property"
        +"Field"
        +"Constant"
        +"Event"
        +"Other"
    }

    private val RelationEntry = structdef {
        field("memberName", string)
        field("memberKind", MemberKind)
        field("filePath", string)       // absolute path (for opening in the editor)
        field("relativePath", string)   // solution-relative path (for Markdown links)
        field("offset", int)
        field("length", int)
        field("line", int)              // 1-based line number
        field("containerName", string)  // enclosing method/property/type for display
        field("accessKind", UsageAccessKind)
        field("previewText", string)    // single-line text for the usage row
        field("usageSnippet", string)   // real example of use (full statement / tag)
    }

    private val RelationsResult = structdef {
        field("targetName", string)     // the class/component the relations are for
        field("entries", immutableList(RelationEntry))
    }

    init {
        // Backend fires this when the "Relations" Code Vision lens is clicked; the frontend
        // advises it and shows the result in the Find Usages view.
        signal("showRelations", RelationsResult)
    }
}

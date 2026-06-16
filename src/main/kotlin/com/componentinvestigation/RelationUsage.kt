package com.componentinvestigation

import com.jetbrains.rd.ide.model.MemberKind
import com.jetbrains.rd.ide.model.RelationEntry
import com.jetbrains.rd.ide.model.UsageAccessKind
import com.intellij.icons.AllIcons
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageInfo2UsageAdapter
import javax.swing.Icon

/**
 * A Find Usages [com.intellij.usages.Usage] that remembers which member it belongs to and how
 * the symbol is accessed, so [MemberUsageGroupingRule] can build the member → access tree.
 */
class RelationUsage(
    usageInfo: UsageInfo,
    val entry: RelationEntry,
) : UsageInfo2UsageAdapter(usageInfo) {

    val memberLabel: String get() = "${memberKindLabel(entry.memberKind)}: ${entry.memberName}"
    val memberOrder: Int get() = entry.memberKind.ordinal
    val memberIcon: Icon get() = memberKindIcon(entry.memberKind)

    val accessLabel: String get() = accessKindLabel(entry.accessKind)
    val accessOrder: Int get() = entry.accessKind.ordinal
    val accessIcon: Icon get() = accessKindIcon(entry.accessKind)

    companion object {
        fun memberKindLabel(kind: MemberKind): String = when (kind) {
            MemberKind.Class -> "Class (component)"
            MemberKind.Method -> "Method"
            MemberKind.Property -> "Property"
            MemberKind.Field -> "Field"
            MemberKind.Constant -> "Constant"
            MemberKind.Event -> "Event"
            MemberKind.Other -> "Other"
        }

        fun accessKindLabel(kind: UsageAccessKind): String = when (kind) {
            UsageAccessKind.Read -> "Read access"
            UsageAccessKind.Write -> "Write access"
            UsageAccessKind.ReadWrite -> "Read/Write access"
            UsageAccessKind.Invocation -> "Invocation"
            UsageAccessKind.Instantiation -> "Instantiation"
            UsageAccessKind.TypeUsage -> "Type usage"
            UsageAccessKind.Other -> "Other"
        }

        private fun memberKindIcon(kind: MemberKind): Icon = when (kind) {
            MemberKind.Class -> AllIcons.Nodes.Class
            MemberKind.Method -> AllIcons.Nodes.Method
            MemberKind.Property -> AllIcons.Nodes.Property
            MemberKind.Field -> AllIcons.Nodes.Field
            MemberKind.Constant -> AllIcons.Nodes.Constant
            MemberKind.Event -> AllIcons.Nodes.Property
            MemberKind.Other -> AllIcons.Nodes.Unknown
        }

        private fun accessKindIcon(kind: UsageAccessKind): Icon = when (kind) {
            UsageAccessKind.Read -> AllIcons.Actions.Show
            UsageAccessKind.Write -> AllIcons.Actions.Edit
            UsageAccessKind.ReadWrite -> AllIcons.Actions.Edit
            UsageAccessKind.Invocation -> AllIcons.Nodes.Method
            UsageAccessKind.Instantiation -> AllIcons.Nodes.Class
            UsageAccessKind.TypeUsage -> AllIcons.Nodes.Class
            UsageAccessKind.Other -> AllIcons.Nodes.Unknown
        }
    }
}

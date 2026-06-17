package com.componentinvestigation

import com.intellij.openapi.project.Project
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageTarget
import com.intellij.usages.rules.UsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRuleProvider
import javax.swing.Icon

/**
 * Two-level grouping for [RelationUsage]s: member → access type. Other usages (regular Find
 * Usages) are ignored, so this rule is inert outside our results view.
 */
class MemberUsageGroupingRule : UsageGroupingRule {

    override fun getParentGroupsFor(usage: Usage, targets: Array<out UsageTarget>): List<UsageGroup> {
        if (usage !is RelationUsage) return emptyList()
        return listOf(
            RelationUsageGroup(usage.memberLabel, usage.memberIcon, usage.memberOrder),
            RelationUsageGroup(usage.accessLabel, usage.accessIcon, 1000 + usage.accessOrder),
        )
    }

    // Place our member/access grouping above the file-location grouping.
    override fun getRank(): Int = 10
}

private class RelationUsageGroup(
    private val label: String,
    private val icon: Icon,
    private val order: Int,
) : UsageGroup {

    override fun getIcon(): Icon = icon

    override fun getPresentableGroupText(): String = label

    override fun compareTo(other: UsageGroup): Int {
        if (other !is RelationUsageGroup) return -1
        return order.compareTo(other.order).let { if (it != 0) it else label.compareTo(other.label) }
    }

    override fun equals(other: Any?): Boolean =
        other is RelationUsageGroup && other.label == label && other.order == order

    override fun hashCode(): Int = 31 * label.hashCode() + order
}

/** Registers [MemberUsageGroupingRule] for every usage view (it self-filters to RelationUsage). */
class RelationGroupingRuleProvider : UsageGroupingRuleProvider {
    override fun getActiveRules(project: Project): Array<UsageGroupingRule> =
        arrayOf(MemberUsageGroupingRule())

    // No custom grouping actions: the folder tree / flat switch (UsageGrouping.DirectoryStructure)
    // and the rest of the standard grouping toolbar are provided by the platform's built-in actions.
}

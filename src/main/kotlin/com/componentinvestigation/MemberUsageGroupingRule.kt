package com.componentinvestigation

import com.intellij.openapi.project.Project
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageTarget
import com.intellij.usages.rules.UsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRuleProvider
import javax.swing.Icon

/**
 * Session-only switch for the results layout. `false` (default) groups member → access (then the
 * platform's file node); `true` prepends a file group (file → member → access). Flipped by the
 * "Group by File/Member" button in the results' lower pane (see [RelationsUsagePresenter]); resets
 * on IDE restart.
 */
object RelationGroupingState {
    @Volatile
    var groupByFileFirst: Boolean = false
}

/**
 * Grouping for [RelationUsage]s. Default (member-first): member → access type. When
 * [RelationGroupingState.groupByFileFirst] is on, a file group is prepended (file → member →
 * access). Other usages (regular Find Usages) are ignored, so this rule is inert outside our view.
 *
 * We emit the file group ourselves rather than relying on rank to slot our groups under the
 * platform's file node: custom grouping rules are always applied *outside* the built-in file/module
 * grouping, so no `getRank()` value can place member/access beneath the platform file node. The
 * trade-off is that the platform's own file node still appears (once) deeper in each branch.
 */
class MemberUsageGroupingRule : UsageGroupingRule {

    override fun getParentGroupsFor(usage: Usage, targets: Array<out UsageTarget>): List<UsageGroup> {
        if (usage !is RelationUsage) return emptyList()
        val member = RelationUsageGroup(usage.memberLabel, usage.memberIcon, usage.memberOrder)
        val access = RelationUsageGroup(usage.accessLabel, usage.accessIcon, 1000 + usage.accessOrder)
        return if (RelationGroupingState.groupByFileFirst) {
            // order 0: all file groups sort together at the top, alphabetically by name.
            listOf(RelationUsageGroup(usage.fileLabel, usage.fileIcon, 0), member, access)
        } else {
            listOf(member, access)
        }
    }

    // Keep our groups above the built-in file/module grouping (lower rank = closer to the root).
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

    // No custom grouping-toolbar actions: a custom grouping toggle there would require internal API
    // (RuleAction / the @Internal RULES_CHANGED topic) to force a regroup, which this plugin avoids.
    // The member-first / file-first switch is offered as a lower-pane button instead, which re-shows
    // the results in a fresh view so getParentGroupsFor re-reads the flag (see RelationsUsagePresenter).
}

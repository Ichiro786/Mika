package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.ui.theme.MikaSpace
import com.wangzan101.mika.ui.theme.MikaRadius
import com.wangzan101.mika.ui.theme.MikaSize
import androidx.compose.ui.res.stringResource
import com.wangzan101.mika.R

internal data class ReadingSection(val title: MdBlock.Heading?, val blocks: List<MdBlock>)

/** Group only top-level H2s. Keep the preamble, nested lists and fenced examples in their original order. */
internal fun readingSections(blocks: List<MdBlock>): List<ReadingSection> = buildList {
    var title: MdBlock.Heading? = null
    var start = 0
    for ((i, block) in blocks.withIndex()) {
        if (block !is MdBlock.Heading || block.level != 2) continue
        if (title != null || start < i) add(ReadingSection(title, blocks.subList(start, i)))
        title = block
        start = i + 1
    }
    if (title != null || start < blocks.size) add(ReadingSection(title, blocks.subList(start, blocks.size)))
}

@Composable
internal fun SkillReadingView(markdown: String) {
    val c = MikaTheme.colors
    val body = MikaType.Callout.copy(color = c.ink2)
    MarkdownDocument(markdown) { blocks ->
        Column(Modifier.fillMaxWidth().padding(top = MikaSpace.S8, bottom = MikaSpace.S4), verticalArrangement = Arrangement.spacedBy(MikaSpace.S16)) {
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
            for ((title, contents) in remember(blocks) { readingSections(blocks) }) {
                val section = title?.let { SkillSection.of(it.text.text) }
                val caution = section == SkillSection.Caution
                val icon = when (section) {
                    SkillSection.Entry -> MikaIcons.Pin
                    SkillSection.Steps -> MikaIcons.Route
                    SkillSection.Caution -> MikaIcons.Info
                    SkillSection.Done -> MikaIcons.Check
                    SkillSection.Handoff -> MikaIcons.Hand
                    null -> null
                }
                val panel = if (caution) Modifier.background(c.warnSoft, RoundedCornerShape(MikaRadius.Sm)).padding(MikaSpace.S12) else Modifier
                Column(Modifier.fillMaxWidth().then(panel), verticalArrangement = Arrangement.spacedBy(MikaSpace.S8)) {
                    if (title != null) Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8)) {
                        if (icon != null) Icon(icon, null, Modifier.size(MikaSize.SmallIcon), tint = if (caution) c.warn else c.accent)
                        // Known sections read in the user's language; the stored skill keeps its own headings.
                        val shown = section?.let { stringResource(it.title) } ?: title.text.text
                        Text(shown, Modifier.semantics { heading() }, style = MikaType.BodyStrong, color = c.ink)
                    }
                    for (block in contents) {
                        if (block is MdBlock.ListBlock && block.start != null && section == SkillSection.Steps) {
                            StepList(block, block.start, body)
                        } else {
                            MarkdownBlocks(listOf(block), body)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepList(list: MdBlock.ListBlock, start: Int, style: TextStyle) {
    val c = MikaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(MikaSpace.S8)) {
        list.items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8)) {
                Box(Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                    .background(c.accentSoft, CircleShape).padding(horizontal = MikaSpace.S4), contentAlignment = Alignment.Center) {
                    Text((start + index).toString(), style = MikaType.Label.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                }
                MarkdownBlocks(item, style, Modifier.weight(1f), depth = 1)
            }
        }
    }
}

/**
 * The five sections a learned skill is written in. Skills Orbit drafts carry the Chinese headings the core
 * writes; people and models also write them in English. Both map here, so the icon and a translated title follow.
 */
internal enum class SkillSection(@androidx.annotation.StringRes val title: Int, private val starts: List<String>) {
    Entry(R.string.skill_section_entry, listOf("入口", "where to start", "entry point", "entry")),
    Steps(R.string.skill_section_steps, listOf("步骤", "步驟", "steps", "how to")),
    Caution(R.string.skill_section_caution, listOf("容易出错的地方", "容易出錯的地方", "pitfalls", "gotchas", "watch out", "common mistakes", "easy to get wrong", "caution")),
    Done(R.string.skill_section_done, listOf("怎样算办成", "怎樣算辦成", "done when", "how to tell it worked", "success", "verify", "verification")),
    Handoff(R.string.skill_section_handoff, listOf("什么时候停下交给用户", "什麼時候停下交給用戶", "when to stop", "when to hand", "hand off", "handoff", "hand back"));

    companion object {
        fun of(heading: String): SkillSection? {
            val h = heading.trim().lowercase()
            return entries.firstOrNull { s -> s.starts.any { h.startsWith(it) } }
        }
    }
}

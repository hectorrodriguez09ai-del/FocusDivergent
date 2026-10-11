package com.focusdivergent.app.presentation.markdown

import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * Convierte Markdown en [MdBlock] con `commonmark` (solo el análisis; no genera HTML).
 *
 * Decisiones para quien escribe notas desde el móvil:
 * - Un salto de línea simple se respeta como salto (en Markdown estricto se uniría con un espacio).
 * - El HTML no se interpreta: se ve como texto, tal cual se escribió.
 * - Las imágenes no se cargan (la Bóveda es local y sin red): se enseña su texto alternativo.
 */
object MarkdownParser {

    private val parser: Parser = Parser.builder().build()

    fun parse(markdown: String): List<MdBlock> = blocks(parser.parse(markdown))

    private fun blocks(parent: Node): List<MdBlock> = buildList {
        var node = parent.firstChild
        while (node != null) {
            block(node)?.let(::add)
            node = node.next
        }
    }

    private fun block(node: Node): MdBlock? = when (node) {
        is Heading -> MdBlock.Heading(node.level, spans(node))
        is Paragraph -> MdBlock.Paragraph(spans(node))
        is BlockQuote -> MdBlock.Quote(blocks(node))
        is BulletList -> MdBlock.Items(ordered = false, start = 1, items = items(node))
        is OrderedList -> MdBlock.Items(ordered = true, start = node.markerStartNumber ?: 1, items = items(node))
        is FencedCodeBlock -> MdBlock.CodeBlock(node.literal.orEmpty().trimEnd('\n'))
        is IndentedCodeBlock -> MdBlock.CodeBlock(node.literal.orEmpty().trimEnd('\n'))
        is ThematicBreak -> MdBlock.Rule
        is HtmlBlock -> MdBlock.Paragraph(listOf(MdSpan.Text(node.literal.orEmpty().trimEnd('\n'))))
        else -> null
    }

    private fun items(list: Node): List<List<MdBlock>> = buildList {
        var item = list.firstChild
        while (item != null) {
            if (item is ListItem) add(blocks(item))
            item = item.next
        }
    }

    private fun spans(parent: Node): List<MdSpan> = buildList {
        var node = parent.firstChild
        while (node != null) {
            when (node) {
                is Text -> add(MdSpan.Text(node.literal.orEmpty()))
                is Code -> add(MdSpan.Code(node.literal.orEmpty()))
                is StrongEmphasis -> add(MdSpan.Strong(spans(node)))
                is Emphasis -> add(MdSpan.Emphasis(spans(node)))
                is Link -> add(MdSpan.Link(node.destination.orEmpty(), spans(node)))
                is SoftLineBreak, is HardLineBreak -> add(MdSpan.LineBreak)
                is HtmlInline -> add(MdSpan.Text(node.literal.orEmpty()))
                // Imágenes y cualquier otro elemento en línea: se conserva el texto que contengan.
                else -> addAll(spans(node))
            }
            node = node.next
        }
    }
}

package com.focusdivergent.app.presentation.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.dp
import com.focusdivergent.app.presentation.theme.Spacing

/** Enlaces que se pueden abrir. Cualquier otro esquema (`javascript:`, `intent:`…) se enseña como texto sin enlace. */
internal fun isSafeLink(destination: String): Boolean {
    val scheme = destination.substringBefore(':', missingDelimiterValue = "").lowercase()
    return scheme == "http" || scheme == "https" || scheme == "mailto"
}

/** Dibuja un documento Markdown con los estilos del tema. Solo lectura. */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        blocks.forEach { MarkdownBlock(it) }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock) {
    when (block) {
        is MdBlock.Heading -> Text(
            text = inline(block.spans),
            style = headingStyle(block.level),
            modifier = Modifier
                .padding(top = Spacing.sm)
                .semantics { heading() },
        )
        is MdBlock.Paragraph -> Text(
            text = inline(block.spans),
            style = MaterialTheme.typography.bodyLarge,
        )
        is MdBlock.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
            Column(
                modifier = Modifier.padding(start = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                block.blocks.forEach { MarkdownBlock(it) }
            }
        }
        is MdBlock.Items -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            block.items.forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = if (block.ordered) "${block.start + index}." else "•",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        item.forEach { MarkdownBlock(it) }
                    }
                }
            }
        }
        is MdBlock.CodeBlock -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(Spacing.sm),
            )
        }
        MdBlock.Rule -> HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.headlineSmall
    2 -> MaterialTheme.typography.titleLarge
    3 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.titleSmall
}

@Composable
private fun inline(spans: List<MdSpan>): AnnotatedString {
    val link = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
    val code = SpanStyle(fontFamily = FontFamily.Monospace, background = MaterialTheme.colorScheme.surfaceContainerHigh)
    return remember(spans, link, code) {
        buildAnnotatedString { appendSpans(spans, link, code) }
    }
}

private fun AnnotatedString.Builder.appendSpans(spans: List<MdSpan>, link: SpanStyle, code: SpanStyle) {
    spans.forEach { span ->
        when (span) {
            is MdSpan.Text -> append(span.text)
            is MdSpan.Code -> withStyle(code) { append(span.text) }
            is MdSpan.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendSpans(span.children, link, code) }
            is MdSpan.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendSpans(span.children, link, code) }
            is MdSpan.Link ->
                if (isSafeLink(span.destination)) {
                    withLink(LinkAnnotation.Url(span.destination, TextLinkStyles(style = link))) {
                        appendSpans(span.children, link, code)
                    }
                } else {
                    appendSpans(span.children, link, code)
                }
            MdSpan.LineBreak -> append('\n')
        }
    }
}

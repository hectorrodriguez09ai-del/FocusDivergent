package com.focusdivergent.app.presentation.markdown

/** Texto en línea de un párrafo, título o elemento de lista. */
sealed interface MdSpan {
    data class Text(val text: String) : MdSpan
    data class Code(val text: String) : MdSpan
    data class Strong(val children: List<MdSpan>) : MdSpan
    data class Emphasis(val children: List<MdSpan>) : MdSpan
    data class Link(val destination: String, val children: List<MdSpan>) : MdSpan
    data object LineBreak : MdSpan
}

/** Bloques de un documento Markdown. Es el subconjunto que la Bóveda sabe enseñar. */
sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock

    /** Lista con viñetas ([ordered] falso) o numerada desde [start]. Cada elemento es una lista de bloques. */
    data class Items(val ordered: Boolean, val start: Int, val items: List<List<MdBlock>>) : MdBlock
    data class CodeBlock(val text: String) : MdBlock
    data object Rule : MdBlock
}

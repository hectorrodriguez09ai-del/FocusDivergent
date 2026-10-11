package com.focusdivergent.app.presentation.mascot

import androidx.compose.ui.graphics.Color

// Generado a partir de props_art.py: cada objeto es una cuadrícula de píxeles, una letra por color.
// Ningún tono es rojo (RNF-3); los marrones y el ámbar caen entre los 25 y los 45 grados de tono.

internal class PixelArt(val rows: List<String>, val palette: Map<Char, Color>) {
    val width: Int get() = rows.maxOf { it.length }
    val height: Int get() = rows.size
}

internal val PixelPalette: Map<Char, Color> = mapOf(
    'g' to Color(0xFF34D399),
    'G' to Color(0xFF15803D),
    'l' to Color(0xFF86EFAC),
    'p' to Color(0xFFB7791F),
    'd' to Color(0xFF7C4A12),
    'o' to Color(0xFFD69E2E),
    'b' to Color(0xFF3B82F6),
    'B' to Color(0xFF1D4ED8),
    'v' to Color(0xFFA78BFA),
    'V' to Color(0xFF6D28D9),
    't' to Color(0xFF2DD4BF),
    'T' to Color(0xFF0F766E),
    'w' to Color(0xFFF5F5F4),
    'k' to Color(0xFF1F2937),
    'y' to Color(0xFFFCD34D),
    'Y' to Color(0xFFF59E0B),
    'h' to Color(0xFFFFF7CC),
    's' to Color(0xFF94A3B8),
    'S' to Color(0xFF475569),
)

internal val PropArtPlant = PixelArt(
    rows = listOf(
        "...g.....",
        "..lgg.g..",
        ".lggGgg..",
        "g.gGggGg.",
        "ggGgGgGgg",
        ".gGgGgGg.",
        "..gGGGg..",
        "..oooooo.",
        "..pppppp.",
        "..pppppp.",
        "...dddd..",
    ),
    palette = PixelPalette,
)

internal val PropArtBooks = PixelArt(
    rows = listOf(
        "..........",
        "..bbbbbbbb",
        "..bBwwwwBb",
        "..bbbbbbbb",
        ".vvvvvvvv.",
        ".vVwwwwVv.",
        ".vvvvvvvv.",
        "..tttttttt",
        "..tTwwwwTt",
        "..tttttttt",
    ),
    palette = PixelPalette,
)

internal val PropArtLamp = PixelArt(
    rows = listOf(
        "..yyyyy..",
        ".yhhhhhy.",
        "yhhhhhhhy",
        "YYYYYYYYY",
        "....s....",
        "....s....",
        "....s....",
        "....s....",
        "....s....",
        "....s....",
        "..sssss..",
        ".SSSSSSS.",
    ),
    palette = PixelPalette,
)

internal val PropArtStar = PixelArt(
    rows = listOf(
        "....y....",
        "....y....",
        "...yhy...",
        "yyyyhyyyy",
        ".yyhhhyy.",
        "..yhhhy..",
        "..yyhyy..",
        ".yyy.yyy.",
        ".yY...Yy.",
    ),
    palette = PixelPalette,
)


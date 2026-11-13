package io.github.alihansarigit.snoop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoopColorContrastTest {

    @Test
    fun `dark theme text colors meet WCAG AA contrast`() {
        assertContrast(SnoopDarkColorScheme.onBackground, SnoopDarkColorScheme.background)
        assertContrast(SnoopDarkColorScheme.onSurface, SnoopDarkColorScheme.surface)
        assertContrast(SnoopDarkColorScheme.onSurfaceVariant, SnoopDarkColorScheme.surfaceVariant)
        assertContrast(SnoopDarkColorScheme.onPrimary, SnoopDarkColorScheme.primary)
        assertContrast(SnoopDarkColorScheme.onError, SnoopDarkColorScheme.error)
        assertContrast(NcColors.ErrorText, NcColors.PanelBg)
    }

    @Test
    fun `colored controls choose readable foreground text`() {
        val fills = listOf(
            NcColors.FilterAll,
            NcColors.FilterErr,
            NcColors.FilterPend,
            NcColors.ToggleReq,
            NcColors.ToggleRes,
            NcColors.CopyEndpoint,
            NcColors.CopyFull,
            NcColors.CopyRequest,
            NcColors.CopyResponse,
            NcColors.CopyCurl,
            Color(0xFFF97316),
            Color(0xFF0891B2),
            Color(0xFF4F46E5),
            Color(0xFFDB2777),
        )

        fills.forEach { fill ->
            assertContrast(readableContentColor(fill), fill)
            val idleFill = fill.copy(alpha = 0.3f)
            assertContrast(
                readableContentColor(idleFill),
                idleFill.compositeOver(NcColors.PanelBg),
            )
            assertContrast(
                readableContentColor(idleFill),
                idleFill.compositeOver(NcColors.RowBg),
            )
        }
    }

    private fun assertContrast(
        foreground: Color,
        background: Color,
        minimum: Float = 4.5f,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "Expected contrast >= $minimum but was $ratio for $foreground on $background",
            ratio >= minimum,
        )
    }
}

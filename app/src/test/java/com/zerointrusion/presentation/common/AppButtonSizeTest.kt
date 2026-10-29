package com.zerointrusion.presentation.common

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppButtonSizeTest {
    @Test
    fun allButtonDimensionsKeepTheirExistingValues() {
        val expected =
            mapOf(
                AppButtonSize.Small to listOf(32.dp, 16.dp, 6.dp, 4.dp, 16.dp, 30.dp),
                AppButtonSize.Compact to listOf(48.dp, 20.dp, 10.dp, 10.dp, 16.dp, 30.dp),
                AppButtonSize.Large to listOf(80.dp, 26.dp, 16.dp, 18.dp, 20.dp, 20.dp),
            )

        assertEquals(AppButtonSize.entries.toSet(), expected.keys)
        expected.forEach { (size, dimensions) ->
            assertEquals(
                "$size button dimensions must stay unchanged",
                dimensions,
                listOf(
                    size.height,
                    size.iconSize,
                    size.iconSpacing,
                    size.verticalPadding,
                    size.horizontalPadding,
                    size.menuHorizontalPadding,
                ),
            )
        }
    }
}

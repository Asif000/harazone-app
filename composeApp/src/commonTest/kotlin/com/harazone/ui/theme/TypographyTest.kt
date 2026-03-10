package com.harazone.ui.theme

import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertTrue

class TypographyTest {

    // Tests reference constants from Type.kt directly.
    // If a size value is changed in Type.kt, the test will fail — unlike the previous
    // approach of asserting a hardcoded local variable against itself.

    @Test
    fun bodyLarge_fontSize_meetsMinimum16sp() {
        // AC #4: All body text is minimum 16sp
        assertTrue(BODY_LARGE_SIZE >= 16.sp, "bodyLarge fontSize must be >= 16sp, was $BODY_LARGE_SIZE")
    }

    @Test
    fun bodyMedium_fontSize_meetsMinimum16sp() {
        // AC #4: All body text is minimum 16sp — bodyMedium is a body text style
        assertTrue(BODY_MEDIUM_SIZE >= 16.sp, "bodyMedium fontSize must be >= 16sp, was $BODY_MEDIUM_SIZE")
    }

    @Test
    fun labelMedium_fontSize_meetsMinimum12sp() {
        // AC #4: labels minimum 12sp
        assertTrue(LABEL_MEDIUM_SIZE >= 12.sp, "labelMedium fontSize must be >= 12sp, was $LABEL_MEDIUM_SIZE")
    }

    @Test
    fun displayMedium_fontSize_matchesSpec() {
        assertTrue(DISPLAY_MEDIUM_SIZE == 28.sp, "displayMedium fontSize must be 28sp per spec, was $DISPLAY_MEDIUM_SIZE")
    }

    @Test
    fun headlineSmall_fontSize_matchesSpec() {
        assertTrue(HEADLINE_SMALL_SIZE == 20.sp, "headlineSmall fontSize must be 20sp per spec, was $HEADLINE_SMALL_SIZE")
    }

    @Test
    fun titleMedium_fontSize_matchesSpec() {
        assertTrue(TITLE_MEDIUM_SIZE == 16.sp, "titleMedium fontSize must be 16sp per spec, was $TITLE_MEDIUM_SIZE")
    }

    @Test
    fun allBodyStyles_meetMinimum16sp() {
        // Verify all body* styles comply with AC #4
        assertTrue(BODY_LARGE_SIZE >= 16.sp, "bodyLarge must be >= 16sp")
        assertTrue(BODY_MEDIUM_SIZE >= 16.sp, "bodyMedium must be >= 16sp")
    }

    @Test
    fun allLabelStyles_meetMinimum12sp() {
        // Verify all label* styles comply with AC #4
        assertTrue(LABEL_MEDIUM_SIZE >= 12.sp, "labelMedium must be >= 12sp")
    }
}

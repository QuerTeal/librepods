package me.kavishdevar.librepods.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDesignTest {
    @Test fun materialIsNotSelectable() {
        assertEquals(listOf(AppDesign.OneUI, AppDesign.Apple), AppDesign.availableDesigns)
    }
    @Test fun legacyMaterialFallsBackToOneUI() {
        assertEquals(AppDesign.OneUI, AppDesign.fromPreferenceValue("material"))
    }
    @Test fun applePreferenceIsPreserved() {
        assertEquals(AppDesign.Apple, AppDesign.fromPreferenceValue("apple"))
    }
    @Test fun missingOrUnknownPreferenceUsesOneUI() {
        assertEquals(AppDesign.OneUI, AppDesign.fromPreferenceValue(null))
        assertEquals(AppDesign.OneUI, AppDesign.fromPreferenceValue("unknown"))
    }
}

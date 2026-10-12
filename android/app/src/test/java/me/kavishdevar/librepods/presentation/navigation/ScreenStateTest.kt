package me.kavishdevar.librepods.presentation.navigation

import org.junit.Assert.*
import org.junit.Test

class ScreenStateTest {
    @Test fun settingsAndArgumentsSurviveSavingABackStack() {
        val stack = listOf(Screen.AirPodsSettings, Screen.AppSettings, Screen.Language, Screen.FeatureStatus,
            Screen.LongPress("Left"), Screen.CallControl("answer:call/한글"))
        assertEquals(stack, stack.map(ScreenState::encode).mapNotNull(ScreenState::decode))
    }

    @Test fun obsoleteDestinationsAreIgnoredDuringRestoration() {
        assertNull(ScreenState.decode("removed_screen"))
        assertEquals(Screen.AppSettings, ScreenState.decode("settings"))
    }
}

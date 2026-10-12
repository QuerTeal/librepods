package me.kavishdevar.librepods.presentation.components

import org.junit.Assert.*
import org.junit.Test

class SliderDetentTest {
    private fun detent(value: Float = 55f) = SliderDetent(listOf(43f), 2f).apply { synchronize(value) }

    @Test fun approachingDefaultFromEitherSideLandsExactly() {
        assertEquals(SliderDetent.Result(43f, true), detent().resolve(44.5f))
        assertEquals(SliderDetent.Result(43f, true), detent(30f).resolve(41.5f))
    }

    @Test fun jitterInsideTheReleaseBandDoesNotRepeatFeedback() {
        val state = detent()
        assertTrue(state.resolve(44.5f).entered)
        state.synchronize(43f)
        for (raw in listOf(42.25f, 45.25f, 40.5f, 43.75f)) {
            assertEquals(SliderDetent.Result(43f, false), state.resolve(raw))
        }
    }

    @Test fun continuedMovementReleasesAndReturningTicksAgain() {
        val state = detent()
        state.resolve(44f)
        assertEquals(SliderDetent.Result(47f, false), state.resolve(47f))
        assertEquals(SliderDetent.Result(43f, true), state.resolve(44f))
    }

    @Test fun fastCrossingCatchesOnceThenAllowsFurtherMovement() {
        val state = detent(30f)
        assertEquals(SliderDetent.Result(43f, true), state.resolve(55f))
        assertEquals(SliderDetent.Result(56f, false), state.resolve(56f))
    }

    @Test fun programmaticDefaultAndItsSurroundingsStaySilent() {
        val state = detent()
        state.synchronize(43f)
        assertEquals(SliderDetent.Result(43f, false), state.resolve(44f))
    }

    @Test fun slidersWithoutReferencesPassThrough() {
        val state = SliderDetent(emptyList(), 0.05f)
        state.synchronize(0.25f)
        assertEquals(SliderDetent.Result(0.75f, false), state.resolve(0.75f))
    }
}

package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.*
import org.junit.Test

class AutomaticConnectionGuardTest {
    private val openCase = ConnectionEvidence(advertisementEligible = true, fromCase = true)
    private val wornFreePod = ConnectionEvidence(advertisementEligible = true)

    @Test fun initialCaseOpeningStillConnects() {
        val guard = AutomaticConnectionGuard()
        guard.observeCase(true, 5)
        assertTrue(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
        assertTrue(guard.allowsAudio(ConnectionIntent.ADVERTISEMENT, false, false, null, false))
    }

    @Test fun repeatedBroadcastsAndAddressRotationDoNotPageAgain() {
        val guard = AutomaticConnectionGuard()
        guard.observeCase(true, 5)
        assertTrue(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
        repeat(100) {
            guard.observeCase(true, 5, seenMillis = it.toLong())
            assertFalse(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
        }
    }

    @Test fun counterChangeAndWrapIdentifyANewOpening() {
        val guard = AutomaticConnectionGuard()
        guard.observeCase(true, 255)
        assertTrue(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
        guard.observeCase(true, 0)
        assertTrue(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
        assertFalse(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
    }

    @Test fun caseWithoutCounterRequiresAConfirmedCloseBeforeReopening() {
        val guard = AutomaticConnectionGuard()
        guard.observeCase(true, null)
        assertTrue(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
        guard.observeCase(true, null)
        assertFalse(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
        guard.observeCase(false, null)
        guard.observeCase(true, null)
        assertTrue(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
    }

    @Test fun actualLoopFromReviewRemainsBlockedAfterLinkLoss() {
        val guard = AutomaticConnectionGuard()
        guard.observeCase(true, 5)
        assertTrue(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
        guard.localConnectionEstablished()
        guard.localConnectionLost(false, 1000)
        assertTrue(guard.paused)
        for (time in listOf(1008L, 1083L, 2826L, 31_000L, 600_000L)) {
            guard.observeCase(true, 5, seenMillis = time)
            for (intent in ConnectionIntent.entries.filter { it != ConnectionIntent.USER }) {
                assertFalse(guard.claimControl(intent, openCase.copy(localLink = true)))
                assertFalse(guard.allowsAudio(intent, false, true, true, false))
            }
        }
    }

    @Test fun closingAndReopeningTheCaseReleasesALinkLossHold() {
        val guard = AutomaticConnectionGuard()
        guard.observeCase(true, 5)
        assertTrue(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
        guard.localConnectionEstablished()
        // The closed advertisement was missed, so the loss stays provisional and holds.
        guard.localConnectionLost(false, 1000)
        assertTrue(guard.paused)
        guard.observeCase(false, 5, seenMillis = 60_000)
        assertTrue(guard.paused)
        guard.observeCase(true, 6, seenMillis = 70_000)
        assertFalse(guard.paused)
        assertTrue(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
        assertTrue(guard.claimControl(ConnectionIntent.EXISTING_LINK, openCase.copy(localLink = true)))
    }

    @Test fun aNewLidCounterReleasesTheHoldEvenIfTheCloseWasMissed() {
        val guard = AutomaticConnectionGuard()
        guard.observeCase(true, 5)
        guard.localConnectionEstablished()
        guard.localConnectionLost(false, 1000)
        guard.observeCase(true, 6, seenMillis = 70_000)
        assertFalse(guard.paused)
    }

    @Test fun reopeningAfterAHandoffStillHolds() {
        val guard = AutomaticConnectionGuard()
        guard.observeCase(true, 5)
        guard.localConnectionEstablished()
        guard.localConnectionLost(false, 1000)
        guard.pause() // the other host confirmed it took over
        guard.observeCase(false, 5, seenMillis = 60_000)
        guard.observeCase(true, 6, seenMillis = 70_000)
        assertTrue(guard.paused)
    }

    @Test fun newCaseOpeningDoesNotOverrideAnExternalHandoff() {
        val guard = AutomaticConnectionGuard()
        guard.pause()
        guard.observeCase(false, 5)
        guard.observeCase(true, 6)
        assertTrue(guard.paused)
        assertFalse(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
    }

    @Test fun restartPresenceAndExistingPhoneLinkDoNotReleaseAPersistedHold() {
        val guard = AutomaticConnectionGuard(initiallyPaused = true)
        guard.observeCase(true, 5)
        guard.localConnectionEstablished()
        assertFalse(guard.claimControl(ConnectionIntent.EXISTING_LINK, openCase.copy(localLink = true)))
        assertTrue(guard.paused)
    }

    @Test fun explicitUserRequestRestoresConnectionDespiteUnknownOrOtherHostEvidence() {
        val guard = AutomaticConnectionGuard(initiallyPaused = true)
        assertTrue(guard.claimControl(ConnectionIntent.USER, ConnectionEvidence(otherHost = true)))
        assertFalse(guard.paused)
        assertTrue(guard.allowsAudio(ConnectionIntent.USER, false, true, false, true))
    }

    @Test fun closingASeatedCasePreservesNextOpenAutomaticConnection() {
        val guard = AutomaticConnectionGuard()
        guard.observeCase(true, 5)
        guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase)
        guard.localConnectionEstablished()
        guard.observeCase(false, 5)
        guard.localConnectionLost(true, 1000)
        assertFalse(guard.paused)
        guard.observeCase(true, 6, seenMillis = 5000)
        assertTrue(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
    }

    @Test fun delayedClosedCaseResolvesOnlyAProvisionalLoss() {
        val guard = AutomaticConnectionGuard()
        guard.localConnectionEstablished()
        guard.localConnectionLost(false, 1000)
        assertTrue(guard.paused)
        guard.observeCase(false, 5, bothSeated = true, seenMillis = 1400)
        assertFalse(guard.paused)
    }

    @Test fun emptyOldOrLateClosedCaseCannotReleaseTheHold() {
        for ((seated, time) in listOf(false to 1200L, true to 0L, true to 3000L)) {
            val guard = AutomaticConnectionGuard()
            guard.localConnectionEstablished()
            guard.localConnectionLost(false, 1000)
            guard.observeCase(false, 5, bothSeated = seated, seenMillis = time)
            assertTrue(guard.paused)
        }
    }

    @Test fun confirmedOtherHostOverridesProvisionalCaseCloseException() {
        val guard = AutomaticConnectionGuard()
        guard.localConnectionEstablished()
        guard.localConnectionLost(false, 1000)
        guard.pause()
        guard.observeCase(false, 5, seenMillis = 1400)
        assertTrue(guard.paused)
    }

    @Test fun initialTransportFailureMayRetryWithoutRepeatingTheCaseClaim() {
        val guard = AutomaticConnectionGuard()
        guard.observeCase(true, 5)
        guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase)
        guard.localConnectionLost(false, 1000)
        assertFalse(guard.paused)
        assertTrue(guard.claimControl(ConnectionIntent.RETRY, openCase))
        assertFalse(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase))
    }

    @Test fun otherHostAndMissingAdvertisementBlockPresencePaging() {
        val guard = AutomaticConnectionGuard()
        guard.observeCase(true, 5)
        assertFalse(guard.claimControl(ConnectionIntent.ADVERTISEMENT, openCase.copy(otherHost = true)))
        assertFalse(guard.claimControl(ConnectionIntent.EXISTING_LINK, ConnectionEvidence()))
        assertFalse(guard.claimControl(ConnectionIntent.RETRY, openCase))
    }

    @Test fun freeWornPodCanConnectOnceButConfirmedRemoteOwnershipCannotRestoreAudio() {
        val guard = AutomaticConnectionGuard()
        assertTrue(guard.claimControl(ConnectionIntent.ADVERTISEMENT, wornFreePod))
        assertFalse(guard.claimControl(ConnectionIntent.ADVERTISEMENT, wornFreePod))
        assertFalse(guard.allowsAudio(ConnectionIntent.WEAR, false, true, false, false))
        assertTrue(guard.allowsAudio(ConnectionIntent.WEAR, false, true, null, false))
    }

    @Test fun anAlreadyConnectedAudioProfileDoesNotNeedAnotherConnectRequest() {
        val guard = AutomaticConnectionGuard()
        guard.claimControl(ConnectionIntent.USER, ConnectionEvidence())
        assertFalse(guard.allowsAudio(ConnectionIntent.WEAR, true, true, true, false))
    }

    @Test fun newHandoffInvalidatesAnOldUserAudioRequest() {
        val guard = AutomaticConnectionGuard()
        guard.resumeForUser()
        val token = guard.revision
        guard.localConnectionEstablished()
        guard.localConnectionLost(false, 1000)
        assertNotEquals(token, guard.revision)
        val pausedToken = guard.revision
        guard.resumeForUser()
        assertNotEquals(pausedToken, guard.revision)
    }
}

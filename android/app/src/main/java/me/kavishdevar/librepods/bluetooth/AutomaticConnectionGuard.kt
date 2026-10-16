package me.kavishdevar.librepods.bluetooth

enum class ConnectionIntent { EXISTING_LINK, ADVERTISEMENT, RETRY, WEAR, TAKEOVER, USER }

data class ConnectionEvidence(
    val localLink: Boolean = false,
    val advertisementEligible: Boolean = false,
    val fromCase: Boolean = false,
    val otherHost: Boolean = false
)

/** Owned by the service's main thread. Radio silence and presence never release a handoff. */
class AutomaticConnectionGuard(initiallyPaused: Boolean = false) {
    var paused = initiallyPaused
        private set
    var revision = 0L
        private set
    private var established = false
    private var automaticLease = false
    private var caseGeneration = 0L
    private var claimedCaseGeneration: Long? = null
    private var lastCaseOpen: Boolean? = null
    private var lastCaseCounter: Int? = null
    private var pendingCaseLossMillis: Long? = null
    /** Paused only because this phone lost the link, not because another host took over. */
    private var pausedByLinkLoss = false

    fun observeCase(open: Boolean, counter: Int?, bothSeated: Boolean = true, seenMillis: Long = 0) {
        val newOpening = open && (lastCaseOpen != true ||
            (counter != null && lastCaseCounter != null && counter != lastCaseCounter))
        if (newOpening) caseGeneration++
        // Closing the case and opening it again is a fresh start on this phone: it releases a
        // hold that only a lost link caused. The same opening (a connect loop with the case left
        // open) and handoffs to another host stay held.
        // A changed lid counter counts too, in case the closed advertisement was missed.
        if (newOpening && pausedByLinkLoss) {
            paused = false
            revision++
            pausedByLinkLoss = false
            pendingCaseLossMillis = null
        }
        if (!open) automaticLease = false
        // Some phones deliver profile loss just before the case's closed advertisement.
        // Only a fresh, seated, closed case resolves that provisional loss; an external
        // ownership event or an old closed packet can never release the hold.
        if (!open && bothSeated && pendingCaseLossMillis?.let { seenMillis - it in -500L..1500L } == true) {
            paused = false
            revision++
            pendingCaseLossMillis = null
            pausedByLinkLoss = false
        }
        lastCaseOpen = open
        if (counter != null) lastCaseCounter = counter
    }

    fun localConnectionEstablished() { established = true }

    fun localConnectionLost(expectedClosedCase: Boolean, nowMillis: Long = 0) {
        if (established && !expectedClosedCase && !paused) {
            paused = true
            revision++
            automaticLease = false
            pendingCaseLossMillis = nowMillis
            pausedByLinkLoss = true
        }
        if (established || expectedClosedCase) automaticLease = false
        established = false
    }

    fun pause() { paused = true; revision++; automaticLease = false; pendingCaseLossMillis = null; pausedByLinkLoss = false }

    /** Only an explicit request on this phone clears the persisted hold. */
    fun resumeForUser() { paused = false; revision++; automaticLease = true; pendingCaseLossMillis = null; pausedByLinkLoss = false }

    fun claimControl(intent: ConnectionIntent, evidence: ConnectionEvidence): Boolean {
        if (intent == ConnectionIntent.USER) { resumeForUser(); return true }
        if (paused) return false
        return when (intent) {
            ConnectionIntent.EXISTING_LINK -> evidence.localLink
            ConnectionIntent.TAKEOVER -> { automaticLease = true; true } // Caller checks the six takeover options.
            ConnectionIntent.RETRY -> evidence.localLink ||
                (automaticLease && evidence.advertisementEligible && !evidence.otherHost)
            ConnectionIntent.ADVERTISEMENT -> {
                if (!evidence.advertisementEligible || evidence.otherHost) false
                else if (evidence.fromCase && (lastCaseOpen != true || claimedCaseGeneration == caseGeneration)) false
                else if (!evidence.fromCase && automaticLease) false
                else {
                    if (evidence.fromCase) claimedCaseGeneration = caseGeneration
                    automaticLease = true
                    true
                }
            }
            else -> false
        }
    }

    fun allowsAudio(intent: ConnectionIntent, localProfile: Boolean, ready: Boolean,
        ownsConnection: Boolean?, otherHost: Boolean): Boolean {
        if (intent == ConnectionIntent.USER) return true
        if (paused || localProfile) return false
        if (intent == ConnectionIntent.TAKEOVER) return true
        if (ownsConnection == false || otherHost) return false
        return when (intent) {
            ConnectionIntent.ADVERTISEMENT -> automaticLease
            ConnectionIntent.WEAR -> ready && (automaticLease || ownsConnection == true)
            else -> false
        }
    }
}

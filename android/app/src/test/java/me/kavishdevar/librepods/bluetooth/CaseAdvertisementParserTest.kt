package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class CaseAdvertisementParserTest {
    private val key = ByteArray(16) { it.toByte() } // Synthetic key; never a device key.
    private val closed = byteArrayOf(
        0x29, 0x20, 0x08, 0x3d, 0xde.toByte(), 0xdd.toByte(), 0x51, 0x0b,
        0, 0, 0, 0, 0xf7.toByte(), 0x82.toByte(), 0x16, 0
    )

    private fun advertisement(block: ByteArray = closed): ByteArray {
        val encrypted = Cipher.getInstance("AES/ECB/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            doFinal(block)
        }
        return byteArrayOf(7, 17, 6) + encrypted
    }

    @Test fun decodesSeparateCaseBatteryLayout() {
        val report = CaseAdvertisementParser.parse(advertisement(), key)!!
        assertEquals(61, report.case.level)
        assertEquals(94, report.left.level)
        assertEquals(93, report.right.level)
        assertFalse(report.case.charging)
        assertTrue(report.left.charging)
        assertTrue(report.right.charging)
        assertTrue(report.bothBudsSeated)
        assertFalse(report.lidOpen)
    }

    @Test fun decodesOpenCounterAndExternallyChargingCase() {
        val block = closed.copyOf().apply { this[2] = 3; this[3] = 0xbd.toByte() }
        val report = CaseAdvertisementParser.parse(advertisement(block), key)!!
        assertTrue(report.lidOpen)
        assertEquals(3, report.lidCounter)
        assertEquals(61, report.case.level)
        assertTrue(report.case.charging)
    }

    @Test fun emptyCaseDoesNotPretendToReportEarStateOrSeatedBuds() {
        val block = closed.copyOf().apply { this[4] = 0xff.toByte(); this[5] = 0xff.toByte() }
        val report = CaseAdvertisementParser.parse(advertisement(block), key)!!
        assertNull(report.left.level)
        assertNull(report.right.level)
        assertFalse(report.left.charging)
        assertFalse(report.right.charging)
        assertFalse(report.bothBudsSeated)
    }

    @Test fun rightRemovalKeepsLeftAndCaseReadings() {
        val report = CaseAdvertisementParser.parse(advertisement(closed.copyOf().apply {
            this[5] = 0xff.toByte()
        }), key)!!
        assertEquals(94, report.left.level)
        assertNull(report.right.level)
        assertEquals(61, report.case.level)
        assertFalse(report.bothBudsSeated)
    }

    @Test fun acceptsRealZeroAndFullChargeWithoutClampingInvalidValues() {
        val report = CaseAdvertisementParser.parse(advertisement(closed.copyOf().apply {
            this[3] = 0; this[4] = 0xe4.toByte(); this[5] = 100
        }), key)!!
        assertEquals(0, report.case.level)
        assertEquals(100, report.left.level)
        assertEquals(100, report.right.level)
        assertTrue(report.left.charging)
        assertFalse(report.right.charging)
    }

    @Test fun acceptsChangedFirmwareVariantAndCarriedCounter() {
        val report = CaseAdvertisementParser.parse(advertisement(closed.copyOf().apply {
            this[6] = 0x5a; this[7] = 0x86.toByte()
            this[12] = 0xff.toByte(); this[13] = 0xff.toByte()
            this[14] = 0xff.toByte(); this[15] = 0x01
        }), key)
        assertNotNull(report)
    }

    @Test fun rejectsWrongKeyRatherThanPublishingRandomBatteryOrLid() {
        assertNull(CaseAdvertisementParser.parse(advertisement(), ByteArray(16) { 0x55 }))
    }

    @Test fun rejectsTruncatedWrongKindAndPodFrames() {
        assertNull(CaseAdvertisementParser.parse(advertisement().dropLast(1).toByteArray(), key))
        assertNull(CaseAdvertisementParser.parse(advertisement().apply { this[0] = 0x12 }, key))
        assertNull(CaseAdvertisementParser.parse(advertisement().apply { this[2] = 7 }, key))
        assertNull(CaseAdvertisementParser.parse(byteArrayOf(7, 25, 1) + ByteArray(24), key))
        assertNull(CaseAdvertisementParser.parse(byteArrayOf(), key))
        assertNull(CaseAdvertisementParser.parse(advertisement(), ByteArray(15)))
    }

    @Test fun rejectsUnexpectedHeaderAndReservedBytes() {
        for (offset in listOf(0, 1, 8, 10, 11)) {
            assertNull("offset $offset", CaseAdvertisementParser.parse(advertisement(
                closed.copyOf().apply { this[offset] = (this[offset].toInt() xor 1).toByte() }
            ), key))
        }
    }

    @Test fun rejectsImpossibleBatteryLevelsInAnyComponent() {
        for (offset in 3..5) {
            assertNull(CaseAdvertisementParser.parse(advertisement(closed.copyOf().apply {
                this[offset] = 101
            }), key))
        }
    }
}

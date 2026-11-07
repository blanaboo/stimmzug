package io.github.blanaboo.stimmzug.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabProtocolTest {

    private val bottom = MicChoice("unten", "Mikro unten", 5)
    private val back = MicChoice("hinten", "Mikro hinten", 6)

    @Test
    fun selfTestCoversEverySourceAndMicrophoneForBothNotes() {
        val protocol = LabProtocols.selfTest(listOf(bottom, back), sdk = 33)

        assertEquals(2 * LabSource.entries.size * 2, protocol.takes.size)
        val configs = protocol.takes.map { Triple(it.source, it.mic, it.noteMidi) }.toSet()
        assertEquals(protocol.takes.size, configs.size)
        assertTrue(protocol.takes.all { it.pauseBefore == null })
        protocol.takes.forEach { take ->
            assertEquals(listOf("quiet", "drone"), take.segments.map { it.key })
            assertFalse(take.segments.any { it.playerPlays })
        }
    }

    @Test
    fun sourcesNewerThanTheDeviceAreLeftOut() {
        val protocol = LabProtocols.selfTest(listOf(MicChoice.SYSTEM), sdk = 28)

        assertFalse(protocol.takes.any { it.source == LabSource.VOICE_PERFORMANCE })
        assertEquals(2 * (LabSource.entries.size - 1), protocol.takes.size)
    }

    @Test
    fun tubaMeasurementHasThreePartsEachStartingWithAPause() {
        val protocol = LabProtocols.tuba(listOf(bottom, back), sdk = 33)

        val withoutDrone = protocol.takes.filterNot { it.usesDrone }
        val withDrone = protocol.takes.filter { it.usesDrone }
        assertEquals(5 * 2 * 2, withoutDrone.size)
        assertEquals(listOf(LabProtocols.KONTRA_F, LabProtocols.GROSSES_F), withoutDrone.map { it.noteMidi }.distinct())
        assertFalse(withoutDrone.any { it.source == LabSource.VOICE_COMMUNICATION })
        // The raw path with both microphones and the processed path in unison, then the raw path an octave higher.
        assertEquals(listOf(0, 0, 0, 1), withDrone.map { it.droneOctave })
        assertEquals(
            listOf(LabSource.UNPROCESSED, LabSource.UNPROCESSED, LabSource.VOICE_RECOGNITION, LabSource.UNPROCESSED),
            withDrone.map { it.source }
        )
        assertEquals(listOf(bottom, back, bottom, bottom), withDrone.map { it.mic })
        assertTrue(withDrone.all { take -> take.segments.all { it.drone } })
        assertEquals(listOf("drone", "low", "clean", "high"), withDrone.first().segments.map { it.key })
        assertEquals(3, protocol.takes.count { it.pauseBefore != null })
        assertTrue(protocol.takes.first().pauseBefore.orEmpty().startsWith("Teil 1 von 3: Kontra-F"))
    }

    @Test
    fun droneMeasurementComparesSpeakerAndBluetoothAt442Hz() {
        val protocol = LabProtocols.drone(listOf(bottom, back))

        val notes =
            listOf(LabProtocols.KONTRA_B, LabProtocols.GROSSES_F, LabProtocols.KLEINES_F, LabProtocols.KLEINES_B)
        assertEquals(notes + notes + LabProtocols.KONTRA_B, protocol.takes.map { it.noteMidi })
        val speaker = DroneOutput.SPEAKER
        val bluetooth = DroneOutput.BLUETOOTH
        assertEquals(
            List(4) { speaker } + List(4) { bluetooth } + speaker,
            protocol.takes.map { it.output }
        )
        assertTrue(protocol.takes.all { it.source == LabSource.UNPROCESSED && it.mic == bottom && it.droneOctave == 0 })
        assertTrue(protocol.takes.all { take -> take.segments.all { it.drone } })
        assertEquals(listOf(0, 4, 8), protocol.takes.indices.filter { protocol.takes[it].pauseBefore != null })
        assertEquals(58.27 * 442.0 / 440.0, protocol.takes.first().noteHz, 1e-3)
        assertEquals("T01-unprocessed-unten-bb1-lautsprecher.wav", protocol.takes.first().fileName)
        assertEquals("T08-unprocessed-unten-bb3-bluetooth.wav", protocol.takes[7].fileName)
        assertEquals("kleines f", protocol.takes[2].noteName)
    }

    @Test
    fun fileNamesAreUniqueAscii() {
        val protocol = LabProtocols.tuba(listOf(bottom, back), sdk = 33)

        val names = protocol.takes.map { it.fileName }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { name -> name.all { it.code < 128 } && name.endsWith(".wav") })
        assertEquals("T01-unprocessed-unten-f1.wav", names.first())
        assertEquals("T24-unprocessed-unten-bb1-drone+1.wav", names.last())
    }

    @Test
    fun segmentBoundsAddUpToTheTakeDuration() {
        val take = LabProtocols.tuba(listOf(MicChoice.SYSTEM), sdk = 33).takes.last()

        assertEquals(listOf(0.0, 3.0, 8.0, 14.0, 19.0), take.segmentBoundsSec)
        assertEquals(19.0, take.durationSec, 1e-9)
        assertEquals("Kontra-B", take.noteName)
        assertEquals(58.27, take.noteHz, 0.005)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateTakeIdsAreRejected() {
        val take = LabProtocols.selfTest(listOf(MicChoice.SYSTEM), sdk = 33).takes.first()
        LabProtocol("x", "x", listOf(take, take), prepSec = 0.0)
    }
}

package io.github.blanaboo.stimmzug.dsp.exercise

import kotlin.random.Random

/**
 * Turns an [Exercise] into its target notes for an [Instrument] and walks through them during a session
 * (next, back, skip). The notes are computed once; random exercises are reproducible from their seed.
 */
class ExerciseSequencer(val exercise: Exercise, val instrument: Instrument) {

    /** All target notes (MIDI, concert pitch) in playing order. */
    val notes: List<Int> = notesOf(exercise, instrument)

    /** Index of the current note; equals [notes].size when the exercise is finished. */
    var position: Int = 0
        private set

    val isFinished: Boolean get() = position >= notes.size

    /** The note to play now, `null` when finished. */
    val current: Int? get() = notes.getOrNull(position)

    /** Moves to the next note (after a hit, or to skip the current one); returns the new current note. */
    fun advance(): Int? {
        if (!isFinished) position++
        return current
    }

    /** Goes back one note; stays on the first. */
    fun back(): Int? {
        if (position > 0) position--
        return current
    }

    companion object {
        fun notesOf(exercise: Exercise, instrument: Instrument): List<Int> {
            val notes = when (exercise) {
                is Exercise.Chromatic -> chromatic(exercise)
                is Exercise.Scale -> scale(exercise)
                is Exercise.Random -> random(exercise, instrument)
            }
            val outside = notes.filter { it !in instrument.range }
            require(outside.isEmpty()) {
                "notes $outside are outside the range ${instrument.range} of $instrument"
            }
            return notes
        }

        private fun chromatic(e: Exercise.Chromatic): List<Int> {
            require(e.lowMidi <= e.highMidi) { "low note ${e.lowMidi} is above high note ${e.highMidi}" }
            val up = (e.lowMidi..e.highMidi).toList()
            return directed(up, up.reversed(), e.direction)
        }

        private fun scale(e: Exercise.Scale): List<Int> {
            require(e.octaves >= 1) { "need at least one octave, was ${e.octaves}" }
            val top = e.tonicMidi + 12 * e.octaves
            val up = (0 until e.octaves).flatMap { o -> e.type.ascending.map { e.tonicMidi + 12 * o + it } } + top
            val down = listOf(top) + (e.octaves - 1 downTo 0).flatMap { o ->
                e.type.descending.reversed().map { e.tonicMidi + 12 * o + it }
            }
            return directed(up, down, e.direction)
        }

        /** UP_DOWN turns at the top without repeating it. */
        private fun directed(up: List<Int>, down: List<Int>, direction: Direction): List<Int> = when (direction) {
            Direction.UP -> up
            Direction.DOWN -> down
            Direction.UP_DOWN -> up + down.drop(1)
        }

        private fun random(e: Exercise.Random, instrument: Instrument): List<Int> {
            require(e.count >= 1) { "need at least one note, was ${e.count}" }
            require(e.lowMidi <= e.highMidi) { "low note ${e.lowMidi} is above high note ${e.highMidi}" }
            require(e.lowMidi in instrument.range && e.highMidi in instrument.range) {
                "range ${e.lowMidi}..${e.highMidi} is outside the range ${instrument.range} of $instrument"
            }
            val pool = e.pool
            require(pool.isNotEmpty()) { "no notes of ${e.key} in ${e.lowMidi}..${e.highMidi}" }
            require(pool.size >= 2 || e.count == 1) { "need at least two notes to avoid repetitions, got $pool" }
            val random = Random(e.seed)
            if (!e.repeat) {
                require(e.count <= pool.size) { "${e.count} notes without repetition, but only ${pool.size} in $pool" }
                return pool.shuffled(random).take(e.count)
            }
            val notes = ArrayList<Int>(e.count)
            repeat(e.count) {
                val previous = notes.lastOrNull()
                // Draw from the pool without the previous note: uniform and never a direct repetition.
                val candidates = if (previous == null) pool else pool - previous
                notes += candidates[random.nextInt(candidates.size)]
            }
            return notes
        }
    }
}

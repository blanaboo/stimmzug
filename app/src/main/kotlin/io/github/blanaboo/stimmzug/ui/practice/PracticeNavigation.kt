package io.github.blanaboo.stimmzug.ui.practice

/** Where the practice flow leads out to. */
class PracticeNavigation(
    val home: () -> Unit = {},
    val openProfiles: () -> Unit = {},
    val openSettings: () -> Unit = {}
)

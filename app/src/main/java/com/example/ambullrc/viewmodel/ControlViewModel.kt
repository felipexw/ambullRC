package com.example.ambullrc.viewmodel

import com.example.ambullrc.model.Direction

/**
 * Holds the tap-handling logic for the control screen. The View forwards each button tap here;
 * this class decides what to record. Currently that means logging the tapped [Direction] through
 * the injected [DirectionLogger] — the seam the future Bluetooth command layer will replace.
 *
 * @param logger destination for tap records. Defaults to the Android Logcat-backed implementation
 *   so the UI can construct the ViewModel with no wiring; unit tests pass a fake.
 */
class ControlViewModel(
    private val logger: DirectionLogger = AndroidDirectionLogger()
) {
    /** Records exactly one occurrence of [direction]. No other side effects. */
    fun onDirectionTapped(direction: Direction) {
        logger.log(direction)
    }
}

package com.example.ambullrc.viewmodel

import android.util.Log
import com.example.ambullrc.model.Direction

/**
 * Logging seam for direction taps. Kept as a single-method interface so [ControlViewModel]
 * logic can be exercised in plain JVM unit tests with a fake implementation, without touching
 * the Android framework. This is also the seam a future Bluetooth command sender will attach to.
 */
fun interface DirectionLogger {
    fun log(direction: Direction)
}

/** Tag used by [AndroidDirectionLogger] for its Logcat records. */
const val DIRECTION_LOG_TAG = "AmbullRC"

/**
 * Production [DirectionLogger] that writes one Logcat record per tap.
 */
class AndroidDirectionLogger : DirectionLogger {
    override fun log(direction: Direction) {
        Log.d(DIRECTION_LOG_TAG, "Direction tapped: $direction")
    }
}

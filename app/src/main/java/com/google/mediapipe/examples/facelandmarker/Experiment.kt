package com.google.mediapipe.examples.facelandmarker

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class Rectangle(
)

@RequiresApi(Build.VERSION_CODES.O)
class Experiment(
    val context: Context?,
    val maxX: Float,
    val maxY: Float,
) {
    companion object {
        const val MIN_SIZE = 50.0f
        const val MAX_SIZE = 400.0f
    }

    var target: Rect? = null
    private val logFile: File?
    private var writer: BufferedWriter
    var method: Int = 0
    var dynamic: Boolean = false

    init {
        val currentDateTime = LocalDateTime.now()
        val formattedTime = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH_mm_ss"))
        val logFileName = "log [$formattedTime].txt"
        logFile = File(context?.filesDir, logFileName)

        Log.e("Test", "Log ${logFile}")
        if (!logFile.exists()) {
            logFile.createNewFile()
        }
        writer = BufferedWriter(FileWriter(logFile, false))
    }

    fun distance(
        x: Float,
        l: Int,
        r: Int
    ): Float {
        if (x < l) {
            return l - x
        } else if (x > r) {
            return x - r
        }
        return 0f
    }

    fun distance(
        cursorX: Float,
        cursorY: Float,
        target: Rect,
    ): Float {
        return min(distance(cursorX, target.left, target.right),
                   distance(cursorY, target.top, target.bottom))
    }

    private fun inside(
        cursorX: Float,
        cursorY: Float,
        target: Rect,
    ): Boolean {
        return cursorX >= target.left &&
                cursorX <= target.right &&
                cursorY >= target.top &&
                cursorY <= target.bottom
    }

    fun inside(
        cursorX: Float,
        cursorY: Float
    ): Boolean {
        if (target == null) {
            return false
        }
        return inside(cursorX, cursorY, target!!)
    }

    fun newRound(
        cursorX: Float,
        cursorY: Float,
        minDisFromCursor: Float,
        width: Float? = null,
        height: Float? = null,
    ): Rect {
        while (true) {
            val rWidth = width ?: (Random.nextFloat() * (MAX_SIZE - MIN_SIZE) + MIN_SIZE)
            val rHeight = height ?: (Random.nextFloat() * (MAX_SIZE - MIN_SIZE) + MIN_SIZE)
            val x = Random.nextInt((rWidth / 2).toInt(), (maxX - rWidth / 2).toInt())
            val y = Random.nextInt((rHeight / 2).toInt(), (maxY - rHeight / 2).toInt())
            val rect = Rect(
                (x - rWidth / 2).toInt(),
                (y - rHeight / 2).toInt(),
                (x + rWidth / 2).toInt(),
                (y + rHeight / 2).toInt()
            )
            if (distance(cursorX, cursorY, rect) > minDisFromCursor) {
//                writer.write("start $method ${System.currentTimeMillis()} $cursorX $cursorY $minDisFromCursor $width $height ${rect.top} ${rect.bottom} ${rect.left} ${rect.right}\n")
                writer.write("start $method $dynamic ${System.currentTimeMillis()}\n")
                writer.flush()
                target = rect
                return rect
            }
        }
    }

    fun activate() {
        writer.write("activate ${System.currentTimeMillis()}\n")
        writer.flush()
    }

    fun deactivate() {
        writer.write("deactivate ${System.currentTimeMillis()}\n")
        writer.flush()
    }

    fun finishRound() {
        writer.write("finish ${System.currentTimeMillis()}\n")
        writer.flush()
        target = null
    }
}
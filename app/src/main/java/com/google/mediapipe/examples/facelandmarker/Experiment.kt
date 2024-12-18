package com.google.mediapipe.examples.facelandmarker

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.google.mediapipe.examples.facelandmarker.fragment.CameraFragment
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList
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
        const val MIN_SIZE = 200.0f
        const val MAX_SIZE = 200.0f
    }

    var target: Rect? = null
    var attraction: ArrayList<Rect> = ArrayList()
    private val logFile: File?
    private var writer: BufferedWriter
    var method: Int = 0
    var dynamic: Boolean = false
    private var roundNum: Int = 0
    private lateinit var textVewRoundNumber: TextView

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

        textVewRoundNumber = (context as? MainActivity)?.getCameraFragment()?.fragmentCameraBinding?.textViewRoundNumber!!
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

            (context as? MainActivity)?.runOnUiThread {
                textVewRoundNumber.text = "Round: $roundNum"
            }

            attraction.clear()
//            val attractionNum = Random.nextInt(1, 5)
            val attractionNum = -1 // 暂时去掉增加额外的attraction这个功能。。
            for (i in 0..attractionNum)
            {
                val attractionWidth = width ?: (Random.nextFloat() * (MAX_SIZE * 2 - MIN_SIZE) + MIN_SIZE)
                val attractionHeight = height ?: (Random.nextFloat() * (MAX_SIZE * 2 - MIN_SIZE) + MIN_SIZE)
                val attractionX = Random.nextInt((attractionWidth / 2).toInt(), (maxX - attractionWidth / 2).toInt())
                val attractionY = Random.nextInt((attractionHeight / 2).toInt(), (maxY - attractionHeight / 2).toInt())
                val attractionRect = Rect(
                    (attractionX - attractionWidth / 2).toInt(),
                    (attractionY - attractionHeight / 2).toInt(),
                    (attractionX + attractionWidth / 2).toInt(),
                    (attractionY + attractionHeight / 2).toInt()
                )
                attraction.add(attractionRect)
            }

            if (distance(cursorX, cursorY, rect) > minDisFromCursor) {
                roundNum += 1

//                writer.write("start $method $dynamic ${System.currentTimeMillis()} ${rect.top} ${rect.bottom} ${rect.left} ${rect.right}\n")
                writer.write("start $roundNum ${System.currentTimeMillis()} ${rect.top} ${rect.bottom} ${rect.left} ${rect.right}\n")
//                writer.write("start $method $dynamic ${System.currentTimeMillis()}\n")
                writer.flush()
                target = rect
                return rect
            }
        }
    }

    fun activate() {

        val method = (context as? MainActivity)?.getCameraFragment()?.fragmentCameraBinding?.methodSpinner?.selectedItem
        val dynamic = (context as? MainActivity)?.getCameraFragment()?.fragmentCameraBinding?.dynamicSpinner?.selectedItem
        val activation = (context as? MainActivity)?.getCameraFragment()?.fragmentCameraBinding?.ignoreSpinner?.selectedItem

        writer.write("activate ${System.currentTimeMillis()} ${method} ${dynamic} ${activation}\n")
        writer.flush()
    }

    fun deactivate() {
        val method = (context as? MainActivity)?.getCameraFragment()?.fragmentCameraBinding?.methodSpinner?.selectedItem
        val dynamic = (context as? MainActivity)?.getCameraFragment()?.fragmentCameraBinding?.dynamicSpinner?.selectedItem
        val activation = (context as? MainActivity)?.getCameraFragment()?.fragmentCameraBinding?.ignoreSpinner?.selectedItem

        writer.write("deactivate ${System.currentTimeMillis()} ${method} ${dynamic} ${activation}\n")
        writer.flush()
    }

    fun finishRound() {
        writer.write("finish ${System.currentTimeMillis()}\n")
        writer.flush()
        target = null
    }
}
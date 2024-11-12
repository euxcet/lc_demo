package com.google.mediapipe.examples.facelandmarker

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs), SensorEventListener {
    companion object {
        const val CURSOR_ORIGIN_X = 600.0f
        const val CURSOR_ORIGIN_Y = 600.0f

        const val CURSOR_MAX_X = 1000.0f
        const val CURSOR_MAX_Y = 2000.0f

        const val SCALE_RATIO = 1.0f
        const val SCALE_RATIO_X = 1.0f
        const val SCALE_RATIO_Y = 1.0f
        const val CD_RATIO_MIN = 2f
        const val CD_RATIO_MAX = 16f
        const val LAMBDA = 0.5
        const val V_MAX = 50f
        const val V_MIN = 0f
        const val V_INF = SCALE_RATIO * (V_MAX - V_MIN) + V_MIN

        const val THRESHOLD_TOUCH = 300f
        const val THRESHOLD_IMU = 300f
    }

    private val cursorPaint = Paint()
    private val targetPaint = Paint()
    private var cursorX = CURSOR_ORIGIN_X
    private var cursorY = CURSOR_ORIGIN_Y
    private var method = 0
    private var dynamic = true

    private var touchLastX = 0.0f
    private var touchLastY = 0.0f

    private var activated = false
    private var allowEye = true

    private val experiment = Experiment(context, CURSOR_MAX_X, CURSOR_MAX_Y)

    private var lastYaw = 0f
    private var lastRoll = 0f
    private var lastPitch = 0f

    private var imuSumX = 0f
    private var imuSumY = 0f
    private var touchSumX = 0f
    private var touchSumY = 0f

    init {
        initPaints()
        val sensorManager = context?.getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
            SensorManager.SENSOR_DELAY_FASTEST
        )
    }

    fun clear() {
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        cursorX = CURSOR_ORIGIN_X
        cursorY = CURSOR_ORIGIN_Y
        cursorPaint.isAntiAlias = true
        cursorPaint.color = Color.GREEN
        cursorPaint.style = Paint.Style.FILL
        cursorPaint.strokeWidth = 10f

        targetPaint.isAntiAlias = true
        targetPaint.color = Color.RED
        targetPaint.style = Paint.Style.FILL
        targetPaint.strokeWidth = 10f
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (activated) {
            canvas.drawCircle(cursorX, cursorY, 20.0f, cursorPaint)
        }
        if (experiment.target != null) {
            canvas.drawRect(experiment.target!!, targetPaint)
        }
    }

    fun newRound() {
        imuSumX = 0f
        imuSumY = 0f
        touchSumX = 0f
        touchSumY = 0f
        experiment.finishRound()
        invalidate()
        CoroutineScope(Dispatchers.Default).launch {
            delay(200)
            allowEye = dynamic
            experiment.newRound(
                cursorX = cursorX,
                cursorY = cursorY,
                minDisFromCursor = 100.0f,
            )
            invalidate()
        }
    }

    fun reset() {
        cursorX = CURSOR_ORIGIN_X
        cursorY = CURSOR_ORIGIN_Y
        invalidate()
    }

    fun activate() {
        activated = true
        newRound()
        invalidate()
    }

    fun deactivate() {
        activated = false
        invalidate()
    }

    // Touch Imu Head
    fun setMethod(newMethod: Int) {
        method = newMethod
        experiment.method = newMethod
    }

    fun setDynamic(newDynamic: Int) {
        dynamic = newDynamic == 0
    }

    fun updateEye(position: Pair<Float, Float>) {
        if (allowEye) {
            moveTo(position.first, position.second)
        }
    }

    fun updateHead(
        results: FaceLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        if (method == 2) {
            allowEye = false
            val position = FaceOrientation.getOrientation(results.faceLandmarks()[0], imageHeight, imageWidth)
            moveTo(
                (0.0 + 1000.0 * position.second).toFloat(),
                (0.0 + 2000.0 * position.first).toFloat()
            )
            invalidate()
        }
    }

    private fun cdRatio(v: Float): Float {
        return ((CD_RATIO_MAX - CD_RATIO_MIN) / (1 + exp(-LAMBDA * (v - V_INF))) + CD_RATIO_MIN).toFloat()
    }

    private fun moveTo(x: Float, y: Float) {
        cursorX = min(max(x, 0f), CURSOR_MAX_X)
        cursorY = min(max(y, 0f), CURSOR_MAX_Y)
        if (experiment.update(cursorX, cursorY)) {
            newRound()
        }
        invalidate()
    }

    private fun move(offsetX: Float, offsetY: Float) {
        moveTo(cursorX + offsetX, cursorY + offsetY)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || method != 0) {
            return super.onTouchEvent(event)
        }
        val x = event.rawX
        val y = event.rawY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchLastX = x
                touchLastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                val offsetX = (x - touchLastX) * cdRatio(abs(x - touchLastX)) * SCALE_RATIO_X
                val offsetY = (y - touchLastY) * cdRatio(abs(y - touchLastY)) * SCALE_RATIO_Y
                touchSumX += offsetX
                touchSumY += offsetY
                if (allowEye) {
                    if (abs(touchSumX) > THRESHOLD_TOUCH || abs(touchSumY) > THRESHOLD_TOUCH) {
                        allowEye = false
                        touchLastX = x
                        touchLastY = y
                    }
                }
                else {
                    move(offsetX, offsetY)
                }
                touchLastX = x
                touchLastY = y
            }
            MotionEvent.ACTION_UP -> {

            }
        }
        return super.onTouchEvent(event)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (method != 1) {
            return
        }
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val matrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(matrix, orientation)
            val yaw = orientation[0]
            val roll = orientation[1]
            val pitch = orientation[2]
            val deltaYaw = lastYaw - yaw
            val deltaRoll = lastRoll - roll
            val deltaPitch = lastPitch - pitch

            lastYaw = yaw
            lastRoll = roll
            lastPitch = pitch

            val velocityX = deltaPitch * 1000f;
            val velocityY = deltaRoll * -2000f;
            imuSumX += velocityX
            imuSumY += velocityY
            if (allowEye) {
                if (abs(imuSumX) > THRESHOLD_IMU || abs(imuSumY) > THRESHOLD_IMU) {
                    allowEye = false
                    move(imuSumX, imuSumY)
                }
            } else {
                move(velocityX, velocityY)
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

}

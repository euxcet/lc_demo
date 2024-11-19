package com.google.mediapipe.examples.facelandmarker

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
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

        const val SCALE_RATIO = 1.0f
        const val SCALE_RATIO_X = 1.0f
        const val SCALE_RATIO_Y = 1.0f
        const val CD_RATIO_MIN = 2f
        const val CD_RATIO_MAX = 16f
        const val LAMBDA = 0.5
        const val V_MAX = 50f
        const val V_MIN = 0f
        const val V_INF = SCALE_RATIO * (V_MAX - V_MIN) + V_MIN

        const val THRESHOLD_TOUCH = 100f
        const val THRESHOLD_IMU = 100f
        const val THRESHOLD_HEAD = 100f
    }

    var CURSOR_MAX_X = 0f
    var CURSOR_MAX_Y = 0f

    private val cursorPaint = Paint()
    private val cursorOutlinePaint = Paint()
    private val targetPaint = Paint()
    private val attractionPaint = Paint()
    private val targetStrokePaint = Paint()
    private var cursorX = CURSOR_ORIGIN_X
    private var cursorY = CURSOR_ORIGIN_Y
    private var method = 0
    private var dynamic = true

    private var touchLastX = 0.0f
    private var touchLastY = 0.0f

    private var headLastX = 0.0f
    private var headLastY = 0.0f

    private var lastYaw = 0f
    private var lastRoll = 0f
    private var lastPitch = 0f

    private var activated = false
    private var allowEye = true

    private lateinit var experiment: Experiment


    private var imuSumX = 0f
    private var imuSumY = 0f
    private var touchSumX = 0f
    private var touchSumY = 0f
    private var headSumX = 0f
    private var headSumY = 0f
    private var headStartX = -1f
    private var headStartY = -1f

    init {
        viewTreeObserver.addOnGlobalLayoutListener {
            if (CURSOR_MAX_X == 0f) {
                CURSOR_MAX_X = width * 1.0f
                CURSOR_MAX_Y = height * 1.0f
                experiment = Experiment(context, CURSOR_MAX_X, CURSOR_MAX_Y)
                newRound()
                initPaints()
                val sensorManager = context?.getSystemService(SENSOR_SERVICE) as SensorManager
                sensorManager.registerListener(
                    this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                    SensorManager.SENSOR_DELAY_FASTEST
                )
            }
        }
    }

    fun clear() {
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        cursorX = CURSOR_ORIGIN_X
        cursorY = CURSOR_ORIGIN_Y

        cursorPaint.isAntiAlias = true
        cursorPaint.color = Color.argb(128, 255, 0, 0)
        cursorPaint.style = Paint.Style.FILL
        cursorPaint.strokeWidth = 10f

        cursorOutlinePaint.isAntiAlias = true
        cursorOutlinePaint.color = Color.argb(255, 0, 0, 0)
        cursorOutlinePaint.style = Paint.Style.STROKE
        cursorOutlinePaint.strokeWidth = 5f

        targetPaint.isAntiAlias = true
        targetPaint.color = Color.argb(128, 235, 125, 125)
        targetPaint.style = Paint.Style.FILL
        targetPaint.strokeWidth = 10f

        attractionPaint.isAntiAlias = true
        attractionPaint.color = Color.argb(128, 235, 125, 125)
        attractionPaint.style = Paint.Style.FILL
        attractionPaint.strokeWidth = 10f

        targetStrokePaint.isAntiAlias = true
        targetStrokePaint.color = Color.argb(128, 55, 55, 55)
        targetStrokePaint.style = Paint.Style.STROKE
        targetStrokePaint.strokeWidth = 10f
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (experiment.target != null) {
            canvas.drawRect(experiment.target!!, targetPaint)
            canvas.drawRect(experiment.target!!, targetStrokePaint)
        }
        if (!experiment.attraction.isEmpty())
        {
            for (attraction in experiment.attraction)
            {
                canvas.drawRect(attraction, attractionPaint)
            }
        }

        if (activated) {
            canvas.drawCircle(cursorX, cursorY, 30.0f, cursorPaint)
            canvas.drawCircle(cursorX, cursorY, 30.0f, cursorOutlinePaint)
        }
    }

    fun newRound() {
        invalidate()
        CoroutineScope(Dispatchers.Default).launch {
            delay(200)
            experiment.newRound(
                cursorX = cursorX,
                cursorY = cursorY,
                minDisFromCursor = 100.0f,
            )
            invalidate()
        }
    }

    fun activate() {
        if (activated) {
            return
        }
        allowEye = dynamic
        imuSumX = 0f
        imuSumY = 0f
        touchSumX = 0f
        touchSumY = 0f
        headStartX = -1f
        headStartY = -1f
        lastYaw = 0f
        lastPitch = 0f
        lastRoll = 0f
        headLastX = 0f
        headLastY = 0f
        headSumX = 0f
        headSumY = 0f
        experiment.activate()
        activated = true
        invalidate()
    }

    fun deactivate() {
        if (!activated) {
            return
        }
        experiment.deactivate()
        activated = false
        if (experiment.inside(cursorX, cursorY)) {
            experiment.finishRound()
            newRound()
        }
        invalidate()
    }

    // Touch Imu Head
    fun setMethod(newMethod: Int) {
        method = newMethod
        experiment.method = newMethod
    }

    fun setDynamic(newDynamic: Int) {
        dynamic = newDynamic == 0
        experiment.dynamic = dynamic
        if (!activated) {
            allowEye = dynamic
        }
    }

    fun updateEye(position: Pair<Float, Float>) {
        if (allowEye || !activated) {
            moveTo(position.first, position.second)
        }
    }

    fun updateHead(
        results: FaceLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        if (method != 2 || !activated) {
            return
        }
        val position = FaceOrientation.getOrientation(results.faceLandmarks()[0], imageHeight, imageWidth)

        val x = (0.0 + 1000.0 * position.second).toFloat()
        val y = (0.0 + 2000.0 * position.first).toFloat()

        if (headLastX == 0f && headLastY == 0f) {
            headLastX = x
            headLastY = y
        }
        val deltaX = x - headLastX
        val deltaY = y - headLastY
        headSumX += deltaX
        headSumY += deltaY
        if (allowEye) {
            if (abs(headSumX) > THRESHOLD_HEAD || abs(headSumY) > THRESHOLD_HEAD) {
                allowEye = false
                move(headSumX, headSumY)
            }
        } else {
            move(deltaX, deltaY)
        }
        headLastX = x
        headLastY = y

        /*
        if (headStartX == -1f || headStartY == -1f) {
            headStartX = x
            headStartY = y
        } else if (allowEye) {
            if (abs(x - headStartX) > THRESHOLD_HEAD || abs(y - headStartY) > THRESHOLD_HEAD) {
                allowEye = false
                moveTo(x, y)
            }
        } else {
            moveTo(x, y)
        }
         */
        invalidate()
    }

    private fun cdRatio(v: Float): Float {
        return ((CD_RATIO_MAX - CD_RATIO_MIN) / (1 + exp(-LAMBDA * (v - V_INF))) + CD_RATIO_MIN).toFloat()
    }

    private fun moveTo(x: Float, y: Float) {
        cursorX = min(max(x, 0f), CURSOR_MAX_X)
        cursorY = min(max(y, 0f), CURSOR_MAX_Y)
        invalidate()
    }

    private fun move(offsetX: Float, offsetY: Float) {
        moveTo(cursorX + offsetX, cursorY + offsetY)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || method != 0 || !activated) {
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
                        move(touchSumX, touchSumY)
                    }
                }
                else {
                    move(offsetX, offsetY)
                }
                touchLastX = x
                touchLastY = y
            }
            MotionEvent.ACTION_UP -> {
                deactivate()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (method != 1 || !activated) {
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
            if (lastYaw == 0f && lastRoll == 0f && lastPitch == 0f) {
                lastYaw = yaw
                lastRoll = roll
                lastPitch = pitch
            }
            val deltaYaw = lastYaw - yaw
            val deltaRoll = lastRoll - roll
            val deltaPitch = lastPitch - pitch
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
            lastYaw = yaw
            lastRoll = roll
            lastPitch = pitch
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

}

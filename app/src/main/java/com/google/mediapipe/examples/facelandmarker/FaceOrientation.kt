package com.google.mediapipe.examples.facelandmarker

import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.apache.commons.math3.geometry.euclidean.threed.Rotation
import org.apache.commons.math3.geometry.euclidean.threed.RotationOrder
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class Vector3(
    val height: Int,
    val width: Int,
) {
    operator fun plusAssign(landmark: NormalizedLandmark) {
        x += landmark.x() * width
        y += landmark.y() * height
        z += landmark.z() * width
    }

    operator fun minusAssign(landmark: NormalizedLandmark) {
        x -= landmark.x() * width
        y -= landmark.y() * height
        z -= landmark.z() * width
    }

    fun toArray(): DoubleArray {
        return doubleArrayOf(x, y, z)
    }

    fun normalize() {
        val l = sqrt(x * x + y * y + z * z)
        x /= l
        y /= l
        z /= l
    }

    override fun toString(): String {
        return "[${x}, ${y}, ${z}]"
    }

    fun cross(v: Vector3): Vector3 {
        val result = Vector3(0, 0)
        result.x = y * v.z - v.y * z
        result.y = v.x * z - v.z * x
        result.z = x * v.y - v.x * y
        return result
    }

    var x = 0.0
    var y = 0.0
    var z = 0.0
}

class FaceOrientation {
    companion object {
        fun getOrientation(landmarks: MutableList<NormalizedLandmark>, height: Int, width: Int): Pair<Double, Double> {
            val xAxis = Vector3(height, width)
            xAxis += landmarks[280]
            xAxis -= landmarks[50]
            xAxis += landmarks[352]
            xAxis -= landmarks[123]
            xAxis += landmarks[280]
            xAxis -= landmarks[50]
            xAxis += landmarks[376]
            xAxis -= landmarks[147]
            xAxis += landmarks[416]
            xAxis -= landmarks[192]
            xAxis += landmarks[298]
            xAxis -= landmarks[68]
            xAxis += landmarks[301]
            xAxis -= landmarks[71]
            xAxis.normalize()

            var yAxis = Vector3(height, width)
            yAxis += landmarks[10]
            yAxis -= landmarks[152]
            yAxis += landmarks[151]
            yAxis -= landmarks[152]
            yAxis += landmarks[8]
            yAxis -= landmarks[17]
            yAxis += landmarks[5]
            yAxis -= landmarks[200]
            yAxis += landmarks[6]
            yAxis -= landmarks[199]
            yAxis += landmarks[8]
            yAxis -= landmarks[18]
            yAxis += landmarks[9]
            yAxis -= landmarks[175]
            yAxis.normalize()

            val zAxis = xAxis.cross(yAxis)
            zAxis.normalize()
            yAxis = zAxis.cross(xAxis)
            val r = Rotation(
                arrayOf(
                    doubleArrayOf(xAxis.x, yAxis.x, zAxis.x),
                    doubleArrayOf(xAxis.y, yAxis.y, zAxis.y),
                    doubleArrayOf(xAxis.z, yAxis.z, zAxis.z),
                ),
                1e-5
            ).applyTo(Rotation(Vector3D(1.0, 0.0, 0.0), -0.25))
            val angles = r.getAngles(RotationOrder.XYZ)
            var px = Math.toDegrees(angles[0])
            var py = Math.toDegrees(angles[1])
            if (px < 0) {
                px += 360
            }
            px = min(max((px - 160) / 80 + 0.5, 0.0), 1.0)
            py = min(max(py / 80 + 0.5, 0.0), 1.0)
            return Pair(px, py)
        }
    }
}
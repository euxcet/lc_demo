package com.google.mediapipe.examples.facelandmarker.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

class EyeClient (
    val callback: (Pair<Float, Float>) -> Unit,
    val port: Int = 12346,
) {
    init {
        CoroutineScope(Dispatchers.IO).launch {
            val serverSocket = ServerSocket(port)
            while (true) {
                val clientSocket = serverSocket.accept()
                handleClient(clientSocket)
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            try {
                while (true) {
                    val message = input.readLine()
                    if (message != null) {
                        for (ordinate in message.split("\n")) {
                            val o = ordinate.split(",")
                            if (o.size == 2) {
                                val x = o[0].toFloat()
                                val y = o[1].toFloat()
                                callback(Pair(x, y))
                            }
                        }
                    } else {
                        break
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                clientSocket.close()
            }
        }
    }
}
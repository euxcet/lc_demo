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

class ActivateClient(
    val callback: (Boolean) -> Unit,
    val port: Int = 12345,
) {
    private var inited = false
    private var last = false

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val serverSocket = ServerSocket(port)
            while (true) {
                val clientSocket = serverSocket.accept()
                handleClient(clientSocket)
            }
        }
    }

    private fun filterCallback(x: Boolean) {
        if (!inited) {
            inited = true
            callback(x)
        }
        else {
            if (last != x) {
                callback(x)
            }
        }
        last = x
    }

    private fun handleClient(clientSocket: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            try {
                while (true) {
                    val message = input.readLine()
                    if (message != null) {
                        for (s in message) {
                            if (s == '0') {
                                filterCallback(true)
                            } else if (s == '1') {
                                filterCallback(false)
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
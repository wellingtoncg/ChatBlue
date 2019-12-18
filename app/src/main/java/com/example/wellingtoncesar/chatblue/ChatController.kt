package com.example.wellingtoncesar.chatblue

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.os.Handler
//import com.example.wellingtoncesar.chatblujavaexample.MainActivity
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class ChatController(context: Context, handler: Handler) {


    private val APP_NAME = "Chat Blue"
    private val MY_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var handler: Handler? = null
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ReadWriteThread? = null
    private var state: Int = 9
    var STATE_NONE = 0
    var STATE_LISTEN = 1
    var STATE_CONNECTING = 2
    var STATE_CONNECTED = 3

    init {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        state = STATE_NONE
        this.handler = handler
    }
    // Set the current state of the chat connection
    @Synchronized
    private fun setState(state: Int) {
        this.state = state

        handler?.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1)?.sendToTarget()
    }

    // get current connection state
    @Synchronized
    fun getState(): Int {
        return state
    }

    // start service
    @Synchronized
    fun start() {
        // Cancel any thread
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }

        // Cancel any running thresd
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        setState(STATE_LISTEN)
        if (acceptThread == null) {
            acceptThread = AcceptThread()
            acceptThread!!.start()
        }
    }

    // inicia  conexão remota com o dispositivo
    @Synchronized
    fun connect(device: BluetoothDevice) {
        // Cancel any thread
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread!!.cancel()
                connectThread = null
            }
        }

        // cancela a thread que está rodando
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        // inicia a thread pra conectar com o dispositivo
        connectThread = ConnectThread(device)
        connectThread!!.start()
        setState(STATE_CONNECTING)
    }

    // gerencia conex~blethoor
    @Synchronized
    fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        // Cancel the thread
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }


        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        if (acceptThread != null) {
            acceptThread!!.cancel()
            acceptThread = null
        }

        // inicia para gerenciar a conexão
        connectedThread = ReadWriteThread(socket)
        connectedThread!!.start()

        // envia o nomme do dispositivo
        val msg = handler!!.obtainMessage(MainActivity.MESSAGE_DEVICE_OBJECT)
        val bundle = Bundle()
        bundle.putParcelable(MainActivity.DEVICE_OBJECT, device)
        msg.data = bundle
        handler!!.sendMessage(msg)

        setState(STATE_CONNECTED)
    }

    // para todas as threads
    @Synchronized
    fun stop() {
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }

        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        if (acceptThread != null) {
            acceptThread!!.cancel()
            acceptThread = null
        }
        setState(STATE_NONE)
    }

    fun write(out: ByteArray) {
        var ru: ReadWriteThread? = null
        synchronized(this) {
            if (state != STATE_CONNECTED)
                return
            ru = connectedThread
        }
        ru!!.write(out)
    }

    private fun connectionFailed() {
        val msg = handler?.obtainMessage(MainActivity.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString("toast", "Não é possivel conectar ao dipositivo")
        msg?.data = bundle
        handler?.sendMessage(msg)

        // Inicie o serviço
        this@ChatController.start()
    }

    private fun connectionLost() {
        val msg = handler?.obtainMessage(MainActivity.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString("toast", "A conexão do dispositivo foi perdida")
        msg?.data = bundle
        handler?.sendMessage(msg)

        // Start the service over to restart listening mode
        this@ChatController.start()
    }

    //funciona enquanto ouve conexões recebidas
    private inner class AcceptThread : Thread() {
        private val serverSocket: BluetoothServerSocket?

        init {
            var tmp: BluetoothServerSocket? = null
            try {
                tmp = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID)
            } catch (ex: IOException) {
                ex.printStackTrace()
            }

            serverSocket = tmp
        }

        override fun run() {
            name = "AcceptThread"
            var socket: BluetoothSocket?
            while (this@ChatController.state != STATE_CONNECTED) {
                try {
                    socket = serverSocket!!.accept()
                } catch (e: IOException) {
                    break
                }

                // se a conexão for aceita
                if (socket != null) {
                    synchronized(this@ChatController) {
                        when (this@ChatController.state) {
                            STATE_LISTEN, STATE_CONNECTING ->
                                // start the connected thread.
                                connected(socket, socket.remoteDevice)
                            STATE_NONE, STATE_CONNECTED ->
                                // Either not ready or already connected. Terminate
                                // new socket.
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                }

                        }
                    }
                }
            }
        }

        fun cancel() {
            try {
                serverSocket!!.close()
            } catch (e: IOException) {
            }

        }
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket?

        init {
            var tmp: BluetoothSocket? = null
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            socket = tmp
        }

        override fun run() {
            name = "ConnectThread"

            // Always cancel discovery because it will slow down a connection
            bluetoothAdapter?.cancelDiscovery()

            // Conecta com o BluetoothSocket
            try {
                socket!!.connect()
            } catch (e: IOException) {
                try {
                    socket!!.close()
                } catch (e2: IOException) {
                }

                connectionFailed()
                return
            }

            // reseta a conexão
            synchronized(this@ChatController) {
                connectThread = null
            }


            connected(socket, device)
        }

        fun cancel() {
            try {
                socket!!.close()
            } catch (e: IOException) {
            }

        }
    }

    // roda enquanto a conexão ta estabelecida
    private inner class ReadWriteThread(private val bluetoothSocket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream?
        private val outputStream: OutputStream?

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            try {
                tmpIn = bluetoothSocket.inputStream
                tmpOut = bluetoothSocket.outputStream
            } catch (e: IOException) {
            }

            inputStream = tmpIn
            outputStream = tmpOut
        }

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int

            //
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = inputStream!!.read(buffer)

                    // Send the obtained bytes to the UI Activity
                    handler?.obtainMessage(
                        MainActivity.MESSAGE_READ, bytes, -1,
                        buffer
                    )?.sendToTarget()
                } catch (e: IOException) {
                    connectionLost()
                    // Start the service over to restart listening mode
                    this@ChatController.start()
                    break
                }

            }
        }


        fun write(buffer: ByteArray) {
            try {
                outputStream!!.write(buffer)
                handler?.obtainMessage(
                    MainActivity.MESSAGE_WRITE, -1, -1,
                    buffer
                )?.sendToTarget()
            } catch (e: IOException) {
            }

        }


        fun cancel() {
            try {
                bluetoothSocket.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }
}
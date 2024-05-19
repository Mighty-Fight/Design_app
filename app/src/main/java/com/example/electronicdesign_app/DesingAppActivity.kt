package com.example.electronicdesign_app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DesignAppActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 123
    private val UDP_INTERVAL_MS = 2000L // 2 seconds
    private var rpm: Int? = null
    private var batteryStatus: String? = null
    private var isRpmQuery = true
    private val udpHandler = Handler()
    private val queryHandler = Handler()

    private val udpRunnable = object : Runnable {
        override fun run() {
            SendUDPTask(this@DesignAppActivity).execute()
            udpHandler.postDelayed(this, UDP_INTERVAL_MS)
        }
    }

    private val queryRunnable = object : Runnable {
        override fun run() {
            if (isRpmQuery) {
                queryEngineRPM()
            } else {
                queryBatteryStatus()
            }
            isRpmQuery = !isRpmQuery
            queryHandler.postDelayed(this, UDP_INTERVAL_MS / 2)
        }
    }

    private var bluetoothSocket: BluetoothSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_desing_app)

        // Buscar el botón por su ID
        val startButton: Button = findViewById(R.id.miboton)

        // Establecer un Listener de clic para el botón
        startButton.setOnClickListener {

            if (!checkLocationPermission()) {
                requestLocationPermission()
                return@setOnClickListener
            }
            // Colocar aquí el código para establecer la conexión Bluetooth y comenzar el envío periódico de paquetes UDP
            val macAddress = "00:10:CC:4F:36:03" // Reemplaza con la dirección MAC del adaptador ELM327
            ConnectBluetoothTask().execute(macAddress)
        }

        if (!checkLocationPermission()) {
            requestLocationPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        udpHandler.removeCallbacks(udpRunnable)
        queryHandler.removeCallbacks(queryRunnable)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido, puedes proceder
            } else {
                showToast("Permiso de ubicación denegado")
            }
        }
    }

    private inner class SendUDPTask(private val context: Context) : AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg params: Void?): Boolean {
            val port = 23001

            // Get GPS coordinates
            val gpsInfo = getGPSInfo()

            // Construct the message
            val formattedMessage = "${gpsInfo?.latitude} ${gpsInfo?.longitude} ${getCurrentDate()} ${getCurrentTime()} $rpm $batteryStatus"

            // List of target IP addresses
            val ipAddresses = listOf("3.95.161.144", "10.20.38.150", "3.138.188.227", "18.119.122.93", "3.147.28.230")

            return try {
                val socket = DatagramSocket()

                for (ipAddress in ipAddresses) {
                    val address = InetAddress.getByName(ipAddress)
                    val sendBuffer = formattedMessage.toByteArray()
                    val packet = DatagramPacket(sendBuffer, sendBuffer.size, address, port)
                    socket.send(packet)
                }

                Log.d("UDP", "Paquetes UDP enviados correctamente")
                true
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("UDP", "Error al enviar los paquetes UDP: ${e.message}")
                false
            }
        }

        @SuppressLint("MissingPermission")
        private fun getGPSInfo(): Location? {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }

        private fun getCurrentDate(): String {
            val dateFormat = SimpleDateFormat("yy/MM/dd", Locale.getDefault())
            return dateFormat.format(Date())
        }

        private fun getCurrentTime(): String {
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return timeFormat.format(Date())
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISSION_REQUEST_CODE
        )
    }

    private inner class ConnectBluetoothTask : AsyncTask<String, Void, BluetoothSocket?>() {

        override fun doInBackground(vararg params: String?): BluetoothSocket? {
            val macAddress = params[0] ?: return null
            return connectToBluetoothDevice(macAddress)
        }

        override fun onPostExecute(socket: BluetoothSocket?) {
            bluetoothSocket = socket
            if (bluetoothSocket != null) {
                // Iniciar el envío periódico de paquetes UDP y la consulta de RPM y estado de batería
                queryHandler.postDelayed(queryRunnable, UDP_INTERVAL_MS / 2)
                udpHandler.postDelayed(udpRunnable, UDP_INTERVAL_MS)
            } else {
                showToast("Error al conectar con el adaptador Bluetooth")
            }
        }
    }

    private fun connectToBluetoothDevice(macAddress: String): BluetoothSocket? {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice? = bluetoothAdapter.getRemoteDevice(macAddress)
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SPP UUID

        return try {
            val socket = device?.createRfcommSocketToServiceRecord(uuid)
            socket?.connect()

            // Configurar la velocidad de baudios después de establecer la conexión
            val outputStream = socket?.outputStream
            outputStream?.apply {
                write("AT+BAUD4\r\n".toByteArray()) // Comando para configurar a 38400 baudios
                flush()
            }

            socket
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun sendATCommand(command: String): String? {
        val outputStream = bluetoothSocket?.outputStream
        val inputStream = bluetoothSocket?.inputStream

        return try {
            outputStream?.write(command.toByteArray())
            outputStream?.write("\r".toByteArray()) // Send carriage return to terminate command
            outputStream?.flush() // Flush the output stream
            val buffer = ByteArray(1024)
            inputStream?.read(buffer)
            String(buffer).trim()
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun queryEngineRPM() {
        val response = sendATCommand("010C") // Send AT command to get engine RPM
        val parts = response?.split(" ")

        val valor1 = parts?.getOrNull(2)
        val valor2 = parts?.getOrNull(3)
        val yy = valor1?.toInt(16)
        val xx = valor2?.toInt(16)
        if (yy != null && xx != null) {
            rpm = (yy * 256 + xx) / 4
        }
    }

    private fun queryBatteryStatus() {
        val response = sendATCommand("ATRV") // Send AT command to get battery status
        batteryStatus = response // The response should directly provide the battery voltage in a readable format
    }
}

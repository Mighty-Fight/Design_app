package com.example.electronicdesign_app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DesignAppActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 123
    private val UDP_INTERVAL_MS = 5000L // 5 seconds

    private val udpHandler = Handler()
    private val udpRunnable = object : Runnable {
        override fun run() {
            SendUDPTask(this@DesignAppActivity).execute()
            udpHandler.postDelayed(this, UDP_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_desing_app)

        // Start sending UDP packets periodically
        udpHandler.postDelayed(udpRunnable, UDP_INTERVAL_MS)
    }

    private inner class SendUDPTask(private val context: Context) : AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg params: Void?): Boolean {
            val port = 23001

            // Get GPS coordinates
            val gpsInfo = getGPSInfo()

            // Construct the message
            val formattedMessage = "${gpsInfo?.latitude} ${gpsInfo?.longitude} ${getCurrentDate()} ${getCurrentTime()}"

            // List of target IP addresses
            val ipAddresses = listOf("3.95.161.144","10.20.38.150", "3.138.188.227", "18.119.122.93", "3.147.28.230")

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
            val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
            return dateFormat.format(Date())
        }

        private fun getCurrentTime(): String {
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return timeFormat.format(Date())
        }

        override fun onPostExecute(result: Boolean) {
            if (result) {
                showToast("Paquetes UDP enviados correctamente")
            } else {
                showToast("Error al enviar los paquetes UDP")
            }
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
}

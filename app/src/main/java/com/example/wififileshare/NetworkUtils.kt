package com.example.wififileshare

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Mendapatkan IP address lokal IPv4 dari interface jaringan Wi-Fi yang aktif
     */
    fun getLocalWifiIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val interfaceList = interfaces.toList()

            // 1. Cek interface khusus Wi-Fi (biasanya wlan0 / wlan1)
            for (intf in interfaceList) {
                if (intf.isLoopback || !intf.isUp) continue
                if (intf.name.contains("wlan", ignoreCase = true) || intf.name.contains("ap", ignoreCase = true)) {
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }

            // 2. Fallback: ambil IP IPv4 non-loopback pertama yang berformat jaringan privat (192.168.x, 10.x, 172.x)
            for (intf in interfaceList) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Memeriksa apakah perangkat tersambung ke jaringan Wi-Fi
     */
    fun isConnectedToWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}


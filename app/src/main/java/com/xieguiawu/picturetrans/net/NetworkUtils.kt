package com.xieguiawu.picturetrans.net

import java.net.Inet4Address
import java.net.NetworkInterface

/** 局域网 IPv4 枚举——纯 JVM，可单测。 */
object NetworkUtils {

    data class Addr(val ip: String, val iface: String, val preferred: Boolean)

    /** WiFi 接口（wlan/swlan/ap 开头）优先；只取 site-local IPv4。 */
    fun siteLocalIps(): List<Addr> =
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni ->
                ni.inetAddresses.asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress }
                    .map { ni.name to it.hostAddress.orEmpty() }
            }
            .filter { (_, ip) -> ip.isNotEmpty() }
            .map { (name, ip) -> Addr(ip, name, isWifiLike(name)) }
            .toList()
            .sortedByDescending { it.preferred }

    fun isWifiLike(ifaceName: String): Boolean =
        ifaceName.startsWith("wlan") || ifaceName.startsWith("swlan") || ifaceName.startsWith("ap")

    fun url(ip: String, port: Int, token: String): String = "http://$ip:$port/t/$token/"
}

package com.github.damontecres.wholphin.util

import org.junit.Assert
import org.junit.Test
import java.net.InetAddress

class JellyfinLocalDiscoveryTest {
    @Test
    fun computeBroadcastAddress_for19216818Subnet() {
        val deviceAddress = InetAddress.getByName("192.168.18.42")

        val broadcast = JellyfinLocalDiscovery.computeBroadcastAddress(deviceAddress, 24)

        Assert.assertEquals("192.168.18.255", broadcast?.hostAddress)
    }
}

package com.github.damontecres.wholphin.util

import org.junit.Assert
import org.junit.Test
import java.net.InetAddress

class JellyfinSubnetDiscoveryTest {
    @Test
    fun hostsInSubnet_includes19216818LanHosts() {
        val deviceAddress = InetAddress.getByName("192.168.18.3") as java.net.Inet4Address

        val hosts = JellyfinSubnetDiscovery.hostsInSubnet(deviceAddress, 24, exclude = deviceAddress)

        Assert.assertTrue("192.168.18.151" in hosts)
        Assert.assertFalse("192.168.18.3" in hosts)
        Assert.assertFalse("192.168.18.0" in hosts)
        Assert.assertFalse("192.168.18.255" in hosts)
    }
}

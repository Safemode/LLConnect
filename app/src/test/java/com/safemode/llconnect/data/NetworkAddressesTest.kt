package com.safemode.llconnect.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAddressesTest {

    @Test fun privateIpv4RangesAreLocal() {
        assertTrue(NetworkAddresses.isLikelyLocal("192.168.1.50"))
        assertTrue(NetworkAddresses.isLikelyLocal("10.0.0.5"))
        assertTrue(NetworkAddresses.isLikelyLocal("172.16.0.1"))
        assertTrue(NetworkAddresses.isLikelyLocal("172.31.255.255"))
        assertTrue(NetworkAddresses.isLikelyLocal("127.0.0.1"))
    }

    @Test fun loopbackAndLanNamesAreLocal() {
        assertTrue(NetworkAddresses.isLikelyLocal("localhost"))
        assertTrue(NetworkAddresses.isLikelyLocal("lubelogger"))        // bare hostname
        assertTrue(NetworkAddresses.isLikelyLocal("garage.local"))
        assertTrue(NetworkAddresses.isLikelyLocal("nas.lan"))
        assertTrue(NetworkAddresses.isLikelyLocal("::1"))
        assertTrue(NetworkAddresses.isLikelyLocal(""))                   // nothing entered yet
    }

    @Test fun publicHostsAreNotLocal() {
        assertFalse(NetworkAddresses.isLikelyLocal("lube.example.com"))
        assertFalse(NetworkAddresses.isLikelyLocal("8.8.8.8"))
        assertFalse(NetworkAddresses.isLikelyLocal("172.32.0.1"))       // just outside 172.16/12
        assertFalse(NetworkAddresses.isLikelyLocal("example.org"))
    }

    @Test fun ignoresSchemeAndPort() {
        assertTrue(NetworkAddresses.isLikelyLocal("http://192.168.1.50:8080"))
        assertFalse(NetworkAddresses.isLikelyLocal("https://lube.example.com:443"))
    }
}

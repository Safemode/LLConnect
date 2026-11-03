package com.safemode.llconnect.data.settings

import com.safemode.llconnect.data.RecordArea
import com.safemode.llconnect.data.scan.ReceiptQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionConfigTest {

    @Test fun baseUrlAddsSchemeAndTrailingSlash() {
        val config = ConnectionConfig(scheme = "https", host = "10.0.0.5", port = "8080")
        assertEquals("https://10.0.0.5:8080/", config.baseUrl())
    }

    @Test fun baseUrlStripsSchemePrefixFromHost() {
        val config = ConnectionConfig(scheme = "http", host = "http://lube.local", port = "")
        assertEquals("http://lube.local/", config.baseUrl())
    }

    @Test fun baseUrlDoesNotDoublePort() {
        val config = ConnectionConfig(scheme = "http", host = "10.0.0.5:9000", port = "8080")
        // An explicit port field wins over a port already in the host.
        assertEquals("http://10.0.0.5:8080/", config.baseUrl())
    }

    @Test fun baseUrlKeepsPortTypedIntoHost() {
        val config = ConnectionConfig(scheme = "http", host = "10.0.0.5:9000", port = "")
        assertEquals("http://10.0.0.5:9000/", config.baseUrl())
    }

    @Test fun baseUrlPutsSubPathAfterPort() {
        val config = ConnectionConfig(scheme = "https", host = "lube.example.com/lubelogger/", port = "8443")
        assertEquals("https://lube.example.com:8443/lubelogger/", config.baseUrl())
    }

    @Test fun baseUrlKeepsSubPathWithoutPort() {
        val config = ConnectionConfig(scheme = "https", host = "https://lube.example.com/lubelogger", port = "")
        assertEquals("https://lube.example.com/lubelogger/", config.baseUrl())
    }

    @Test fun baseUrlBracketsBareIpv6() {
        val config = ConnectionConfig(scheme = "http", host = "fd00::5", port = "8080")
        assertEquals("http://[fd00::5]:8080/", config.baseUrl())
    }

    @Test fun baseUrlReplacesPortOnBracketedIpv6() {
        val config = ConnectionConfig(scheme = "http", host = "[fd00::5]:9000", port = "8080")
        assertEquals("http://[fd00::5]:8080/", config.baseUrl())
    }

    @Test fun receiptQualityDefaultsToCompressed() {
        val config = ConnectionConfig()
        assertEquals(ReceiptQuality.COMPRESSED, config.receiptQuality(RecordArea.GAS))
    }

    @Test fun receiptQualityHonorsFullQualityAreas() {
        val config = ConnectionConfig(fullQualityAreas = setOf(RecordArea.SERVICE.name))
        assertEquals(ReceiptQuality.FULL, config.receiptQuality(RecordArea.SERVICE))
        assertEquals(ReceiptQuality.COMPRESSED, config.receiptQuality(RecordArea.GAS))
    }

    @Test fun isConfiguredRequiresHostAndCredential() {
        assertFalse(ConnectionConfig().isConfigured)
        assertFalse(ConnectionConfig(host = "10.0.0.5").isConfigured)
        assertTrue(ConnectionConfig(host = "10.0.0.5", apiKey = "k").isConfigured)
        assertTrue(
            ConnectionConfig(host = "10.0.0.5", authMode = AuthMode.BASIC, basicUsername = "u")
                .isConfigured,
        )
    }
}

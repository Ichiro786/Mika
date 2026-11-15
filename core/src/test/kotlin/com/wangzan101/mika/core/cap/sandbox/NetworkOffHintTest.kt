package com.wangzan101.mika.core.cap.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkOffHintTest {
    @Test
    fun networkCommandsThatFailWhileOfflineGetTheHint() {
        for (c in listOf("pip install requests", "pip3 install x", "cd /workspace && npm install", "apk add git", "curl -O https://a.b/c", "wget https://a.b", "git clone https://a.b/c.git")) {
            assertEquals(HINT_NETWORK_OFF, networkOffHint(false, 1, c, ""), c)
        }
    }

    @Test
    fun networkErrorOutputGetsTheHintEvenForOtherCommands() {
        assertEquals(HINT_NETWORK_OFF, networkOffHint(false, 1, "python3 fetch.py", "socket.gaierror: Temporary failure in name resolution"))
    }

    @Test
    fun noHintWhenOnlineSucceededOrUnrelated() {
        assertNull(networkOffHint(true, 1, "pip install x", ""))
        assertNull(networkOffHint(false, 0, "pip --version", ""))
        assertNull(networkOffHint(false, 1, "python3 a.py", "ValueError"))
        assertNull(networkOffHint(false, 1, "echo pipeline", ""))
    }

    @Test
    fun hintNamesTheSettingsPath() {
        assertTrue("Allow scripts to go online" in HINT_NETWORK_OFF && "Files & computing environment" in HINT_NETWORK_OFF)
    }
}

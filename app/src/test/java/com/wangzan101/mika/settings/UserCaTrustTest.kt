package com.wangzan101.mika.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class UserCaTrustTest {
    @Test
    fun presetAndAccountHostsAreOfficial() {
        assertTrue(UserCaTrust.isOfficialHost("api.deepseek.com"))
        assertTrue(UserCaTrust.isOfficialHost("API.OpenAI.com."))
        assertTrue(UserCaTrust.isOfficialHost("auth.openai.com"))
        assertTrue(UserCaTrust.isOfficialHost("chatgpt.com"))
        assertTrue(UserCaTrust.isOfficialHost("accounts.x.ai"))
        assertTrue(UserCaTrust.isOfficialHost("api.github.com"))
        assertTrue(UserCaTrust.isOfficialHost("api.anthropic.com"))
    }

    @Test
    fun selfHostedAndUnknownHostsMayUseUserCas() {
        assertFalse(UserCaTrust.isOfficialHost("gpustack.corp.example"))
        assertFalse(UserCaTrust.isOfficialHost("192.168.1.10"))
        assertFalse(UserCaTrust.isOfficialHost("evilopenai.com"))
        assertFalse(UserCaTrust.isOfficialHost("openai.com.evil.example"))
    }

    @Test
    fun missingHostIsTreatedAsOfficial() {
        assertTrue(UserCaTrust.isOfficialHost(null))
        assertTrue(UserCaTrust.isOfficialHost(""))
    }

    private class Recording(val name: String, val log: MutableList<String>, val accept: Boolean) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            log += name
            if (!accept) throw CertificateException(name)
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    @Test
    fun twoArgumentCheckAlwaysUsesSystemOnlyManager() {
        val log = mutableListOf<String>()
        val tm = UserCaTrust.HostAwareTrustManager(Recording("strict", log, true)) { Recording("inclusive", log, true) }
        tm.checkServerTrusted(emptyArray<X509Certificate>(), "RSA")
        assertEquals(listOf("strict"), log)
    }
}

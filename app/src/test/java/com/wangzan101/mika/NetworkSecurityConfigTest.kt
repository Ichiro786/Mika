package com.wangzan101.mika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 网络安全配置的守护:明文 http 留着(局域网模型服务),信任锚只有系统 CA;用户 CA 只由 UserCaTrust 给自建服务。
 */
class NetworkSecurityConfigTest {

    private fun baseConfig(): Element {
        val file = File("src/main/res/xml/network_security_config.xml")
        assertTrue("network security config not found from ${File(".").absolutePath}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val configs = doc.getElementsByTagName("base-config")
        assertEquals("expected exactly one base-config", 1, configs.length)
        return configs.item(0) as Element
    }

    /** 用户自己在本机或局域网跑的模型服务多半只有 http,这条删了它们全连不上。 */
    @Test
    fun cleartextStaysPermitted() {
        assertEquals("true", baseConfig().getAttribute("cleartextTrafficPermitted"))
    }

    /**
     * 全局只信系统 CA(ORB-047):官方服务商、GitHub、浏览器不接受用户装的 CA。
     * 公司内部 CA 签发的自建端点(#361)由连自定义服务商、MCP 的客户端另信用户 CA,见 UserCaTrust。
     */
    @Test
    fun trustAnchorsAreSystemOnly() {
        val anchors = baseConfig().getElementsByTagName("certificates")
        val sources = (0 until anchors.length).map { (anchors.item(it) as Element).getAttribute("src") }
        assertEquals(listOf("system"), sources)
    }
}

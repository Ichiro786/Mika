package com.wangzan101.mika.settings

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * 用户安装的 CA 只对自建、自定义的服务生效(ORB-047;#361 的企业内网模型服务靠它)。
 *
 * `network_security_config.xml` 全局只信系统 CA。需要信用户 CA 的 OkHttp 客户端(连模型服务商、MCP 服务的)
 * 经 [install] 换上 [HostAwareTrustManager]:握手的主机是官方服务商(见 [isOfficialHost])时仍只信系统 CA,
 * 其余主机(自建、局域网、公司内网)再信 `AndroidCAStore` 里用户装的证书。认不出主机时按官方处理(更严)。
 */
object UserCaTrust {
    /** 预设之外、同样不接受用户 CA 的主机(账号登录、订阅、GitHub 等);本身及其子域名都算。 */
    private val EXTRA_OFFICIAL_HOSTS = listOf(
        "openai.com", "chatgpt.com", "x.ai", "openrouter.ai", "anthropic.com", "github.com", "githubusercontent.com", "googleapis.com",
    )

    private val officialHosts: Set<String> by lazy {
        ProviderPresets.all.filter { !it.selfHosted && it.baseUrl.isNotEmpty() }
            .mapNotNull { it.baseUrl.toHttpUrlOrNull()?.host?.lowercase() }
            .plus(EXTRA_OFFICIAL_HOSTS).toSet()
    }

    /** [host] 是官方服务商(预设里的主机,或 [EXTRA_OFFICIAL_HOSTS] 及其子域名)。空的、认不出的按官方算。 */
    fun isOfficialHost(host: String?): Boolean {
        val h = host?.trim()?.trimEnd('.')?.lowercase().orEmpty()
        if (h.isEmpty()) return true
        return officialHosts.any { h == it || h.endsWith(".$it") }
    }

    /** 给 [builder] 装上按主机区分的信任;拿不到系统的信任管理器时原样返回(仍是系统 CA)。 */
    fun install(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val strict = runCatching { platformTrustManager() }.getOrNull() ?: return builder
        return runCatching {
            val tm = HostAwareTrustManager(strict) { userInclusiveTrustManager() }
            val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
            builder.sslSocketFactory(context.socketFactory, tm)
        }.getOrDefault(builder)
    }

    private fun platformTrustManager(): X509TrustManager? =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
            .trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()

    @Volatile private var cachedInclusive: Pair<Long, X509TrustManager?>? = null

    /** 系统加用户证书的信任管理器(`AndroidCAStore` 里 `system:`、`user:` 两类);缓存半分钟,用户刚装的 CA 不用重启就能用。取不到为 null。 */
    private fun userInclusiveTrustManager(): X509TrustManager? {
        val now = System.currentTimeMillis()
        cachedInclusive?.takeIf { now - it.first < CACHE_MILLIS }?.let { return it.second }
        val tm = runCatching {
            val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
                .trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        }.getOrNull()
        cachedInclusive = now to tm
        return tm
    }

    private const val CACHE_MILLIS = 30_000L

    /**
     * 握手的主机是官方服务商,或看不出主机时用 [strict](系统 CA);否则用 [inclusive] 给出的(系统加用户 CA),给不出时退回 [strict]。
     * 没有套接字、引擎的两参数检查(几乎不会被调到)一律走 [strict]。
     */
    internal class HostAwareTrustManager(
        private val strict: X509TrustManager,
        private val inclusive: () -> X509TrustManager?,
    ) : X509ExtendedTrustManager() {
        private fun pick(host: String?): X509TrustManager = if (isOfficialHost(host)) strict else inclusive() ?: strict

        private fun hostOf(socket: Socket?): String? =
            (socket as? SSLSocket)?.let { s -> runCatching { s.handshakeSession?.peerHost }.getOrNull() }

        private fun hostOf(engine: SSLEngine?): String? =
            engine?.let { e -> runCatching { e.handshakeSession?.peerHost ?: e.peerHost }.getOrNull() }

        private fun server(tm: X509TrustManager, chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
            if (tm is X509ExtendedTrustManager && socket != null) tm.checkServerTrusted(chain, authType, socket)
            else tm.checkServerTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) =
            server(pick(hostOf(socket)), chain, authType, socket)

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
            val tm = pick(hostOf(engine))
            if (tm is X509ExtendedTrustManager && engine != null) tm.checkServerTrusted(chain, authType, engine)
            else tm.checkServerTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            strict.checkServerTrusted(chain, authType)

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            strict.checkClientTrusted(chain, authType)

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) =
            strict.checkClientTrusted(chain, authType)

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) =
            strict.checkClientTrusted(chain, authType)

        /** 两套的根都认得:OkHttp 靠它整理证书链(给证书固定用)。 */
        override fun getAcceptedIssuers(): Array<X509Certificate> =
            (strict.acceptedIssuers.toList() + (inclusive()?.acceptedIssuers?.toList() ?: emptyList())).toTypedArray()
    }
}

package com.github.damontecres.wholphin.util

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Finds Jellyfin servers by probing the local subnet over HTTP.
 *
 * Docker and some router setups do not expose Jellyfin's UDP auto-discovery port (7359), so UDP
 * discovery alone often finds nothing even when a server is reachable at a LAN IP like
 * 192.168.18.151:8096.
 */
object JellyfinSubnetDiscovery {
    private const val PUBLIC_INFO_PATH = "/System/Info/Public"
    private const val CONNECT_TIMEOUT_MS = 350
    private const val READ_TIMEOUT_MS = 900
    private const val MAX_PARALLEL_PROBES = 32

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    private val probePorts = listOf(8096, 8920)

    fun discover(
        totalTimeoutMs: Long = DEFAULT_TOTAL_TIMEOUT_MS,
        maxServers: Int = DEFAULT_MAX_SERVERS,
    ): Flow<ServerDiscoveryInfo> =
        flow {
            val deadline = SystemClock.elapsedRealtime() + totalTimeoutMs
            val seenIds = linkedSetOf<String>()
            val localAddresses = localIpv4Addresses()
            if (localAddresses.isEmpty()) {
                Timber.w("No local IPv4 addresses available for subnet Jellyfin discovery")
                return@flow
            }

            val targets =
                localAddresses
                    .flatMap { (address, prefixLength) ->
                        hostsInSubnet(address, prefixLength, exclude = address).flatMap { host ->
                            probePorts.map { port -> host to port }
                        }
                    }.distinct()

            Timber.d(
                "Starting subnet Jellyfin discovery across %d targets on %d local interface(s)",
                targets.size,
                localAddresses.size,
            )

            val semaphore = Semaphore(MAX_PARALLEL_PROBES)
            coroutineScope {
                targets
                    .map { (host, port) ->
                        async(Dispatchers.IO) {
                            if (
                                !coroutineContext.isActive ||
                                SystemClock.elapsedRealtime() >= deadline ||
                                seenIds.size >= maxServers
                            ) {
                                return@async null
                            }

                            semaphore.withPermit {
                                probeHost(host, port)
                            }
                        }
                    }.chunked(MAX_PARALLEL_PROBES)
                    .forEach { chunk ->
                        if (!coroutineContext.isActive || SystemClock.elapsedRealtime() >= deadline) return@forEach
                        chunk
                            .awaitAll()
                            .filterNotNull()
                            .forEach { info ->
                                if (seenIds.add(info.id)) {
                                    emit(info)
                                }
                            }
                    }
            }
        }.flowOn(Dispatchers.IO)

    internal fun hostsInSubnet(
        address: Inet4Address,
        prefixLength: Int,
        exclude: Inet4Address? = null,
    ): List<String> {
        if (prefixLength <= 0 || prefixLength > 32) return emptyList()

        val octets = address.address
        val ip =
            (octets[0].toInt() and 0xFF shl 24) or
                (octets[1].toInt() and 0xFF shl 16) or
                (octets[2].toInt() and 0xFF shl 8) or
                (octets[3].toInt() and 0xFF)
        val hostBits = 32 - prefixLength
        if (hostBits <= 1) return emptyList()

        val network = ip and (-1 shl hostBits)
        val broadcast = network or ((1 shl hostBits) - 1)
        val excludeIp = exclude?.let {
            (it.address[0].toInt() and 0xFF shl 24) or
                (it.address[1].toInt() and 0xFF shl 16) or
                (it.address[2].toInt() and 0xFF shl 8) or
                (it.address[3].toInt() and 0xFF)
        }

        return buildList {
            for (candidate in (network + 1) until broadcast) {
                if (candidate == excludeIp) continue
                add(
                    InetAddress.getByAddress(
                        byteArrayOf(
                            (candidate shr 24 and 0xFF).toByte(),
                            (candidate shr 16 and 0xFF).toByte(),
                            (candidate shr 8 and 0xFF).toByte(),
                            (candidate and 0xFF).toByte(),
                        ),
                    ).hostAddress,
                )
            }
        }
    }

    private fun localIpv4Addresses(): List<Pair<Inet4Address, Int>> =
        runCatching {
            NetworkInterface
                .getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .flatMap { networkInterface ->
                    if (!networkInterface.isUp || networkInterface.isLoopback) {
                        return@flatMap emptyList()
                    }
                    networkInterface.interfaceAddresses.mapNotNull { interfaceAddress ->
                        val address = interfaceAddress.address
                        if (address !is Inet4Address || address.isLoopbackAddress || address.isLinkLocalAddress) {
                            null
                        } else {
                            address to interfaceAddress.networkPrefixLength.toInt()
                        }
                    }
                }.distinct()
        }.onFailure { ex ->
            Timber.w(ex, "Unable to enumerate local IPv4 addresses for subnet discovery")
        }.getOrDefault(emptyList())

    private fun probeHost(
        host: String,
        port: Int,
    ): ServerDiscoveryInfo? {
        if (!isTcpPortOpen(host, port)) return null

        val scheme = if (port == 8920) "https" else "http"
        val reachableUrl = "$scheme://$host:$port"
        val systemInfo = fetchPublicSystemInfo(reachableUrl) ?: return null
        val id = systemInfo.id?.takeIf { it.isNotBlank() } ?: return null
        if (systemInfo.startupWizardCompleted == false) return null

        Timber.i("Found Jellyfin server %s at %s", systemInfo.serverName, reachableUrl)
        return ServerDiscoveryInfo(
            address = reachableUrl,
            id = id,
            name = systemInfo.serverName ?: reachableUrl,
            endpointAddress = host,
        )
    }

    private fun isTcpPortOpen(
        host: String,
        port: Int,
    ): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                true
            }
        }.getOrDefault(false)
    }

    private fun fetchPublicSystemInfo(baseUrl: String): PublicSystemInfo? {
        val connection =
            runCatching {
                (URL("$baseUrl$PUBLIC_INFO_PATH").openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                }
            }.getOrNull() ?: return null

        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString(PublicSystemInfo.serializer(), body)
        } catch (ex: Exception) {
            Timber.d(ex, "No Jellyfin public info at %s", baseUrl)
            null
        } finally {
            connection.disconnect()
        }
    }

    const val DEFAULT_TOTAL_TIMEOUT_MS = 10_000L
    const val DEFAULT_MAX_SERVERS = 15
}

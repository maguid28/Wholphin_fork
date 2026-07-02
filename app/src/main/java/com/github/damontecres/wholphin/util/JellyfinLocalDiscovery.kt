package com.github.damontecres.wholphin.util

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlin.coroutines.coroutineContext

/**
 * UDP discovery for Jellyfin servers on the local network.
 *
 * The Jellyfin SDK discovery path does not enable [DatagramSocket.setBroadcast] on Android, which
 * prevents broadcast packets from being sent. This helper also probes subnet broadcast addresses.
 */
object JellyfinLocalDiscovery {
    private const val DISCOVERY_MESSAGE = "who is JellyfinServer?"
    private const val DISCOVERY_PORT = 7359
    private const val RECEIVE_BUFFER_SIZE = 1024
    private const val PER_RECEIVE_TIMEOUT_MS = 500

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    fun discover(
        totalTimeoutMs: Long = DEFAULT_TOTAL_TIMEOUT_MS,
        maxServers: Int = DEFAULT_MAX_SERVERS,
    ): Flow<ServerDiscoveryInfo> =
        flow {
            val socket =
                DatagramSocket().apply {
                    soTimeout = PER_RECEIVE_TIMEOUT_MS
                    broadcast = true
                    reuseAddress = true
                }
            try {
                val seenIds = linkedSetOf<String>()
                val deadline = SystemClock.elapsedRealtime() + totalTimeoutMs

                for (target in broadcastTargets()) {
                    sendDiscoveryRequest(socket, target)
                }

                while (
                    coroutineContext.isActive &&
                    SystemClock.elapsedRealtime() < deadline &&
                    seenIds.size < maxServers
                ) {
                    val info = receiveDiscoveryInfo(socket) ?: continue
                    if (seenIds.add(info.id)) {
                        emit(info)
                    }
                }
            } finally {
                runCatching { socket.close() }
            }
        }.flowOn(Dispatchers.IO)

    private fun broadcastTargets(): List<InetAddress> {
        val targets = linkedSetOf<InetAddress>()
        runCatching {
            targets.add(InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1)))
        }.onFailure { ex ->
            Timber.w(ex, "Unable to resolve global broadcast address")
        }

        runCatching {
            NetworkInterface
                .getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .forEach { networkInterface ->
                    if (!networkInterface.isUp || networkInterface.isLoopback) return@forEach
                    networkInterface.interfaceAddresses.forEach { interfaceAddress ->
                        val broadcast =
                            interfaceAddress.broadcast
                                ?: computeBroadcastAddress(
                                    interfaceAddress.address,
                                    interfaceAddress.networkPrefixLength.toInt(),
                                )
                        broadcast?.let { targets.add(it) }
                    }
                }
        }.onFailure { ex ->
            Timber.w(ex, "Unable to enumerate subnet broadcast addresses")
        }

        Timber.d("Jellyfin discovery broadcast targets: %s", targets.mapNotNull { it.hostAddress })
        return targets.toList()
    }

    internal fun computeBroadcastAddress(
        address: InetAddress,
        prefixLength: Int,
    ): InetAddress? {
        if (address !is Inet4Address || address.isLoopbackAddress || address.isLinkLocalAddress) {
            return null
        }
        if (prefixLength <= 0 || prefixLength > 32) return null

        val octets = address.address
        val ip =
            (octets[0].toInt() and 0xFF shl 24) or
                (octets[1].toInt() and 0xFF shl 16) or
                (octets[2].toInt() and 0xFF shl 8) or
                (octets[3].toInt() and 0xFF)
        val hostBits = 32 - prefixLength
        if (hostBits <= 0) return null

        val broadcastIp = ip or ((1 shl hostBits) - 1)
        return InetAddress.getByAddress(
            byteArrayOf(
                (broadcastIp shr 24 and 0xFF).toByte(),
                (broadcastIp shr 16 and 0xFF).toByte(),
                (broadcastIp shr 8 and 0xFF).toByte(),
                (broadcastIp and 0xFF).toByte(),
            ),
        )
    }

    private fun sendDiscoveryRequest(
        socket: DatagramSocket,
        target: InetAddress,
    ) {
        runCatching {
            val payload = DISCOVERY_MESSAGE.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(payload, payload.size, target, DISCOVERY_PORT)
            socket.send(packet)
            Timber.d("Sent Jellyfin discovery broadcast to %s", target.hostAddress)
        }.onFailure { ex ->
            Timber.w(ex, "Unable to send Jellyfin discovery broadcast to %s", target.hostAddress)
        }
    }

    private fun receiveDiscoveryInfo(socket: DatagramSocket): ServerDiscoveryInfo? {
        val buffer = ByteArray(RECEIVE_BUFFER_SIZE)
        val packet = DatagramPacket(buffer, buffer.size)
        return try {
            socket.receive(packet)
            val message =
                String(
                    packet.data,
                    packet.offset,
                    packet.length,
                    Charsets.UTF_8,
                )
            Timber.d("Received Jellyfin discovery response: %s", message)
            val info = json.decodeFromString(ServerDiscoveryInfo.serializer(), message)
            normalizeEndpointAddress(info, packet.address?.hostAddress)
        } catch (_: SocketTimeoutException) {
            null
        } catch (ex: Exception) {
            Timber.w(ex, "Unable to parse Jellyfin discovery response")
            null
        }
    }

    private fun normalizeEndpointAddress(
        info: ServerDiscoveryInfo,
        senderAddress: String?,
    ): ServerDiscoveryInfo {
        val endpoint = info.endpointAddress?.takeIf { it.isNotBlank() } ?: senderAddress ?: return info
        if (!JellyfinDiscoverySupport.isLocalOnlyDiscoveryHost(info.address.toDiscoveryHttpUrlHost())) {
            return info
        }
        return info.copy(endpointAddress = endpoint)
    }

    private fun String.toDiscoveryHttpUrlHost(): String? {
        val trimmed = trim()
        if (trimmed.isBlank()) return null
        val withoutScheme = trimmed.substringAfter("://", trimmed)
        return withoutScheme.substringBefore('/').substringBefore(':').takeIf { it.isNotBlank() }
    }

    const val DEFAULT_TOTAL_TIMEOUT_MS = 5_000L
    const val DEFAULT_MAX_SERVERS = 15
}

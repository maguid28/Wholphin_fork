package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.data.model.JellyfinServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber

object JellyfinDiscoverySupport {
    private const val JELLYFIN_HTTP_PORT = 8096

    fun resolveServerUrl(
        discoveryInfo: ServerDiscoveryInfo,
        addressCandidates: (String) -> Collection<String>,
    ): String? = discoveryCandidates(discoveryInfo, addressCandidates).firstOrNull()

    fun discoveryCandidates(
        discoveryInfo: ServerDiscoveryInfo,
        addressCandidates: (String) -> Collection<String>,
    ): List<String> {
        val advertisedAddress = discoveryInfo.address.trim()
        val advertisedUrl = advertisedAddress.toDiscoveryHttpUrlOrNull()
        val endpointCandidate = discoveryInfo.endpointAddress?.toEndpointAddressCandidate(advertisedUrl)

        return buildSet {
            if (isLocalOnlyDiscoveryHost(advertisedUrl?.host)) {
                endpointCandidate?.let { add(it) }
            }
            if (advertisedAddress.isNotBlank()) add(advertisedAddress)
            endpointCandidate?.let { add(it) }
        }.flatMap { candidate ->
            buildSet {
                add(candidate)
                addAll(addressCandidates(candidate))
            }
        }.distinct()
    }

    fun toJellyfinServer(
        discoveryInfo: ServerDiscoveryInfo,
        url: String?,
    ): JellyfinServer? {
        val serverId = discoveryInfo.id.toUUIDOrNull()
        if (serverId == null) {
            Timber.w("Ignoring discovered server with invalid id ${discoveryInfo.id} at ${discoveryInfo.address}")
            return null
        }
        val serverUrl = url ?: discoveryInfo.address
        if (serverUrl.isBlank()) return null

        return JellyfinServer(
            id = serverId,
            name = discoveryInfo.name,
            url = serverUrl,
            version = null,
        )
    }

    fun isLocalOnlyDiscoveryHost(host: String?): Boolean =
        host != null &&
            (
                host.equals("localhost", ignoreCase = true) ||
                    host.startsWith("127.") ||
                    host == "0.0.0.0" ||
                    host == "::" ||
                    host == "::1" ||
                    host == "0:0:0:0:0:0:0:0" ||
                    host == "0:0:0:0:0:0:0:1"
            )

    private fun String.toEndpointAddressCandidate(advertisedUrl: HttpUrl?): String? {
        val endpointHost = toEndpointHostOrNull() ?: return null
        return advertisedUrl
            ?.newBuilder()
            ?.hostOrNull(endpointHost)
            ?.build()
            ?.toString()
            ?.trimEnd('/')
            ?: buildDiscoveryUrl("http", endpointHost, JELLYFIN_HTTP_PORT)
    }

    private fun HttpUrl.Builder.hostOrNull(host: String): HttpUrl.Builder? =
        runCatching { host(host) }.getOrNull()

    private fun buildDiscoveryUrl(
        scheme: String,
        host: String,
        port: Int,
    ): String? =
        runCatching {
            HttpUrl
                .Builder()
                .scheme(scheme)
                .host(host)
                .port(port)
                .build()
                .toString()
                .trimEnd('/')
        }.getOrNull()

    private fun String.toEndpointHostOrNull(): String? =
        toDiscoveryHttpUrlOrNull()?.host
            ?: trim()
                .takeIf { it.isNotBlank() && "/" !in it }
                ?.substringBeforeLast(':')
                ?.takeIf { it.isNotBlank() }

    private fun String.toDiscoveryHttpUrlOrNull(): HttpUrl? {
        val value = trim()
        if (value.isBlank()) return null
        return value.toHttpUrlOrNull() ?: "http://$value".toHttpUrlOrNull()
    }
}

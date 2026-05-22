package com.github.damontecres.wholphin.ui.setup

import android.content.Context
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.services.hilt.DefaultDispatcher
import com.github.damontecres.wholphin.services.hilt.IoDispatcher
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.discovery.RecommendedServerIssue
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class SwitchServerViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val jellyfin: Jellyfin,
        val serverRepository: ServerRepository,
        val serverDao: JellyfinServerDao,
        val navigationManager: SetupNavigationManager,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        val servers = MutableLiveData<List<JellyfinServer>>(listOf())
        val serverStatus = MutableLiveData<Map<UUID, ServerConnectionStatus>>(mapOf())
        val serverQuickConnect = MutableLiveData<Map<UUID, Boolean>>(mapOf())

        val discoveredServers = MutableLiveData<List<JellyfinServer>>(listOf())
        val serverDiscoveryRunning = MutableLiveData(false)

        val addServerState = MutableLiveData<LoadingState>(LoadingState.Pending)

        private var discoveryJob: Job? = null

        fun clearAddServerState() {
            addServerState.value = LoadingState.Pending
        }

        fun init() {
            viewModelScope.launchIO {
                withContext(Dispatchers.Main) {
                    serverStatus.value = mapOf()
                    serverQuickConnect.value = mapOf()
                }

                val allServers =
                    serverDao
                        .getServers()
                        .map { it.server }
                        .sortedWith(compareBy<JellyfinServer> { it.name }.thenBy { it.url })
                withContext(Dispatchers.Main) {
                    servers.value = allServers
                }
                allServers.forEach { server ->
                    internalTestServer(server)
                }
            }
        }

        fun testServer(server: JellyfinServer) {
            serverStatus.value =
                serverStatus.value!!.toMutableMap().apply {
                    put(
                        server.id,
                        ServerConnectionStatus.Pending,
                    )
                }
            viewModelScope.launchIO {
                delay(1000)
                val result = internalTestServer(server)
                if (result is ServerConnectionStatus.Success) {
                    showToast(context, context.getString(R.string.success), Toast.LENGTH_SHORT)
                } else if (result is ServerConnectionStatus.Error) {
                    showToast(context, result.message ?: "Error", Toast.LENGTH_SHORT)
                }
            }
        }

        private suspend fun internalTestServer(server: JellyfinServer): ServerConnectionStatus =
            try {
                val systemInfo =
                    jellyfin
                        .createApi(
                            server.url,
                            httpClientOptions =
                                HttpClientOptions(
                                    requestTimeout = 6.seconds,
                                    connectTimeout = 6.seconds,
                                    socketTimeout = 6.seconds,
                                ),
                        ).systemApi
                        .getPublicSystemInfo()
                        .content
                val result = ServerConnectionStatus.Success(systemInfo)
                withContext(Dispatchers.Main) {
                    serverStatus.value =
                        serverStatus.value!!.toMutableMap().apply {
                            put(
                                server.id,
                                result,
                            )
                        }
                }
                result
            } catch (ex: Exception) {
                val status = ServerConnectionStatus.Error(ex.localizedMessage)
                Timber.w(ex, "Error checking server ${server.url}")
                withContext(Dispatchers.Main) {
                    serverStatus.value =
                        serverStatus.value!!.toMutableMap().apply {
                            put(
                                server.id,
                                status,
                            )
                        }
                }
                status
            }

        fun switchServer(server: JellyfinServer) {
            viewModelScope.launchIO {
                withContext(Dispatchers.Main) {
                    serverStatus.value =
                        serverStatus.value!!.toMutableMap().apply {
                            put(
                                server.id,
                                ServerConnectionStatus.Pending,
                            )
                        }
                }
                val result = internalTestServer(server)
                if (result is ServerConnectionStatus.Success) {
                    val updatedServer =
                        server.copy(
                            name = result.systemInfo.serverName,
                            version = result.systemInfo.version,
                        )
                    serverRepository.addAndChangeServer(updatedServer)
                    withContext(Dispatchers.Main) {
                        navigationManager.navigateTo(SetupDestination.UserList(updatedServer))
                    }
                } else if (result is ServerConnectionStatus.Error) {
                    showToast(context, "Error connecting: $${result.message}")
                }
            }
        }

        fun addServer(inputUrl: String) {
            addServerState.value = LoadingState.Loading
            viewModelScope.launchIO {
                try {
                    val scores =
                        jellyfin.discovery.getRecommendedServers(inputUrl).sortedBy { it.score }
                    val bestServer =
                        scores.firstOrNull { it.score != RecommendedServerInfoScore.BAD }
                    val serverInfo = bestServer?.systemInfo?.getOrNull()
                    if (bestServer != null && serverInfo != null) {
                        val serverUrl = bestServer.address

                        val id = serverInfo.id?.toUUIDOrNull()

                        if (id != null && serverInfo.startupWizardCompleted == true) {
                            val server =
                                JellyfinServer(
                                    id = id,
                                    name = serverInfo.serverName,
                                    url = serverUrl,
                                    version = serverInfo.version,
                                )
                            serverRepository.addAndChangeServer(server)
                            val quickConnect =
                                jellyfin
                                    .createApi(serverUrl)
                                    .quickConnectApi
                                    .getQuickConnectEnabled()
                                    .content
                            withContext(Dispatchers.Main) {
                                serverQuickConnect.value =
                                    serverQuickConnect.value!!.toMutableMap().apply {
                                        put(id, quickConnect)
                                    }
                            }
                            withContext(Dispatchers.Main) {
                                addServerState.value = LoadingState.Success
                                navigationManager.navigateTo(SetupDestination.UserList(server))
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                addServerState.value =
                                    LoadingState.Error("Server returned invalid response")
                            }
                        }
                    } else {
                        Timber.w("Error connecting with %s: %s", inputUrl, scores)
                        // No good server candidate
                        val errors =
                            scores.joinToString("\n") {
                                val issues =
                                    it.issues.firstOrNull()?.let {
                                        when (it) {
                                            is RecommendedServerIssue.InvalidProductName,
                                            is RecommendedServerIssue.MissingSystemInfo,
                                            -> "Invalid server info"

                                            is RecommendedServerIssue.SecureConnectionFailed,
                                            is RecommendedServerIssue.ServerUnreachable,
                                            is RecommendedServerIssue.SlowResponse,
                                            -> "Unable to connect"

                                            RecommendedServerIssue.MissingVersion,
                                            is RecommendedServerIssue.OutdatedServerVersion,
                                            is RecommendedServerIssue.UnsupportedServerVersion,
                                            -> "Unsupported server version"
                                        }
                                    }
                                "${it.address} - $issues"
                            }
                        val message = "Error, tried addresses:\n$errors"
                        addServerState.setValueOnMain(LoadingState.Error(message))
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Error creating API for $inputUrl")
                    withContext(Dispatchers.Main) {
                        addServerState.value =
                            LoadingState.Error(exception = ex)
                    }
                }
            }
        }

        fun removeServer(server: JellyfinServer) {
            viewModelScope.launchIO {
                serverRepository.removeServer(server)
                init()
            }
        }

        fun discoverServers() {
            discoveryJob?.cancel()
            discoveredServers.value = listOf()
            serverDiscoveryRunning.value = true
            discoveryJob =
                viewModelScope.launchIO {
                    val multicastLock = acquireMulticastLock()
                    try {
                        jellyfin.discovery
                            .discoverLocalServers(DISCOVERY_TIMEOUT_MS, DISCOVERY_MAX_SERVERS)
                            .collect { discoveryInfo ->
                                val server = resolveDiscoveredServer(discoveryInfo) ?: return@collect
                                addDiscoveredServer(server)
                            }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Error discovering local Jellyfin servers")
                    } finally {
                        multicastLock?.releaseIfHeld()
                        serverDiscoveryRunning.setValueOnMain(false)
                    }
                }
        }

        fun stopDiscoveringServers() {
            discoveryJob?.cancel()
            discoveryJob = null
            serverDiscoveryRunning.value = false
        }

        private fun acquireMulticastLock(): WifiManager.MulticastLock? =
            runCatching {
                context.applicationContext
                    .getSystemService(WifiManager::class.java)
                    ?.createMulticastLock(MULTICAST_LOCK_TAG)
                    ?.apply {
                        setReferenceCounted(false)
                        acquire()
                    }
            }.onFailure { ex ->
                Timber.w(ex, "Unable to acquire multicast lock for server discovery")
            }.getOrNull()

        private fun WifiManager.MulticastLock.releaseIfHeld() {
            runCatching {
                if (isHeld) release()
            }.onFailure { ex ->
                Timber.w(ex, "Unable to release multicast lock for server discovery")
            }
        }

        private suspend fun resolveDiscoveredServer(discoveryInfo: ServerDiscoveryInfo): JellyfinServer? {
            val candidates = discoveryCandidates(discoveryInfo)
            val recommendedServer =
                runCatching {
                    jellyfin.discovery
                        .getRecommendedServers(candidates)
                        .sortedBy { it.score }
                        .firstNotNullOfOrNull { server ->
                            server
                                .takeUnless { it.score == RecommendedServerInfoScore.BAD }
                                ?.toJellyfinServerOrNull()
                        }
                }.onFailure { ex ->
                    Timber.w(ex, "Unable to verify discovered server ${discoveryInfo.address}")
                }.getOrNull()

            return recommendedServer ?: discoveryInfo.toJellyfinServerOrNull(candidates.firstOrNull())
        }

        private fun discoveryCandidates(discoveryInfo: ServerDiscoveryInfo): List<String> {
            val advertisedAddress = discoveryInfo.address.trim()
            val advertisedUrl = advertisedAddress.toDiscoveryHttpUrlOrNull()
            val endpointCandidate = discoveryInfo.endpointAddress?.toEndpointAddressCandidate(advertisedUrl)

            return buildSet {
                if (advertisedUrl?.host.isLocalOnlyDiscoveryHost()) {
                    endpointCandidate?.let { add(it) }
                }
                if (advertisedAddress.isNotBlank()) add(advertisedAddress)
                endpointCandidate?.let { add(it) }
            }.flatMap { candidate ->
                buildSet {
                    add(candidate)
                    addAll(jellyfin.discovery.getAddressCandidates(candidate))
                }
            }.distinct()
        }

        private fun RecommendedServerInfo.toJellyfinServerOrNull(): JellyfinServer? {
            val serverInfo = systemInfo.getOrNull() ?: return null
            val id = serverInfo.id?.toUUIDOrNull() ?: return null
            if (serverInfo.startupWizardCompleted != true) return null

            return JellyfinServer(
                id = id,
                name = serverInfo.serverName,
                url = address,
                version = serverInfo.version,
            )
        }

        private fun ServerDiscoveryInfo.toJellyfinServerOrNull(url: String?): JellyfinServer? {
            val serverId = id.toUUIDOrNull()
            if (serverId == null) {
                Timber.w("Ignoring discovered server with invalid id $id at $address")
                return null
            }
            val serverUrl = url ?: address
            if (serverUrl.isBlank()) return null

            return JellyfinServer(
                id = serverId,
                name = name,
                url = serverUrl,
                version = null,
            )
        }

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

        private fun String?.isLocalOnlyDiscoveryHost(): Boolean =
            this != null &&
                (
                    equals("localhost", ignoreCase = true) ||
                        startsWith("127.") ||
                        this == "0.0.0.0" ||
                        this == "::" ||
                        this == "::1" ||
                        this == "0:0:0:0:0:0:0:0" ||
                        this == "0:0:0:0:0:0:0:1"
                )

        private suspend fun addDiscoveredServer(server: JellyfinServer) {
            withContext(Dispatchers.Main) {
                val currentServers = discoveredServers.value.orEmpty()
                if (
                    currentServers.none {
                        it.id == server.id || it.url.equals(server.url, ignoreCase = true)
                    }
                ) {
                    discoveredServers.value =
                        (currentServers + server)
                            .sortedWith(compareBy<JellyfinServer> { it.name }.thenBy { it.url })
                }
            }
        }

        companion object {
            private const val DISCOVERY_TIMEOUT_MS = 2_500
            private const val DISCOVERY_MAX_SERVERS = 15
            private const val JELLYFIN_HTTP_PORT = 8096
            private const val MULTICAST_LOCK_TAG = "WholphinServerDiscovery"
        }
    }

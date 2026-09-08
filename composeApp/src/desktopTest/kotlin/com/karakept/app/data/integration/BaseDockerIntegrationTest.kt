package com.karakept.app.data.integration

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.karakept.app.data.local.AppDatabase
import com.karakept.app.data.model.Server
import com.karakept.app.data.model.SyncStrategy
import com.karakept.app.data.remote.RemoteDataSource
import com.karakept.app.data.repository.BookmarkActionsRepository
import com.karakept.app.data.repository.BookmarkRepository
import com.karakept.app.data.repository.HighlightRepository
import com.karakept.app.data.repository.ListRepository
import com.karakept.app.data.repository.ServerRepository
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.utils.ImageCacheManager
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import java.io.File
import java.util.concurrent.TimeUnit
import com.karakept.app.utils.DefaultAppDispatchers

/**
 * Base class for integration tests that require a running Karakeep backend.
 *
 * Manages Docker Compose lifecycle for the test suite and provides:
 * - A real HTTP client connected to the Docker backend
 * - An in-memory Room database
 * - All repository instances wired together
 *
 * Subclasses only need to implement test methods; Docker setup/teardown is handled here.
 */
abstract class BaseDockerIntegrationTest {

    companion object {
        private val DOCKER_COMPOSE_FILE: File by lazy {
            val possiblePaths = listOf(
                "karakeep-upstream/docker/docker-compose.dev.yml",
                "../karakeep-upstream/docker/docker-compose.dev.yml",
                "/workspaces/karakept-kmp/karakeep-upstream/docker/docker-compose.dev.yml"
            )

            possiblePaths.map { File(it).absoluteFile }
                .firstOrNull { it.exists() }
                ?: throw IllegalStateException("Docker compose file not found. Checked: $possiblePaths")
        }

        var isDockerRunning = false
            private set
        var baseUrl: String = ""
            private set
        var apiKey: String = ""
            private set

        private var tempComposeFile: File? = null
        private var tempDockerConfigDir: File? = null
        private val projectName: String = "karakept-integration"
        private var skipCleanupOnFailure: Boolean = false

        fun postJson(urlStr: String, body: String, token: String? = null): String {
            val url = java.net.URI(urlStr).toURL()
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            connection.outputStream.use { it.write(body.toByteArray()) }

            val responseCode = connection.responseCode
            return if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText() ?: "Error code: $responseCode"
            }
        }

        fun signupAndGetToken(apiBaseUrl: String): String {
            val email = "test-${System.currentTimeMillis()}@example.com"
            val password = "Password123!"
            val name = "Test User"

            val signupUrl = "$apiBaseUrl/api/trpc/users.create?batch=1"
            val signupBody = "{\"0\": {\"json\": {\"name\": \"$name\", \"email\": \"$email\", \"password\": \"$password\", \"confirmPassword\": \"$password\"}}}"

            println("Registering test user: $email")
            val signupResponse = postJson(signupUrl, signupBody)
            if (!signupResponse.contains("\"data\"")) {
                throw IllegalStateException("Signup failed: $signupResponse")
            }

            val exchangeUrl = "$apiBaseUrl/api/trpc/apiKeys.exchange?batch=1"
            val exchangeBody = "{\"0\": {\"json\": {\"keyName\": \"IntegrationTest\", \"email\": \"$email\", \"password\": \"$password\"}}}"

            println("Exchanging credentials for API key")
            val exchangeResponse = postJson(exchangeUrl, exchangeBody)

            val keyRegex = "\"key\":\\s*\"([^\"]+)\"".toRegex()
            val match = keyRegex.find(exchangeResponse)
            return match?.groupValues?.get(1)
                ?: throw IllegalStateException("Failed to extract API key: $exchangeResponse")
        }

        fun seedBookmarkViaTrpc(apiBaseUrl: String, token: String, url: String): String {
            val trpcUrl = "$apiBaseUrl/api/trpc/bookmarks.createBookmark?batch=1"
            val body = """{"0": {"json": {"type": "link", "url": "$url", "title": "Untitled"}}}"""

            println("Seeding bookmark via tRPC: $url")
            val output = postJson(trpcUrl, body, token)

            val idRegex = "\"id\":\\s*\"([^\"]+)\"".toRegex()
            val match = idRegex.find(output)
            return match?.groupValues?.get(1)
                ?: throw IllegalStateException("Failed to seed bookmark: $output")
        }

        fun seedListViaTrpc(apiBaseUrl: String, token: String, name: String): String {
            val trpcUrl = "$apiBaseUrl/api/trpc/lists.create?batch=1"
            val body = """{"0": {"json": {"name": "$name", "icon": "📋", "type": "manual"}}}"""

            println("Seeding list via tRPC: $name")
            val output = postJson(trpcUrl, body, token)

            val idRegex = "\"id\":\\s*\"([^\"]+)\"".toRegex()
            val match = idRegex.find(output)
            return match?.groupValues?.get(1)
                ?: throw IllegalStateException("Failed to seed list: $output")
        }

        @JvmStatic
        @BeforeClass
        fun setupDocker() {
            if (!DOCKER_COMPOSE_FILE.exists()) {
                throw IllegalStateException("Docker compose file not found at $DOCKER_COMPOSE_FILE")
            }
            println("Starting Karakeep backend for integration tests...")

            try {
                val possibleHosts = listOf("localhost", "172.17.0.1", "host.docker.internal")

                // Check if already running
                for (host in possibleHosts) {
                    try {
                        val testUrl = "http://$host:3000/api/health"
                        val connection = java.net.URI(testUrl).toURL().openConnection() as java.net.HttpURLConnection
                        connection.connectTimeout = 500
                        connection.readTimeout = 500
                        if (connection.responseCode in 200..499) {
                            println("Found existing Karakeep instance at http://$host:3000. Reusing.")
                            baseUrl = "http://$host:3000"
                            apiKey = signupAndGetToken(baseUrl)
                            isDockerRunning = true
                            return
                        }
                    } catch (e: Exception) {
                        // Not available on this host
                    }
                }

                val hostWorkspacePath = resolveHostWorkspacePath()
                println("Host workspace path: $hostWorkspacePath")

                val upstreamHostPath = "$hostWorkspacePath/karakeep-upstream"
                var correctedContent = DOCKER_COMPOSE_FILE.readText()
                    .replace("..:/app", "$upstreamHostPath:/app:z")
                correctedContent = correctedContent.replace("ports:\n      - 9222:9222", "# ports removed")

                val dockerDir = DOCKER_COMPOSE_FILE.parentFile
                tempComposeFile = File(dockerDir, "docker-compose.integration.tmp.yml")
                tempComposeFile?.writeText(correctedContent)

                tempDockerConfigDir = File(System.getProperty("java.io.tmpdir"), "docker-config-${System.currentTimeMillis()}")
                tempDockerConfigDir?.mkdirs()
                File(tempDockerConfigDir, "config.json").writeText("{}")

                val env = mutableMapOf<String, String>()
                env.putAll(System.getenv())
                env["DOCKER_CONFIG"] = tempDockerConfigDir?.absolutePath ?: ""
                env["DOCKER_API_VERSION"] = "1.44"

                try {
                    val killProcess = ProcessBuilder("sh", "-c", "docker rm -f $(docker ps -q --filter publish=3000) 2>/dev/null || true")
                    killProcess.environment().putAll(env)
                    killProcess.start().waitFor()
                } catch (e: Exception) { /* ignore */ }

                val cleanupBuilder = ProcessBuilder("docker-compose", "-p", projectName, "-f", tempComposeFile?.absolutePath ?: "", "down")
                cleanupBuilder.environment().putAll(env)
                cleanupBuilder.start().waitFor()

                val processBuilder = ProcessBuilder("docker-compose", "-p", projectName, "-f", tempComposeFile?.absolutePath ?: "", "up", "-d")
                processBuilder.environment().putAll(env)
                processBuilder.redirectErrorStream(true)

                val upProcess = processBuilder.start()
                val outputThread = Thread {
                    upProcess.inputStream.bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            println("DOCKER: $line")
                        }
                    }
                }
                outputThread.start()

                val completed = upProcess.waitFor(5, TimeUnit.MINUTES)
                if (!completed) {
                    upProcess.destroyForcibly()
                    throw IllegalStateException("docker-compose up timed out")
                }
                if (upProcess.exitValue() != 0) {
                    throw IllegalStateException("docker-compose up failed (exit ${upProcess.exitValue()})")
                }

                isDockerRunning = true

                println("Waiting for API to be ready...")
                val start = System.currentTimeMillis()
                var ready = false
                var workingHost = "localhost"

                while (System.currentTimeMillis() - start < 90000) {
                    for (host in possibleHosts) {
                        try {
                            val connection = java.net.URI("http://$host:3000/api/health").toURL().openConnection() as java.net.HttpURLConnection
                            connection.connectTimeout = 1000
                            connection.readTimeout = 1000
                            if (connection.responseCode in 200..499) {
                                workingHost = host
                                ready = true
                                break
                            }
                        } catch (e: Exception) { /* retry */ }
                    }
                    if (ready) break
                    Thread.sleep(2000)
                }

                if (!ready) {
                    fetchContainerLogs(projectName, env)
                    skipCleanupOnFailure = true
                    throw IllegalStateException("API did not become ready in time")
                }

                baseUrl = "http://$workingHost:3000"
                println("Karakeep backend running at $baseUrl")

                apiKey = signupAndGetToken(baseUrl)
            } catch (e: Exception) {
                println("Failed to start Docker environment: ${e.message}")
                skipCleanupOnFailure = true
                if (isDockerRunning || tempComposeFile != null) {
                    fetchContainerLogs(projectName, buildEnv())
                }
                throw e
            }
        }

        private fun buildEnv(): Map<String, String> = mutableMapOf<String, String>().apply {
            putAll(System.getenv())
            tempDockerConfigDir?.let { put("DOCKER_CONFIG", it.absolutePath) }
            put("DOCKER_API_VERSION", "1.44")
        }

        private fun fetchContainerLogs(projectName: String, env: Map<String, String>) {
            println("--- DOCKER LOGS ---")
            for (container in listOf("prep", "web", "meilisearch")) {
                println("Logs for $container:")
                try {
                    val process = ProcessBuilder("docker", "logs", "$projectName-$container-1")
                    process.environment().putAll(env)
                    process.redirectErrorStream(true)
                    println(process.start().inputStream.bufferedReader().readText())
                } catch (e: Exception) {
                    println("Could not fetch logs: ${e.message}")
                }
            }
        }

        private fun resolveHostWorkspacePath(): String {
            return try {
                val hostname = java.net.InetAddress.getLocalHost().hostName
                val process = ProcessBuilder("docker", "inspect", hostname).start()
                val output = process.inputStream.bufferedReader().readText()
                val regex = "\"Source\":\\s*\"([^\"]+)\",\\s*\"Target\":\\s*\"/workspaces/karakept-kmp\"".toRegex()
                regex.find(output)?.groupValues?.get(1) ?: "/home/lm/git/karakept-kmp"
            } catch (e: Exception) {
                "/home/lm/git/karakept-kmp"
            }
        }

        @JvmStatic
        @AfterClass
        fun tearDownDocker() {
            if (!isDockerRunning) {
                tempComposeFile?.delete()
                tempDockerConfigDir?.deleteRecursively()
                return
            }

            if (skipCleanupOnFailure) {
                println("Skipping Docker cleanup due to failure. Run 'docker-compose -p $projectName down' manually.")
                return
            }

            println("Stopping Karakeep backend...")
            try {
                val env = buildEnv()
                val process = ProcessBuilder("docker-compose", "-p", projectName, "-f", tempComposeFile?.absolutePath ?: "", "down")
                    .inheritIO()
                process.environment().putAll(env)
                process.start().waitFor(2, TimeUnit.MINUTES)
                tempComposeFile?.delete()
                tempDockerConfigDir?.deleteRecursively()
            } catch (e: Exception) {
                println("Failed to stop Docker containers: ${e.message}")
            }
        }
    }

    // Per-test state
    protected lateinit var db: AppDatabase
    protected lateinit var remoteDataSource: RemoteDataSource
    protected lateinit var bookmarkRepository: BookmarkRepository
    protected lateinit var highlightRepository: HighlightRepository
    protected lateinit var bookmarkActionsRepository: BookmarkActionsRepository
    protected lateinit var listRepository: ListRepository
    protected lateinit var client: HttpClient

    protected val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    protected val serverRepository = mockk<ServerRepository>(relaxed = true)
    protected val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)

    protected val testDispatcher = StandardTestDispatcher()
    protected val testScope = TestScope(testDispatcher)

    /** The test server configuration pointing to the Docker backend. */
    protected val testServer: Server
        get() = Server(
            id = "integration-test-server",
            url = baseUrl,
            apiKey = apiKey,
            label = "Integration Test Server"
        )

    // Integration tests exercise a real Docker backend on real threads.
    protected val appDispatchers = DefaultAppDispatchers()

    @Before
    fun baseSetup() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        client = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                    explicitNulls = false
                })
            }
        }

        remoteDataSource = RemoteDataSource(client)

        bookmarkActionsRepository = BookmarkActionsRepository(
            bookmarkDao = db.bookmarkDao(),
            pendingActionDao = db.pendingActionDao(),
            remoteDataSource = remoteDataSource,
            serverRepository = serverRepository,
            settingsRepository = settingsRepository,
            appDispatchers = appDispatchers
        )
        bookmarkActionsRepository.setHighlightDao(db.highlightDao())

        highlightRepository = HighlightRepository(
            highlightDao = db.highlightDao(),
            remoteDataSource = remoteDataSource,
            bookmarkActionsRepository = bookmarkActionsRepository
        )

        bookmarkRepository = BookmarkRepository(
            bookmarkDao = db.bookmarkDao(),
            assetDao = db.assetDao(),
            remoteDataSource = remoteDataSource,
            bookmarkActionsRepository = bookmarkActionsRepository,
            settingsRepository = settingsRepository,
            serverRepository = serverRepository,
            highlightRepository = highlightRepository,
            imageCacheManager = imageCacheManager,
            listDao = db.listDao(),
            appDispatchers = appDispatchers
        )
        bookmarkActionsRepository.setBookmarkRepository(bookmarkRepository)

        listRepository = ListRepository(
            remoteDataSource = remoteDataSource,
            listDao = db.listDao(),
            settingsRepository = settingsRepository,
            appDispatchers = appDispatchers
        )

        every { serverRepository.servers } returns flowOf(listOf(testServer))
        every { settingsRepository.contentSyncStrategy } returns flowOf(SyncStrategy.NEVER)
        every { settingsRepository.offlineMode } returns flowOf(false)

        setup()
    }

    /** Override to add test-specific setup after the base setup. */
    open fun setup() {}

    @After
    fun baseTearDown() {
        db.close()
        tearDown()
    }

    /** Override to add test-specific teardown. */
    open fun tearDown() {}

    /**
     * Insert a bookmark into the local database, simulating what a sync would do.
     * Returns the inserted entity for further use in tests.
     */
    protected suspend fun insertLocalBookmark(
        remoteId: String,
        url: String = "https://example.com/$remoteId",
        title: String = "Test Bookmark",
        isArchived: Boolean = false,
        isStarred: Boolean = false,
        isRead: Boolean = false,
        tags: String = "",
        listIds: String = ""
    ): com.karakept.app.data.local.entity.BookmarkEntity {
        val entity = com.karakept.app.data.local.entity.BookmarkEntity(
            localId = 0L,
            remoteId = remoteId,
            serverId = testServer.id,
            url = url,
            title = title,
            content = null,
            imageUrl = null,
            bannerImageAssetId = null,
            screenshotAssetId = null,
            description = null,
            createdAt = System.currentTimeMillis(),
            isArchived = isArchived,
            isStarred = isStarred,
            isRead = isRead,
            tags = tags,
            listIds = listIds
        )
        db.bookmarkDao().insertBookmark(entity)
        return db.bookmarkDao().getBookmarkByRemoteId(remoteId, testServer.id)
            ?: throw IllegalStateException("Failed to insert bookmark with remoteId=$remoteId")
    }
}

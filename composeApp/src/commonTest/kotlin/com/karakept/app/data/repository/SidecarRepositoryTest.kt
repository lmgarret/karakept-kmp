package com.karakept.app.data.repository

import com.karakept.app.data.model.SidecarCapabilities
import com.karakept.app.data.model.SidecarConfig
import com.karakept.app.data.model.sidecarKey
import com.karakept.app.data.remote.ApiException
import com.karakept.app.data.remote.SidecarDataSource
import com.karakept.app.data.secure.SecureCredentialStore
import com.karakept.app.domain.sidecar.SidecarKind
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [SidecarRepository].
 *
 * Three contracts matter here: configuration round-trips through DataStore, the bearer token goes
 * to the credential store and *not* into settings, and a failed capability probe never demotes a
 * sidecar that was known to work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SidecarRepositoryTest : BaseRepositoryTest() {

    private lateinit var dataStore: FakeDataStore
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var secureStore: SecureCredentialStore
    private lateinit var dataSource: SidecarDataSource
    private lateinit var repository: SidecarRepository

    private val serverId = "server-1"
    private val kind = SidecarKind.HTML_SCRUB
    private val config = SidecarConfig(
        serverId = serverId,
        kind = kind,
        baseUrl = "http://scrub:8080",
        enabled = true
    )

    @BeforeTest
    override fun setup() {
        super.setup()
        dataStore = FakeDataStore()
        settingsRepository = SettingsRepository(dataStore)
        secureStore = mockk(relaxed = true)
        dataSource = mockk(relaxed = true)
        repository = SidecarRepository(settingsRepository, secureStore, dataSource, testAppDispatchers)
    }

    @Test
    fun `configuration round-trips through settings`() = runTest(testDispatcher) {
        repository.save(config)

        assertEquals(config, repository.config(serverId, kind))
        assertEquals(config, repository.sidecars.first()[sidecarKey(serverId, kind)])
    }

    @Test
    fun `the token goes to the credential store and never into settings`() = runTest(testDispatcher) {
        val alias = slot<String>()
        val value = slot<String>()
        every { secureStore.storeApiKey(capture(alias), capture(value)) } returns Unit

        repository.save(config, token = "s3cret")

        verify(exactly = 1) { secureStore.storeApiKey(any(), "s3cret") }
        assertTrue(alias.captured.startsWith("sidecar:$serverId"))
        val stored = repository.sidecars.first()[sidecarKey(serverId, kind)]
        assertEquals("http://scrub:8080", stored?.baseUrl)
        // The whole point: nothing in the settings blob carries the secret.
        assertFalse(dataStore.data.first().asMap().values.any { it.toString().contains("s3cret") })
    }

    @Test
    fun `a null token leaves the stored one alone`() = runTest(testDispatcher) {
        repository.save(config, token = null)

        verify(exactly = 0) { secureStore.storeApiKey(any(), any()) }
        verify(exactly = 0) { secureStore.removeApiKey(any()) }
    }

    @Test
    fun `a blank token clears the stored one`() = runTest(testDispatcher) {
        repository.save(config, token = "")

        verify(exactly = 1) { secureStore.removeApiKey(any()) }
        verify(exactly = 0) { secureStore.storeApiKey(any(), any()) }
    }

    @Test
    fun `a successful probe makes the sidecar usable`() = runTest(testDispatcher) {
        coEvery { dataSource.fetchCapabilities(any(), any()) } returns
            SidecarCapabilities(service = "karhtmlscrub", llmEnabled = true)
        repository.save(config)

        repository.refreshCapabilities(serverId, kind)

        assertTrue(repository.isUsable(serverId, kind))
    }

    @Test
    fun `a sidecar without a model is configured but not usable`() = runTest(testDispatcher) {
        // karhtmlscrub answers /healthz happily with no model and then cleans nothing, so
        // reachability alone must not light up the action.
        coEvery { dataSource.fetchCapabilities(any(), any()) } returns
            SidecarCapabilities(service = "karhtmlscrub", llmEnabled = false)
        repository.save(config)

        repository.refreshCapabilities(serverId, kind)

        assertFalse(repository.isUsable(serverId, kind))
    }

    @Test
    fun `a failed probe leaves a known-good capability intact`() = runTest(testDispatcher) {
        coEvery { dataSource.fetchCapabilities(any(), any()) } returns
            SidecarCapabilities(llmEnabled = true)
        repository.save(config)
        repository.refreshCapabilities(serverId, kind)

        coEvery { dataSource.fetchCapabilities(any(), any()) } throws ApiException("boom")
        repository.refreshCapabilities(serverId, kind)

        // Demoting here would make the action vanish under the user mid-session.
        assertTrue(repository.isUsable(serverId, kind))
    }

    @Test
    fun `a disabled sidecar is never usable and is not probed`() = runTest(testDispatcher) {
        repository.save(config.copy(enabled = false))

        repository.refreshCapabilities(serverId, kind)

        assertFalse(repository.isUsable(serverId, kind))
        assertNull(repository.capabilitiesFor(serverId, kind))
    }

    @Test
    fun `an unconfigured server offers nothing`() = runTest(testDispatcher) {
        assertFalse(repository.isUsable("other-server", kind))
        assertFalse(repository.isUsable(null, kind))
    }

    @Test
    fun `removing a sidecar drops its config, token and capabilities`() = runTest(testDispatcher) {
        coEvery { dataSource.fetchCapabilities(any(), any()) } returns
            SidecarCapabilities(llmEnabled = true)
        repository.save(config, token = "s3cret")
        repository.refreshCapabilities(serverId, kind)

        repository.remove(serverId, kind)

        assertNull(repository.config(serverId, kind))
        assertNull(repository.capabilitiesFor(serverId, kind))
        verify { secureStore.removeApiKey(any()) }
    }
}

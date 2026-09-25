package com.karakept.app.ui.screens

import com.karakept.app.data.remote.ApiException
import com.karakept.app.data.remote.RemoteDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class OidcSignInScreenModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val remoteDataSource = mockk<RemoteDataSource>()
    private lateinit var screenModel: OidcSignInScreenModel

    private val server = "https://kk.example.com"
    private val session = listOf("__Secure-next-auth.session-token" to "jwt")

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        screenModel = OidcSignInScreenModel(remoteDataSource)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pageWithSessionCookie_createsKey() = runTest(testDispatcher) {
        coEvery { remoteDataSource.createApiKeyFromSession(server, any(), any()) } returns "ak1_key"

        screenModel.onPageLoaded(server, "$server/dashboard/bookmarks", session)
        runCurrent()

        assertEquals(OidcSignInState.Done("ak1_key"), screenModel.state.value)
        coVerify(exactly = 1) {
            remoteDataSource.createApiKeyFromSession(server, "__Secure-next-auth.session-token=jwt", any())
        }
    }

    @Test
    fun pageWithoutSession_keepsWaiting() = runTest(testDispatcher) {
        screenModel.onPageLoaded(server, "$server/signin", listOf("next-auth.csrf-token" to "x"))
        runCurrent()

        assertEquals(OidcSignInState.WaitingForLogin, screenModel.state.value)
        coVerify(exactly = 0) { remoteDataSource.createApiKeyFromSession(any(), any(), any()) }
    }

    @Test
    fun identityProviderPage_isIgnored() = runTest(testDispatcher) {
        screenModel.onPageLoaded(server, "https://auth.example.com/consent", session)
        runCurrent()

        assertEquals(OidcSignInState.WaitingForLogin, screenModel.state.value)
        coVerify(exactly = 0) { remoteDataSource.createApiKeyFromSession(any(), any(), any()) }
    }

    @Test
    fun staleSession_goesBackToWaiting() = runTest(testDispatcher) {
        coEvery { remoteDataSource.createApiKeyFromSession(any(), any(), any()) } throws
            ApiException("HTTP 401", statusCode = 401)

        screenModel.onPageLoaded(server, "$server/signin", session)
        runCurrent()

        assertEquals(OidcSignInState.WaitingForLogin, screenModel.state.value)
    }

    @Test
    fun otherFailure_isReported() = runTest(testDispatcher) {
        coEvery { remoteDataSource.createApiKeyFromSession(any(), any(), any()) } throws
            ApiException("HTTP 500: boom", statusCode = 500)

        screenModel.onPageLoaded(server, "$server/dashboard", session)
        runCurrent()

        val state = assertIs<OidcSignInState.Failed>(screenModel.state.value)
        assertEquals("HTTP 500: boom", state.message)

        screenModel.retry()
        assertEquals(OidcSignInState.WaitingForLogin, screenModel.state.value)
    }

    @Test
    fun repeatedPageLoads_createOnlyOneKey() = runTest(testDispatcher) {
        coEvery { remoteDataSource.createApiKeyFromSession(any(), any(), any()) } returns "ak1_key"

        screenModel.onPageLoaded(server, "$server/dashboard", session)
        screenModel.onPageLoaded(server, "$server/dashboard/bookmarks", session)
        runCurrent()
        screenModel.onPageLoaded(server, "$server/dashboard/lists", session)
        runCurrent()

        coVerify(exactly = 1) { remoteDataSource.createApiKeyFromSession(any(), any(), any()) }
    }
}

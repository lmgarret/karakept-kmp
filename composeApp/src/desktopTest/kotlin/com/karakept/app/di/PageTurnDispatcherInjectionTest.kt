package com.karakept.app.di

import com.karakept.app.data.repository.FakeDataStore
import com.karakept.app.data.repository.SettingsRepository
import com.karakept.app.ui.input.PageTurnDispatcher
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * `PageTurnDispatcher` must be registered as a `single`: the platform key-dispatch entry point and
 * every screen that scrolls resolve it independently, and a factory would hand each of them its
 * own event stream — pressing a button would then scroll nothing. Nothing else in the codebase
 * fails if that declaration regresses, so it is pinned here.
 *
 * Resolution goes through the real [appModule]. Only [SettingsRepository] is overridden, to keep
 * the test off the platform DataStore; everything on the path to the dispatcher is the production
 * declaration.
 */
class PageTurnDispatcherInjectionTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `page turn dispatcher resolves from appModule as a singleton`() {
        val koin = startKoin {
            modules(
                appModule,
                module { single { SettingsRepository(FakeDataStore()) } }
            )
        }.koin

        assertSame(
            koin.get<PageTurnDispatcher>(),
            koin.get<PageTurnDispatcher>(),
            "PageTurnDispatcher must be a single — separate instances would not share key events"
        )
    }
}

package com.karakept.app.data.repository

import com.karakept.app.utils.TestAppDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BaseRepositoryTest {
    protected val testDispatcher = StandardTestDispatcher()
    protected val testScope = TestScope(testDispatcher)
    protected val testAppDispatchers = TestAppDispatchers(testDispatcher)

    @BeforeTest
    open fun setup() {
        // Common setup logic for all repository tests
    }

    @AfterTest
    open fun tearDown() {
        // Common teardown logic
    }
}

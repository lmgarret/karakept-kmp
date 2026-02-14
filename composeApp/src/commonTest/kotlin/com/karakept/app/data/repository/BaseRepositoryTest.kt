package com.karakept.app.data.repository

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BaseRepositoryTest {
    protected val testDispatcher = StandardTestDispatcher()
    protected val testScope = TestScope(testDispatcher)

    @BeforeTest
    open fun setup() {
        // Common setup logic for all repository tests
    }

    @AfterTest
    open fun tearDown() {
        // Common teardown logic
    }
}

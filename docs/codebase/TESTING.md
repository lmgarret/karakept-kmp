# Testing Patterns

**Analysis Date:** 2026-03-20

## Test Framework

**Runner:**
- Kotlin Test framework (stdlib `kotlin.test`)
- JUnit (for desktop/integration tests)
- MockK for mocking

**Assertion Library:**
- Kotlin stdlib assertions: `kotlin.test.assertEquals`, `kotlin.test.assertTrue`, `kotlin.test.assertNull`, `kotlin.test.assertNotNull`

**Run Commands:**
```bash
./gradlew desktopTest           # Desktop (JVM) suite — commonTest + desktopTest
./gradlew :composeApp:testAndroidHostTest   # Android (Robolectric) suite
./gradlew test                  # Everything (alias for allTests)
./gradlew -t test               # Watch mode (continuous testing)
```

## Test File Organization

**Location:**
- Common unit tests: `composeApp/src/commonTest/kotlin/com/karakept/app/`
- Desktop-specific integration tests: `composeApp/src/desktopTest/kotlin/com/karakept/app/data/integration/`
- Tests are co-located with source by feature area (not separate test directory structure)
- Test package matches source package exactly

**Naming:**
- Unit tests suffixed with `UnitTest`: `BookmarkRepositoryUnitTest.kt`, `ListHierarchyUtilsTest.kt`
- Integration tests suffixed with `IntegrationTest`: `BaseDockerIntegrationTest.kt`, `BookmarkSyncIntegrationTest.kt`
- Serialization tests suffixed with `SerializationTest`: `BackupSettingsSerializationTest.kt`, `StoredSettingsSerializationTest.kt`
- Utility tests named after the utility + `Test`: `BookmarkFilterUtilsTest.kt`, `DefaultFilterResolverTest.kt`

**Structure:**
```
composeApp/src/commonTest/
├── kotlin/com/karakept/app/
│   ├── data/
│   │   ├── model/
│   │   │   └── BackupSettingsSerializationTest.kt
│   │   └── repository/
│   │       ├── BaseRepositoryTest.kt              # Base class for all repository tests
│   │       ├── BookmarkRepositoryUnitTest.kt
│   │       ├── BookmarkActionsRepositoryUnitTest.kt
│   │       └── BackupRepositoryTest.kt
│   ├── domain/
│   │   ├── BookmarkFilterUtilsTest.kt
│   │   ├── DefaultFilterResolverTest.kt
│   │   └── ListHierarchyUtilsTest.kt
│   └── ui/screens/
│       └── PaginationUtilsTest.kt

composeApp/src/desktopTest/
├── kotlin/com/karakept/app/data/integration/
│   ├── BaseDockerIntegrationTest.kt              # Base class for Docker tests
│   ├── ApiClientIntegrationTest.kt
│   ├── BookmarkMutationIntegrationTest.kt
│   ├── BookmarkSyncIntegrationTest.kt
│   ├── HighlightRepositoryIntegrationTest.kt
│   └── ListRepositoryIntegrationTest.kt
```

## Test Structure

**Suite Organization:**
```kotlin
class BookmarkRepositoryUnitTest : BaseRepositoryTest() {
    // 1. Setup: mock dependencies
    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val assetDao = mockk<AssetDao>(relaxed = true)
    private val remoteDataSource = mockk<RemoteDataSource>(relaxed = true)

    // 2. SUT (System Under Test) initialization
    private val repository = BookmarkRepository(
        bookmarkDao,
        assetDao,
        remoteDataSource,
        // ... other dependencies
    )

    // 3. Test methods
    @Test
    fun testCreateBookmark_Success() = runTest(testDispatcher) {
        // Arrange
        val testServer = Server("1", "http://localhost", "key", "Label")
        coEvery { serverRepository.servers } returns flowOf(listOf(testServer))

        // Act
        val result = repository.createBookmark(testUrl)

        // Assert
        assertTrue(result.isSuccess)
        assertEquals("Mock Title", result.getOrNull()?.title)
    }
}
```

**Patterns:**
- Setup/Arrange: Create mocks and test data
- Execution/Act: Call the function under test
- Verification/Assert: Check the results using kotlin.test assertions
- Tests inherit from `BaseRepositoryTest` for common setup (testDispatcher, testScope)

## Mocking

**Framework:** MockK

**Patterns:**
```kotlin
// Basic mock creation with relaxed mode (returns default values for unmocked calls)
private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)

// Setting expectations on regular functions
every { urlValidator.isValid(any()) } returns true

// Setting expectations on suspend functions
coEvery { remoteDataSource.createBookmark(any(), any()) } returns mockDto
coEvery { remoteDataSource.fetchBookmark(any(), any()) } returns mockDto

// Mocking flows
every { database.bookmarkDao() } returns bookmarkDao
coEvery { serverRepository.servers } returns flowOf(listOf(testServer))

// Creating mock objects with specific properties
mockk<KarakeepList>(relaxed = true) {
    every { this@mockk.id } returns id
    every { this@mockk.parentId } returns parentId
}
```

**What to Mock:**
- External dependencies: DAOs, remote data sources, repositories, utilities
- Services and clients: API clients, file system access
- Flows and StateFlows returned by other repositories
- Time-dependent operations (not used; no clock mocking observed)

**What NOT to Mock:**
- Data models and entities: create real instances for testing
- Pure utility functions: call them directly (see ListHierarchyUtilsTest)
- Sealed classes and data classes: instantiate directly
- Business logic being tested: call the actual implementation

## Fixtures and Factories

**Test Data:**
```kotlin
// Simple test object creation
private fun makeList(id: String, parentId: String? = null): KarakeepList =
    mockk<KarakeepList>(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.parentId } returns parentId
    }

// Used in tests
val lists = listOf(
    makeList("parent"),
    makeList("child", parentId = "parent")
)
```

**Location:**
- Test fixtures defined as private helper functions within test classes
- No separate fixture/factory files; helpers are inline within tests
- Factories use builder pattern (e.g., `makeList()`) for simplicity

## Coverage

**Requirements:** No coverage requirements enforced; coverage is not tracked in gradle configuration

**View Coverage:** No coverage reporting configured

## Test Types

**Unit Tests:**
- Scope: Test individual repository methods, utilities, and domain logic in isolation
- Approach: Mock all dependencies; test business logic without I/O
- Located: `composeApp/src/commonTest/kotlin/`
- Examples: `BookmarkRepositoryUnitTest.kt`, `ListHierarchyUtilsTest.kt`, `BookmarkFilterUtilsTest.kt`
- Run on all platforms (commonTest)

**Integration Tests:**
- Scope: Test real database (Room in-memory), real HTTP client, multiple layers working together
- Approach: Start Docker Compose with real backend, use real room database, no mocking of data layer
- Located: `composeApp/src/desktopTest/kotlin/com/karakept/app/data/integration/`
- Docker management: BaseDockerIntegrationTest handles Docker Compose lifecycle
- Run on desktop only (desktopTest) due to Docker dependency
- Examples: `BookmarkSyncIntegrationTest.kt`, `ApiClientIntegrationTest.kt`, `ListRepositoryIntegrationTest.kt`

**E2E Tests:**
- Not present in codebase
- No Espresso, Robolectric, or Compose test framework configurations

## Common Patterns

**Async Testing:**
```kotlin
// Using coroutine test scope
@Test
fun testCreateBookmark_Success() = runTest(testDispatcher) {
    val result = repository.createBookmark(testUrl)
    assertTrue(result.isSuccess)
}

// Integration tests also use runTest
@Test
fun testSyncBookmarks_WithDocker() = runTest {
    // Docker is already running (set up in companion object)
    val result = repository.syncBookmarks(server)
    assertTrue(result > 0)
}
```

**Error Testing:**
```kotlin
// Testing failure paths with exceptions
@Test
fun testCreateBookmark_Failure() = runTest(testDispatcher) {
    coEvery { remoteDataSource.createBookmark(any(), any()) } throws Exception("Network Error")

    val result = repository.createBookmark(testUrl)

    assertTrue(result.isFailure)
    assertEquals("Network Error", result.exceptionOrNull()?.message)
}

// Verifying exceptions in thrown operations
@Test
fun testSyncHandlesException() = runTest {
    coEvery { remoteApi.fetchBookmarks() } throws IOException("Connection failed")

    val ex = assertFailsWith<IOException> {
        repository.syncBookmarks(server)
    }
    assertEquals("Connection failed", ex.message)
}
```

**Docker Integration Tests:**
```kotlin
// Base class manages Docker lifecycle
abstract class BaseDockerIntegrationTest {
    companion object {
        // Docker containers started before any tests run
        var isDockerRunning = false
        var baseUrl: String = ""
        var apiKey: String = ""

        @BeforeClass
        fun setupDocker() {
            // Start docker-compose.dev.yml
            // Wait for backend readiness
            // Create test user account
        }

        @AfterClass
        fun teardownDocker() {
            // Stop and clean up containers
        }
    }

    // Tests inherit full setup
    @Test
    fun testRealApiCall() = runTest {
        // baseUrl and apiKey are already configured
        val result = apiClient.getBookmarks(baseUrl, apiKey)
        assertTrue(result.isNotEmpty())
    }
}
```

**Serialization Testing:**
```kotlin
// Testing JSON serialization round-trips
@Test
fun settingsSerializeAndDeserialize() {
    val original = BackupSettings(/* ... */)

    val json = Json.encodeToString(original)
    val deserialized = Json.decodeFromString<BackupSettings>(json)

    assertEquals(original, deserialized)
}
```

## Test Inheritance Hierarchy

**BaseRepositoryTest** (`composeApp/src/commonTest/kotlin/com/karakept/app/data/repository/BaseRepositoryTest.kt`)
- Provides `testDispatcher` (StandardTestDispatcher for controlled execution)
- Provides `testScope` (TestScope for running suspend functions)
- Defines `@BeforeTest` and `@AfterTest` hooks for setup/teardown
- All repository unit tests inherit from this class

**BaseDockerIntegrationTest** (`composeApp/src/desktopTest/kotlin/com/karakept/app/data/integration/BaseDockerIntegrationTest.kt`)
- Manages Docker Compose lifecycle for backend
- Provides static configuration: `baseUrl`, `apiKey`
- Detects docker-compose.dev.yml location (handles multiple possible paths)
- Starts Docker only once per test suite run (static setup)
- Provides HTTP client helpers for API testing
- Handles user signup and token generation for auth

## Running Tests

**All tests:**
```bash
./gradlew test
```

**Specific test class:**
```bash
./gradlew :composeApp:desktopTest --tests BookmarkRepositoryUnitTest
./gradlew :composeApp:desktopTest --tests BookmarkSyncIntegrationTest
```

**Watch mode (re-run on changes):**
```bash
./gradlew -t test
```

**With verbose output:**
```bash
./gradlew test --info
```

---

*Testing analysis: 2026-03-20*

# Karakeep API Client Documentation

This document describes the auto-generated API client and how it is integrated into the Karakeep KMP application.

## Overview

The API client is housed in a separate Kotlin Multiplatform module called `:api-client`. It is auto-generated from the Karakeep OpenAPI specification using the `openapi-generator-gradle-plugin`.

### Key Components

- **Generated Sources**: Located in `api-client/build/generated/openapi`.
- **API Interfaces**: Found in `com.karakept.api.client` (e.g., `BookmarksApi`, `ListsApi`, `TagsApi`, `HighlightsApi`, `UsersApi`).
- **Models**: Found in `com.karakept.api.model` (e.g., `Bookmark`, `KarakeepList`, `PaginatedBookmarks`, `BookmarkContent`).
- **Infrastructure**: Found in `com.karakept.api.infrastructure` (e.g., `ApiClient`).

## Integration Pattern

The application uses the auto-generated API client while maintaining `RemoteDataSource` as a high-level abstraction that handles server-specific configuration (URLs and authentication).

### 1. Dependency Injection (Koin)

The shared `HttpClient` is provided as a singleton in `AppModule.kt`:

```kotlin
single {
    HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
}

single { RemoteDataSource(get()) }
```

### 2. RemoteDataSource: Server-Aware API Adapter

The `RemoteDataSource` creates API instances dynamically for each server, configuring the base URL and authentication:

```kotlin
class RemoteDataSource(
    private val client: HttpClient
) {
    private fun getBaseUrl(server: Server): String {
        val base = if (server.url.endsWith("/")) server.url.removeSuffix("/") else server.url
        return if (base.endsWith("/api/v1")) base else "$base/api/v1"
    }

    private fun bookmarksApi(server: Server) = BookmarksApi(getBaseUrl(server), client).apply {
        setBearerToken(server.apiKey)
    }

    suspend fun fetchBookmarks(
        server: Server,
        cursor: String? = null,
        limit: Int = 50,
        includeContent: Boolean = false,
        archived: Boolean? = null,
        favourited: Boolean? = null
    ): PaginatedBookmarks {
        return bookmarksApi(server).bookmarksGet(
            cursor = cursor,
            limit = limit.toDouble(),
            includeContent = includeContent,
            archived = archived,
            favourited = favourited
        ).body()
    }
}
```

**Key Pattern**: Each method creates a fresh API instance with server-specific configuration, ensuring correct URL and authentication for multi-server support.

### 3. Model Usage Throughout Application

Generated models from `com.karakept.api.model` are used directly throughout the application:

- **Bookmark**: Main bookmark model with content, tags, assets
- **PaginatedBookmarks**: Paginated response with bookmarks list and cursor
- **KarakeepList**: List/collection model (aliased to avoid conflict with Kotlin's `List`)
- **BookmarkContent**: Bookmark content with type discrimination (link, text, asset)
- **BookmarksBookmarkIdAssetsPost201Response**: Asset metadata with type enum

#### Type Aliasing for Name Conflicts

```kotlin
import com.karakept.api.model.KarakeepList
```

The generator creates `KarakeepList` as the class name (avoiding conflict with Kotlin's built-in `List`).

### 4. Custom Template for Lenient Parsing

To handle API inconsistencies (missing fields, oneOf flattening), the client uses a custom Mustache template that makes all fields nullable:

**`api-client/templates/data_class_req_var.mustache`**:
```mustache
{{#isContainer}}
@SerialName(value = "{{baseName}}") {{#isReadOnly}}val{{/isReadOnly}}{{^isReadOnly}}var{{/isReadOnly}} {{name}}: {{{dataType}}}? = null{{^-last}},{{/-last}}
{{/isContainer}}
{{^isContainer}}
@SerialName(value = "{{baseName}}") {{#isReadOnly}}val{{/isReadOnly}}{{^isReadOnly}}var{{/isReadOnly}} {{name}}: {{{dataType}}}? = null{{^-last}},{{/-last}}
{{/isContainer}}
```

This ensures `kotlinx.serialization` can parse responses even when "required" fields are missing.

### 5. Null Safety Patterns

Since all generated fields are nullable, the application uses consistent null-safety patterns:

#### Nullable Collections
```kotlin
dto.tags?.joinToString(",") { it.name ?: "" } ?: ""
dto.assets?.find { it.assetType == AssetType.BANNER_IMAGE }?.id
response.bookmarks ?: emptyList()
```

#### Nullable Primitives
```kotlin
dto.id ?: ""
dto.archived ?: false
dto.favourited ?: false
```

#### Enum Comparisons
```kotlin
// Use fully qualified enum values
it.assetType == BookmarksBookmarkIdAssetsPost201Response.AssetType.LINK_HTML_CONTENT
dto.content?.type == BookmarkContent.Type.LINK
```

#### UI Component Null Handling
```kotlin
// Lists
list.id ?: ""
list.name ?: ""
list.icon ?: ""

// Tags (filtering nulls)
list.tags?.filterNotNull() ?: emptyList()
```

## Regeneration

To regenerate the client after updating the OpenAPI spec:

1. Update the `karakeep-upstream` submodule or place updated spec at `karakeep-upstream/apps/web/public/openapi.json`
2. Run `./gradlew :api-client:openApiGenerate`
3. The custom template in `api-client/templates/` will be automatically applied
4. Generated sources appear in `api-client/build/generated/openapi`

The build process is configured to automatically include generated sources in compilation.

## Best Practices

### Model Usage
- **Use Generated Models Directly**: Import from `com.karakept.api.model` (e.g., `Bookmark`, `PaginatedBookmarks`)
- **No Manual DTOs**: Avoid creating wrapper DTOs; use generated models throughout the application
- **Handle Nullability**: All fields are nullable by design; use Elvis operators and safe calls consistently

### Error Handling
- The generated client throws exceptions for non-2xx responses
- Wrap API calls in try-catch blocks in `RemoteDataSource`
- Use custom `ApiException` for domain-specific error messages

```kotlin
suspend fun fetchBookmark(server: Server, bookmarkId: String): Bookmark {
    return try {
        bookmarksApi(server).bookmarksBookmarkIdGet(bookmarkId, includeContent = true).body()
    } catch (e: Exception) {
        throw ApiException("Error fetching bookmark: ${e.message}", e)
    }
}
```

### Multi-Server Support
- **Never cache API instances**: Create fresh instances per request with server-specific config
- **Configure per request**: Set base URL and bearer token for each API call
- **Use method-scoped factory functions**: `private fun bookmarksApi(server: Server)` pattern

### Type Safety
- **Fully qualify enums** when ambiguous: `BookmarkContent.Type.LINK` → `com.karakept.api.model.BookmarkContent.Type.LINK`
- **Check enum types correctly**: Use enum values, not string comparisons
- **Preserve type information**: Don't convert to strings prematurely; work with typed models

## Common Patterns

### Fetching Paginated Data
```kotlin
suspend fun fetchAllBookmarks(server: Server): List<Bookmark> {
    val allBookmarks = mutableListOf<Bookmark>()
    var cursor: String? = null

    do {
        val response = remoteDataSource.fetchBookmarks(
            server = server,
            cursor = cursor,
            limit = 50
        )
        allBookmarks.addAll(response.bookmarks ?: emptyList())
        cursor = response.nextCursor
    } while (cursor != null)

    return allBookmarks
}
```

### Working with Bookmark Content
```kotlin
// Content type discrimination
val url = when (dto.content?.type) {
    BookmarkContent.Type.LINK -> dto.content?.url ?: ""
    BookmarkContent.Type.TEXT -> ""
    else -> dto.content?.url ?: ""
}

// Extracting assets by type
val bannerImageAssetId = dto.assets
    ?.find { it.assetType == BookmarksBookmarkIdAssetsPost201Response.AssetType.BANNER_IMAGE }
    ?.id
```

### Repository Layer Mapping
```kotlin
// Map generated model to entity
BookmarkEntity(
    localId = 0L,
    remoteId = dto.id ?: "",
    serverId = server.id,
    title = dto.title ?: dto.content?.title ?: "Untitled",
    url = dto.content?.url ?: "",
    tags = dto.tags?.joinToString(",") { it.name ?: "" } ?: "",
    isStarred = dto.favourited ?: false,
    isArchived = dto.archived ?: false,
    createdAt = Instant.parse(dto.createdAt ?: "").toEpochMilliseconds()
)
```

## Troubleshooting

### Build Errors After Regeneration
- Ensure all DTO references use `com.karakept.api.model.*` imports
- Check for proper null-safety operators (`?.`, `?:`, `!!`)
- Verify enum comparisons use enum values, not strings

### Missing Fields at Runtime
- The lenient template makes all fields nullable to handle API inconsistencies
- Always provide defaults with Elvis operators
- Log unexpected nulls for API debugging

### Type Conflicts
- Use type aliases for conflicting names (e.g., `KarakeepList`)
- Fully qualify enum types when necessary
- Import specific types to avoid wildcard conflicts

## Implementing New API Features

This section provides a step-by-step guide for implementing new API features (like highlights, bookmarks, tags, etc.) using the OpenAPI client.

### Architecture Overview

The app uses an **offline-first architecture** with the following layers:

```
UI Layer (Compose)
    ↓
ScreenModel (ViewModel)
    ↓
Repository (business logic + offline queue)
    ↓
├── Local: Room DAO (SQLite)
└── Remote: RemoteDataSource → OpenAPI Client
```

### Step 1: Check Generated API Classes

First, verify the OpenAPI client has generated the necessary API class and models:

```
api-client/build/generated/openapi/src/main/kotlin/com/karakept/api/
├── client/
│   ├── BookmarksApi.kt
│   ├── HighlightsApi.kt  ← API class with methods
│   ├── ListsApi.kt
│   └── TagsApi.kt
└── model/
    ├── Highlight.kt       ← Response model
    ├── HighlightsPostRequest.kt  ← Request body for POST
    ├── HighlightsHighlightIdPatchRequest.kt  ← Request body for PATCH
    └── PaginatedHighlights.kt  ← Paginated response
```

If the API class doesn't exist, regenerate: `./gradlew :api-client:openApiGenerate`

### Step 2: Add Factory Method in RemoteDataSource

Add a private factory method that creates the API instance with server-specific config:

```kotlin
// In RemoteDataSource.kt
private fun highlightsApi(server: Server) = HighlightsApi(getBaseUrl(server), client).apply {
    setBearerToken(server.apiKey)
}
```

### Step 3: Implement Remote Methods

Add methods in `RemoteDataSource` that wrap the generated API calls:

```kotlin
suspend fun fetchAllHighlights(server: Server): List<Highlight> {
    return try {
        val response = highlightsApi(server).highlightsGet(limit = 1000.0, cursor = null)
        response.body().highlights ?: emptyList()
    } catch (e: Exception) {
        throw ApiException("Error fetching highlights: ${e.message}", e)
    }
}

suspend fun createHighlight(
    server: Server,
    bookmarkId: String,
    text: String,
    startOffset: Int,
    endOffset: Int,
    note: String? = null,
    color: String? = null
): Highlight {
    return try {
        // Convert string color to enum
        val colorEnum = when (color?.lowercase()) {
            "red" -> HighlightsPostRequest.Color.RED
            "green" -> HighlightsPostRequest.Color.GREEN
            "blue" -> HighlightsPostRequest.Color.BLUE
            else -> HighlightsPostRequest.Color.YELLOW
        }

        val request = HighlightsPostRequest(
            bookmarkId = bookmarkId,
            text = text,
            startOffset = startOffset.toDouble(),  // API uses Double
            endOffset = endOffset.toDouble(),
            note = note ?: "",
            color = colorEnum
        )

        highlightsApi(server).highlightsPost(request).body()
    } catch (e: Exception) {
        throw ApiException("Error creating highlight: ${e.message}", e)
    }
}
```

**Key Points:**
- All API calls should be wrapped in try-catch
- Convert types as needed (Int → Double, String → Enum)
- Handle nullable response fields with `?: emptyList()` or `?: ""`

### Step 4: Configure JSON Serialization

Ensure `KtorClient.kt` has proper JSON configuration:

```kotlin
json(Json {
    ignoreUnknownKeys = true    // Ignore unknown fields from server
    prettyPrint = true
    isLenient = true            // Allow lenient parsing
    explicitNulls = true        // Include null fields in output
    encodeDefaults = true       // CRITICAL: Include fields with default values
})
```

**Important:** `encodeDefaults = true` is required when the generated request models have default values (like `color = Color.YELLOW`). Without this, fields with defaults won't be serialized.

### Step 5: Implement Offline-First Queue (Optional)

For write operations (create, update, delete), use the pending actions queue:

```kotlin
// In PendingActionEntity.kt, add action types
object PendingActionType {
    const val CREATE_HIGHLIGHT = "create_highlight"
    const val UPDATE_HIGHLIGHT = "update_highlight"
    const val DELETE_HIGHLIGHT = "delete_highlight"
}
```

```kotlin
// In BookmarkActionsRepository.kt
suspend fun queueCreateHighlight(
    server: Server,
    bookmarkLocalId: Long,
    bookmarkRemoteId: String,
    text: String,
    startOffset: Int,
    endOffset: Int,
    note: String?,
    color: String?,
    tempId: String
) {
    withContext(Dispatchers.IO) {
        queueAction(
            bookmarkRemoteId = bookmarkLocalId,
            serverId = server.id,
            actionType = PendingActionType.CREATE_HIGHLIGHT,
            actionData = json.encodeToString(mapOf(
                "bookmarkRemoteId" to bookmarkRemoteId,
                "text" to text,
                "startOffset" to startOffset.toString(),
                "endOffset" to endOffset.toString(),
                "note" to (note ?: ""),
                "color" to (color ?: "yellow"),
                "tempId" to tempId
            ))
        )
        triggerAutoSync(server.id)
    }
}
```

Then handle the action in `processAction()`:

```kotlin
PendingActionType.CREATE_HIGHLIGHT -> {
    val data = json.decodeFromString<Map<String, String>>(action.actionData)
    val result = remoteDataSource.createHighlight(
        server = server,
        bookmarkId = data["bookmarkRemoteId"] ?: return,
        text = data["text"] ?: return,
        startOffset = data["startOffset"]?.toIntOrNull() ?: return,
        endOffset = data["endOffset"]?.toIntOrNull() ?: return,
        note = data["note"]?.takeIf { it.isNotBlank() },
        color = data["color"]
    )
    // Update temp ID with server-assigned ID if needed
}
```

**Important for DELETE/UPDATE actions:** Some actions don't need bookmark lookup. Add them to the skip list:

```kotlin
val highlightActions = listOf(
    PendingActionType.DELETE_HIGHLIGHT,
    PendingActionType.UPDATE_HIGHLIGHT
)
if (action.actionType in highlightActions) {
    // Skip bookmark lookup, process directly
}
```

### Step 6: Map API Models to Local Entities

In the Repository layer, map between API models and Room entities:

```kotlin
// In HighlightRepository.kt
val entities = remoteHighlights.map { highlight ->
    HighlightEntity(
        remoteId = highlight.id ?: "",                           // Nullable String
        serverId = server.id,
        bookmarkRemoteId = highlight.bookmarkId ?: "",
        text = highlight.text ?: "",
        startOffset = highlight.startOffset?.toInt() ?: 0,       // Double → Int
        endOffset = highlight.endOffset?.toInt() ?: 0,
        note = highlight.note,
        color = highlight.color?.value,                          // Enum → String
        createdAt = try {
            Instant.parse(highlight.createdAt ?: "").toEpochMilliseconds()
        } catch (e: Exception) { 0L }
    )
}
```

**Type Conversions:**
- `Double?` → `Int`: Use `?.toInt() ?: 0`
- `Enum?` → `String?`: Use `.value` property (e.g., `Color.YELLOW.value` = "yellow")
- `String?` (date) → `Long`: Parse with `Instant.parse().toEpochMilliseconds()`

### Step 7: Delete Old Custom DTOs

After migrating to OpenAPI client, delete any manual DTO files:

```
composeApp/src/commonMain/kotlin/.../data/remote/model/
├── HighlightDto.kt          ← DELETE
├── CreateHighlightDto.kt    ← DELETE
└── HighlightsResponse.kt    ← DELETE
```

### Common Pitfalls

#### 1. Missing Request Body Fields
**Problem:** Server receives empty or partial request body.
**Solution:** Add `encodeDefaults = true` to JSON config.

#### 2. Type Mismatches
**Problem:** API uses `Double` but app uses `Int`.
**Solution:** Convert explicitly: `startOffset.toDouble()` and `?.toInt()`

#### 3. Enum Handling
**Problem:** API returns/expects enum values like "yellow", "red".
**Solution:**
- Request: Convert string to enum: `HighlightsPostRequest.Color.YELLOW`
- Response: Extract string value: `highlight.color?.value`

#### 4. Offline Queue Action Not Processing
**Problem:** Actions silently fail without reaching the API.
**Solution:** Check if the action type needs to bypass bookmark lookup in `processAction()`.

#### 5. Nullable Fields Causing Crashes
**Problem:** `NullPointerException` when accessing API response fields.
**Solution:** All generated fields are nullable. Always use `?.` and `?:`.

### Testing Checklist

When implementing a new API feature, verify:

- [ ] GET endpoint returns data correctly
- [ ] POST endpoint creates resource on server
- [ ] PATCH endpoint updates resource on server
- [ ] DELETE endpoint removes resource from server
- [ ] Offline queue stores actions when offline
- [ ] Actions sync successfully when back online
- [ ] Temp IDs are replaced with server IDs after creation
- [ ] Local database is updated optimistically
- [ ] UI reflects changes immediately (optimistic updates)
- [ ] Error handling shows appropriate messages

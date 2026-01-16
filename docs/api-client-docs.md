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
    remoteId = (dto.id ?: "").hashCode().toLong(),
    originalRemoteId = dto.id ?: "",
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

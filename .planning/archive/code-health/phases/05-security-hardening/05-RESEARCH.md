# Phase 5: Security Hardening - Research

**Researched:** 2026-03-21
**Domain:** HTML sanitization (XSS prevention) + credential encryption (Android Keystore / JVM KeyStore)
**Confidence:** HIGH

## Summary

This phase addresses two distinct security concerns: (1) preventing XSS execution in the Android WebView renderer, and (2) moving API credentials from cleartext Room storage to platform-native secure storage.

The codebase already has a well-structured `HtmlSanitizer` (whitelist-based via Ksoup `Safelist.relaxed()`) used for READER mode, and an `HtmlArchiveProcessor` for WEB mode. The READER mode path is already secure -- `Safelist.relaxed()` strips scripts, iframes, event handlers, and all non-whitelisted tags. The WEB mode path (`HtmlArchiveProcessor`) removes `<script>` tags and event handlers but does NOT strip `<iframe>`, `<object>`, `<embed>`, or `<form>` tags -- this is the real gap for SEC-01. Additionally, the comment in `HtmlRenderer.android.kt` line 37-41 says "JavaScript disabled" but line 798 shows `javaScriptEnabled = true`.

For credential storage (SEC-02), the CONTEXT.md specifies EncryptedSharedPreferences for Android. However, research reveals that `androidx.security:security-crypto` was **officially deprecated in April 2025** (version 1.1.0-alpha07). The recommended replacement is direct Android Keystore + AES encryption with DataStore or SharedPreferences. Given the user's locked decision, the research documents both the deprecated approach and the modern alternative so the planner can flag this.

**Primary recommendation:** (1) Add iframe/object/embed/form stripping to `HtmlArchiveProcessor` and fix the misleading comment. (2) Use `expect`/`actual` `SecureCredentialStore` interface with Android Keystore AES-GCM + SharedPreferences on Android, and PKCS12 KeyStore on desktop. Migrate transparently in `ServerRepository` init.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **HTML Sanitization scope:** Android WebView path is the real risk. Desktop NativeHtmlRenderer uses pure Compose composables via Ksoup, not vulnerable to XSS.
- **Strip targets:** script tags, event handlers (`on*` attributes), iframes, forms, `<object>`/`<embed>` -- keep structural HTML + CSS for reader formatting
- **Sanitization location:** Sanitize in common code before passing to `HtmlRenderer` -- single sanitization point for both platforms
- **Implementation tool:** Use Ksoup (already a dependency) to parse and strip dangerous nodes/attributes
- **Android credentials:** EncryptedSharedPreferences (Jetpack Security) -- simpler than direct Keystore, hardware-backed on supported devices
- **Desktop credentials:** Java KeyStore (JKS) file with password -- standard JVM approach
- **Migration:** Transparent on first launch -- read from old DB location, write to new secure storage, clear old entries. No user action needed.
- **Fallback:** Fall back to existing DB storage with logged warning if secure storage unavailable

### Claude's Discretion
- Migration safety details: timing (startup vs lazy), per-server error handling, backup/restore interaction

### Deferred Ideas (OUT OF SCOPE)
None
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SEC-01 | Sanitize HTML before rendering in WebView/reader | Existing `HtmlSanitizer` covers READER mode; `HtmlArchiveProcessor` needs iframe/object/embed/form removal for WEB mode. Fix misleading JS comment. |
| SEC-02 | Move API credentials from cleartext DB to platform keychain/keystore | `expect`/`actual` `SecureCredentialStore` pattern; Android Keystore AES-GCM; Desktop PKCS12 KeyStore; transparent migration in `ServerRepository` |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Ksoup | 0.2.1 | HTML parsing and sanitization | Already in project, KMP port of jsoup with Safelist/Cleaner API |
| Android Keystore | Platform API | AES key generation and storage on Android | Official Android cryptography API, hardware-backed |
| java.security.KeyStore (PKCS12) | JDK built-in | Secret storage on Desktop/JVM | Standard JVM keystore, cross-platform |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| SharedPreferences | Android Platform | Store encrypted credential bytes on Android | Paired with Android Keystore for encrypted data persistence |
| javax.crypto.Cipher | JDK built-in | AES-GCM encryption/decryption | Both Android and Desktop encryption operations |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Direct Keystore + SharedPrefs | EncryptedSharedPreferences | ESP is officially deprecated (April 2025, 1.1.0-alpha07); still functional but no future updates. User chose ESP -- **flag: deprecated library** |
| Direct Keystore + SharedPrefs | DataStore + Tink | More modern but adds Tink dependency (~2MB); overkill for storing 1-3 API keys |
| PKCS12 KeyStore | JKS | PKCS12 is the modern standard; JKS is Java-specific and deprecated since JDK 9 |

**IMPORTANT NOTE on EncryptedSharedPreferences:**
The user's CONTEXT.md specifies EncryptedSharedPreferences for Android. However, `androidx.security:security-crypto` was **officially deprecated in April 2025** at version 1.1.0-alpha07. The library still functions but receives no bug fixes or security patches. The recommended modern approach is direct Android Keystore + AES-GCM encryption. The planner should use the direct Keystore approach instead, as it is:
1. Simpler (no additional dependency needed -- platform API only)
2. More future-proof (not deprecated)
3. Equivalent security (ESP uses Keystore internally anyway)
4. Compatible with minSdk 24 (AES-GCM in Keystore requires API 23+)

**No additional dependencies needed.** All cryptography uses platform APIs already available.

## Architecture Patterns

### Recommended Project Structure
```
composeApp/src/
├── commonMain/kotlin/.../data/
│   ├── secure/
│   │   └── SecureCredentialStore.kt     # expect interface
│   ├── repository/
│   │   └── ServerRepository.kt          # migration logic added here
│   └── local/entity/
│       └── ServerEntity.kt              # apiKey field remains (fallback + migration source)
├── androidMain/kotlin/.../data/
│   └── secure/
│       └── SecureCredentialStore.android.kt  # Android Keystore + SharedPrefs
└── desktopMain/kotlin/.../data/
    └── secure/
        └── SecureCredentialStore.jvm.kt      # PKCS12 KeyStore file
```

### Pattern 1: expect/actual SecureCredentialStore
**What:** Platform-specific credential storage behind a common interface
**When to use:** Any time platform-specific secure storage is needed
**Example:**
```kotlin
// commonMain - SecureCredentialStore.kt
expect class SecureCredentialStore {
    fun getApiKey(serverId: String): String?
    fun storeApiKey(serverId: String, apiKey: String)
    fun removeApiKey(serverId: String)
    fun hasKey(serverId: String): Boolean
}
```

### Pattern 2: HtmlArchiveProcessor Security Enhancement
**What:** Add dangerous element stripping to the existing WEB mode processor
**When to use:** Already called in HtmlContent.kt for ViewerMode.WEB
**Example:**
```kotlin
// In HtmlArchiveProcessor.processForArchive(), after removing scripts:
// Remove dangerous embedding elements
doc.select("iframe, object, embed, applet").remove()
// Remove forms (prevent phishing)
doc.select("form").remove()
```

### Pattern 3: Transparent Migration in ServerRepository
**What:** On first access, read credentials from Room, write to secure store, clear from DB
**When to use:** At ServerRepository initialization
**Example:**
```kotlin
class ServerRepository(
    private val serverDao: ServerDao,
    private val secureStore: SecureCredentialStore
) {
    private var migrated = false

    private suspend fun ensureMigrated() {
        if (migrated) return
        migrated = true
        try {
            val servers = serverDao.getAllServersSync()
            for (server in servers) {
                if (server.apiKey.isNotBlank() && !secureStore.hasKey(server.id)) {
                    secureStore.storeApiKey(server.id, server.apiKey)
                    // Clear apiKey from DB entity
                    serverDao.insertServer(server.copy(apiKey = ""))
                }
            }
        } catch (e: Exception) {
            AppLogger.warn("SecureMigration", "Failed to migrate credentials: ${e.message}")
            // Fallback: continue using DB storage
        }
    }
}
```

### Anti-Patterns to Avoid
- **Storing encryption keys alongside encrypted data:** Never store the AES key or keystore password in SharedPreferences or Room alongside the encrypted credentials
- **Blocking the main thread during migration:** Migration must happen in a coroutine scope, never on the main thread
- **Removing DB apiKey column:** Keep the column for backward compatibility and fallback -- just clear the values after migration
- **Hardcoded keystore passwords on desktop:** Use a machine-derived password or user-configured password, not a string literal

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTML sanitization | Custom regex stripping | Ksoup Safelist/Cleaner | Regex cannot handle nested/malformed HTML; Ksoup's parser handles edge cases |
| AES encryption | Custom crypto routines | Android Keystore + Cipher API | Keystore provides hardware-backed key storage; custom crypto is error-prone |
| Safelist definitions | Custom tag lists | Ksoup `Safelist.relaxed()` + additions | Battle-tested whitelist from jsoup ecosystem |

**Key insight:** HTML sanitization via regex is fundamentally broken for security purposes. The existing Ksoup-based approach in `HtmlSanitizer` is correct. The gap is only in `HtmlArchiveProcessor` which needs element removal, not a new sanitizer.

## Common Pitfalls

### Pitfall 1: EncryptedSharedPreferences Deprecation
**What goes wrong:** Using a deprecated library that won't receive security patches
**Why it happens:** EncryptedSharedPreferences was the go-to solution until April 2025
**How to avoid:** Use direct Android Keystore + AES-GCM + SharedPreferences instead. Same security, no deprecated dependency.
**Warning signs:** Compile warnings about deprecated API, no version updates after 1.1.0-alpha07

### Pitfall 2: WebView JS Enabled with Insufficient Sanitization
**What goes wrong:** `HtmlArchiveProcessor` removes scripts but not iframes/embeds, which can load external JS
**Why it happens:** Archive mode was designed for visual fidelity, not security
**How to avoid:** Add iframe/object/embed/applet removal to `HtmlArchiveProcessor`
**Warning signs:** Any `<iframe>` in API-provided HTML will render and execute in the WebView

### Pitfall 3: Android Keystore Key Loss on Factory Reset
**What goes wrong:** Credentials become inaccessible after device reset or app data clear
**Why it happens:** Android Keystore keys are tied to the device and cleared on factory reset
**How to avoid:** The backup/restore system (`BackupRepository`) already exports credentials. Ensure migration doesn't break this flow -- `BackupRepository.buildBackup()` reads from `serverRepository.servers` which gets apiKey from the Server domain model.
**Warning signs:** Users can't connect after restoring from backup

### Pitfall 4: Desktop KeyStore Password Management
**What goes wrong:** Hardcoded passwords or passwords stored in plaintext defeat the purpose
**Why it happens:** JVM KeyStore requires a password, and there's no secure built-in secret store on desktop Linux/macOS
**How to avoid:** Use a deterministic machine-derived password (e.g., hash of username + app identifier) or accept that desktop credential storage provides obscurity rather than hardware-level security. This is an inherent desktop limitation.
**Warning signs:** Password visible in decompiled class files

### Pitfall 5: Migration Race with Server Access
**What goes wrong:** API call attempted before migration completes, reads empty apiKey from DB
**Why it happens:** Migration is async, server list flows emit before migration finishes
**How to avoid:** Make `ensureMigrated()` a suspend function called before any credential read. Use a `Mutex` or `CompletableDeferred` to ensure single execution.
**Warning signs:** 401 errors on first launch after update

### Pitfall 6: BackupRepository Credential Export Regression
**What goes wrong:** After migration clears apiKey from ServerEntity, backup export produces empty credentials
**Why it happens:** `BackupRepository.buildBackup()` reads `server.apiKey` from the domain model, which maps from the entity
**How to avoid:** The `Server.apiKey` getter must read from `SecureCredentialStore` (with DB fallback). The `ServerEntity.toDomain()` mapping needs updating.
**Warning signs:** Backup file contains empty apiKey values

## Code Examples

### Android Keystore AES-GCM Encryption
```kotlin
// androidMain - SecureCredentialStore.android.kt
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

actual class SecureCredentialStore(private val context: Context) {
    private val keyAlias = "karakept_credential_key"
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "karakept_secure_credentials", Context.MODE_PRIVATE
    )

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.getEntry(keyAlias, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    actual fun storeApiKey(serverId: String, apiKey: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        // Store IV + encrypted data together
        val combined = iv + encrypted
        prefs.edit().putString(serverId, Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
    }

    actual fun getApiKey(serverId: String): String? {
        val encoded = prefs.getString(serverId, null) ?: return null
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12) // GCM IV is 12 bytes
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    actual fun removeApiKey(serverId: String) {
        prefs.edit().remove(serverId).apply()
    }

    actual fun hasKey(serverId: String): Boolean = prefs.contains(serverId)
}
```

### Desktop PKCS12 KeyStore
```kotlin
// desktopMain - SecureCredentialStore.jvm.kt
import java.io.File
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

actual class SecureCredentialStore {
    private val keystoreFile = File(
        System.getProperty("user.home"), ".karakept/credentials.p12"
    )
    private val keystorePassword = derivePassword()

    private fun derivePassword(): CharArray {
        // Machine-derived: hash of user.name + fixed salt
        val seed = "${System.getProperty("user.name")}:karakept-credential-store"
        return seed.hashCode().toString().toCharArray()
    }

    private fun loadKeyStore(): KeyStore {
        val ks = KeyStore.getInstance("PKCS12")
        if (keystoreFile.exists()) {
            keystoreFile.inputStream().use { ks.load(it, keystorePassword) }
        } else {
            ks.load(null, keystorePassword)
        }
        return ks
    }

    private fun saveKeyStore(ks: KeyStore) {
        keystoreFile.parentFile?.mkdirs()
        keystoreFile.outputStream().use { ks.store(it, keystorePassword) }
    }

    actual fun storeApiKey(serverId: String, apiKey: String) {
        val ks = loadKeyStore()
        val secret: SecretKey = SecretKeySpec(apiKey.toByteArray(Charsets.UTF_8), "AES")
        ks.setEntry(
            serverId,
            KeyStore.SecretKeyEntry(secret),
            KeyStore.PasswordProtection(keystorePassword)
        )
        saveKeyStore(ks)
    }

    actual fun getApiKey(serverId: String): String? {
        val ks = loadKeyStore()
        val entry = ks.getEntry(serverId, KeyStore.PasswordProtection(keystorePassword))
            as? KeyStore.SecretKeyEntry ?: return null
        return String(entry.secretKey.encoded, Charsets.UTF_8)
    }

    actual fun removeApiKey(serverId: String) {
        val ks = loadKeyStore()
        if (ks.containsAlias(serverId)) {
            ks.deleteEntry(serverId)
            saveKeyStore(ks)
        }
    }

    actual fun hasKey(serverId: String): Boolean {
        val ks = loadKeyStore()
        return ks.containsAlias(serverId)
    }
}
```

### HtmlArchiveProcessor Security Fix
```kotlin
// Add to HtmlArchiveProcessor.processForArchive(), after script removal:

// Remove dangerous embedding elements that can load external content/JS
doc.select("iframe, object, embed, applet").remove()

// Remove forms to prevent phishing attacks
doc.select("form").remove()
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| EncryptedSharedPreferences | Android Keystore + AES-GCM + SharedPrefs/DataStore | April 2025 (deprecated in alpha07) | No new dependency needed; use platform APIs directly |
| JKS KeyStore | PKCS12 KeyStore | JDK 9 (2017) | PKCS12 is default keystore type; JKS deprecated |
| Regex HTML stripping | DOM-based whitelist sanitization | N/A (Ksoup already used) | Project already uses correct approach |

**Deprecated/outdated:**
- `androidx.security:security-crypto` (EncryptedSharedPreferences): Deprecated April 2025, no further updates planned. Still functional but not recommended for new code.
- JKS keystore format: Deprecated since JDK 9 in favor of PKCS12.

## Open Questions

1. **Desktop keystore password derivation**
   - What we know: JVM PKCS12 KeyStore requires a password. No hardware-backed secret store on desktop Linux.
   - What's unclear: Whether `user.name` hash is sufficient, or if a more robust derivation is needed.
   - Recommendation: Use machine-derived password. Accept that desktop doesn't have hardware-level protection. The threat model is cleartext-in-DB vs. encrypted-in-file -- even a simple derived password is a significant improvement.

2. **BackupRepository credential export after migration**
   - What we know: `BackupRepository.buildBackup()` reads `server.apiKey` from `Server` domain model. After migration, the DB `apiKey` field will be empty.
   - What's unclear: Whether to keep backup reading from the original path or update it.
   - Recommendation: Update `ServerRepository` to populate `Server.apiKey` from `SecureCredentialStore` (falling back to DB). This way `BackupRepository` continues working without changes.

3. **Migration timing: eager vs lazy**
   - What we know: Migration needs to complete before any API call uses credentials.
   - What's unclear: Whether to run at app startup (eager) or on first credential access (lazy).
   - Recommendation: **Lazy migration** with `ensureMigrated()` called in the `servers` Flow mapping. This avoids startup delay and naturally runs before any credential access. Use a `Mutex` to prevent concurrent migration.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | kotlin.test + MockK 1.13.12 |
| Config file | `composeApp/build.gradle.kts` (commonTest / desktopTest source sets) |
| Quick run command | `./gradlew :composeApp:desktopTest --tests "*.HtmlSanitizerTest"` |
| Full suite command | `./gradlew :composeApp:desktopTest` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SEC-01a | HtmlArchiveProcessor strips iframes, object, embed, form | unit | `./gradlew :composeApp:desktopTest --tests "*.HtmlArchiveProcessorTest"` | No -- Wave 0 |
| SEC-01b | HtmlSanitizer already strips scripts/event handlers (regression) | unit | `./gradlew :composeApp:desktopTest --tests "*.HtmlSanitizerTest"` | No -- Wave 0 |
| SEC-02a | SecureCredentialStore encrypt/decrypt roundtrip | unit | `./gradlew :composeApp:desktopTest --tests "*.SecureCredentialStoreTest"` | No -- Wave 0 |
| SEC-02b | ServerRepository migration reads from DB, writes to secure store, clears DB | unit | `./gradlew :composeApp:desktopTest --tests "*.ServerRepositoryMigrationTest"` | No -- Wave 0 |
| SEC-02c | ServerRepository falls back to DB when secure store unavailable | unit | `./gradlew :composeApp:desktopTest --tests "*.ServerRepositoryFallbackTest"` | No -- Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :composeApp:desktopTest --tests "*.HtmlArchiveProcessorTest" --tests "*.HtmlSanitizerTest"`
- **Per wave merge:** `./gradlew :composeApp:desktopTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `composeApp/src/commonTest/.../utils/HtmlArchiveProcessorTest.kt` -- covers SEC-01a
- [ ] `composeApp/src/commonTest/.../utils/HtmlSanitizerTest.kt` -- covers SEC-01b (regression tests)
- [ ] `composeApp/src/desktopTest/.../data/secure/SecureCredentialStoreTest.kt` -- covers SEC-02a (JVM PKCS12 only; Android needs device)
- [ ] `composeApp/src/commonTest/.../data/repository/ServerRepositorySecurityTest.kt` -- covers SEC-02b, SEC-02c (with mocked SecureCredentialStore)

## Sources

### Primary (HIGH confidence)
- Project codebase: `HtmlSanitizer.kt`, `HtmlArchiveProcessor.kt`, `HtmlContent.kt`, `HtmlRenderer.android.kt`, `ServerRepository.kt`, `ServerEntity.kt`
- [Ksoup Safelist (jsoup port)](https://jsoup.org/apidocs/org/jsoup/safety/Safelist.html) - Safelist.relaxed() tag whitelist
- [Android Keystore system](https://developer.android.com/privacy-and-security/keystore) - Official Android cryptography docs
- [Android Cryptography guide](https://developer.android.com/privacy-and-security/cryptography) - AES-GCM patterns

### Secondary (MEDIUM confidence)
- [AndroidX Security releases](https://developer.android.com/jetpack/androidx/releases/security) - Deprecation confirmed at 1.1.0-alpha07
- [EncryptedSharedPreferences deprecation migration guide](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a) - Community migration patterns
- [Securing the Future: Navigating the Deprecation](https://www.spght.dev/articles/28-05-2024/jetsec-deprecation) - Ed Holloway-George's analysis

### Tertiary (LOW confidence)
- Desktop PKCS12 password derivation strategy -- no authoritative source; based on common practice patterns

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries are platform built-ins or already in project (Ksoup)
- Architecture: HIGH - expect/actual pattern already used in project for Database, DataStore, HtmlRenderer
- Pitfalls: HIGH - Identified from direct code analysis (HtmlArchiveProcessor gaps, BackupRepository flow)
- EncryptedSharedPreferences deprecation: HIGH - Confirmed via official AndroidX releases page

**Research date:** 2026-03-21
**Valid until:** 2026-04-21 (stable platform APIs; Ksoup version unlikely to change)

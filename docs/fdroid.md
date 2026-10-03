# F-Droid

F-Droid builds the APK itself, from the release tag. For the result to carry the same signature
as the APK on GitHub, the build is **reproducible**: F-Droid compares its unsigned build against
the published APK and, if they match, copies the signature across. The signing key never leaves
GitHub.

## What the repo does for it

| Requirement | Where |
|---|---|
| `versionCode` / `versionName` readable from source at the tag | literals in `androidApp/build.gradle.kts`, written by the `bump-version` job in `release.yml` |
| Unsigned `release` build without a keystore | `androidApp/build.gradle.kts` (`devRelease` keeps the debug-key fallback for fork PRs) |
| No Google-only dependency blob, no VCS stamp | `dependenciesInfo`, `vcsInfo.include = false` |
| v2+ signing only (v1 adds entries a rebuild cannot match) | `enableV1Signing = false` |
| Store listing, icon, screenshots | `fastlane/metadata/android/en-US/` |

The version code is `MAJOR * 1_000_000 + MINOR * 1_000 + PATCH` (2.6.1 → 2006001). It replaced
the workflow run number, which was 133 at the switch, so updates still go upward.

## Releasing

Dispatch `Release` as before. `bump-version` writes the resolved version into
`androidApp/build.gradle.kts`, commits `chore(release): X.Y.Z` to `main`, and every build job and
the tag use that commit. Pushing a `v*` tag by hand still works, but only on a commit whose source
already holds that version; otherwise the job fails rather than ship an APK F-Droid cannot rebuild.

`main` is protected, so the push needs a token whose actor bypasses the `main` ruleset: store it
as `RELEASE_PUSH_TOKEN` in the `release` environment (a GitHub App token or a fine-grained PAT
with `contents: write`). Without it the job falls back to `GITHUB_TOKEN`, which the ruleset
rejects unless GitHub Actions is on its bypass list.

## The fdroiddata recipe

Submitted as `metadata/com.karakept.app.yml` in
[fdroiddata](https://gitlab.com/fdroid/fdroiddata). Fill in the first release cut after this
setup:

```yaml
Categories:
  - Internet
  - Reading
License: MIT
SourceCode: https://github.com/lmgarret/karakept-kmp
IssueTracker: https://github.com/lmgarret/karakept-kmp/issues
Changelog: https://github.com/lmgarret/karakept-kmp/releases

AutoName: Karakept

RepoType: git
Repo: https://github.com/lmgarret/karakept-kmp.git
Binaries: https://github.com/lmgarret/karakept-kmp/releases/download/v%v/Karakept-%v.apk

Builds:
  - versionName: X.Y.Z
    versionCode: N
    commit: vX.Y.Z
    subdir: androidApp
    submodules: true
    # Only karakeep-upstream/packages/open-api is read by the build.
    rm:
      - karakeep-upstream/apps
    gradle:
      - yes

AllowedAPKSigningKeys: <SHA-256 of the signing certificate>

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
CurrentVersion: X.Y.Z
CurrentVersionCode: N
```

`AllowedAPKSigningKeys` is the certificate digest without colons:
`apksigner verify --print-certs Karakept-X.Y.Z.apk | grep SHA-256`.

## Checking reproducibility locally

Build the tag without a keystore and compare against the published APK:

```bash
git checkout vX.Y.Z && git submodule update --init
./gradlew :androidApp:assembleRelease
apksigcopier compare Karakept-X.Y.Z.apk --unsigned androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk
```

A mismatch is easiest to read with `diffoscope`. The usual causes are a different JDK (CI uses
Temurin 21) or build-tools version from the one F-Droid's build server runs.

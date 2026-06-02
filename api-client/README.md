# api-client

This module contains a Kotlin Multiplatform HTTP client for the
[Karakeep](https://github.com/karakeep-app/karakeep) API.

## Provenance

The Kotlin sources in this module are **generated** — they are not committed.
They are produced at build time by [OpenAPI Generator](https://openapi-generator.tech/)
(`kotlin`, `multiplatform` library) from Karakeep's OpenAPI specification at:

```
karakeep-upstream/packages/open-api/karakeep-openapi-spec.json
```

(`karakeep-upstream` is a git submodule pointing at the upstream Karakeep repository.)

Generated models live under `com.karakept.api.*`. The only place in the app that
consumes them is `RemoteDataSource.kt`.

## Do not hand-edit generated code

Generated files are overwritten on every regeneration. To change the output,
edit the Mustache templates under `templates/` (the file header lives in
`templates/licenseInfo.mustache`) or the generator configuration in
`build.gradle.kts`, then regenerate.

## Attribution

Karakeep is an independent open-source project (AGPL-3.0). This client is
unofficial and not affiliated with or endorsed by the Karakeep team. The API
shape described by the generated code is owned by the Karakeep project.

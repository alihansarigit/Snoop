# Publishing to Maven Central

Snoop uses the [vanniktech maven-publish](https://github.com/vanniktech/gradle-maven-publish-plugin)
plugin. Coordinates and POM metadata live in the root `gradle.properties`
(`GROUP`, `VERSION_NAME`, `POM_*`). Namespace `io.github.alihansarigit` is already
verified in the Sonatype Central Portal.

## One-time machine setup

Put credentials in `~/.gradle/gradle.properties` (NEVER commit these):

```properties
# Sonatype Central Portal user token (Account → Generate User Token)
mavenCentralUsername=<token-username>
mavenCentralPassword=<token-password>

# GPG signing (in-memory key)
signingInMemoryKey=<contents of ~/snoop-signing-key.asc, newlines as \n>
signingInMemoryKeyId=6247E5E0B3AC43D5
signingInMemoryKeyPassword=<your GPG passphrase>
```

Export the signing key (run in your own terminal so the passphrase prompt works):

```bash
gpg --export-secret-keys --armor 6247E5E0B3AC43D5 > ~/snoop-signing-key.asc
```

For `signingInMemoryKey`, paste the armored key as a single line with literal
`\n` between lines, or use the file-based properties (`signing.keyId`,
`signing.password`, `signing.secretKeyRingFile`) if you prefer a keyring.

## Snapshot (no signing required)

`VERSION_NAME` ending in `-SNAPSHOT` publishes to the snapshot repo unsigned:

```bash
./gradlew publishToMavenLocal              # local ~/.m2
./gradlew publishAllPublicationsToMavenCentralRepository   # remote snapshot
```

## Release

1. Set a release version in `gradle.properties`, e.g. `VERSION_NAME=0.1.0`.
2. Publish (signed) and push to the Central Portal staging:
   ```bash
   ./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
   ```
3. With `SONATYPE_HOST=CENTRAL_PORTAL` the plugin uploads and (with
   `publishAndReleaseToMavenCentral`) auto-releases after validation.
4. Bump back to the next `-SNAPSHOT` for ongoing development.

## Checklist before a release

- [ ] All three library modules build: `./gradlew assembleRelease`
- [ ] `VERSION_NAME` bumped, no `-SNAPSHOT`
- [ ] `POM_URL`/`POM_SCM_*` point at the real repo
- [ ] GPG key uploaded to a keyserver (`keyserver.ubuntu.com`)
- [ ] Credentials present in `~/.gradle/gradle.properties`

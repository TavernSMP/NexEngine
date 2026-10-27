# NexEngine — TavernSMP Paper 26.3 fork

Full source build of the deployed NexEngine 2.2.12 line, maintained for **Paper 26.3 / Java 25**. The source history comes from [nulli0n/NexEngine-spigot](https://github.com/nulli0n/NexEngine-spigot), based on commit `cf39cf5bdb0257aba153c0a89d86e13caa5997f3`. This is a complete source project: Gradle compiles all 123 NexEngine source files plus the two upstream annotation sources. The build does not merge or reuse compiled classes from an original NexEngine JAR.

## Build

Install Java 25, then run:

```powershell
$env:GRADLE_USER_HOME = 'D:\cache\.gradle'
.\gradlew.bat clean build --no-daemon
```

The Gradle 9.8.1 wrapper verifies its distribution checksum. Output is `build/libs/NexEngine-2.2.12-tavern.26.3.1.jar`. All 140 compiled classes target Java 25. Maven build files were replaced with one Gradle build; upstream Git history and license remain intact. The conventional source directory `NexEngine/src/main/java` is retained to make upstream comparisons straightforward.

Compile dependencies are public: the legacy Paper API, HikariCP, fastutil, SQLite JDBC, VaultAPI, PlaceholderAPI, and Floodgate API. The optional local Maven cache on D: accelerates this workspace's build; a fresh clone resolves public repositories. No original plugin JAR is required for a normal source build. An independent clean source-copy build also passed without an original NexEngine JAR or Paper server JAR; three tests ran and the optional original-bytecode comparison skipped. Runtime HikariCP 5.0.1 and fastutil 8.5.11 remain in the original descriptor's library list.

The compiler intentionally uses Paper API 1.20.2 to retain the erased enum/API signatures consumed by existing ExcellentCrates 5.0.0 and other legacy dependents. This is an ABI compatibility measure; the output targets Java 25 and is verified on Paper 26.3. The descriptor's existing `api-version: 1.17` is retained so Paper applies its legacy enum compatibility transformations. Changing it without migrating dependent plugins would break that contract.

## Compatibility changes

- `ItemUtil` uses supported Paper item serialization and profile APIs for new items and custom skulls. Its bounded legacy reader uses named Mojang NBT methods on Paper 26.3, without requiring server internals on the build classpath.
- Existing Base32/uncompressed-NBT reward strings remain readable. Unversioned records are upgraded as Minecraft 1.20.4 data (version 3700), matching the final legacy format in this engine line. New writes retain the Base32 container and carry the current item data version. Malformed records fail explicitly instead of silently dropping metadata.
- `Version` recognizes Minecraft 26.3 through Bukkit's version API.
- The deployed JAR's public `EntityUtil.ENTITY_COUNTER` field, absent from the archived upstream head, is retained. Since modern Minecraft no longer has the old global entity counter, it allocates negative virtual-entity IDs from an engine-local atomic counter. It does not reserve native world-entity IDs.
- Menu slot lookup uses standard Java array streams, retaining null-array behavior and avoiding the old Commons Lang helper.

The source cleanup follows Ponytail: use the original typed source wherever it matches the deployed classes, keep existing public APIs, add explicit control-flow braces, normalize formatting, remove leftover generated parameter names and a dead commented declaration, and avoid adding frameworks, shading, adapters, or runtime dependencies. Existing documentation and third-party notices remain.

## Deploy

At the next operator-controlled shutdown, replace only the NexEngine JAR and remove the older NexEngine JAR so exactly one is installed. Keep the existing `plugins/NexEngine` directory, ExcellentCrates configs and database, and booster state. The tested dependents are unchanged ExcellentCrates 5.0.0 and TavernCrateBooster 1.0.2. No config or database migration is required. Modern item saves are not intended for downgrading to an older Minecraft server.

## Verification

See [VERIFICATION.md](VERIFICATION.md) and [verification/results.json](verification/results.json). Four offline regressions passed, including 10,000 deterministic random-value comparisons against the deployed JAR. All 123 original public/protected engine type APIs match, with only `Version.V26_3` added. The packaged config and language resources are byte-identical.

The source-built JAR passed 31 full-stack checkpoints across two isolated Paper process boots, with a network test client, existing preview GUI, actual controlled reward delivery, booster reload/expiry/restoration, restart resumption, and persisted player opening count. All 1,092 stored previews, 306 reward items, and 21,925 original player records were checked. The original 170 config/data files remained unchanged.

No existing production or development server was restarted or reloaded. The dedicated loopback test instance used port 25686 and is stopped. Optional provider integrations and production-world placement still require their own staging acceptance. Detailed local evidence and copied player data are ignored by Git and excluded from source archives.

## License and provenance

This fork retains the upstream [GNU GPL v3 license](LICENSE), original commit history, and existing JetBrains Apache notices. The original recovery input was `NexEngine.jar` 2.2.12, SHA-256 `ffda465bcb13e4e0d8e8dab417a8e40dcca482c8cc49907e71b070e19f070a4e`. The archived typed source covers every first-party top-level class in that JAR. Public APIs and deployed resources were compared before accepting the source as the recovery baseline.

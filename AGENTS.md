**Agents**

This document tells automated agents (CI, bots, or AI coding agents) how to build, lint, test and follow
the style rules for this repository. It is intentionally pragmatic — run the provided commands and follow
the formatting / naming / logging rules below before opening changes or creating PRs.

- Repo type: Java (Minecraft mod) using Gradle wrapper (`./gradlew`).
- Formatting is enforced with Spotless using the Eclipse formatter and an import-order file in `spotless/`.

Build / run / lint / test (quick commands)
- Use the Gradle wrapper always (POSIX): `./gradlew <task>`; on Windows use `gradlew.bat <task>`.
- Full build (runs tests):
  - `./gradlew clean build`

- Build without running tests (faster):
  - `./gradlew clean build -x test`

- Assemble / produce jars only:
  - `./gradlew assemble`
  - `./gradlew jar` (or `./gradlew shadowJar` if the project enables shadowing)

- Development tasks (Minecraft dev environment):
  - `./gradlew runClient` — start a development client (requires the dev workspace/Vanilla assets)
  - `./gradlew runServer` — start a development dedicated server
  - If `enableJava17RunTasks` is enabled in `buildscript.properties` you can run `runClient17` / `runServer17`.

- Formatting / lint (Spotless):
  - `./gradlew spotlessApply` — apply formatting (recommended before committing)
  - `./gradlew spotlessCheck` — fail the build if formatting is incorrect (CI should run this)
  - Formatting rules live in: `spotless/spotless.eclipseformat.xml` and `spotless/spotless.importorder`.

- Tests (general):
  - `./gradlew test` — run all unit tests
  - Tests use the toolchain configured in `build.gradle` and the test logging settings in that file.

- Run a single test (recommended method)
  - Single test class (fully qualified):
    - `./gradlew test --tests "com.sysnote8.bquclaim.YourTestClass"`
  - Single test method:
    - `./gradlew test --tests "com.sysnote8.bquclaim.YourTestClass.yourTestMethod"`
  - Wildcards / patterns:
    - `./gradlew test --tests "com.sysnote8.bquclaim.**.*ClaimTest"`
  - Extra verbosity / debug for failing tests:
    - `./gradlew test --tests "..." --info` or `--debug`

Notes about tests
- The project enables JUnit through `enableJUnit` in `buildscript.properties`. If tests are not running,
  check that file for `enableJUnit = true`.
- The Gradle `test` task has been configured to run with Java 8 toolchain by default (see `build.gradle`).
  If the project switches to modern Java syntax (Jabel) set `enableModernJavaSyntax` in `buildscript.properties`.

Formatting / imports
- Do not hand-format files. Run `./gradlew spotlessApply` and commit the result.
- Import order is enforced by `spotless/spotless.importorder` and should be:
  1. `java` packages
  2. `javax`
  3. `net`
  4. `org`
  5. `com`
- Spotless is configured to remove unused imports and to avoid wildcard imports — do not force wildcard imports.
- Keep static imports explicit and grouped consistently. If Spotless rearranges imports, follow its output.

Code style guidelines
- Language level & toolchain
  - Target compatibility is Java 8 unless `enableModernJavaSyntax` is set. Avoid modern Java features (var, records,
    text blocks, etc.) unless the build explicitly enables them.
  - If you need Java 17+ features for development, ensure `enableModernJavaSyntax` / `enableJava17RunTasks` are set
    and the team acknowledges the change.

- Naming
  - Packages: lower-case reverse-domain, e.g. `com.sysnote8.bquclaim`.
  - Classes / enums / interfaces: PascalCase (e.g. `ChunkManagerData`, `TicketManager`).
  - Methods and non-constant fields: camelCase (e.g. `serializeAll()`, `maxClaimsPerPlayer`).
  - Constants (static final): UPPER_SNAKE_CASE (e.g. `MODID`, `VERSION`) — the repo also follows `LOGGER` as an
    all-caps static final logger field.
  - Event handlers and callbacks: name them for clarity, keep `onXxx` / `handleXxx` style when appropriate.

- Types & nullability
  - Prefer primitives for numeric/boolean fields when null is not meaningful (`int`, `boolean`, etc.).
  - Use generic types explicitly — avoid raw types.
  - `org.jetbrains:annotations` is available in the build (`compileOnlyApi`); annotate public APIs with
    `@NotNull` / `@Nullable` to make intent explicit.

- Code structure
  - Keep classes small and single-responsibility where possible. Split large classes into focused helpers.
  - Put public API classes into stable packages; internal helpers may be package-private.
  - For Forge/Minecraft code, follow the event bus patterns already present in the codebase (use
    `@Mod.EventBusSubscriber`, register with `MinecraftForge.EVENT_BUS`, etc.).

- Imports
  - Follow the import groups in `spotless/spotless.importorder`.
  - Remove unused imports; Spotless will do this automatically when applied.

Error handling and logging
- Logging
  - Use the project logger pattern: `private static final Logger LOGGER = LogManager.getLogger(Tags.MODID);`
  - Prefer `LOGGER.error(msg, t)` / `LOGGER.warn(...)` / `LOGGER.info(...)` over `System.out` or `e.printStackTrace()`.
  - Keep log messages informative and include context (which mod id / world / player) when useful.

- Exceptions / error policy
  - Do not catch `Exception` or `Throwable` indiscriminately. Catch the most specific exception possible.
  - When catching, either handle the condition or log with the throwable and fail fast when appropriate.
  - For build logic (Gradle), use `throw new GradleException("meaningful message")` as the project already does.
  - In event handlers (Forge), prefer to log and degrade gracefully rather than allowing an exception to bubble
    and potentially break the event dispatch.

Networking and serialization
- Register network messages centrally in `com.sysnote8.bquclaim.network.ModNetwork`.
- Message handlers must validate inputs coming from the network and avoid trusting untrusted clients.
- Prefer defensive deserialization: validate bounds, sizes and contents before applying changes to world state.

Tests & CI guidance
- CI should run (at minimum): `./gradlew spotlessCheck` and `./gradlew test`.
- If a change is large or touches formatting, run `./gradlew spotlessApply` locally and commit the formatting
  changes in a separate commit.
- For heavy/integration tests that need Minecraft assets or patched classes, use the configured Gradle tasks and
  consider marking long-running tests so CI can skip them if needed.

Cursor / Copilot / other AI rules
- Repository does not contain `.cursor/rules/` or `.cursorrules` files.
- Repository does not contain `.github/copilot-instructions.md`.
- Formatting / import instructions above (Spotless) are the canonical machine-enforced rules — respect them.

Where to look (key files)
- Build configuration and tasks: `build.gradle`
- Build defaults: `buildscript.properties` and `gradle.properties`
- Formatter / import order: `spotless/spotless.eclipseformat.xml` and `spotless/spotless.importorder`
- Example code / entrypoints: `src/main/java/com/sysnote8/bquclaim/BQuClaim.java`

Agent checklist (what an automated agent should do before creating a PR)
1. Run `./gradlew spotlessApply` and include only formatting changes in a separate commit if needed.
2. Run `./gradlew test` (or the single-test command if working on a small change) and fix failures.
3. Run `./gradlew clean build` to ensure the project builds end-to-end.
4. Do not modify formatting rules files under `spotless/` without project owner approval.

If you are blocked
- Check `buildscript.properties` for properties that toggle behavior (Spotless, JUnit, modern Java syntax).
- Open `build.gradle` for task configuration details (test toolchain, logging, and run tasks).

Thank you — following these commands and conventions keeps CI green and makes review fast.

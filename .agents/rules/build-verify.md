Build verification steps. Run these in order for any code change.

1. `./gradlew spotlessCheck` — formatting MUST pass.
2. `./gradlew build` — compilation MUST succeed with no errors.
3. `./gradlew test` — all tests MUST pass (if tests exist).
4. Verify `dependencies.gradle` and `gradle/libs.versions.toml` are consistent.

If spotlessCheck fails, run `./gradlew spotlessApply` first, then re-check.
If build fails, fix compilation errors before proceeding.

Visual/UI changes require `./gradlew runClient` verification — type checking alone does not verify feature correctness.

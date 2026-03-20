---
name: add-integration
description: Scaffold a new mod integration (e.g., JourneyMap, TheOneProbe) with proper optional dependency handling.
user_invocable: true
args: mod_name
---

Create a new mod integration for BQuClaim.

Arguments:
- `mod_name`: The target mod to integrate with (e.g., `journeymap`, `theoneprobe`)

Steps:
1. Create the integration class in `src/main/java/com/sysnote8/bquclaim/integration/` package (create the package if it doesn't exist).
2. Use `@Optional.Method` or `Loader.isModLoaded()` guards so the integration is safe when the target mod is absent.
3. If the target mod needs a compile-time dependency, add it to `dependencies.gradle`:
   - Use `compileOnly` or `compileOnlyApi` for optional deps.
   - Use `devOnlyNonPublishable(rfg.deobf(...))` for runtime testing.
4. Update the `@Mod` annotation's `dependencies` field in `BQuClaim.java` if needed (use `after:modid;` for optional deps).
5. Run `./gradlew spotlessApply` to format.

---
name: build
description: Build the mod jar. Runs spotlessApply then gradle build. Reports errors with file/line references.
user_invocable: true
---

Run the full build pipeline for BQuClaim:

1. Run `./gradlew spotlessApply` first to fix formatting.
2. Then run `./gradlew build` to compile and package.
3. If the build fails, analyze the error output:
   - For compilation errors: identify the file and line number, read the relevant code, and suggest a fix.
   - For spotless errors: run `spotlessApply` again and retry.
4. If the build succeeds, report the output jar location from `build/libs/`.

---
name: diagnose-crash
description: Analyze a Minecraft crash report or stacktrace and identify the root cause in this mod's code.
user_invocable: true
args: crash_log_path_or_paste
---

Diagnose a Minecraft crash or error related to BQuClaim.

Steps:
1. If a file path is provided, read the crash report. Otherwise, analyze the pasted stacktrace.
2. Look for BQuClaim classes (`com.github.gtexpert.bquclaim`) in the stacktrace to find the origin.
3. Read the identified source files at the relevant line numbers.
4. Check for common 1.12.2 Forge mod issues:
   - Side-only class access on wrong side (client classes on server)
   - NBT serialization mismatches between client/server
   - Null `WorldSavedData` or `MapStorage`
   - Network message handler running off main thread
   - Missing `@SideOnly` annotations
   - Mixin target method signature changes
5. Explain the root cause and propose a fix with specific code changes.

---
name: run-client
description: Launch the Minecraft client with the mod loaded for testing.
user_invocable: true
---

Launch the Minecraft development client:

1. Run `./gradlew runClient` in the background using `run_in_background: true`.
2. Tell the user the client is starting and they will be notified when it exits.
3. If it fails immediately, check the log output for common issues:
   - Missing dependencies or broken access transformers
   - Mixin apply failures
   - Class not found errors (may indicate a deobf issue)

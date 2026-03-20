---
name: add-gui
description: Scaffold a new ModularUI screen/widget for the mod's GUI.
user_invocable: true
args: screen_name
---

Create a new ModularUI-based GUI screen for BQuClaim.

Arguments:
- `screen_name`: The name for the screen class (e.g., `ClaimInfoScreen`)

Steps:
1. Read existing GUI classes in `src/main/java/com/sysnote8/bquclaim/gui/` (especially `ChunkMapScreen.java`) to understand the project's ModularUI patterns.
2. Create the new screen class in the `gui/` package following the existing conventions:
   - Extend the appropriate ModularUI base class.
   - Use ModularUI widget system for layout.
   - Follow the same import style and formatting as existing screens.
3. If a keybinding is needed, update `ModKeyBindings.java` and `KeyInputHandler.java`.
4. Run `./gradlew spotlessApply` to format.

---
name: add-network-message
description: Scaffold a new network message class (IMessage + IMessageHandler) and register it in ModNetwork.
user_invocable: true
args: message_name, direction
---

Create a new Forge SimpleNetworkWrapper message for BQuClaim.

Arguments:
- `message_name`: The class name for the message (e.g., `MessageUnclaimChunk`)
- `direction`: Either `client_to_server` or `server_to_client`

Steps:
1. Read `src/main/java/com/sysnote8/bquclaim/network/ModNetwork.java` to see the current message registrations and the next discriminator ID.
2. Create the new message class in `src/main/java/com/sysnote8/bquclaim/network/` following the existing pattern:
   - Implement `IMessage` with `fromBytes`/`toBytes` for serialization.
   - Implement a static inner `Handler` class implementing `IMessageHandler`.
   - Use `IThreadListener.addScheduledTask()` in the handler to run on the main thread.
3. Register the message in `ModNetwork.init()` by appending a new `INSTANCE.registerMessage(...)` line with the next `id++` value. Use `Side.SERVER` for client-to-server, `Side.CLIENT` for server-to-client.
4. Run `./gradlew spotlessApply` to format the new file.

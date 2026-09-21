package com.github.gtexpert.blpc.common.command.admin;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.api.party.PartyRole;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.command.BLPCCommandHelper;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.network.message.ClientNotify;
import com.github.gtexpert.blpc.common.party.PartyManagerData;

public class KickCommand extends AdminSubCommand {

    @Override
    public @NotNull String getName() {
        return "kick";
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "/blpc admin kick <partyName> <player>";
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        String @NotNull [] args) throws CommandException {
        if (args.length != 2) {
            throw new CommandException("/blpc admin kick <partyName> <player>");
        }
        Party party = BLPCCommandHelper.requirePartyByName(args[0]);

        UUID targetUUID = resolveMemberUUID(server, party, args[1]);
        if (targetUUID == null) {
            throw new CommandException("Player is not a member of this party: " + args[1]);
        }
        if (party.getRole(targetUUID) == PartyRole.OWNER) {
            throw new CommandException("Cannot kick the party owner. Use /blpc admin move-owner first.");
        }

        String targetName = BLPCCommandHelper.resolveName(server, party, targetUUID);
        ChunkManagerData.getInstance().releaseAllClaims(targetUUID, sender.getEntityWorld());
        PartyManagerData.getInstance().removeMember(party.getPartyId(), targetUUID);
        PartyManagerData.getInstance().setBQuLinked(targetUUID, false);
        PartyProviderRegistry.get().syncToAll();
        BLPCSaveHandler.INSTANCE.markDirty();

        EntityPlayerMP target = server.getPlayerList().getPlayerByUUID(targetUUID);
        if (target != null) {
            ModNetwork.INSTANCE.sendTo(
                    ClientNotify.partyEvent(ClientNotify.EVENT_KICKED, targetName, ""),
                    target);
        }
        sender.sendMessage(
                new TextComponentTranslation("command.blpc.kick.success", targetName, party.getName()));
    }

    @Override
    public @NotNull List<String> getTabCompletions(@NotNull MinecraftServer server,
                                                   @NotNull ICommandSender sender,
                                                   String @NotNull [] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, BLPCCommandHelper.allPartyNames());
        }
        if (args.length == 2) {
            Party party = BLPCCommandHelper.findPartyByName(args[0]);
            if (party != null) {
                List<String> names = party.getMembers().keySet().stream()
                        .filter(uuid -> party.getRole(uuid) != PartyRole.OWNER)
                        .map(uuid -> BLPCCommandHelper.resolveName(server, party, uuid))
                        .collect(Collectors.toList());
                return getListOfStringsMatchingLastWord(args, names);
            }
        }
        return Collections.emptyList();
    }

    /**
     * Cached-name-first, online-fallback — same precedence as
     * {@link com.github.gtexpert.blpc.common.party.DefaultPartyProvider#kickOrLeave} so a typed
     * name resolves to the same member here as it would from the party GUI's kick button.
     */
    @Nullable
    private static UUID resolveMemberUUID(MinecraftServer server, Party party, String name) {
        for (UUID uuid : party.getMembers().keySet()) {
            String resolved = BLPCCommandHelper.resolveName(server, party, uuid);
            if (resolved.equalsIgnoreCase(name)) return uuid;
        }
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(name);
        if (online != null && party.isMember(online.getUniqueID())) {
            return online.getUniqueID();
        }
        return null;
    }
}

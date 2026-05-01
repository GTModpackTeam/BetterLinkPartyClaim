package com.github.gtexpert.blpc.common.command;

import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;
import com.github.gtexpert.blpc.common.party.PartyRole;

public class ListCommand extends CommandBase {

    @Override
    public @NotNull String getName() {
        return "list";
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "/blpc list";
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        String @NotNull [] args) {
        var parties = PartyManagerData.getInstance().getAllParties();
        if (parties.isEmpty()) {
            sender.sendMessage(new TextComponentTranslation("command.blpc.list.empty"));
            return;
        }
        sender.sendMessage(new TextComponentTranslation("command.blpc.list.header", parties.size()));
        for (Party party : parties) {
            UUID owner = party.getOwner();
            String ownerName = owner != null ? resolveName(server, party, owner) : "-";
            var line = new TextComponentString(String.format("- %s%s%s (%s%d%s) - %s",
                    TextFormatting.AQUA, party.getName(), TextFormatting.RESET,
                    TextFormatting.GRAY, party.getMembers().size(), TextFormatting.RESET,
                    ownerName));
            sender.sendMessage(line);
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(@NotNull MinecraftServer server, @NotNull ICommandSender sender) {
        return true;
    }

    static String resolveName(MinecraftServer server, Party party, UUID uuid) {
        var online = server.getPlayerList().getPlayerByUUID(uuid);
        if (online != null) return online.getName();
        String cached = party.getPlayerName(uuid);
        if (cached != null) return cached;
        String global = net.minecraftforge.common.UsernameCache.getLastKnownUsername(uuid);
        return global != null ? global : uuid.toString().substring(0, 8);
    }

    static String roleLabel(PartyRole role) {
        if (role == null) return "-";
        return switch (role) {
            case OWNER -> TextFormatting.GOLD + "OWNER" + TextFormatting.RESET;
            case ADMIN -> TextFormatting.YELLOW + "ADMIN" + TextFormatting.RESET;
            case MEMBER -> "MEMBER";
        };
    }
}

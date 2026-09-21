package com.github.gtexpert.blpc.common.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyRole;
import com.github.gtexpert.blpc.api.util.PartyQueryUtil;

public class ListCommand extends PlayerCommand {

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
        var parties = PartyQueryUtil.provider().getAllParties();
        if (parties.isEmpty()) {
            sender.sendMessage(new TextComponentTranslation("command.blpc.list.empty"));
            return;
        }
        sender.sendMessage(new TextComponentTranslation("command.blpc.list.header", parties.size()));
        for (Party party : parties) {
            String ownerName = BLPCCommandHelper.resolveOwnerName(server, party);
            var line = new TextComponentString(String.format("- %s%s%s (%s%d%s) - %s",
                    TextFormatting.AQUA, party.getName(), TextFormatting.RESET,
                    TextFormatting.GRAY, party.getMembers().size(), TextFormatting.RESET,
                    ownerName));
            sender.sendMessage(line);
        }
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

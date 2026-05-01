package com.github.gtexpert.blpc.common.command;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.server.command.CommandTreeBase;

import org.jetbrains.annotations.NotNull;

public class AdminCommand extends CommandTreeBase {

    public AdminCommand() {
        addSubcommand(new MoveOwnerCommand());
        addSubcommand(new KickCommand());
        addSubcommand(new DisbandCommand());
    }

    @Override
    public @NotNull String getName() {
        return "admin";
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "/blpc admin <move-owner|kick|disband>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }
}

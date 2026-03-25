package com.github.gtexpert.blpc.common.command;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.server.command.CommandTreeBase;

import org.jetbrains.annotations.NotNull;

public class BLPCCommand extends CommandTreeBase {

    public BLPCCommand() {
        addSubcommand(new MoveOwnerCommand());
    }

    @Override
    public @NotNull String getName() {
        return "blpc";
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "/blpc <move-owner>";
    }
}

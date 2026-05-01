package com.github.gtexpert.blpc.common.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import org.jetbrains.annotations.NotNull;

import com.github.gtexpert.blpc.common.chunk.ChunkManagerData;
import com.github.gtexpert.blpc.common.chunk.ClaimedChunkData;

public class HereCommand extends CommandBase {

    @Override
    public @NotNull String getName() {
        return "here";
    }

    @Override
    public @NotNull String getUsage(@NotNull ICommandSender sender) {
        return "/blpc here";
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        String @NotNull [] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        BlockPos pos = player.getPosition();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        ClaimedChunkData claim = ChunkManagerData.getInstance().getClaim(chunkX, chunkZ);
        if (claim == null) {
            sender.sendMessage(new TextComponentTranslation("command.blpc.here.wilderness", chunkX, chunkZ));
            return;
        }

        String partyName = claim.partyName == null || claim.partyName.isEmpty() ? "-" : claim.partyName;
        if (claim.isForceLoaded) {
            sender.sendMessage(new TextComponentTranslation("command.blpc.here.claimed_force",
                    chunkX, chunkZ, partyName, claim.ownerName));
        } else {
            sender.sendMessage(new TextComponentTranslation("command.blpc.here.claimed",
                    chunkX, chunkZ, partyName, claim.ownerName));
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
}

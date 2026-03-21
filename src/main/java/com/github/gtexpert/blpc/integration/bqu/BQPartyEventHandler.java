package com.github.gtexpert.blpc.integration.bqu;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.github.gtexpert.blpc.common.party.ClientPartyCache;
import com.github.gtexpert.blpc.common.party.Party;

import betterquesting.api.enums.EnumPartyStatus;
import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.storage.DBEntry;
import betterquesting.questing.party.PartyManager;

public class BQPartyEventHandler {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onPartyUpdate(DatabaseEvent.Update event) {
        if (event.getType() != DatabaseEvent.DBType.PARTY && event.getType() != DatabaseEvent.DBType.ALL) return;

        // Build party list from BQu
        NBTTagList list = new NBTTagList();
        Set<UUID> bquMembers = new HashSet<>();
        for (DBEntry<IParty> entry : PartyManager.INSTANCE.getEntries()) {
            IParty bqParty = entry.getValue();
            if (bqParty.getMembers().isEmpty()) continue;
            Party party = new Party(entry.getID(),
                    bqParty.getProperties().getProperty(NativeProps.NAME),
                    0L);
            for (UUID memberId : bqParty.getMembers()) {
                EnumPartyStatus status = bqParty.getStatus(memberId);
                party.addMember(memberId, BQPartyProvider.mapRole(status));
                bquMembers.add(memberId);
            }
            list.appendTag(party.toNBT());
        }

        // Also keep self-managed parties for players NOT in any BQu party.
        // This preserves chunk claim associations when BQu party is deleted.
        for (Party existing : ClientPartyCache.getAllParties()) {
            boolean allInBQu = true;
            for (UUID memberId : existing.getMemberUUIDs()) {
                if (!bquMembers.contains(memberId)) {
                    allInBQu = false;
                    break;
                }
            }
            if (!allInBQu) {
                list.appendTag(existing.toNBT());
            }
        }

        // Auto-unlink local player if their BQu party is gone
        UUID localPlayer = Minecraft.getMinecraft().player != null ? Minecraft.getMinecraft().player.getUniqueID() :
                null;
        if (localPlayer != null && ClientPartyCache.isBQuLinked(localPlayer) && !bquMembers.contains(localPlayer)) {
            ClientPartyCache.setLocalBQuLinked(localPlayer, false);
        }

        NBTTagCompound root = new NBTTagCompound();
        root.setTag("parties", list);
        ClientPartyCache.loadFromNBT(root);
    }
}

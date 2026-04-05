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
import com.github.gtexpert.blpc.common.party.TrustAction;

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
        if (event.getType() != DatabaseEvent.DBType.PARTY) return;

        // Build party list from BQu
        NBTTagList list = new NBTTagList();
        Set<UUID> bquMembers = new HashSet<>();
        for (DBEntry<IParty> entry : PartyManager.INSTANCE.getEntries()) {
            IParty bqParty = entry.getValue();
            if (bqParty.getMembers().isEmpty()) continue;
            Party party = new Party(Party.uuidFromIntId(entry.getID()),
                    bqParty.getProperties().getProperty(NativeProps.NAME),
                    0L);
            for (UUID memberId : bqParty.getMembers()) {
                EnumPartyStatus status = bqParty.getStatus(memberId);
                party.addMember(memberId, BQPartyProvider.mapRole(status));
                bquMembers.add(memberId);
            }
            // Preserve BLPC settings from existing cache
            Party cachedParty = ClientPartyCache.getParty(Party.uuidFromIntId(entry.getID()));
            if (cachedParty != null) {
                party.setDescription(cachedParty.getDescription());
                party.setColor(cachedParty.getColor());
                party.setFreeToJoin(cachedParty.isFreeToJoin());
                party.setFakePlayerTrustLevel(cachedParty.getFakePlayerTrustLevel());
                party.setProtectExplosions(cachedParty.protectsExplosions());
                for (TrustAction ta : TrustAction.values()) {
                    party.setTrustLevel(ta, cachedParty.getTrustLevel(ta));
                }
                for (UUID allyId : cachedParty.getAllies()) {
                    party.addAlly(allyId);
                }
                for (UUID enemyId : cachedParty.getEnemies()) {
                    party.addEnemy(enemyId);
                }
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
        // Preserve current bquLinked state so loadFromNBT does not clear optimistic flags
        NBTTagList bquLinkedList = new NBTTagList();
        for (UUID uuid : ClientPartyCache.getBQuLinkedPlayers()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("uuid", uuid);
            bquLinkedList.appendTag(tag);
        }
        root.setTag("bquLinked", bquLinkedList);
        ClientPartyCache.loadFromNBT(root);
    }
}

package com.github.gtexpert.blpc.integration.bqu;

import java.util.UUID;

import net.minecraft.world.World;

import com.github.gtexpert.blpc.BLPCMod;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;
import com.github.gtexpert.blpc.common.party.PartyRole;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumPartyStatus;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.storage.DBEntry;

public class BQMigrationHelper {

    public static void migrateIfNeeded(World world) {
        PartyManagerData data = PartyManagerData.getInstance();

        if (data.isMigrated()) return;

        try {
            int count = 0;
            for (DBEntry<IParty> entry : QuestingAPI.getAPI(ApiReference.PARTY_DB).getEntries()) {
                IParty bqParty = entry.getValue();
                String name = bqParty.getProperties().getProperty(NativeProps.NAME);
                if (name == null || name.isEmpty()) name = "New Party";

                UUID ownerUUID = null;
                for (UUID memberId : bqParty.getMembers()) {
                    if (bqParty.getStatus(memberId) == EnumPartyStatus.OWNER) {
                        ownerUUID = memberId;
                        break;
                    }
                }

                if (ownerUUID == null) {
                    BLPCMod.LOGGER.warn("[Migration] Skipping BQu party '{}' (no owner)", name);
                    continue;
                }

                if (data.getPartyByPlayer(ownerUUID) != null) {
                    BLPCMod.LOGGER.debug("[Migration] Skipping BQu party '{}' (owner already in a party)", name);
                    continue;
                }

                Party party = data.createParty(name, ownerUUID);

                for (UUID memberId : bqParty.getMembers()) {
                    if (memberId.equals(ownerUUID)) continue;
                    EnumPartyStatus status = bqParty.getStatus(memberId);
                    PartyRole role = BQPartyProvider.mapRole(status);
                    party.addMember(memberId, role);
                }

                count++;
                BLPCMod.LOGGER.info("[Migration] BQu party '{}' -> BLPC party (id={})", name,
                        party.getPartyId());
            }

            data.setMigrated(true);
            BLPCMod.LOGGER.info("[Migration] Complete. {} BQu parties migrated.", count);
        } catch (Exception e) {
            BLPCMod.LOGGER.error("[Migration] Failed to migrate BQu parties", e);
        }
    }
}

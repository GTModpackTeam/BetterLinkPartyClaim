package com.sysnote8.bquclaim.api.integration;

import betterquesting.api.questing.party.IParty;
import betterquesting.api2.storage.DBEntry;
import betterquesting.questing.party.PartyManager;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class BetterQuestingAPI {
    @Nullable
    public static DBEntry<IParty> getPlayerParty(UUID playerUuid) {
        return PartyManager.INSTANCE.getParty(playerUuid);
    }

    public static int getPartyId(UUID playerUuid) {
        DBEntry<IParty> party = getPlayerParty(playerUuid);
        return party == null ? -1: party.getID(); // id or default -1
    }
}

package com.github.gtexpert.bquclaim;

import java.util.UUID;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.storage.DBEntry;

public class BQPartyHelper {

    public static boolean areInSameParty(UUID playerA, UUID playerB) {
        try {
            for (DBEntry<IParty> entry : QuestingAPI.getAPI(ApiReference.PARTY_DB).getEntries()) {
                IParty party = entry.getValue();
                if (party.getStatus(playerA) != null && party.getStatus(playerB) != null) {
                    return true;
                }
            }
        } catch (Exception e) {
            BQuClaim.LOGGER.debug("Failed to check party status", e);
        }
        return false;
    }
}

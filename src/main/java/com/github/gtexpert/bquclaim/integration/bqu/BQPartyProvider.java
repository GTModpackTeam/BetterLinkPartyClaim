package com.github.gtexpert.bquclaim.integration.bqu;

import java.util.UUID;

import javax.annotation.Nullable;

import com.github.gtexpert.bquclaim.BQuClaim;
import com.github.gtexpert.bquclaim.api.party.IPartyProvider;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.storage.DBEntry;

public class BQPartyProvider implements IPartyProvider {

    @Override
    public boolean areInSameParty(UUID playerA, UUID playerB) {
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

    @Override
    @Nullable
    public String getPartyName(UUID playerUUID) {
        try {
            for (DBEntry<IParty> entry : QuestingAPI.getAPI(ApiReference.PARTY_DB).getEntries()) {
                IParty party = entry.getValue();
                if (party.getStatus(playerUUID) != null) {
                    return party.getProperties().getProperty(NativeProps.NAME);
                }
            }
        } catch (Exception e) {
            BQuClaim.LOGGER.debug("Failed to get party name", e);
        }
        return null;
    }
}

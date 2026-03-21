package com.github.gtexpert.bquclaim.api.party;

import java.util.UUID;

import javax.annotation.Nullable;

public class PartyProviderRegistry {

    private static final IPartyProvider NO_OP = new IPartyProvider() {

        @Override
        public boolean areInSameParty(UUID playerA, UUID playerB) {
            return false;
        }

        @Override
        @Nullable
        public String getPartyName(UUID playerUUID) {
            return null;
        }
    };

    private static IPartyProvider provider = NO_OP;

    public static void register(IPartyProvider partyProvider) {
        provider = partyProvider;
    }

    public static IPartyProvider get() {
        return provider;
    }
}

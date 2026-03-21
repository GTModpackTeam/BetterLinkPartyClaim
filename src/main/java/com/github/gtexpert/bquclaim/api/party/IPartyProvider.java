package com.github.gtexpert.bquclaim.api.party;

import java.util.UUID;

import javax.annotation.Nullable;

public interface IPartyProvider {

    boolean areInSameParty(UUID playerA, UUID playerB);

    @Nullable
    String getPartyName(UUID playerUUID);
}

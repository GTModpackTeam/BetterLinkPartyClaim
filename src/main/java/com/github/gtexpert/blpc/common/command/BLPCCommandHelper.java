package com.github.gtexpert.blpc.common.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;

import com.github.gtexpert.blpc.api.party.IPartyProvider;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.common.party.DefaultPartyProvider;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.PartyManagerData;

final class BLPCCommandHelper {

    private static final DefaultPartyProvider SELF_PROVIDER = new DefaultPartyProvider();

    private BLPCCommandHelper() {}

    @Nullable
    static Party findPartyByName(String name) {
        return PartyManagerData.getInstance().getAllParties().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    static List<String> allPartyNames() {
        return PartyManagerData.getInstance().getAllParties().stream()
                .map(Party::getName)
                .collect(Collectors.toList());
    }

    /**
     * Returns the provider that should handle a player-initiated mutation.
     * Mirrors {@code MessagePartyAction.Handler}: BQu-linked players go through
     * the BQu provider, others through the self-managed default.
     */
    static IPartyProvider activeProviderFor(EntityPlayerMP player) {
        boolean linked = PartyManagerData.getInstance().isBQuLinked(player.getUniqueID());
        return linked ? PartyProviderRegistry.get() : SELF_PROVIDER;
    }

    /** Returns parties that have a pending invite for the given player. */
    static List<Party> pendingInvitesFor(UUID playerUUID) {
        List<Party> result = new ArrayList<>();
        for (Party party : PartyManagerData.getInstance().getAllParties()) {
            if (party.hasInvite(playerUUID)) result.add(party);
        }
        return result;
    }
}

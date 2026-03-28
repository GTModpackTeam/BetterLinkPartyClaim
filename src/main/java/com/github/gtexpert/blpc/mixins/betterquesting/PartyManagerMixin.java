package com.github.gtexpert.blpc.mixins.betterquesting;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.common.BLPCSaveHandler;
import com.github.gtexpert.blpc.common.party.PartyManagerData;

import betterquesting.api.questing.party.IParty;
import betterquesting.network.handlers.NetPartyAction;
import betterquesting.questing.party.PartyManager;

/**
 * Hooks into BQu's NetPartyAction.deleteParty to detect party deletion.
 * Automatically unlinks affected players from BQu in BLPC's PartyManagerData.
 */
@Mixin(value = NetPartyAction.class, remap = false)
public class PartyManagerMixin {

    @Inject(method = "deleteParty", at = @At("HEAD"))
    private static void blpc$onPartyDelete(int partyID, CallbackInfo ci) {
        IParty party = PartyManager.INSTANCE.getValue(partyID);
        if (party == null) return;

        PartyManagerData data = PartyManagerData.getInstance();
        for (UUID memberId : party.getMembers()) {
            if (data.isBQuLinked(memberId)) {
                data.setBQuLinked(memberId, false);
            }
        }
        BLPCSaveHandler.INSTANCE.saveConfig(data);
        PartyProviderRegistry.get().syncToAll();
    }
}

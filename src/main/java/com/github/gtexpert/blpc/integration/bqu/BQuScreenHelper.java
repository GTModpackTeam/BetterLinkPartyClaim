package com.github.gtexpert.blpc.integration.bqu;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.storage.DBEntry;
import betterquesting.client.gui2.party.GuiPartyCreate;
import betterquesting.client.gui2.party.GuiPartyManage;
import betterquesting.questing.party.PartyManager;

@SideOnly(Side.CLIENT)
public class BQuScreenHelper {

    public static void openPartyScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        DBEntry<IParty> party = PartyManager.INSTANCE.getParty(QuestingAPI.getQuestingUUID(mc.player));

        if (party != null) {
            mc.displayGuiScreen(new GuiPartyManage(mc.currentScreen));
        } else {
            mc.displayGuiScreen(new GuiPartyCreate(mc.currentScreen));
        }
    }
}

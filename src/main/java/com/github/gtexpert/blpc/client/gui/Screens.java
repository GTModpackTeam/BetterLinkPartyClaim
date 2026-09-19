package com.github.gtexpert.blpc.client.gui;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.CustomModularScreen;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.client.gui.party.CreatePanel;
import com.github.gtexpert.blpc.client.gui.party.MainPanel;
import com.github.gtexpert.blpc.client.gui.party.MembersPanel;
import com.github.gtexpert.blpc.client.gui.party.ModeratorsPanel;
import com.github.gtexpert.blpc.client.gui.party.SettingsPanel;
import com.github.gtexpert.blpc.client.gui.party.TransferOwnerPanel;

/**
 * Central catalog of every BLPC screen and panel — the single place to discover
 * "what GUIs exist and how to open them".
 * <p>
 * The {@code *_ID} constants mirror each panel's own {@code PANEL_ID} so the full
 * set of panel identifiers is browsable from one file; the {@code open*} / build
 * helpers are the canonical entry points that callers (keybinds, buttons, sub-panels)
 * route through instead of constructing screens ad-hoc.
 */
@SideOnly(Side.CLIENT)
public final class Screens {

    /** Full-screen chunk map (top-level {@link ChunkMapScreen}). */
    public static final String MAP = "blpc.map";

    /** Party main menu. */
    public static final String PARTY = MainPanel.PANEL_ID;
    /** Create-or-join panel (shown when the player has no party). */
    public static final String PARTY_CREATE = CreatePanel.PANEL_ID;
    /** Protection / ally / enemy settings. */
    public static final String PARTY_SETTINGS = SettingsPanel.PANEL_ID;
    /** Member list + invite. */
    public static final String PARTY_MEMBERS = MembersPanel.PANEL_ID;
    /** Moderator promote / demote. */
    public static final String PARTY_MODERATORS = ModeratorsPanel.PANEL_ID;
    /** Transfer-ownership dialog. */
    public static final String PARTY_TRANSFER = TransferOwnerPanel.PANEL_ID;
    /**
     * Addons hub — per-mod integration settings. Opened from {@link MainPanel}; its
     * per-mod sub-panels ({@code blpc.party.addons.journeymap}, {@code blpc.party.addons.bqu})
     * are registered by the integration modules via {@code IntegrationPanelRegistry}.
     */
    public static final String PARTY_ADDONS = AddonsPanel.PANEL_ID;

    private Screens() {}

    /** Opens the full-screen chunk map. No-op when another screen is already open. */
    public static void openMap() {
        if (Minecraft.getMinecraft().currentScreen == null) {
            ClientGUI.open(new ChunkMapScreen());
        }
    }

    /** Opens the party main panel directly via keybind. No-op when another screen is already open. */
    public static void openPartyDirect() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null) return;
        ClientGUI.open(new CustomModularScreen(Tags.MODID) {

            @Override
            public ModularPanel buildUI(ModularGuiContext context) {
                return partyMain(mc.player.getUniqueID(), null);
            }
        });
    }

    /**
     * Builds the party main panel for {@code playerId}, falling back to
     * {@link CreatePanel} when the player has no party.
     *
     * @param reopener (nullable) parent factory re-invoked after a successful join
     */
    public static ModularPanel partyMain(UUID playerId, IPanelHandler reopener) {
        return MainPanel.build(playerId, reopener);
    }
}

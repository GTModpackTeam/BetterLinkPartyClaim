package com.github.gtexpert.blpc.client.gui.party.widget;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import com.github.gtexpert.blpc.client.gui.GuiColors;
import com.github.gtexpert.blpc.client.gui.party.PanelBuilder;
import com.github.gtexpert.blpc.client.gui.party.PanelSizes;
import com.github.gtexpert.blpc.client.gui.party.PartyWidgets;

/**
 * Reusable toggle-list panel template following the FTB Utilities pattern.
 * <p>
 * Shows ALL relevant players in a single scrollable list. Each row's text
 * color indicates status (active vs inactive). Clicking a row toggles
 * the player's status.
 */
public final class PlayerListPanel {

    private PlayerListPanel() {}

    public static Builder builder(String panelId) {
        return new Builder(panelId);
    }

    public static class Builder {

        private final String panelId;
        private String titleKey = "";
        private Set<UUID> activeSet = Collections.emptySet();
        private int activeColor = GuiColors.GREEN;
        private int inactiveColor = GuiColors.GRAY_LIGHT;
        private Consumer<String> onActivate;
        private Consumer<String> onDeactivate;
        private Set<UUID> excludeUUIDs = Collections.emptySet();
        private boolean showOnlineOnly = true;
        private Collection<UUID> fixedPlayerSet;
        private Predicate<UUID> canClick = uuid -> true;
        private java.util.function.Function<UUID, String> suffixProvider;
        private String tooltipKey;
        private int width = PanelSizes.STANDARD_W;
        private int height = PanelSizes.STANDARD_H;

        Builder(String panelId) {
            this.panelId = panelId;
        }

        public Builder title(String langKey) {
            this.titleKey = langKey;
            return this;
        }

        /** The set of UUIDs that are currently "active" (colored with activeColor). */
        public Builder activeSet(Set<UUID> activeSet) {
            this.activeSet = activeSet;
            return this;
        }

        /** Color for active (toggled-on) players. */
        public Builder activeColor(int color) {
            this.activeColor = color;
            return this;
        }

        /** Color for inactive (toggled-off) players. */
        public Builder inactiveColor(int color) {
            this.inactiveColor = color;
            return this;
        }

        /** Called when clicking an inactive player (to activate). */
        public Builder onActivate(Consumer<String> onActivate) {
            this.onActivate = onActivate;
            return this;
        }

        /** Called when clicking an active player (to deactivate). */
        public Builder onDeactivate(Consumer<String> onDeactivate) {
            this.onDeactivate = onDeactivate;
            return this;
        }

        /** UUIDs to exclude from the list entirely (e.g. party members). */
        public Builder excludeUUIDs(Set<UUID> exclude) {
            this.excludeUUIDs = exclude;
            return this;
        }

        /**
         * Use a fixed set of players instead of online players.
         * When set, only these UUIDs are shown (useful for Moderators panel).
         */
        public Builder fixedPlayerSet(Collection<UUID> players) {
            this.fixedPlayerSet = players;
            this.showOnlineOnly = false;
            return this;
        }

        /** Predicate to check if a player row is clickable. */
        public Builder canClick(Predicate<UUID> canClick) {
            this.canClick = canClick;
            return this;
        }

        /** Provides a suffix string for each player (e.g. role name). */
        public Builder suffixProvider(java.util.function.Function<UUID, String> provider) {
            this.suffixProvider = provider;
            return this;
        }

        /** Lang key for the tooltip shown on each player row. */
        public Builder tooltipKey(String langKey) {
            this.tooltipKey = langKey;
            return this;
        }

        public Builder size(int w, int h) {
            this.width = w;
            this.height = h;
            return this;
        }

        public ModularPanel build() {
            ModularPanel panel = new ModularPanel(panelId);
            panel.size(width, height);

            PanelBuilder.addHeader(panel, titleKey);

            @SuppressWarnings("rawtypes")
            ListWidget list = new ListWidget();

            List<PlayerEntry> entries = collectPlayers();
            for (PlayerEntry entry : entries) {
                list.child(createToggleRow(entry));
            }

            PanelBuilder.addList(panel, list);
            return panel;
        }

        private List<PlayerEntry> collectPlayers() {
            List<PlayerEntry> result = new ArrayList<>();

            if (fixedPlayerSet != null) {
                // Fixed player set mode (e.g. Moderators: show party members)
                for (UUID uuid : fixedPlayerSet) {
                    if (excludeUUIDs.contains(uuid)) continue;
                    String name = PartyWidgets.getDisplayName(uuid);
                    boolean active = activeSet.contains(uuid);
                    result.add(new PlayerEntry(uuid, name, active));
                }
            } else {
                // Online players mode
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.getConnection() == null) return result;

                for (NetworkPlayerInfo info : mc.getConnection().getPlayerInfoMap()) {
                    UUID uuid = info.getGameProfile().getId();
                    if (excludeUUIDs.contains(uuid)) continue;
                    String name = info.getGameProfile().getName();
                    boolean active = activeSet.contains(uuid);
                    result.add(new PlayerEntry(uuid, name, active));
                }
            }

            result.sort(Comparator.comparing(e -> e.name));
            return result;
        }

        private Flow createToggleRow(PlayerEntry entry) {
            int color = entry.active ? activeColor : inactiveColor;
            boolean clickable = canClick.test(entry.uuid);

            String label = entry.name;
            if (suffixProvider != null) {
                String suffix = suffixProvider.apply(entry.uuid);
                if (suffix != null && !suffix.isEmpty()) {
                    label = entry.name + " [" + suffix + "]";
                }
            }

            String finalLabel = label;
            Consumer<String> activateAction = this.onActivate;
            Consumer<String> deactivateAction = this.onDeactivate;

            ButtonWidget<?> btn = new ButtonWidget<>();
            btn.widthRel(1f).height(PanelSizes.BTN_H).padding(4, 0, 0, 0);
            btn.overlay(IKey.str(finalLabel).color(color).shadow(true).alignment(Alignment.CenterLeft));

            if (clickable && (activateAction != null || deactivateAction != null)) {
                String playerName = entry.name;
                boolean isActive = entry.active;
                btn.onMousePressed(b -> {
                    if (isActive && deactivateAction != null) {
                        deactivateAction.accept(playerName);
                    } else if (!isActive && activateAction != null) {
                        activateAction.accept(playerName);
                    }
                    return true;
                });
            }

            if (tooltipKey != null && !tooltipKey.isEmpty()) {
                btn.addTooltipLine(IKey.lang(tooltipKey));
            }

            return Flow.row()
                    .widthRel(1f).height(PanelSizes.BTN_H)
                    .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                    .child(btn);
        }

        private static class PlayerEntry {

            final UUID uuid;
            final String name;
            final boolean active;

            PlayerEntry(UUID uuid, String name, boolean active) {
                this.uuid = uuid;
                this.name = name;
                this.active = active;
            }
        }
    }
}

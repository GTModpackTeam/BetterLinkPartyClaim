package com.github.gtexpert.blpc.client.gui.widget;

import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

import com.github.gtexpert.blpc.client.gui.GuiColors;
import com.github.gtexpert.blpc.common.ModConfig;
import com.github.gtexpert.blpc.common.party.RelationType;

/**
 * Generic reusable toast notification for BLPC events.
 * <p>
 * Uses the Builder pattern for flexible configuration.
 * The vanilla {@link IToast} API renders toasts in the top-right corner.
 *
 * <pre>
 * {@code
 * BLPCToast.builder()
 *     .fromTransit(RelationType.ENEMY, true, "PlayerName")
 *     .build();
 * }
 * </pre>
 */
public class BLPCToast implements IToast {

    private final String title;
    private final int color;
    private long firstDrawTime = -1L;

    private BLPCToast(String title, int color) {
        this.title = title;
        this.color = color;
    }

    @Override
    public Visibility draw(GuiToast toastGui, long delta) {
        if (firstDrawTime < 0) {
            firstDrawTime = delta;
        }

        toastGui.getMinecraft().getTextureManager()
                .bindTexture(TEXTURE_TOASTS);
        GlStateManager.color(1.0f, 1.0f, 1.0f);
        toastGui.drawTexturedModalRect(0, 0, 0, 0, 160, 32);

        toastGui.getMinecraft().fontRenderer.drawString(
                title, 7, 12, color);

        long elapsed = delta - firstDrawTime;
        return elapsed >= ModConfig.transitToastDuration ? Visibility.HIDE : Visibility.SHOW;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String titleKey = "";
        private Object[] titleArgs = {};
        private int color = GuiColors.WHITE;

        /** Sets the title lang key and arguments directly. */
        public Builder title(String langKey, Object... args) {
            this.titleKey = langKey;
            this.titleArgs = args;
            return this;
        }

        /** Sets the color for the toast text. */
        public Builder color(int argb) {
            this.color = argb;
            return this;
        }

        /**
         * Auto-configures title and color based on relation type and direction.
         *
         * @param relation   relationship between the transiting player and the chunk owner
         * @param entered    true if the player entered the chunk, false if they left
         * @param playerName display name of the transiting player
         */
        public Builder fromTransit(RelationType relation, boolean entered, String playerName) {
            String direction = entered ? "enter" : "leave";
            switch (relation) {
                case MEMBER -> {
                    this.titleKey = "blpc.transit.member." + direction;
                    this.color = GuiColors.GREEN;
                }
                case ALLY -> {
                    this.titleKey = "blpc.transit.ally." + direction;
                    this.color = GuiColors.GOLD;
                }
                case ENEMY -> {
                    this.titleKey = "blpc.transit.enemy." + direction;
                    this.color = GuiColors.RED;
                }
                case NONE -> this.titleKey = "";
            }
            this.titleArgs = new Object[] { playerName };
            return this;
        }

        /**
         * Configures the toast for a party event notification.
         *
         * @param eventType  event type string (e.g. "MEMBER_JOINED")
         * @param playerName name of the relevant player
         * @param partyName  party name or role name (context-dependent)
         */
        public Builder fromPartyEvent(String eventType, String playerName, String partyName) {
            switch (eventType) {
                case "MEMBER_JOINED" -> {
                    this.titleKey = "blpc.toast.member_joined";
                    this.titleArgs = new Object[] { playerName };
                    this.color = GuiColors.GREEN;
                }
                case "MEMBER_LEFT" -> {
                    this.titleKey = "blpc.toast.member_left";
                    this.titleArgs = new Object[] { playerName };
                    this.color = GuiColors.GRAY;
                }
                case "KICKED" -> {
                    this.titleKey = "blpc.toast.kicked";
                    this.titleArgs = new Object[] { playerName };
                    this.color = GuiColors.GRAY;
                }
                case "DISBANDED" -> {
                    this.titleKey = "blpc.toast.disbanded";
                    this.titleArgs = new Object[] {};
                    this.color = GuiColors.RED;
                }
                case "INVITE_RECEIVED" -> {
                    this.titleKey = "blpc.toast.invite_received";
                    this.titleArgs = new Object[] { playerName, partyName };
                    this.color = GuiColors.GOLD;
                }
                case "OWNER_TRANSFERRED" -> {
                    this.titleKey = "blpc.toast.owner_transferred";
                    this.titleArgs = new Object[] { playerName };
                    this.color = GuiColors.GOLD;
                }
                case "ROLE_CHANGED" -> {
                    this.titleKey = "blpc.toast.role_changed";
                    this.titleArgs = new Object[] { partyName };
                    this.color = GuiColors.GREEN;
                }
                case "BQU_LINKED" -> {
                    this.titleKey = "blpc.toast.bqu_linked";
                    this.titleArgs = new Object[] {};
                    this.color = GuiColors.WHITE;
                }
                case "BQU_UNLINKED" -> {
                    this.titleKey = "blpc.toast.bqu_unlinked";
                    this.titleArgs = new Object[] {};
                    this.color = GuiColors.WHITE;
                }
                default -> this.titleKey = "";
            }
            return this;
        }

        /**
         * Configures the toast for a claim failure notification.
         *
         * @param reason  failure reason ("CLAIM_LIMIT" or "FORCELOAD_LIMIT")
         * @param current current count
         * @param max     maximum allowed count
         */
        public Builder fromClaimFailed(String reason, int current, int max) {
            switch (reason) {
                case "CLAIM_LIMIT" -> {
                    this.titleKey = "blpc.toast.claim_limit";
                    this.titleArgs = new Object[] { current, max };
                    this.color = GuiColors.RED;
                }
                case "FORCELOAD_LIMIT" -> {
                    this.titleKey = "blpc.toast.forceload_limit";
                    this.titleArgs = new Object[] { current, max };
                    this.color = GuiColors.RED;
                }
                default -> this.titleKey = "";
            }
            return this;
        }

        public BLPCToast build() {
            String resolved = titleKey.isEmpty() ? "" : I18n.format(titleKey, titleArgs);
            return new BLPCToast(resolved, color);
        }
    }
}

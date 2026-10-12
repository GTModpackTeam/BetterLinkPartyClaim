package com.github.gtexpert.blpc.client.gui;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;

/**
 * Renders a Minecraft player's face (8x8 from the 64x64 skin texture)
 * with the hat overlay layer, usable as an {@link IDrawable} in MUI widgets.
 */
public class PlayerFaceDrawable implements IDrawable {

    private static final float FACE_U0 = 8f / 64f;
    private static final float FACE_V0 = 8f / 64f;
    private static final float FACE_U1 = 16f / 64f;
    private static final float FACE_V1 = 16f / 64f;

    private static final float HAT_U0 = 40f / 64f;
    private static final float HAT_V0 = 8f / 64f;
    private static final float HAT_U1 = 48f / 64f;
    private static final float HAT_V1 = 16f / 64f;

    private final UUID playerUUID;

    public PlayerFaceDrawable(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
        ResourceLocation skin = resolveSkin(playerUUID);
        GuiDraw.drawTexture(skin, x, y, x + width, y + height, FACE_U0, FACE_V0, FACE_U1, FACE_V1, true);
        GuiDraw.drawTexture(skin, x, y, x + width, y + height, HAT_U0, HAT_V0, HAT_U1, HAT_V1, true);
    }

    /** Resolves a player's skin texture, falling back to the default Steve/Alex skin if not yet loaded. */
    public static ResourceLocation resolveSkin(UUID playerUUID) {
        NetHandlerPlayClient conn = Minecraft.getMinecraft().getConnection();
        if (conn != null) {
            NetworkPlayerInfo info = conn.getPlayerInfo(playerUUID);
            if (info != null) {
                return info.getLocationSkin();
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(playerUUID);
    }

    @Override
    public int getDefaultWidth() {
        return 8;
    }

    @Override
    public int getDefaultHeight() {
        return 8;
    }
}

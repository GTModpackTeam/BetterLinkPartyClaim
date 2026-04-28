package com.github.gtexpert.blpc.client.gui;

/**
 * Shared ARGB color constants for all BLPC GUI components.
 * <p>
 * Values match the Minecraft color palette.
 * Format: {@code 0xAARRGGBB} (fully opaque = {@code 0xFF} alpha).
 */
public final class GuiColors {

    /** White — titles, borders, default text. Matches {@link TextFormatting#WHITE} (\u00a7f). */
    public static final int WHITE = 0xFFFFFFFF;
    /** Gold — OWNER role, section headers. Matches {@link TextFormatting#GOLD} (\u00a76). */
    public static final int GOLD = 0xFFFFAA00;
    /** Green — ADMIN role, active items. Matches {@link TextFormatting#GREEN} (\u00a7a). */
    public static final int GREEN = 0xFF55FF55;
    /** Red — warnings, limit exceeded. Matches {@link TextFormatting#RED} (\u00a7c). */
    public static final int RED = 0xFFFF5555;
    /** Gray — sub-text, messages. Matches {@link TextFormatting#GRAY} (\u00a77). */
    public static final int GRAY = 0xFFAAAAAA;
    /** Light gray — inactive items, non-members. */
    public static final int GRAY_LIGHT = 0xFFCCCCCC;
    /** Semi-transparent white — button hover background. */
    public static final int HOVER = 0x40FFFFFF;
    /** Semi-transparent white — section divider line. */
    public static final int DIVIDER = 0x30FFFFFF;

    private GuiColors() {}
}

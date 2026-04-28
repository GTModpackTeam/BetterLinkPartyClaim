package com.github.gtexpert.blpc.client.gui.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.value.EnumValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ColorPickerDialog;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.menu.DropdownWidget;

import com.github.gtexpert.blpc.client.gui.GuiColors;
import com.github.gtexpert.blpc.client.gui.PlayerFaceDrawable;
import com.github.gtexpert.blpc.client.gui.party.widget.InputDialog;
import com.github.gtexpert.blpc.common.network.MessagePartyAction;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.TrustAction;
import com.github.gtexpert.blpc.common.party.TrustLevel;

/**
 * Protection and party settings panel (panel ID: {@value #PANEL_ID}).
 * <p>
 * Uses a {@link PagedWidget} with four tabs:
 * <ul>
 * <li><b>Party Info</b> — name, description, color, free-to-join</li>
 * <li><b>Protection</b> — trust levels per action, FakePlayer trust, explosion protection</li>
 * <li><b>Allies</b> — manage allied parties</li>
 * <li><b>Enemies</b> — manage enemy parties</li>
 * </ul>
 */
public class SettingsPanel {

    public static final String PANEL_ID = "blpc.party.settings";
    private static final int BTN_H = PartyWidgets.BTN_H;

    private static final TrustLevel[] CYCLE_LEVELS = { TrustLevel.NONE, TrustLevel.ALLY, TrustLevel.MEMBER };

    private static final int TAB_H = 16;

    public static ModularPanel build(Party party) {
        ModularPanel panel = new ModularPanel(PANEL_ID);
        panel.size(PartyWidgets.LARGE_W, PartyWidgets.LARGE_H);

        PartyWidgets.addHeader(panel, "blpc.party.settings_title");

        // Tab controller
        var controller = new PagedWidget.Controller();

        // Tab button row
        var tabRow = Flow.row()
                .childPadding(2)
                .left(4).right(4).top(22).height(TAB_H)
                .child(new PageButton(0, controller).height(TAB_H).expanded()
                        .overlay(IKey.lang("blpc.party.settings_tab_party")))
                .child(new PageButton(1, controller).height(TAB_H).expanded()
                        .overlay(IKey.lang("blpc.party.settings_tab_protection")))
                .child(new PageButton(2, controller).height(TAB_H).expanded()
                        .overlay(IKey.lang("blpc.party.settings_tab_allies")))
                .child(new PageButton(3, controller).height(TAB_H).expanded()
                        .overlay(IKey.lang("blpc.party.settings_tab_enemies")));

        // Paged content area
        var pagedWidget = new PagedWidget<>()
                .controller(controller)
                .left(4).right(4).top(40).bottom(4)
                .addPage(buildPartyInfoPage(party, panel))
                .addPage(buildProtectionPage(party))
                .addPage(buildAlliesPage(party))
                .addPage(buildEnemiesPage(party));

        panel.child(tabRow);
        panel.child(pagedWidget);

        return panel;
    }

    private static IWidget buildPartyInfoPage(Party party, ModularPanel panel) {
        var list = newList();

        // Pre-create handlers to avoid "same panel handler already exists" on repeated clicks.
        IPanelHandler renameHandler = IPanelHandler.simple(panel, (pp, player) -> InputDialog
                .builder("blpc.party.dialog.rename")
                .title("blpc.party.name_field")
                .defaultValue(party.getName())
                .confirmLabel("blpc.map.yes")
                .onSubmit(text -> {
                    party.setName(text);
                    ModNetwork.INSTANCE.sendToServer(MessagePartyAction.rename(text));
                })
                .build(), true);

        IPanelHandler descHandler = IPanelHandler.simple(panel, (pp, player) -> InputDialog
                .builder("blpc.party.dialog.description")
                .title("blpc.party.description_field")
                .defaultValue(party.getDescription())
                .confirmLabel("blpc.map.yes")
                .onSubmit(text -> {
                    party.setDescription(text);
                    ModNetwork.INSTANCE.sendToServer(MessagePartyAction.setDescription(text));
                })
                .build(), true);

        IPanelHandler colorHandler = IPanelHandler.simple(panel, (pp, player) -> {
            int startArgb = 0xFF000000 | (party.getColor() & 0xFFFFFF);
            var dialog = new ColorPickerDialog(color -> {
                int rgb = color & 0xFFFFFF;
                party.setColor(rgb);
                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.setColor(rgb));
            }, startArgb, false);
            dialog.setCloseOnOutOfBoundsClick(true);
            return dialog;
        }, true);

        list.child(PartyWidgets.createActionButton(
                IKey.dynamic(() -> IKey.lang("blpc.party.name_field").get() + ": " + party.getName())
                        .alignment(Alignment.CenterLeft),
                "Edit name",
                () -> {
                    renameHandler.deleteCachedPanel();
                    renameHandler.openPanel();
                })
                .addTooltipLine(underlineKey("blpc.party.tooltip.name"))
                .addTooltipLine(IKey.dynamic(() -> defaultTooltip("\"\"")))
                .widthRel(1f).height(BTN_H).padding(4, 0, 0, 0));

        list.child(PartyWidgets.createActionButton(
                IKey.dynamic(() -> IKey.lang("blpc.party.description_field").get() + ": " +
                        (party.getDescription().isEmpty() ? "-" : party.getDescription()))
                        .alignment(Alignment.CenterLeft),
                "Edit description",
                () -> {
                    descHandler.deleteCachedPanel();
                    descHandler.openPanel();
                })
                .addTooltipLine(underlineKey("blpc.party.tooltip.description"))
                .addTooltipLine(IKey.dynamic(() -> defaultTooltip("\"\"")))
                .widthRel(1f).height(BTN_H).padding(4, 0, 0, 0));

        // Divider between name/description and color/free-to-join
        list.child(new Rectangle().color(GuiColors.DIVIDER).asWidget().height(1).widthRel(1f).marginTop(4)
                .marginBottom(4));

        list.child(PartyWidgets.createActionButton(
                IKey.dynamic(() -> IKey.lang("blpc.party.color").get() + ": " + formatColorHex(party.getColor()))
                        .alignment(Alignment.CenterLeft),
                "Pick color",
                () -> {
                    colorHandler.deleteCachedPanel();
                    colorHandler.openPanel();
                })
                .addTooltipLine(underlineKey("blpc.party.tooltip.color"))
                .widthRel(1f).height(BTN_H).padding(4, 0, 0, 0));

        list.child(new ToggleButton()
                .widthRel(1f).height(BTN_H).padding(4, 0, 0, 0)
                .value(new BoolValue.Dynamic(
                        party::isFreeToJoin,
                        val -> {
                            party.setFreeToJoin(val);
                            ModNetwork.INSTANCE.sendToServer(MessagePartyAction.setFreeToJoin(val));
                        }))
                .overlay(false, IKey.lang("blpc.party.free_to_join_off").alignment(Alignment.CenterLeft))
                .overlay(true, IKey.lang("blpc.party.free_to_join_on").alignment(Alignment.CenterLeft))
                .addTooltipLine(underlineKey("blpc.party.tooltip.free_to_join"))
                .addTooltipLine(IKey.dynamic(() -> defaultTooltip("false"))));

        // Max members: label + slider (0 = unlimited, max = 100)
        list.child(IKey.dynamic(() -> buildMaxMembersLabel(party))
                .alignment(Alignment.CenterLeft)
                .asWidget().widthRel(1f).height(10).marginLeft(4).marginTop(4)
                .addTooltipLine(underlineKey("blpc.party.tooltip.max_members"))
                .addTooltipLine(IKey.dynamic(() -> defaultTooltip("0"))));

        list.child(new SliderWidget()
                .widthRel(1f).height(10).marginLeft(4).marginRight(4).marginBottom(4)
                .bounds(0, 100).stopper(1)
                .value(new DoubleValue.Dynamic(
                        () -> party.getMaxMembers(),
                        val -> {
                            int max = (int) Math.round(val);
                            if (max == party.getMaxMembers()) return;
                            party.setMaxMembers(max);
                            ModNetwork.INSTANCE.sendToServer(MessagePartyAction.setMaxMembers(max));
                        })));

        return list;
    }

    private static String buildMaxMembersLabel(Party party) {
        int max = party.getMaxMembers();
        int current = party.getMembers().size();
        String value = max == 0 ? IKey.lang("blpc.party.max_members_unlimited").get() :
                current + " / " + max;
        return IKey.lang("blpc.party.max_members").get() + ": " + value;
    }

    private static IWidget buildProtectionPage(Party party) {
        var list = newList();

        for (TrustAction action : TrustAction.values()) {
            list.child(createTrustDropdown(party, action));
        }
        list.child(createFakePlayerDropdown(party));

        // Divider between trust settings and explosion toggle
        list.child(new Rectangle().color(GuiColors.DIVIDER).asWidget().height(1).widthRel(1f).marginTop(4)
                .marginBottom(4));

        list.child(new ToggleButton()
                .widthRel(1f).height(BTN_H).padding(4, 0, 0, 0)
                .value(new BoolValue.Dynamic(
                        party::protectsExplosions,
                        val -> {
                            party.setProtectExplosions(val);
                            ModNetwork.INSTANCE.sendToServer(MessagePartyAction.setExplosionProtection(val));
                        }))
                .overlay(false, IKey.lang("blpc.party.explosion_off").alignment(Alignment.CenterLeft))
                .overlay(true, IKey.lang("blpc.party.explosion_on").alignment(Alignment.CenterLeft))
                .addTooltipLine(underlineKey("blpc.party.tooltip.explosion"))
                .addTooltipLine(IKey.dynamic(() -> defaultTooltip("true"))));

        return list;
    }

    private static IWidget buildAlliesPage(Party party) {
        return buildTrustPage(party, false);
    }

    private static IWidget buildEnemiesPage(Party party) {
        return buildTrustPage(party, true);
    }

    /**
     * Builds the inner two-tab (Parties / Players) layout for ally or enemy management.
     * Toggle buttons update color in-place via {@link IKey#dynamicKey} — panel stays open.
     */
    private static IWidget buildTrustPage(Party party, boolean isEnemy) {
        var controller = new PagedWidget.Controller();

        var tabRow = Flow.row()
                .childPadding(2)
                .widthRel(1f).height(TAB_H)
                .child(new PageButton(0, controller).height(TAB_H).expanded()
                        .overlay(IKey.lang("blpc.party.tab.parties")))
                .child(new PageButton(1, controller).height(TAB_H).expanded()
                        .overlay(IKey.lang("blpc.party.tab.players")));

        var pagedContent = new PagedWidget<>()
                .controller(controller)
                .widthRel(1f).expanded()
                .addPage(buildTrustPartyList(party, isEnemy))
                .addPage(buildTrustPlayerList(party, isEnemy));

        return Flow.column()
                .widthRel(1f).heightRel(1f)
                .child(tabRow)
                .child(pagedContent);
    }

    private static IWidget buildTrustPartyList(Party party, boolean isEnemy) {
        var list = newList();
        final UUID myPartyId = party.getPartyId();
        Collection<Party> allParties = ClientPartyCache.getAllParties();
        var widgets = new ArrayList<IWidget>();
        var searchNames = new ArrayList<String>();

        for (Party other : allParties) {
            if (other.getPartyId().equals(myPartyId)) continue;
            final UUID pid = other.getPartyId();
            final String name = other.getName();

            var btn = new ButtonWidget<>()
                    .widthRel(1f).height(BTN_H).padding(4, 0, 0, 0)
                    .hoverBackground(new Rectangle().color(GuiColors.HOVER))
                    .overlay(IKey.dynamicKey(() -> {
                        int col = trustColor(party, pid);
                        return IKey.str(name).color(col).alignment(Alignment.CenterLeft);
                    }))
                    .addTooltipLine(trustTooltip(isEnemy))
                    .onMousePressed(b -> {
                        toggleTrust(party, pid, isEnemy);
                        return true;
                    });

            widgets.add(btn);
            searchNames.add(name.toLowerCase(Locale.ROOT));
            list.child(btn);
        }

        if (widgets.isEmpty()) {
            list.child(IKey.lang("blpc.party.no_other_parties").color(GuiColors.GRAY)
                    .asWidget().widthRel(1f).height(BTN_H).marginLeft(4));
            return list;
        }

        return PartyWidgets.wrapWithSearchBox(list, widgets, searchNames);
    }

    private static IWidget buildTrustPlayerList(Party party, boolean isEnemy) {
        var list = newList();
        final UUID myPartyId = party.getPartyId();
        var conn = Minecraft.getMinecraft().getConnection();
        if (conn == null) return list;

        var widgets = new ArrayList<IWidget>();
        var searchNames = new ArrayList<String>();

        for (NetworkPlayerInfo info : conn.getPlayerInfoMap()) {
            UUID playerUUID = info.getGameProfile().getId();
            String playerName = info.getGameProfile().getName();
            Party playerParty = ClientPartyCache.getPartyByPlayer(playerUUID);

            if (playerParty != null && playerParty.getPartyId().equals(myPartyId)) continue;

            if (playerParty == null) {
                String noPartyLabel = playerName + " (" + IKey.lang("blpc.party.tab.no_party").get() + ")";
                var row = Flow.row()
                        .widthRel(1f).height(BTN_H)
                        .padding(4, 0, 0, 0)
                        .childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                        .child(new PlayerFaceDrawable(playerUUID).asWidget().size(PartyWidgets.FACE_SIZE,
                                PartyWidgets.FACE_SIZE))
                        .child(IKey.str(noPartyLabel).color(GuiColors.GRAY).alignment(Alignment.CenterLeft)
                                .asWidget().expanded());
                widgets.add(row);
                searchNames.add(playerName.toLowerCase(Locale.ROOT));
                list.child(row);
            } else {
                final UUID pid = playerParty.getPartyId();
                final String partyLabel = playerName + " (" + playerParty.getName() + ")";
                var btn = new ButtonWidget<>()
                        .widthRel(1f).height(BTN_H).padding(0)
                        .hoverBackground(new Rectangle().color(GuiColors.HOVER))
                        .child(Flow.row()
                                .widthRel(1f).heightRel(1f)
                                .padding(4, 0, 0, 0)
                                .childPadding(4)
                                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                                .child(new PlayerFaceDrawable(playerUUID).asWidget().size(PartyWidgets.FACE_SIZE,
                                        PartyWidgets.FACE_SIZE))
                                .child(IKey.dynamicKey(() -> IKey.str(partyLabel).color(trustColor(party, pid))
                                        .alignment(Alignment.CenterLeft)).asWidget().expanded()))
                        .addTooltipLine(trustTooltip(isEnemy))
                        .onMousePressed(b -> {
                            toggleTrust(party, pid, isEnemy);
                            return true;
                        });
                widgets.add(btn);
                searchNames.add(playerName.toLowerCase(Locale.ROOT));
                list.child(btn);
            }
        }

        if (widgets.isEmpty()) {
            list.child(IKey.lang("blpc.party.no_players_online").color(GuiColors.GRAY)
                    .asWidget().widthRel(1f).height(BTN_H).marginLeft(4));
            return list;
        }

        return PartyWidgets.wrapWithSearchBox(list, widgets, searchNames);
    }

    // --- Trust toggle helpers ---

    private static void toggleTrust(Party party, UUID pid, boolean isEnemy) {
        boolean active = isEnemy ? party.isEnemy(pid) : party.isAlly(pid);
        if (active) {
            if (isEnemy) {
                party.removeEnemy(pid);
                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.removeEnemy(pid));
            } else {
                party.removeAlly(pid);
                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.removeAlly(pid));
            }
        } else {
            if (isEnemy) {
                party.addEnemy(pid);
                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.addEnemy(pid));
            } else {
                party.addAlly(pid);
                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.addAlly(pid));
            }
        }
    }

    private static int trustColor(Party party, UUID pid) {
        if (party.isAlly(pid)) return GuiColors.GOLD;
        if (party.isEnemy(pid)) return GuiColors.RED;
        return GuiColors.WHITE;
    }

    private static IKey trustTooltip(boolean isEnemy) {
        return IKey.lang(isEnemy ? "blpc.party.tooltip.toggle_enemy" : "blpc.party.tooltip.toggle_ally");
    }

    @SuppressWarnings("unchecked")
    private static ListWidget<IWidget, ?> newList() {
        var list = new ListWidget<>();
        list.widthRel(1f).heightRel(1f);
        list.crossAxisAlignment(Alignment.CrossAxis.START);
        return list;
    }

    private static String defaultTooltip(String value) {
        return IKey.lang("blpc.party.tooltip.default", () -> new Object[] { TextFormatting.GREEN + value }).get();
    }

    /** Returns an underlined dynamic tooltip label for the given translation key. */
    private static IKey underlineKey(String langKey) {
        return IKey.dynamic(() -> TextFormatting.UNDERLINE + IKey.lang(langKey).get());
    }

    private static IWidget createTrustDropdown(Party party, TrustAction action) {
        return createTrustDropdownCommon(
                "blpc.trust." + action.getNbtKey(),
                () -> party.getTrustLevel(action),
                level -> {
                    if (level == party.getTrustLevel(action)) return;
                    party.setTrustLevel(action, level);
                    ModNetwork.INSTANCE.sendToServer(
                            MessagePartyAction.setTrustLevel(action.getNbtKey() + ":" + level.name()));
                },
                () -> buildTrustLabel(party, action),
                "blpc.party.tooltip.trust_level",
                () -> IKey.lang("blpc.party.trust_level." + action.getDefaultLevel().name().toLowerCase(Locale.ROOT))
                        .get());
    }

    private static IWidget createFakePlayerDropdown(Party party) {
        return createTrustDropdownCommon(
                "blpc.trust.fakeplayer",
                party::getFakePlayerTrustLevel,
                level -> {
                    if (level == party.getFakePlayerTrustLevel()) return;
                    party.setFakePlayerTrustLevel(level);
                    ModNetwork.INSTANCE.sendToServer(MessagePartyAction.setFakePlayerTrust(level.name()));
                },
                () -> buildFakePlayerLabel(party),
                "blpc.party.tooltip.fakeplayer",
                () -> IKey.lang("blpc.party.trust_level." + TrustLevel.ALLY.name().toLowerCase(Locale.ROOT)).get());
    }

    private static IWidget createTrustDropdownCommon(
                                                     String panelName,
                                                     Supplier<TrustLevel> getter,
                                                     Consumer<TrustLevel> setter,
                                                     Supplier<String> labelBuilder,
                                                     String tooltipKey,
                                                     Supplier<String> defaultValueBuilder) {
        return new DropdownWidget<>(panelName, TrustLevel.class)
                .options(CYCLE_LEVELS)
                .optionToWidget((level, forSelected) -> {
                    if (forSelected) {
                        return IKey.dynamic(labelBuilder::get)
                                .color(GuiColors.WHITE)
                                .shadow(true)
                                .alignment(Alignment.CenterLeft)
                                .asWidget()
                                .widthRel(1f).heightRel(1f).padding(4, 0, 0, 0);
                    }
                    // Menu items render inside MenuPanel (context_menu theme):
                    // theme provides "menu" background, #404040 text, no shadow.
                    String roleStr = IKey.lang(
                            "blpc.party.trust_level." + level.name().toLowerCase(Locale.ROOT)).get();
                    return IKey.str(roleStr)
                            .alignment(Alignment.CenterLeft)
                            .asWidget()
                            .widthRel(1f).padding(4, 0, 0, 0);
                })
                .value(new EnumValue.Dynamic<>(TrustLevel.class, getter, setter))
                .widthRel(1f).height(BTN_H).marginBottom(2)
                .addTooltipLine(underlineKey(tooltipKey))
                .addTooltipLine(IKey.dynamic(() -> defaultTooltip(defaultValueBuilder.get())));
    }

    private static String buildTrustLabel(Party party, TrustAction action) {
        TrustLevel current = party.getTrustLevel(action);
        return IKey.lang("blpc.party.trust." + action.getNbtKey()).get() + ": " +
                IKey.lang("blpc.party.trust_level." + current.name().toLowerCase(Locale.ROOT)).get();
    }

    private static String buildFakePlayerLabel(Party party) {
        TrustLevel level = party.getFakePlayerTrustLevel();
        return IKey.lang("blpc.party.fakeplayer_trust").get() + ": " +
                IKey.lang("blpc.party.trust_level." + level.name().toLowerCase(Locale.ROOT)).get();
    }

    private static String formatColorHex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}

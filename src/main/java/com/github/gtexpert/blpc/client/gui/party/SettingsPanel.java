package com.github.gtexpert.blpc.client.gui.party;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;

import com.github.gtexpert.blpc.client.gui.GuiColors;
import com.github.gtexpert.blpc.client.gui.party.widget.InputDialog;
import com.github.gtexpert.blpc.client.gui.party.widget.PartySelectDialog;
import com.github.gtexpert.blpc.common.network.MessagePartyAction;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.Party;
import com.github.gtexpert.blpc.common.party.TrustAction;
import com.github.gtexpert.blpc.common.party.TrustLevel;

/**
 * Protection and party settings panel (panel ID: {@value #PANEL_ID}).
 * <p>
 * Uses a scrollable {@link ListWidget} with section headers to organize
 * settings into four groups:
 * <ul>
 * <li><b>Protection</b> — trust levels per action, FakePlayer trust, explosion protection</li>
 * <li><b>Allies</b> — manage allied parties</li>
 * <li><b>Enemies</b> — manage enemy parties</li>
 * <li><b>Party</b> — free to join, color, title, description</li>
 * </ul>
 */
public class SettingsPanel {

    public static final String PANEL_ID = "blpc.party.settings";
    private static final int W = PanelSizes.LARGE_W;
    private static final int BTN_H = PanelSizes.BTN_H;

    private static final TrustLevel[] CYCLE_LEVELS = { TrustLevel.NONE, TrustLevel.ALLY, TrustLevel.MEMBER };

    private static final EnumDyeColor[] DYE_COLORS = EnumDyeColor.values();

    public static ModularPanel build(Party party) {
        ModularPanel panel = new ModularPanel(PANEL_ID);
        panel.size(PanelSizes.LARGE_W, PanelSizes.LARGE_H);

        Runnable refreshPanel = () -> PartyWidgets.reopenPanel(panel, () -> SettingsPanel.build(party));

        PanelBuilder.addHeader(panel, "blpc.party.settings_title");

        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.left(4).right(4).top(24).bottom(4);
        list.crossAxisAlignment(Alignment.CrossAxis.START);

        // --- Party Info section ---
        list.child(sectionHeader("blpc.party.settings_party"));

        list.child(PartyWidgets.createActionButton(
                IKey.dynamic(() -> IKey.lang("blpc.party.name_field").get() + ": " + party.getName())
                        .alignment(Alignment.CenterLeft),
                "Edit name",
                () -> IPanelHandler.simple(panel, (pp, player) -> InputDialog
                        .builder("blpc.party.dialog.rename")
                        .title("blpc.party.name_field")
                        .defaultValue(party.getName())
                        .confirmLabel("blpc.map.yes")
                        .onSubmit(text -> {
                            party.setName(text);
                            ModNetwork.INSTANCE.sendToServer(MessagePartyAction.rename(text));
                        })
                        .build(), true).openPanel())
                .addTooltipLine(
                        IKey.dynamic(() -> TextFormatting.UNDERLINE + IKey.lang("blpc.party.tooltip.name").get()))
                .addTooltipLine(IKey.dynamic(() -> defaultTooltip("\"\"")))
                .size(W - 16, BTN_H).padding(4, 0, 0, 0));

        list.child(PartyWidgets.createActionButton(
                IKey.dynamic(() -> IKey.lang("blpc.party.description_field").get() + ": " +
                        (party.getDescription().isEmpty() ? "-" : party.getDescription()))
                        .alignment(Alignment.CenterLeft),
                "Edit description",
                () -> IPanelHandler.simple(panel, (pp, player) -> InputDialog
                        .builder("blpc.party.dialog.description")
                        .title("blpc.party.description_field")
                        .defaultValue(party.getDescription())
                        .confirmLabel("blpc.map.yes")
                        .onSubmit(text -> {
                            party.setDescription(text);
                            ModNetwork.INSTANCE.sendToServer(MessagePartyAction.setDescription(text));
                        })
                        .build(), true).openPanel())
                .addTooltipLine(IKey
                        .dynamic(() -> TextFormatting.UNDERLINE + IKey.lang("blpc.party.tooltip.description").get()))
                .addTooltipLine(IKey.dynamic(() -> defaultTooltip("\"\"")))
                .size(W - 16, BTN_H).padding(4, 0, 0, 0));

        CycleButtonWidget colorBtn = new CycleButtonWidget()
                .size(W - 16, BTN_H).padding(4, 0, 0, 0)
                .stateCount(DYE_COLORS.length)
                .value(new IntValue.Dynamic(
                        () -> colorToIndex(party.getColor()),
                        val -> {
                            int color = dyeColorValue(DYE_COLORS[val]);
                            party.setColor(color);
                            ModNetwork.INSTANCE.sendToServer(MessagePartyAction.setColor(color));
                        }))
                .overlay(IKey.dynamic(() -> buildColorLabel(party)).alignment(Alignment.CenterLeft));
        colorBtn.addTooltipLine(
                IKey.dynamic(() -> TextFormatting.UNDERLINE + IKey.lang("blpc.party.tooltip.color").get()));
        colorBtn.addTooltipLine(IKey.dynamic(() -> defaultTooltip(getDyeColorName(EnumDyeColor.BLUE))));
        colorBtn.addTooltipLine(IKey.str(""));
        colorBtn.addTooltipLine(IKey.dynamic(() -> buildColorSelectionList(party.getColor())));
        list.child(colorBtn);

        list.child(new ToggleButton()
                .size(W - 16, BTN_H).padding(4, 0, 0, 0)
                .value(new BoolValue.Dynamic(
                        party::isFreeToJoin,
                        val -> {
                            party.setFreeToJoin(val);
                            ModNetwork.INSTANCE.sendToServer(MessagePartyAction.setFreeToJoin(val));
                        }))
                .overlay(false, IKey.lang("blpc.party.free_to_join_off").alignment(Alignment.CenterLeft))
                .overlay(true, IKey.lang("blpc.party.free_to_join_on").alignment(Alignment.CenterLeft))
                .addTooltipLine(IKey
                        .dynamic(() -> TextFormatting.UNDERLINE + IKey.lang("blpc.party.tooltip.free_to_join").get()))
                .addTooltipLine(IKey.dynamic(() -> defaultTooltip("false"))));

        // --- Protection section ---
        list.child(sectionHeader("blpc.party.settings_protection").marginTop(8));

        for (TrustAction action : TrustAction.values()) {
            list.child(createTrustCycleButton(party, action));
        }
        list.child(createFakePlayerCycleButton(party));

        list.child(new ToggleButton()
                .size(W - 16, BTN_H).padding(4, 0, 0, 0)
                .value(new BoolValue.Dynamic(
                        party::protectsExplosions,
                        val -> {
                            party.setProtectExplosions(val);
                            ModNetwork.INSTANCE.sendToServer(MessagePartyAction.setExplosionProtection(val));
                        }))
                .overlay(false, IKey.lang("blpc.party.explosion_off").alignment(Alignment.CenterLeft))
                .overlay(true, IKey.lang("blpc.party.explosion_on").alignment(Alignment.CenterLeft))
                .addTooltipLine(
                        IKey.dynamic(() -> TextFormatting.UNDERLINE + IKey.lang("blpc.party.tooltip.explosion").get()))
                .addTooltipLine(IKey.dynamic(() -> defaultTooltip("true"))));

        list.child(sectionHeader("blpc.party.settings_allies").marginTop(8));

        // Show current allies as removable entries
        list.children(party.getAllies(), allyId -> {
            String allyName = party.getAllyPartyName(allyId);
            return new ButtonWidget<>().size(W - 16, BTN_H).padding(4, 0, 0, 0)
                    .overlay(IKey.str(TextFormatting.YELLOW.toString() + allyName).alignment(Alignment.CenterLeft))
                    .addTooltipLine(IKey.lang("blpc.party.tooltip.remove_ally"))
                    .onMousePressed(btn -> {
                        party.removeAlly(allyId);
                        ModNetwork.INSTANCE.sendToServer(MessagePartyAction.removeAlly(allyId));
                        refreshPanel.run();
                        return true;
                    });
        });

        final UUID myPartyId = party.getPartyId();
        list.child(PartyWidgets.createActionButton(
                IKey.lang("blpc.party.add_ally").alignment(Alignment.CenterLeft),
                "Add ally party",
                () -> {
                    Set<UUID> excluded = new HashSet<>(party.getAllies());
                    excluded.addAll(party.getEnemies());
                    excluded.add(myPartyId);
                    IPanelHandler.simple(panel, (pp, player) -> PartySelectDialog
                            .builder("blpc.party.dialog.add_ally")
                            .excluded(excluded)
                            .onSelect(selectedId -> {
                                party.addAlly(selectedId);
                                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.addAlly(selectedId));
                                refreshPanel.run();
                            })
                            .build(), true).openPanel();
                })
                .size(W - 16, BTN_H).padding(4, 0, 0, 0));

        list.child(sectionHeader("blpc.party.settings_enemies").marginTop(8));

        // Show current enemies as removable entries
        list.children(party.getEnemies(), enemyId -> {
            String enemyName = party.getEnemyPartyName(enemyId);
            return new ButtonWidget<>().size(W - 16, BTN_H).padding(4, 0, 0, 0)
                    .overlay(IKey.str(TextFormatting.RED.toString() + enemyName).alignment(Alignment.CenterLeft))
                    .addTooltipLine(IKey.lang("blpc.party.tooltip.remove_enemy"))
                    .onMousePressed(btn -> {
                        party.removeEnemy(enemyId);
                        ModNetwork.INSTANCE.sendToServer(MessagePartyAction.removeEnemy(enemyId));
                        refreshPanel.run();
                        return true;
                    });
        });

        list.child(PartyWidgets.createActionButton(
                IKey.lang("blpc.party.add_enemy").alignment(Alignment.CenterLeft),
                "Add enemy party",
                () -> {
                    Set<UUID> excluded = new HashSet<>(party.getAllies());
                    excluded.addAll(party.getEnemies());
                    excluded.add(myPartyId);
                    IPanelHandler.simple(panel, (pp, player) -> PartySelectDialog
                            .builder("blpc.party.dialog.add_enemy")
                            .excluded(excluded)
                            .onSelect(selectedId -> {
                                party.addEnemy(selectedId);
                                ModNetwork.INSTANCE.sendToServer(MessagePartyAction.addEnemy(selectedId));
                                refreshPanel.run();
                            })
                            .build(), true).openPanel();
                })
                .size(W - 16, BTN_H).padding(4, 0, 0, 0));

        panel.child(list);

        // Re-build panel on server sync so ally/enemy lists stay current
        PartyWidgets.addPartyRefreshListener(panel, party.getPartyId(), SettingsPanel::build);

        return panel;
    }

    private static String defaultTooltip(String value) {
        return I18n.format("blpc.party.tooltip.default", TextFormatting.GREEN + value);
    }

    private static TextWidget<?> sectionHeader(String langKey) {
        return IKey.lang(langKey).color(GuiColors.GOLD).shadow(true)
                .asWidget().height(14).widthRel(1f);
    }

    private static CycleButtonWidget createTrustCycleButton(Party party, TrustAction action) {
        return createCycleButton(
                () -> trustLevelToIndex(party.getTrustLevel(action)),
                val -> {
                    TrustLevel level = CYCLE_LEVELS[val];
                    party.setTrustLevel(action, level);
                    ModNetwork.INSTANCE.sendToServer(
                            MessagePartyAction.setTrustLevel(action.getNbtKey() + ":" + level.name()));
                },
                () -> buildTrustLabel(party, action),
                "blpc.party.tooltip.trust_level",
                () -> IKey.lang("blpc.party.trust_level." + action.getDefaultLevel().name().toLowerCase(Locale.ROOT))
                        .get(),
                () -> party.getTrustLevel(action));
    }

    private static CycleButtonWidget createFakePlayerCycleButton(Party party) {
        return createCycleButton(
                () -> trustLevelToIndex(party.getFakePlayerTrustLevel()),
                val -> {
                    TrustLevel level = CYCLE_LEVELS[val];
                    party.setFakePlayerTrustLevel(level);
                    ModNetwork.INSTANCE.sendToServer(MessagePartyAction.setFakePlayerTrust(level.name()));
                },
                () -> buildFakePlayerLabel(party),
                "blpc.party.tooltip.fakeplayer",
                () -> IKey.lang("blpc.party.trust_level." + TrustLevel.ALLY.name().toLowerCase(Locale.ROOT)).get(),
                party::getFakePlayerTrustLevel);
    }

    private static CycleButtonWidget createCycleButton(
                                                       IntSupplier getter, IntConsumer setter,
                                                       Supplier<String> labelBuilder,
                                                       String tooltipKey, Supplier<String> defaultValueBuilder,
                                                       Supplier<TrustLevel> currentLevelForList) {
        var btn = new CycleButtonWidget()
                .size(W - 16, BTN_H).padding(4, 0, 0, 0)
                .stateCount(CYCLE_LEVELS.length)
                .value(new IntValue.Dynamic(getter, setter))
                .overlay(IKey.dynamic(labelBuilder::get).alignment(Alignment.CenterLeft));
        btn.addTooltipLine(
                IKey.dynamic(() -> TextFormatting.UNDERLINE + IKey.lang(tooltipKey).get()));
        btn.addTooltipLine(IKey.dynamic(() -> defaultTooltip(defaultValueBuilder.get())));
        btn.addTooltipLine(IKey.str(""));
        btn.addTooltipLine(IKey.dynamic(() -> buildSelectionList(currentLevelForList.get())));
        return btn;
    }

    private static int trustLevelToIndex(TrustLevel level) {
        return Math.max(0, Arrays.asList(CYCLE_LEVELS).indexOf(level));
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

    private static <T> String buildOptionList(T[] options, T current, Function<T, String> nameResolver) {
        StringBuilder sb = new StringBuilder();
        for (T option : options) {
            if (option == current) {
                sb.append(TextFormatting.GREEN).append("+ ").append(nameResolver.apply(option));
            } else {
                sb.append(TextFormatting.GRAY).append("- ").append(nameResolver.apply(option));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private static String buildSelectionList(TrustLevel current) {
        return buildOptionList(CYCLE_LEVELS, current,
                level -> IKey.lang("blpc.party.trust_level." + level.name().toLowerCase(Locale.ROOT)).get());
    }

    private static int dyeColorValue(EnumDyeColor dye) {
        return dye.getColorValue();
    }

    private static int colorToIndex(int color) {
        for (int i = 0; i < DYE_COLORS.length; i++) {
            if (dyeColorValue(DYE_COLORS[i]) == color) return i;
        }
        return 0;
    }

    private static String getDyeColorName(EnumDyeColor dye) {
        return I18n.format("item.fireworksCharge." + dye.getTranslationKey());
    }

    private static String getColorName(int color) {
        for (EnumDyeColor dye : DYE_COLORS) {
            if (dyeColorValue(dye) == color) return getDyeColorName(dye);
        }
        return String.format("#%06X", color);
    }

    private static String buildColorLabel(Party party) {
        return IKey.lang("blpc.party.color").get() + ": " + getColorName(party.getColor());
    }

    private static String buildColorSelectionList(int currentColor) {
        EnumDyeColor currentDye = null;
        for (EnumDyeColor dye : DYE_COLORS) {
            if (dyeColorValue(dye) == currentColor) {
                currentDye = dye;
                break;
            }
        }
        return buildOptionList(DYE_COLORS, currentDye, SettingsPanel::getDyeColorName);
    }
}

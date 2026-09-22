package com.github.gtexpert.blpc.client.gui.party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import com.github.gtexpert.blpc.api.party.Party;
import com.github.gtexpert.blpc.api.party.PartyRole;
import com.github.gtexpert.blpc.client.gui.BLPCColors;
import com.github.gtexpert.blpc.client.gui.PlayerFaceDrawable;
import com.github.gtexpert.blpc.client.gui.party.widget.LiveSearchableList;
import com.github.gtexpert.blpc.common.network.ModNetwork;
import com.github.gtexpert.blpc.common.party.ClientPartyCache;

/** Shared constants, layout helpers, and live-update plumbing for party UI panels. */
public final class PartyWidgets {

    public static final int STANDARD_W = 220;
    public static final int STANDARD_H = 180;
    public static final int LARGE_W = 260;
    public static final int LARGE_H = 220;
    public static final int DIALOG_W = 220;
    public static final int DIALOG_H = 70;
    public static final int BTN_H = 18;
    public static final int FACE_SIZE = 8;
    public static final int TAB_H = 16;
    /** Left text indent inside full-width row buttons. */
    public static final int ROW_INDENT = 4;
    /** Text-field / inline action-button height. */
    public static final int INPUT_H = 14;
    /** Inline submit/create button width. */
    public static final int SUBMIT_BTN_W = 50;
    /** Confirm-dialog yes/no button size. */
    public static final int CONFIRM_BTN_W = 80;
    public static final int CONFIRM_BTN_H = 16;
    /** Top inset that clears the header for a full-panel list (header + padding). */
    public static final int CONTENT_TOP = 22;

    private PartyWidgets() {}

    /** MUI reuses stale panel handlers by name — append a monotonic suffix each time. */
    public static String uniquePanelId(String base) {
        return base + "#" + System.nanoTime();
    }

    /** Centered title + close button at the top of the panel. */
    public static void addHeader(ModularPanel panel, IKey title) {
        panel.child(title.color(BLPCColors.text()).shadow(BLPCColors.textShadow())
                .asWidget().alignment(Alignment.Center).left(0).right(0).top(8).height(10));
        panel.child(ButtonWidget.panelCloseButton());
    }

    public static void addHeader(ModularPanel panel, String titleKey) {
        addHeader(panel, IKey.lang(titleKey));
    }

    /** Positions {@code list} below the header (top=22). */
    @SuppressWarnings("rawtypes")
    public static void addList(ModularPanel panel, ListWidget list) {
        list.left(8).right(8).top(CONTENT_TOP).bottom(8);
        list.crossAxisAlignment(Alignment.CrossAxis.START);
        panel.child(list);
    }

    /** Insets a full-panel content container below the header (matches {@link #addList}). */
    public static Flow fillBelowHeader(Flow content) {
        return content.margin(8, 8, CONTENT_TOP, 8);
    }

    /**
     * Resolves a player UUID via party name cache → local player → network info
     * → truncated UUID. The cache check covers offline players.
     */
    public static String getDisplayName(UUID uuid) {
        for (Party p : ClientPartyCache.getAllParties()) {
            String cached = p.getPlayerName(uuid);
            if (cached != null) return cached;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && mc.player.getUniqueID().equals(uuid)) {
            return mc.player.getName();
        }
        if (mc.getConnection() != null) {
            NetworkPlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) return info.getGameProfile().getName();
        }
        return uuid.toString().substring(0, 8);
    }

    public static int getRoleColor(PartyRole role) {
        return switch (role) {
            case OWNER -> BLPCColors.owner();
            case ADMIN -> BLPCColors.admin();
            // Member rows render on a gray button — white reads, black would not.
            case MEMBER -> BLPCColors.buttonText();
        };
    }

    /** Styles a label for rendering on a gray button: white text + drop shadow. */
    public static IKey buttonLabel(IKey label) {
        return label.color(BLPCColors.buttonText()).shadow(BLPCColors.buttonTextShadow());
    }

    /** {@link #buttonLabel} aligned to the left — the standard row-button label. */
    public static IKey buttonLabelLeft(IKey label) {
        return buttonLabel(label).alignment(Alignment.CenterLeft);
    }

    /**
     * Styles an arbitrary-colored label for a gray button (role colors): keeps {@code color}, adds shadow + left align.
     */
    public static IKey rowLabel(IKey label, int color) {
        return label.color(color).shadow(BLPCColors.buttonTextShadow()).alignment(Alignment.CenterLeft);
    }

    /** Button that opens {@code handler}'s dialog (rebuilding it first). */
    public static ButtonWidget<?> dialogButton(IKey label, IPanelHandler handler) {
        return (ButtonWidget<?>) new ButtonWidget<>()
                .overlay(buttonLabel(label))
                .onMousePressed(btn -> {
                    handler.deleteCachedPanel();
                    handler.openPanel();
                    return true;
                });
    }

    /**
     * Two-state toggle button styled like the rest of the party UI: full-width,
     * {@link #BTN_H} tall, left-aligned white+shadow labels for the off/on states.
     * Returned so callers can chain {@code addTooltipLine(...)}.
     */
    public static ToggleButton toggleButton(BoolValue.Dynamic value, String offKey, String onKey) {
        ToggleButton btn = new ToggleButton();
        btn.widthRel(1f).height(BTN_H).padding(ROW_INDENT, 0, 0, 0)
                .value(value)
                .overlay(false, buttonLabelLeft(IKey.lang(offKey)))
                .overlay(true, buttonLabelLeft(IKey.lang(onKey)));
        return btn;
    }

    /**
     * Sends {@code action} to the server, applies {@code optimistic} to the
     * cached party (if still present), then fires sync listeners so live-update
     * panels rebuild. Returns {@code true} for use as an {@code onMousePressed} body.
     */
    public static boolean sendAndApply(IMessage action, UUID partyId, Consumer<Party> optimistic) {
        ModNetwork.INSTANCE.sendToServer(action);
        Party p = ClientPartyCache.getParty(partyId);
        if (p != null) optimistic.accept(p);
        ClientPartyCache.fireSyncListeners();
        return true;
    }

    /**
     * Runs {@code onSync} on the next tick after each server sync. Auto-removed
     * on panel close. Deferred via {@link Minecraft#addScheduledTask} so a click
     * handler that triggers {@link ClientPartyCache#fireSyncListeners} doesn't
     * mutate the widget tree it's currently inside.
     */
    public static void addSyncRefreshListener(ModularPanel panel, Runnable onSync) {
        Runnable syncListener = () -> {
            if (!panel.isOpen()) return;
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (panel.isOpen()) onSync.run();
            });
        };
        ClientPartyCache.addSyncListener(syncListener);
        panel.onCloseAction(() -> ClientPartyCache.removeSyncListener(syncListener));
    }

    /** Closes only when {@code panel} is on top — leaves parent panels alone. */
    public static void closeIfTopMost(ModularPanel panel) {
        if (!panel.isOpen()) return;
        if (panel.getScreen() == null) return;
        if (panel.getScreen().getPanelManager().getTopMostPanel() != panel) return;
        panel.closeIfOpen();
    }

    /**
     * Re-fetches the live {@link Party} from {@link ClientPartyCache} on every
     * call. {@link ClientPartyCache#loadFromNBT} replaces every instance on
     * sync, so reads through this supplier stay current.
     */
    public static Supplier<Party> livePartyRef(UUID partyId, Party fallback) {
        return () -> {
            Party fresh = ClientPartyCache.getParty(partyId);
            return fresh != null ? fresh : fallback;
        };
    }

    /** Gray "no rows" placeholder sized to match a {@link #BTN_H} row. */
    public static IWidget emptyStateRow(String langKey) {
        return IKey.lang(langKey).color(BLPCColors.subtext())
                .asWidget().widthRel(1f).height(BTN_H).marginLeft(4);
    }

    /** Optimistically sets the local BQu link flag for the current player. */
    public static void setLocalBQuLinked(boolean linked) {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();
        ClientPartyCache.setLocalBQuLinked(playerId, linked);
        ClientPartyCache.fireSyncListeners();
    }

    /** Optimistically clears local party data (used after disband). */
    public static void clearLocalPartyData() {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();
        ClientPartyCache.setLocalBQuLinked(playerId, false);
        ClientPartyCache.clear();
        ClientPartyCache.fireSyncListeners();
    }

    /** Tab row + paged content positioned below the header. */
    public static void addTabs(ModularPanel panel, PagedWidget.Controller controller,
                               String[] labelKeys, IWidget[] pages) {
        var tabRow = buildTabRow(controller, labelKeys).left(4).right(4).top(22).height(TAB_H);
        var paged = buildPagedArea(controller, pages).left(4).right(4).top(40).bottom(4);
        panel.child(tabRow);
        panel.child(paged);
    }

    /** Tab row + paged content as a single {@link Flow#column} that fills its parent. */
    public static IWidget buildInnerTabs(String[] labelKeys, IWidget[] pages) {
        var controller = new PagedWidget.Controller();
        var tabRow = buildTabRow(controller, labelKeys).widthRel(1f).height(TAB_H);
        var paged = buildPagedArea(controller, pages).widthRel(1f).expanded();
        return Flow.column().widthRel(1f).heightRel(1f).child(tabRow).child(paged);
    }

    private static Flow buildTabRow(PagedWidget.Controller controller, String[] labelKeys) {
        var row = Flow.row().childPadding(2);
        for (int i = 0; i < labelKeys.length; i++) {
            row.child(new PageButton(i, controller).height(TAB_H).expanded()
                    .overlay(buttonLabel(IKey.lang(labelKeys[i]))));
        }
        return row;
    }

    private static PagedWidget<?> buildPagedArea(PagedWidget.Controller controller, IWidget[] pages) {
        var paged = new PagedWidget<>().controller(controller);
        for (IWidget page : pages) paged.addPage(page);
        return paged;
    }

    /**
     * Search field that toggles {@code setEnabled} on the parallel widgets to
     * filter via {@link ListWidget#collapseDisabledChild} (true by default).
     */
    public static Flow wrapWithSearchBox(ListWidget<IWidget, ?> list,
                                         List<IWidget> widgets, List<String> searchNames) {
        String[] filterText = { "" };
        var searchBox = new TextFieldWidget()
                .widthRel(1f).height(14)
                .hintText(IKey.lang("blpc.party.search").get())
                .autoUpdateOnChange(true)
                .value(new StringValue.Dynamic(
                        () -> filterText[0],
                        text -> {
                            filterText[0] = text;
                            String lower = text.toLowerCase(Locale.ROOT);
                            for (int i = 0; i < widgets.size(); i++) {
                                widgets.get(i).setEnabled(lower.isEmpty() || searchNames.get(i).contains(lower));
                            }
                        }));

        return Flow.column()
                .child(searchBox)
                .child(list.widthRel(1f).expanded());
    }

    /**
     * Adds an empty-state row and returns {@code list} when {@code widgets} is
     * empty; otherwise returns {@link #wrapWithSearchBox}. Use for one-shot
     * (non-live) searchable lists built from a snapshot.
     */
    public static IWidget finalizeSearchableList(ListWidget<IWidget, ?> list, List<IWidget> widgets,
                                                 List<String> searchNames, String emptyKey) {
        if (widgets.isEmpty()) {
            list.child(emptyStateRow(emptyKey));
            return list;
        }
        return wrapWithSearchBox(list, widgets, searchNames);
    }

    /** Formats {@code "Name [Role]"} (or just {@code name} when role is null). */
    public static String formatMemberLabel(String name, PartyRole role) {
        if (role == null) return name;
        String roleStr = IKey.lang("blpc.party.role." + role.name().toLowerCase(Locale.ROOT)).get();
        return name + " [" + roleStr + "]";
    }

    /**
     * Creates a face-icon + label row {@link Flow} from a {@link MemberEntry}.
     * Convenience for use in {@link ListWidget}s or other containers that require
     * {@code IWidget} children, avoiding direct field access from addon code.
     *
     * @param entry member data (role may be {@code null} for invite candidates)
     * @param color text color (e.g. {@link BLPCColors#text()})
     * @return a ready-to-use {@link Flow} widget with face icon and formatted label
     */
    public static Flow memberRow(MemberEntry entry, int color) {
        return faceRow(entry.uuid, rowLabel(IKey.str(formatMemberLabel(entry.name, entry.role)), color));
    }

    /** Face-icon + label as a {@link Flow#row}; the label is supplied pre-styled. */
    public static Flow faceRow(UUID uuid, IKey label) {
        return Flow.row()
                .widthRel(1f).heightRel(1f)
                .padding(ROW_INDENT, 0, 0, 0)
                .childPadding(ROW_INDENT)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new PlayerFaceDrawable(uuid).asWidget().size(FACE_SIZE, FACE_SIZE))
                .child(label.asWidget().expanded());
    }

    /** Clickable {@link #faceRow} with a colored, shadowed label. */
    public static ButtonWidget<?> createPlayerRow(UUID uuid, String label, int color) {
        var btn = new ButtonWidget<>();
        btn.widthRel(1f).height(BTN_H).padding(0);
        btn.child(faceRow(uuid, rowLabel(IKey.str(label), color)));
        return btn;
    }

    /** Text field that runs {@code onSubmit} on Enter. */
    public static TextFieldWidget createEnterSubmitTextField(Runnable onSubmit) {
        return new TextFieldWidget() {

            @Override
            public Interactable.Result onKeyPressed(char c, int keyCode) {
                if (keyCode == Keyboard.KEY_RETURN) {
                    onSubmit.run();
                    return Interactable.Result.SUCCESS;
                }
                return super.onKeyPressed(c, keyCode);
            }
        };
    }

    /** Full-width 1px section divider with the standard vertical margins. */
    public static IWidget divider() {
        return new Rectangle().color(BLPCColors.divider()).asWidget()
                .height(1).widthRel(1f).marginTop(4).marginBottom(4);
    }

    /** List sized to fill its parent page (used inside paged tab content). */
    @SuppressWarnings("rawtypes")
    public static ListWidget newPageList() {
        ListWidget list = new ListWidget<>();
        list.widthRel(1f).heightRel(1f);
        list.crossAxisAlignment(Alignment.CrossAxis.START);
        return list;
    }

    /** Dialog title (+ optional message) column, top-anchored. Shared by Confirm/Input dialogs. */
    public static Flow dialogHeader(String titleKey, @Nullable String messageKey) {
        Flow header = Flow.col()
                .childPadding(4)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .left(8).right(8).top(6);
        header.child(IKey.lang(titleKey).color(BLPCColors.text()).shadow(BLPCColors.textShadow()).asWidget());
        if (messageKey != null) {
            header.child(IKey.lang(messageKey).color(BLPCColors.subtext()).shadow(BLPCColors.textShadow())
                    .asWidget());
        }
        return header;
    }

    /**
     * Every member as a {@link MemberEntry}, sorted by {@link #byRoleThenName}, optionally
     * skipping one UUID (e.g. the acting player in a transfer/kick picker).
     */
    public static List<MemberEntry> collectSortedMembers(Party party, @Nullable UUID exclude) {
        return collectSortedMembers(party, exclude, null);
    }

    /**
     * Collects sorted member entries, optionally filtered by role predicate.
     * 
     * @param roleFilter if non-null, only members whose role passes this predicate are included
     */
    public static List<MemberEntry> collectSortedMembers(Party party, @Nullable UUID exclude,
                                                         @Nullable java.util.function.Predicate<PartyRole> roleFilter) {
        List<MemberEntry> result = new ArrayList<>();
        for (Map.Entry<UUID, PartyRole> entry : party.getMembers().entrySet()) {
            if (exclude != null && entry.getKey().equals(exclude)) continue;
            if (roleFilter != null && !roleFilter.test(entry.getValue())) continue;
            result.add(new MemberEntry(entry.getKey(), getDisplayName(entry.getKey()), entry.getValue()));
        }
        result.sort(byRoleThenName());
        return result;
    }

    /** OWNER → ADMIN → MEMBER, then alphabetical within a role. Null roles (e.g. invite candidates) sort last. */
    public static Comparator<MemberEntry> byRoleThenName() {
        return (a, b) -> {
            int ao = a.role == null ? Integer.MIN_VALUE : a.role.ordinal();
            int bo = b.role == null ? Integer.MIN_VALUE : b.role.ordinal();
            int cmp = bo - ao;
            if (cmp != 0) return cmp;
            return a.name.compareToIgnoreCase(b.name);
        };
    }

    /**
     * Formats one option line for a cycle button's tooltip: the selected option gets
     * a yellow arrow + white name, others are gray. Shared by trust-level and role cycles.
     */
    public static String formatCycleOptionLine(String langPrefix, String optionName, boolean selected) {
        String name = IKey.lang(langPrefix + optionName.toLowerCase(Locale.ROOT)).get();
        if (selected) {
            return TextFormatting.YELLOW + "→ " + TextFormatting.WHITE + name;
        }
        return TextFormatting.GRAY + "  " + name;
    }

    /** Underlined tooltip header line for a setting's name. */
    public static IKey underlineKey(String langKey) {
        return IKey.dynamic(() -> TextFormatting.UNDERLINE + IKey.lang(langKey).get());
    }

    /** Tooltip line showing a setting's default value in green. */
    public static String defaultTooltip(String value) {
        return IKey.lang("blpc.party.tooltip.default", () -> new Object[] { TextFormatting.GREEN + value }).get();
    }

    /** Shared row data for member/player lists (role is {@code null} for invite candidates). */
    public static final class MemberEntry {

        public final UUID uuid;
        public final String name;
        @Nullable
        public final PartyRole role;

        public MemberEntry(UUID uuid, String name, @Nullable PartyRole role) {
            this.uuid = uuid;
            this.name = name;
            this.role = role;
        }

        public UUID uuid() {
            return uuid;
        }

        public String name() {
            return name;
        }

        @Nullable
        public PartyRole role() {
            return role;
        }
    }

    /** ModularUI widget wrapper around a {@link MemberEntry} (face icon + label). */
    public static Flow memberEntryWidget(MemberEntry entry) {
        return memberEntryWidget(entry, BLPCColors.text());
    }

    /**
     * Variant with a custom text color.
     *
     * @param entry member data
     * @param color text color (e.g. {@link BLPCColors#text()})
     * @return a ready-to-use widget with face icon and formatted label
     */
    public static Flow memberEntryWidget(MemberEntry entry, int color) {
        return Flow.row()
                .widthRel(1f).heightRel(1f)
                .padding(ROW_INDENT, 0, 0, 0)
                .childPadding(ROW_INDENT)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new PlayerFaceDrawable(entry.uuid).asWidget().size(FACE_SIZE, FACE_SIZE))
                .child(rowLabel(IKey.str(formatMemberLabel(entry.name, entry.role)), color).asWidget().expanded());
    }

    // ── Common MemberPanel construction ──

    /** Builds a searchable member panel with header, list, and sync refresh. */
    public static ModularPanel buildMemberPanel(String panelId, String titleKey,
                                                Function<MemberEntry, IWidget> rowFactory,
                                                java.util.function.Function<Party, List<MemberEntry>> memberCollector,
                                                UUID partyId, java.util.function.Predicate<Party> check) {
        UUID playerId = Minecraft.getMinecraft().player.getUniqueID();
        ModularPanel panel = new ModularPanel(uniquePanelId(panelId));
        panel.size(LARGE_W, LARGE_H);
        addHeader(panel, titleKey);

        LiveSearchableList<MemberEntry> list = new LiveSearchableList<>(
                rowFactory, MemberEntry::name, "blpc.party.no_players_online");
        panel.child(fillBelowHeader(list.buildContainer()));

        list.rebuild(memberCollector.apply(ClientPartyCache.getParty(partyId)));

        addSyncRefreshListener(panel, () -> {
            Party fresh = ClientPartyCache.getParty(partyId);
            if (fresh == null || !check.test(fresh)) {
                closeIfTopMost(panel);
                return;
            }
            list.rebuild(memberCollector.apply(fresh));
        });

        return panel;
    }
}

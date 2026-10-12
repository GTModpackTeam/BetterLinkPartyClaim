package com.github.gtexpert.blpc.client.gui.party.widget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import com.github.gtexpert.blpc.client.gui.party.PartyWidgets;

/**
 * Searchable list whose rows are repopulated by {@link #rebuild}, typically
 * from a {@link PartyWidgets#addSyncRefreshListener} callback. The search box
 * stays mounted across rebuilds so the active filter is preserved.
 */
public class LiveSearchableList<T> {

    @SuppressWarnings("unchecked")
    private final ListWidget<IWidget, ?> list = (ListWidget<IWidget, ?>) new ListWidget<>()
            .crossAxisAlignment(Alignment.CrossAxis.START);
    private final List<IWidget> rows = new ArrayList<>();
    private final List<String> searchNames = new ArrayList<>();

    private final Function<T, IWidget> rowFactory;
    private final Function<T, String> nameExtractor;
    private final String emptyStateKey;
    private String filter = "";

    /** {@code emptyStateKey} may be {@code null} to leave the list blank when empty. */
    public LiveSearchableList(Function<T, IWidget> rowFactory,
                              Function<T, String> nameExtractor,
                              String emptyStateKey) {
        this.rowFactory = rowFactory;
        this.nameExtractor = nameExtractor;
        this.emptyStateKey = emptyStateKey;
    }

    /** Search box stacked above the inner list, ready to be added to a panel. */
    public Flow buildContainer() {
        TextFieldWidget searchBox = new TextFieldWidget()
                .widthRel(1f).height(14)
                .hintText(IKey.lang("blpc.party.search").get())
                .autoUpdateOnChange(true)
                .value(new StringValue.Dynamic(
                        () -> filter,
                        text -> {
                            filter = text;
                            applyFilter();
                        }));
        return Flow.column()
                .child(searchBox)
                .child(list.widthRel(1f).expanded());
    }

    public void rebuild(Collection<T> entries) {
        list.removeAll();
        rows.clear();
        searchNames.clear();

        if (entries.isEmpty()) {
            if (emptyStateKey != null) {
                list.child(PartyWidgets.emptyStateRow(emptyStateKey));
            }
            return;
        }

        for (T entry : entries) {
            IWidget row = rowFactory.apply(entry);
            list.child(row);
            rows.add(row);
            searchNames.add(nameExtractor.apply(entry).toLowerCase(Locale.ROOT));
        }
        applyFilter();
    }

    private void applyFilter() {
        String lower = filter.toLowerCase(Locale.ROOT);
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setEnabled(lower.isEmpty() || searchNames.get(i).contains(lower));
        }
    }
}

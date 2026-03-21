package com.github.gtexpert.bquclaim.api.util;

import net.minecraftforge.fml.common.Loader;

public enum Mods {

    BetterQuesting(Names.BETTER_QUESTING),
    ModularUI(Names.MODULAR_UI),
    JourneyMap(Names.JOURNEY_MAP);

    public static class Names {

        public static final String BETTER_QUESTING = "betterquesting";
        public static final String MODULAR_UI = "modularui";
        public static final String JOURNEY_MAP = "journeymap";
    }

    private final String ID;
    protected Boolean modLoaded;

    Mods(String ID) {
        this.ID = ID;
    }

    public boolean isModLoaded() {
        if (this.modLoaded == null) {
            this.modLoaded = Loader.isModLoaded(this.ID);
        }
        return this.modLoaded;
    }

    public String getId() {
        return ID;
    }
}

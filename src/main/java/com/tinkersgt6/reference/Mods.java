package com.tinkersgt6.reference;

/**
 * Mod IDs this addon cares about.
 */
public final class Mods {

    /** GregTech 6 API - provides all materials and stats. Loads before us. */
    public static final String GREGAPI = "gregapi";

    /** GregTech 6 post-init API. Loads AFTER TConstruct, so we must not depend on it. */
    public static final String GREGAPI_POST = "gregapi_post";

    /** Tinker's Construct - consumer of everything we register. Loads after us. */
    public static final String TCONSTRUCT = "TConstruct";

    /** Adds 8 effective harvest levels, so GT6 qualities need scaling up. */
    public static final String IGUANA_TWEAKS_TCONSTRUCT = "IguanaTweaksTConstruct";

    /** GT5-era sibling addon. Overlaps our material ID space if both are installed. */
    public static final String TGREGWORKS = "TGregworks";

    private Mods() {}
}

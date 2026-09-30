package com.tinkersgt6.reference;

/**
 * Keys and category names used by {@link com.tinkersgt6.config.TGConfig}.
 */
public final class Config {

    public static final String CAT_GENERAL = "general";
    public static final String CAT_STATS = "stats";
    public static final String CAT_ENCHANT = "enchant";
    public static final String CAT_MATERIALS = "materials";

    /* ---- general ---- */
    public static final String MATERIAL_ID_RANGE_START = "materialIDRangeStart";
    public static final String ON_ID_CONFLICT = "onIdConflict";
    public static final String ADD_MATERIALS_ANYWAY = "addMaterialsAnyway";
    public static final String MATERIAL_NAME_PREFIX = "materialNamePrefix";
    public static final String CREATIVE_TAB_MODE = "creativeTabMode";
    public static final String CLIENT_RENDER_MAPPING = "clientRenderMapping";
    public static final String DEBUG_LOGGING = "debugLogging";

    /* ---- stats ---- */
    public static final String HARVEST_LEVEL_MUL = "harvestLevelMul";
    public static final String HARVEST_LEVEL_MUL_IGUANA = "harvestLevelMulIguanaTweaks";
    public static final String HARVEST_LEVEL_ADD = "harvestLevelAdd";
    public static final String HARVEST_LEVEL_CAP = "harvestLevelCap";

    public static final String DURABILITY_MUL = "durabilityMul";
    public static final String DURABILITY_EXTREME_THRESHOLD = "durabilityExtremeThreshold";
    public static final String DURABILITY_EXTREME_MODE = "durabilityExtremeMode";
    public static final String DURABILITY_EXTREME_VALUE = "durabilityExtremeValue";
    public static final String DURABILITY_LOG_SCALE = "durabilityLogScale";
    public static final String DURABILITY_LOG_BASE = "durabilityLogBase";
    public static final String DURABILITY_CAP = "durabilityCap";

    public static final String MINING_SPEED_MUL = "miningSpeedMul";
    public static final String MINING_SPEED_PER_LEVEL = "miningSpeedPerLevel";
    public static final String MINING_SPEED_EXTREME_THRESHOLD = "miningSpeedExtremeThreshold";
    public static final String MINING_SPEED_EXTREME_MODE = "miningSpeedExtremeMode";
    public static final String MINING_SPEED_EXTREME_VALUE = "miningSpeedExtremeValue";
    public static final String MINING_SPEED_LOG_BASE = "miningSpeedLogBase";
    public static final String MINING_SPEED_CAP = "miningSpeedCap";

    public static final String ATTACK_MUL = "attackMul";
    public static final String ATTACK_SPEED = "attackSpeed";
    public static final String ATTACK_CAP = "attackCap";

    public static final String HANDLE_MODIFIER_MUL = "handleModifierMul";
    public static final String HANDLE_MODIFIER_ADD = "handleModifierAdd";
    public static final String HANDLE_MODIFIER_CAP = "handleModifierCap";

    /* ---- enchant ---- */
    public static final String ENCHANT_COMBINE_MODE = "combineMode";
    public static final String ENCHANT_LEVEL_CAP = "levelCap";
    public static final String AMMO_LOOTING_COMPENSATION = "ammoLootingCompensation";

    /* ---- materials.<name> ---- */
    public static final String MATERIAL_ENABLED = "enabled";
    public static final String MATERIAL_ID = "material-id";
    public static final String MATERIAL_HARVEST_LEVEL = "harvestlevel";
    public static final String MATERIAL_DURABILITY = "durability";
    public static final String MATERIAL_MINING_SPEED = "miningspeed";
    public static final String MATERIAL_ATTACK = "attack";
    public static final String MATERIAL_HANDLE_MODIFIER = "handlemodifier";
    public static final String MATERIAL_REINFORCED = "reinforced";
    public static final String MATERIAL_STONEBOUND = "stonebound";
    public static final String MATERIAL_CREATIVE_TAB = "creative-tab";

    /** Sentinel for "no explicit value, use the default rule". */
    public static final int NO_INT_OVERRIDE = -1000;

    /** Sentinel for "no explicit value, use the default rule". */
    public static final double NO_DOUBLE_OVERRIDE = -1000.0;

    private Config() {}
}

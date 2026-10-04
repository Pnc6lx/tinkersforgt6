package com.tinkersgt6.reference;

/**
 * Keys and category names used by {@link com.tinkersgt6.config.TGConfig}.
 */
public final class Config {

    public static final String CAT_GENERAL = "general";
    public static final String CAT_STATS = "stats";
    public static final String CAT_ENCHANT = "enchant";
    public static final String CAT_MATERIALS = "materials";
    public static final String CAT_RECIPES = "recipes";
    public static final String CAT_PARTS = "parts";
    public static final String CAT_POWER = "power";

    /* ---- general ---- */
    public static final String MATERIAL_ID_RANGE_START = "materialIDRangeStart";
    public static final String ON_ID_CONFLICT = "onIdConflict";
    public static final String MATERIAL_NAME_PREFIX = "materialNamePrefix";
    public static final String CREATIVE_TAB_MODE = "creativeTabMode";
    public static final String CLIENT_RENDER_MAPPING = "clientRenderMapping";
    public static final String DEBUG_LOGGING = "debugLogging";
    public static final String ROD_COMPAT = "rodCompat";
    public static final String ROD_BLACKLIST = "rodBlacklist";
    public static final String TOOL_CLICK_COMPAT = "toolClickCompat";
    public static final String TOOL_CLICK_DAMAGE = "toolClickDamage";
    public static final String TOOL_CLICK_BEAMS = "toolClickBeams";

    /* ---- power (GT6 battery upgrade) ---- */
    public static final String BATTERY_UPGRADE = "batteryUpgrade";
    public static final String BATTERY_DURABILITY_MODE = "batteryDurabilityMode";
    public static final String BATTERY_EU_PER_DURABILITY = "batteryEuPerDurability";
    public static final String BATTERY_RECHARGE_HOTBAR = "batteryRechargeFromHotbar";
    public static final String BATTERY_RECHARGE_INTERVAL = "batteryRechargeIntervalTicks";
    public static final String BATTERY_RECHARGE_PACKETS = "batteryRechargePacketsPerTick";
    public static final String BATTERY_MIN_CAPACITY = "batteryMinCapacity";
    public static final String BATTERY_ALLOW_UPGRADE = "batteryAllowUpgrade";

    /* ---- general (MazeBreaker) ---- */
    public static final String MAZE_BREAKER = "mazebreaker";
    public static final String MAZE_BREAKER_SPEED = "mazebreakerSpeedMultiplier";
    public static final String MAZE_BREAKER_DROPS = "mazebreakerDrops";

    /* ---- general (UniMixins prerequisite) ---- */
    public static final String REQUIRE_UNIMIXINS = "requireUniMixins";

    /* ---- recipes ---- */
    public static final String PART_RECIPE_EUT = "partRecipeEUt";
    public static final String PART_RECIPE_DURATION_PER_POINT = "partRecipeDurationPerPoint";
    public static final String PART_RECIPE_MIN_DURATION = "partRecipeMinDuration";
    public static final String PART_RECIPE_MAX_DURATION = "partRecipeMaxDuration";
    public static final String PART_INPUT_MODE = "partInputMode";
    public static final String PART_INPUT_CAP = "partInputCap";
    public static final String PART_FORM_MODE = "partFormMode";

    /* ---- stats ---- */
    public static final String HARVEST_LEVEL_MUL = "harvestLevelMul";
    public static final String HARVEST_LEVEL_ADD_IGUANA = "harvestLevelAddIguanaTweaks";
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

    public static final String HANDLE_MODIFIER_BASE = "handleModifierBase";
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

    /* ---- parts.<name> ---- */
    public static final String PART_ENABLED = "enabled";

    /** Sentinel for "no explicit value, use the default rule". */
    public static final int NO_INT_OVERRIDE = -1000;

    /** Sentinel for "no explicit value, use the default rule". */
    public static final double NO_DOUBLE_OVERRIDE = -1000.0;

    private Config() {}
}

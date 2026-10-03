package com.tinkersgt6.config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.tinkersgt6.reference.Config;

/**
 * Thin wrapper over the Forge {@link Configuration} that caches every lookup, because the stat mapping is evaluated
 * once per material during registration and then again during the init-phase re-evaluation pass.
 */
public class TGConfig {

    private static Configuration config;

    private static final Map<String, Double> doubleCache = new HashMap<>();
    private static final Map<String, Integer> intCache = new HashMap<>();
    private static final Map<String, Boolean> boolCache = new HashMap<>();
    private static final Map<String, String> stringCache = new HashMap<>();
    private static final Map<String, Long> longCache = new HashMap<>();

    /** Material name -> per-material property bag. */
    private static final Map<String, Map<String, Property>> materialProps = new HashMap<>();

    public static void init(File configFile) {
        config = new Configuration(configFile);
        config.load();
        loadGeneral();
    }

    public static Configuration raw() {
        return config;
    }

    public static void saveIfChanged() {
        if (config != null && config.hasChanged()) config.save();
    }

    /* ------------------------------------------------------------------ */
    /* general */
    /* ------------------------------------------------------------------ */

    private static void loadGeneral() {
        config.get(
            Config.CAT_GENERAL,
            Config.MATERIAL_ID_RANGE_START,
            3000,
            "Lowest material ID used by Tinker's For Gregtech 6. TinkersGregworks defaults to 1500 and occupies roughly"
                + " 1500-1875, and TiC-Tooltips treats 2000-2999 as ExtraTiC's range - it resolves the part item through"
                + " a map that is only filled when ExtraTiC is present, so an ID inside that window makes it build a"
                + " stack from a null item and crash. 3000 stays clear of both. Only IDs above this value are used.",
            300,
            15000)
            .getInt();
        config
            .get(
                Config.CAT_GENERAL,
                Config.ON_ID_CONFLICT,
                "REALLOCATE",
                "What to do when a material ID is already taken. REALLOCATE picks the next free ID, SKIP drops the"
                    + " material, CRASH preserves TConstruct's native behaviour (throws).",
                new String[] { "REALLOCATE", "SKIP", "CRASH" })
            .getString();
        config
            .get(
                Config.CAT_GENERAL,
                Config.MATERIAL_NAME_PREFIX,
                "",
                "Prefix prepended to every registered material name. Useful to avoid name collisions with other"
                    + " TConstruct addons.")
            .getString();
        config
            .get(
                Config.CAT_GENERAL,
                Config.CREATIVE_TAB_MODE,
                "ENABLED_NOT_HIDDEN",
                "Which materials get added to the creative tabs / NEI. ALL = every registered material,"
                    + " ENABLED_NOT_HIDDEN = skip GT6 hidden materials and disabled ones, NONE = add nothing.",
                new String[] { "ALL", "ENABLED_NOT_HIDDEN", "NONE" })
            .getString();
        config
            .get(
                Config.CAT_GENERAL,
                Config.CLIENT_RENDER_MAPPING,
                false,
                "Register client-side render mappings so tool parts look for their textures under the"
                    + " 'tinker:<folder>/<material><suffix>' path. Left off by default: with no such mapping"
                    + " TConstruct falls back to its own default part silhouette and tints it with the material"
                    + " colour, which is what GT6 materials should look like. Turn this on only if you ship textures"
                    + " for every material - a mapping without a texture makes the part borrow whatever material"
                    + " happens to own that name (Iron, Steel, ...) and stops the colouring.")
            .getBoolean();
        config.get(Config.CAT_GENERAL, Config.DEBUG_LOGGING, false, "Log each registered material individually.")
            .getBoolean();
        config
            .get(
                Config.CAT_GENERAL,
                Config.ROD_COMPAT,
                true,
                "Let the Tool Station accept GregTech rods as tool handles by translating them through GregTech's"
                    + " OreDict data into the matching TinkersGT6 tool rod.")
            .getBoolean();
        config
            .get(
                Config.CAT_GENERAL,
                Config.ROD_BLACKLIST,
                true,
                "Exempt the tool rods registered by this mod from GregTech's OreDict unification, so a handle in the"
                    + " inventory is never silently swapped for a GregTech stick.")
            .getBoolean();
        config
            .get(
                Config.CAT_GENERAL,
                Config.TOOL_CLICK_COMPAT,
                true,
                "Let TConstruct tools trigger GregTech's tool interactions when right-clicking GregTech blocks, the same"
                    + " way a GregTech tool would: a Shovel pulls the waste out of a Crucible, Smeltery or solid"
                    + " Generator, and a Chisel carves a Mold or cleans Boiler Coke.")
            .getBoolean();
        config
            .get(
                Config.CAT_GENERAL,
                Config.TOOL_CLICK_DAMAGE,
                1,
                "Durability points a successful interaction costs, multiplied by how much GregTech charged for it"
                    + " (GregTech deducts 10000 = 1 point, and most of these interactions cost less than that)."
                    + " Set to 0 to use these interactions for free.",
                0,
                64)
            .getInt();
        config
            .get(
                Config.CAT_GENERAL,
                Config.TOOL_CLICK_BEAMS,
                true,
                "Let the Hatchet and Lumber Axe turn logs into the matching GregTech beam. Unlike GregTech's own axe,"
                    + " which falls back to one generic beam for logs it does not know, this asks GregTech's wood"
                    + " dictionary for the beam belonging to that exact kind of wood, and leaves unknown logs alone.")
            .getBoolean();
        config
            .get(
                Config.CAT_GENERAL,
                Config.MAZE_BREAKER,
                true,
                "MazeBreaker, GregTech's Twilight Forest material trait: a tool whose head material has it mines"
                    + " Mazestone, Maze Hedge and Towerwood much faster, and still gets their drops when the tool is"
                    + " otherwise too weak for them. Does nothing without TwilightForest.")
            .getBoolean();
        config
            .get(
                Config.CAT_GENERAL,
                Config.MAZE_BREAKER_SPEED,
                40.0,
                "How much faster a MazeBreaker tool mines those three blocks. GregTech uses 40; TConstruct computes"
                    + " its mining speed differently, so this is a relative boost rather than an exact match.",
                1.0,
                10000.0)
            .getDouble();
        config
            .get(
                Config.CAT_GENERAL,
                Config.MAZE_BREAKER_DROPS,
                true,
                "Take over breaking those blocks whenever vanilla would drop nothing because the tool's harvest level"
                    + " is below what the block asks for - otherwise the block disappears without loot even though it"
                    + " can still be mined.")
            .getBoolean();
    }

    /* ------------------------------------------------------------------ */
    /* generic accessors */
    /* ------------------------------------------------------------------ */

    public static double getDouble(String category, String key, double default_, String comment) {
        String id = category + "." + key;
        Double cached = doubleCache.get(id);
        if (cached != null) return cached;
        double value = config.get(category, key, default_, comment)
            .getDouble(default_);
        doubleCache.put(id, value);
        return value;
    }

    public static int getInt(String category, String key, int default_, int min, int max, String comment) {
        String id = category + "." + key;
        Integer cached = intCache.get(id);
        if (cached != null) return cached;
        int value = config.get(category, key, default_, comment, min, max)
            .getInt(default_);
        intCache.put(id, value);
        return value;
    }

    public static boolean getBoolean(String category, String key, boolean default_, String comment) {
        String id = category + "." + key;
        Boolean cached = boolCache.get(id);
        if (cached != null) return cached;
        boolean value = config.get(category, key, default_, comment)
            .getBoolean(default_);
        boolCache.put(id, value);
        return value;
    }

    public static String getString(String category, String key, String default_, String[] valid, String comment) {
        String id = category + "." + key;
        String cached = stringCache.get(id);
        if (cached != null) return cached;
        Property prop = valid == null ? config.get(category, key, default_, comment)
            : config.get(category, key, default_, comment, valid);
        String value = prop.getString();
        stringCache.put(id, value);
        return value;
    }

    /* ------------------------------------------------------------------ */
    /* per-material accessors */
    /* ------------------------------------------------------------------ */

    private static Property materialProp(String material, String key, Object default_, String comment) {
        Map<String, Property> bag = materialProps.get(material);
        if (bag == null) {
            bag = new HashMap<>();
            materialProps.put(material, bag);
        }
        Property prop = bag.get(key);
        if (prop != null) return prop;

        String category = Config.CAT_MATERIALS + "." + material;
        if (default_ instanceof Boolean) {
            prop = config.get(category, key, ((Boolean) default_).booleanValue(), comment);
        } else if (default_ instanceof Integer) {
            prop = config.get(category, key, ((Integer) default_).intValue(), comment);
        } else {
            prop = config.get(category, key, ((Double) default_).doubleValue(), comment);
        }
        bag.put(key, prop);
        return prop;
    }

    public static boolean isMaterialEnabled(String material) {
        return materialProp(material, Config.MATERIAL_ENABLED, Boolean.TRUE, "Set to false to skip this material.")
            .getBoolean(true);
    }

    /**
     * @return the persisted material ID, or 0 when the entry is missing / damaged - 0 means "let the registry assign
     *         one", which keeps a hand-edited config from breaking the launch.
     */
    public static int getMaterialID(String material) {
        try {
            return materialProp(
                material,
                Config.MATERIAL_ID,
                Integer.valueOf(0),
                "TConstruct material ID. 0 = assign automatically on first launch and write the result back here.")
                    .getInt();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static void setMaterialID(String material, int id) {
        materialProp(material, Config.MATERIAL_ID, Integer.valueOf(0), "").set(id);
    }

    /** Per-material multiplier; {@link Config#NO_DOUBLE_OVERRIDE} means "use the global rule". */
    public static double getMaterialMultiplier(String material, String key) {
        return materialProp(
            material,
            key,
            Double.valueOf(Config.NO_DOUBLE_OVERRIDE),
            "Per-material multiplier applied on top of the global value. " + Config.NO_DOUBLE_OVERRIDE
                + " = use the global rule.").getDouble(Config.NO_DOUBLE_OVERRIDE);
    }

    public static int getMaterialIntOverride(String material, String key) {
        return materialProp(
            material,
            key,
            Integer.valueOf(Config.NO_INT_OVERRIDE),
            "Per-material override. " + Config.NO_INT_OVERRIDE + " = use the default rule.")
                .getInt(Config.NO_INT_OVERRIDE);
    }

    public static double getMaterialDoubleOverride(String material, String key) {
        return materialProp(
            material,
            key,
            Double.valueOf(Config.NO_DOUBLE_OVERRIDE),
            "Per-material override. " + Config.NO_DOUBLE_OVERRIDE + " = use the default rule.")
                .getDouble(Config.NO_DOUBLE_OVERRIDE);
    }

    public static boolean showInCreativeTab(String material, boolean default_) {
        return materialProp(
            material,
            Config.MATERIAL_CREATIVE_TAB,
            Boolean.valueOf(default_),
            "Force this material into / out of the creative tabs, ignoring creativeTabMode.").getBoolean(default_);
    }

    /* ------------------------------------------------------------------ */
    /* typed shortcuts */
    /* ------------------------------------------------------------------ */

    public static int materialIDRangeStart() {
        return getInt(
            Config.CAT_GENERAL,
            Config.MATERIAL_ID_RANGE_START,
            3000,
            300,
            15000,
            "Lowest material ID used by Tinker's For Gregtech 6.");
    }

    public static String onIdConflict() {
        return getString(Config.CAT_GENERAL, Config.ON_ID_CONFLICT, "REALLOCATE", null, "ID conflict strategy.");
    }

    public static String materialNamePrefix() {
        return getString(Config.CAT_GENERAL, Config.MATERIAL_NAME_PREFIX, "", null, "Material name prefix.");
    }

    public static String creativeTabMode() {
        return getString(
            Config.CAT_GENERAL,
            Config.CREATIVE_TAB_MODE,
            "ENABLED_NOT_HIDDEN",
            null,
            "Creative tab population mode.");
    }

    public static boolean clientRenderMapping() {
        return getBoolean(
            Config.CAT_GENERAL,
            Config.CLIENT_RENDER_MAPPING,
            false,
            "Register client render mappings. Off by default: TConstruct then draws its default silhouette tinted"
                + " with the material colour.");
    }

    public static boolean rodCompat() {
        return getBoolean(Config.CAT_GENERAL, Config.ROD_COMPAT, true, "Accept GregTech rods in the Tool Station.");
    }

    public static boolean rodBlacklist() {
        return getBoolean(
            Config.CAT_GENERAL,
            Config.ROD_BLACKLIST,
            true,
            "Exempt our tool rods from GregTech unification.");
    }

    public static boolean toolClickCompat() {
        return getBoolean(
            Config.CAT_GENERAL,
            Config.TOOL_CLICK_COMPAT,
            true,
            "Forward GregTech tool interactions from TConstruct tools.");
    }

    public static int toolClickDamage() {
        return getInt(
            Config.CAT_GENERAL,
            Config.TOOL_CLICK_DAMAGE,
            1,
            0,
            64,
            "Durability points per forwarded GregTech tool interaction.");
    }

    public static boolean toolClickBeams() {
        return getBoolean(
            Config.CAT_GENERAL,
            Config.TOOL_CLICK_BEAMS,
            true,
            "Turn logs into their matching GregTech beam when right-clicked with an axe.");
    }

    public static boolean mazeBreaker() {
        return getBoolean(
            Config.CAT_GENERAL,
            Config.MAZE_BREAKER,
            true,
            "MazeBreaker: faster Twilight Forest maze blocks, and drops for tools too weak for them.");
    }

    public static double mazeBreakerSpeedMultiplier() {
        return getDouble(
            Config.CAT_GENERAL,
            Config.MAZE_BREAKER_SPEED,
            40.0,
            "Mining speed multiplier a MazeBreaker tool gets on the three maze blocks. GregTech uses 40.");
    }

    public static boolean mazeBreakerDrops() {
        return getBoolean(
            Config.CAT_GENERAL,
            Config.MAZE_BREAKER_DROPS,
            true,
            "Hand out the drops when vanilla would quietly discard them for a too-weak MazeBreaker tool.");
    }

    /* ------------------------------------------------------------------ */
    /* power */
    /* ------------------------------------------------------------------ */

    public static boolean powerBoolean(String key, boolean default_, String comment) {
        return getBoolean(Config.CAT_POWER, key, default_, comment);
    }

    public static int powerInt(String key, int default_, int min, int max, String comment) {
        return getInt(Config.CAT_POWER, key, default_, min, max, comment);
    }

    public static double powerDouble(String key, double default_, String comment) {
        return getDouble(Config.CAT_POWER, key, default_, comment);
    }

    public static String powerString(String key, String default_, String[] valid, String comment) {
        return getString(Config.CAT_POWER, key, default_, valid, comment);
    }

    public static boolean batteryUpgrade() {
        return powerBoolean(
            Config.BATTERY_UPGRADE,
            true,
            "Register the GregTech battery upgrade: put a chargeable EU battery next to a TConstruct tool in the Tool"
                + " Station and the tool runs on EU instead of durability. Costs one modifier slot.");
    }

    /**
     * How a battery tool pays for a use.
     * {@code "ENERGY_ONLY"} spends charge and leaves durability alone, {@code "ENERGY_AND_DURABILITY"} copies
     * GregTech's own electric tools, which additionally take the durability hit with the probability
     * {@code 1 / max(10, toolQuality * 20)}.
     */
    public static String batteryDurabilityMode() {
        return powerString(
            Config.BATTERY_DURABILITY_MODE,
            "ENERGY_AND_DURABILITY",
            new String[] { "ENERGY_ONLY", "ENERGY_AND_DURABILITY" },
            "ENERGY_ONLY spends charge instead of durability. ENERGY_AND_DURABILITY spends charge and additionally"
                + " takes the durability hit with the same odds GregTech's electric tools use"
                + " (1 / max(10, tool quality * 20) per use).");
    }

    public static double batteryEuPerDurability() {
        return powerDouble(
            Config.BATTERY_EU_PER_DURABILITY,
            100.0,
            "EU spent per point of TConstruct durability the action would have cost. TConstruct charges one point per"
                + " block broken, and GregTech charges its own tools 100 units for the same block, so 100 keeps the two"
                + " scales comparable. 0 makes the battery free.");
    }

    public static boolean batteryRechargeFromHotbar() {
        return powerBoolean(
            Config.BATTERY_RECHARGE_HOTBAR,
            true,
            "Let a battery tool refill itself from chargeable EU batteries sitting in the player's hotbar, the way"
                + " TConstruct's own Flux upgrade does with Redstone Flux cells.");
    }

    public static int batteryRechargeIntervalTicks() {
        return powerInt(
            Config.BATTERY_RECHARGE_INTERVAL,
            20,
            1,
            1200,
            "Ticks between two automatic hotbar refills. 1 refunds continuously but checks the hotbar every tick.");
    }

    public static int batteryRechargePacketsPerTick() {
        return powerInt(
            Config.BATTERY_RECHARGE_PACKETS,
            4,
            1,
            1024,
            "How many EU packets may move per refill. A packet is one unit of the tool's voltage, so LV (32 EU)"
                + " refills four packets = 128 EU per check.");
    }

    public static double batteryMinCapacity() {
        return powerDouble(
            Config.BATTERY_MIN_CAPACITY,
            1.0,
            "Smallest EU capacity a battery must have before it can be built into a tool. GregTech extends huge gadget"
                + " cells into the same class of item, so this keeps clearly-unsuitable batteries out of the upgrade.");
    }

    public static boolean batteryAllowUpgrade() {
        return powerBoolean(
            Config.BATTERY_ALLOW_UPGRADE,
            true,
            "Allow swapping the installed battery for a bigger one without spending another modifier slot.");
    }

    public static boolean debugLogging() {
        return getBoolean(Config.CAT_GENERAL, Config.DEBUG_LOGGING, false, "Verbose per-material logging.");
    }

    public static double stat(String key, double default_, String comment) {
        return getDouble(Config.CAT_STATS, key, default_, comment);
    }

    public static int statInt(String key, int default_, int min, int max, String comment) {
        return getInt(Config.CAT_STATS, key, default_, min, max, comment);
    }

    public static String statString(String key, String default_, String comment) {
        return getString(Config.CAT_STATS, key, default_, null, comment);
    }

    public static double recipeDouble(String key, double default_, String comment) {
        return getDouble(Config.CAT_RECIPES, key, default_, comment);
    }

    public static long recipeLong(String key, long default_, String comment) {
        String id = Config.CAT_RECIPES + "." + key;
        Long cached = longCache.get(id);
        if (cached != null) return cached.longValue();
        long value = default_;
        try {
            value = Long.parseLong(
                config.get(Config.CAT_RECIPES, key, Long.toString(default_), comment)
                    .getString());
        } catch (RuntimeException e) {
            value = default_;
        }
        longCache.put(id, value);
        return value;
    }

    public static String recipeString(String key, String default_, String[] valid, String comment) {
        return getString(Config.CAT_RECIPES, key, default_, valid, comment);
    }

    /** Per part type; {@code default_} comes from the part table, so cold parts can ship switched off. */
    public static boolean isPartEnabled(String part, boolean default_) {
        return getBoolean(
            Config.CAT_PARTS + "." + part,
            Config.PART_ENABLED,
            default_,
            "Generate extruder recipes for this part.");
    }

    public static String enchantString(String key, String default_, String comment) {
        return getString(Config.CAT_ENCHANT, key, default_, null, comment);
    }

    public static int enchantInt(String key, int default_, int min, int max, String comment) {
        return getInt(Config.CAT_ENCHANT, key, default_, min, max, comment);
    }

    public static boolean enchantBoolean(String key, boolean default_, String comment) {
        return getBoolean(Config.CAT_ENCHANT, key, default_, comment);
    }
}

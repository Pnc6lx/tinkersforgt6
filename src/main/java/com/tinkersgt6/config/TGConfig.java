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
            2500,
            "Lowest material ID used by Tinker's For Gregtech 6. TinkersGregworks defaults to 1500 and occupies roughly"
                + " 1500-1875, so 2500 keeps both addons fully separated. Only IDs above this value are used.",
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
                Config.ADD_MATERIALS_ANYWAY,
                false,
                "Register a material even if TConstruct already knows a material with the same name.")
            .getBoolean();
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
                true,
                "Register client-side render mappings so tool parts look for their textures under the"
                    + " 'tinker:<folder>/<material><suffix>' path.")
            .getBoolean();
        config.get(Config.CAT_GENERAL, Config.DEBUG_LOGGING, false, "Log each registered material individually.")
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
            2500,
            300,
            15000,
            "Lowest material ID used by Tinker's For Gregtech 6.");
    }

    public static String onIdConflict() {
        return getString(Config.CAT_GENERAL, Config.ON_ID_CONFLICT, "REALLOCATE", null, "ID conflict strategy.");
    }

    public static boolean addMaterialsAnyway() {
        return getBoolean(Config.CAT_GENERAL, Config.ADD_MATERIALS_ANYWAY, false, "Register duplicate names anyway.");
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
        return getBoolean(Config.CAT_GENERAL, Config.CLIENT_RENDER_MAPPING, true, "Register client render mappings.");
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

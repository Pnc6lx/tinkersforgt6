package com.tinkersgt6.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gregapi.oredict.OreDictMaterial;
import tconstruct.library.tools.ToolMaterial;

/**
 * Logging helpers. Registration writes exactly one summary line; the per-material dump is opt-in via
 * {@code debugLogging}.
 */
public final class TinkersGT6Log {

    public static final Logger LOG = LogManager.getLogger("TinkersGT6");

    public static void info(String message) {
        LOG.info(message);
    }

    public static void warn(String message) {
        LOG.warn(message);
    }

    public static void error(String message, Throwable t) {
        LOG.error(message, t);
    }

    public static void debug(String message) {
        LOG.debug(message);
    }

    public static void debug(OreDictMaterial material, int id, ToolMaterial toolMaterial) {
        LOG.debug(
            "{} -> id {}, harvest {}, durability {}, speed {}, attack {}, handle {}, reinforced {}, stonebound {}, color #{})",
            material.mNameInternal,
            id,
            toolMaterial.harvestLevel,
            toolMaterial.durability,
            toolMaterial.miningspeed,
            toolMaterial.attack,
            toolMaterial.handleModifier,
            toolMaterial.reinforced,
            toolMaterial.stonebound,
            Integer.toHexString(toolMaterial.primaryColor));
    }

    private TinkersGT6Log() {}
}

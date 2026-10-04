package com.tinkersgt6.util;

/**
 * Whether the optional mixins under {@code com.tinkersgt6.mixin.late} are in play.
 *
 * <p>
 * They are queued by UniMixins' GTNHMixins module, which finds our {@code @LateMixin} loader by scanning FML's
 * annotation data. That module is what decides whether the mixins are applied, so its own class is the thing to look
 * for - a mod id is not, because GTNHMixins ships as a part of UniMixins rather than as a mod of its own.
 * </p>
 *
 * <p>
 * Answering this from here - and never loading a mixin class from the rest of the mod - is what keeps the package inert
 * on an install that skipped UniMixins rather than letting it blow up in an unpredictable place. The launch itself is
 * stopped by {@code CommonProxy.requireUniMixins()}, which reports the missing prerequisite before anything touches
 * these classes; anything that also has a plain Forge event implementation keeps working when that check is switched
 * off.
 * </p>
 */
public final class MixinSupport {

    private static final String GTNH_MIXINS = "com.gtnewhorizon.gtnhmixins.GTNHMixins";

    private static final boolean LATE_MIXINS = detect();

    public static boolean lateMixins() {
        return LATE_MIXINS;
    }

    private static boolean detect() {
        try {
            // false: never run its static initialiser, we only want to know whether it is there.
            Class.forName(GTNH_MIXINS, false, MixinSupport.class.getClassLoader());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private MixinSupport() {}
}

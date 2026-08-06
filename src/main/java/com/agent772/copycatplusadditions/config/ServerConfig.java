package com.agent772.copycatplusadditions.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side (world-authoritative) config for Copycats+ Additions.
 *
 * <p>Lives on the logical server so every connected player shares one value —
 * the extra-rotation gesture toggles a world-saved blockstate property, so it
 * is a server-side mechanic, not a per-client visual preference.
 *
 * <p>The single option gates only the <i>interaction</i>: when disabled,
 * right-clicking a uniform-material corner slope with its own material no
 * longer toggles the roof orientation. Already-stored orientations are still
 * honoured by the renderer, so turning this off never scrambles existing
 * builds.
 */
public final class ServerConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_EXTRA_ROTATION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ENABLE_EXTRA_ROTATION = builder
            .comment(
                "Allow rotating the sloped-roof texture of corner slopes whose material looks the same on all",
                "sides (e.g. planks). Right-click the block with its own material to toggle the plank grain",
                "between running along the eave (default) and running up the slope. Materials that already",
                "have a rotatable property (logs, pillars, ...) keep their normal cycling behaviour.",
                "Disabling this only stops the toggle gesture; blocks already rotated keep their look.")
            .define("enableExtraRotation", true);
        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    public static boolean enableExtraRotation() {
        return ENABLE_EXTRA_ROTATION.get();
    }
}

package com.agent772.copycatplusadditions.registry;

import static com.copycatsplus.copycats.foundation.tooltip.CopycatCharacteristics.COPYCAT;
import static com.copycatsplus.copycats.foundation.tooltip.CopycatCharacteristics.CT_TOGGLE;
import static com.copycatsplus.copycats.foundation.tooltip.CopycatCharacteristics.STACKABLE;

import java.util.List;
import java.util.Map;

import com.copycatsplus.copycats.foundation.tooltip.CopycatCharacteristics;
import com.copycatsplus.copycats.foundation.tooltip.CopycatDescription;

import net.minecraft.world.level.ItemLike;

/**
 * Copycats+ characteristics tooltip for our items (issue #62). Copycats+ attaches these
 * through Registrate's {@code onRegister(CopycatDescription.register(...))}; with a plain
 * {@code DeferredRegister} we fill the same map by hand. Sets mirror the upstream
 * {@code copycat_slope} (COPYCAT, CT_TOGGLE) and {@code copycat_slope_layer}
 * (+ STACKABLE), so the existing {@code tooltip.copycats.characteristics.*} lang keys apply.
 */
public final class ModTooltips {

    private static final List<CopycatCharacteristics> SLOPE = List.of(COPYCAT, CT_TOGGLE);
    private static final List<CopycatCharacteristics> SLOPE_LAYER = List.of(COPYCAT, CT_TOGGLE, STACKABLE);

    public static final Map<ItemLike, List<CopycatCharacteristics>> CHARACTERISTICS = Map.of(
        ModItems.CORNER_SLOPE, SLOPE,
        ModItems.INNER_CORNER_SLOPE, SLOPE,
        ModItems.CORNER_SLOPE_LAYER, SLOPE_LAYER,
        ModItems.INNER_CORNER_SLOPE_LAYER, SLOPE_LAYER,
        ModItems.ADV_SLOPE_LAYER, SLOPE_LAYER
    );

    private ModTooltips() {
    }

    /**
     * Common-side: only touches Copycats+' static map, no client classes. Writes the map
     * directly, which is all {@code CopycatDescription.register(ItemLike, ...)} does;
     * calling it would need Registrate on the compile classpath to resolve its overload.
     */
    public static void registerCharacteristics() {
        CHARACTERISTICS.forEach((item, characteristics) ->
            CopycatDescription.ITEM_CHARACTERISTICS.put(item.asItem(), characteristics));
    }
}

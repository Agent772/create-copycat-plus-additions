package com.agent772.copycatplusadditions.gametest;

import static com.copycatsplus.copycats.foundation.tooltip.CopycatCharacteristics.COPYCAT;
import static com.copycatsplus.copycats.foundation.tooltip.CopycatCharacteristics.CT_TOGGLE;
import static com.copycatsplus.copycats.foundation.tooltip.CopycatCharacteristics.STACKABLE;

import java.util.List;

import com.agent772.copycatplusadditions.registry.ModItems;
import com.copycatsplus.copycats.foundation.tooltip.CopycatCharacteristics;
import com.copycatsplus.copycats.foundation.tooltip.CopycatDescription;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Our items carry the same Copycats+ characteristics tooltip as their upstream
 * counterparts (issue #62). Rendering is client-only, so this checks the data the
 * tooltip is built from.
 */
@GameTestHolder("copycatplusadditions")
@PrefixGameTestTemplate(false)
public class TooltipCharacteristicsGameTests {

    private static final List<CopycatCharacteristics> SLOPE = List.of(COPYCAT, CT_TOGGLE);
    private static final List<CopycatCharacteristics> SLOPE_LAYER = List.of(COPYCAT, CT_TOGGLE, STACKABLE);

    @GameTest(template = "empty")
    public void slopesHaveSlopeCharacteristics(GameTestHelper helper) {
        assertCharacteristics(ModItems.CORNER_SLOPE.get(), SLOPE);
        assertCharacteristics(ModItems.INNER_CORNER_SLOPE.get(), SLOPE);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void layersHaveSlopeLayerCharacteristics(GameTestHelper helper) {
        assertCharacteristics(ModItems.CORNER_SLOPE_LAYER.get(), SLOPE_LAYER);
        assertCharacteristics(ModItems.INNER_CORNER_SLOPE_LAYER.get(), SLOPE_LAYER);
        assertCharacteristics(ModItems.ADV_SLOPE_LAYER.get(), SLOPE_LAYER);
        helper.succeed();
    }

    /** Guards the expected sets above against upstream drift. */
    @GameTest(template = "empty")
    public void expectedSetsMatchUpstream(GameTestHelper helper) {
        assertCharacteristics(upstreamItem("copycat_slope"), SLOPE);
        assertCharacteristics(upstreamItem("copycat_slope_layer"), SLOPE_LAYER);
        helper.succeed();
    }

    private static Item upstreamItem(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("copycats", path));
    }

    private static void assertCharacteristics(Item item, List<CopycatCharacteristics> expected) {
        List<CopycatCharacteristics> actual = CopycatDescription.ITEM_CHARACTERISTICS.get(item);
        if (!expected.equals(actual)) {
            throw new GameTestAssertException(
                BuiltInRegistries.ITEM.getKey(item) + ": expected " + expected + ", got " + actual);
        }
    }
}

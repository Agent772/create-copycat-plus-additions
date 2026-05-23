package com.agent772.copycatplusadditions.client;

import java.util.Map;

import com.agent772.copycatplusadditions.registry.ModBlocks;
import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Client-only setup for Copycats+ Additions.
 *
 * <p>Copycats+ wires its custom block models through Create's Registrate
 * ({@code CCCustomModels} / {@code CreateRegistrate.blockModel}). This mod uses a plain
 * NeoForge {@code DeferredRegister}, so the equivalent model swap is performed manually
 * here: after baking, every {@code adv_slope_layer} block-state model (a plain
 * {@code minecraft:block/air} model, per the blockstate JSON) is wrapped in a
 * {@link CopycatVerticalSlopeLayerModelCore} so the slope geometry is emitted at render
 * time with the copycat material's texture.
 *
 * <p>This class — and everything it imports — must only be touched on the physical
 * client; {@code CopycatPlusAdditions} guards the call to {@link #init(IEventBus)} with
 * a {@code Dist} check.
 */
public final class CopycatPlusAdditionsClient {

    private CopycatPlusAdditionsClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(CopycatPlusAdditionsClient::onModifyBakingResult);
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        Block block = ModBlocks.ADV_SLOPE_LAYER.get();
        ResourceLocation blockId = ModBlocks.ADV_SLOPE_LAYER.getId();

        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation location = BlockModelShaper.stateToModelLocation(blockId, state);
            BakedModel original = models.get(location);
            if (original != null) {
                models.put(location, CopycatModelCore.createModel(original, new CopycatVerticalSlopeLayerModelCore()));
            }
        }
    }
}

package com.agent772.copycatplusadditions.client;

import java.util.Map;
import java.util.function.Supplier;

import com.agent772.copycatplusadditions.registry.ModBlocks;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Client-only setup for Copycats+ Additions.
 *
 * <p>Copycats+ wires its custom block models through Create's Registrate
 * ({@code CCCustomModels} / {@code CreateRegistrate.blockModel}). This mod uses a plain
 * NeoForge {@code DeferredRegister}, so the equivalent model swap is performed manually
 * here: after baking, every block's plain {@code minecraft:block/air} blockstate model is
 * wrapped in the matching {@link CopycatModelCore} so the slope geometry is emitted at
 * render time with the copycat material's texture.
 *
 * <p>The same Registrate gap applies to block tint: Copycats+ passes
 * {@code .color(() -> ICopycatBlock::wrappedColor)} to its builders so tinted materials
 * (dyed armor from Create: Armored Constructs, grass, leaves, …) resolve their color
 * through the copycat's material state. Without a {@link net.minecraft.client.color.block.BlockColor}
 * registered, {@code BlockColors.getColor} returns {@code -1} and every tinted quad draws
 * untinted (e.g. white armor). {@link #onRegisterBlockColors} replicates that wiring for
 * our five blocks (issue #36).
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
        modEventBus.addListener(CopycatPlusAdditionsClient::onRegisterBlockColors);
    }

    // Delegate tint resolution to the copycat's material state, matching how Copycats+
    // wires single-material copycats through Registrate. Every block this addon registers
    // is a single-material copycat, so iterate the whole register — any future block is
    // covered automatically with no per-block wiring to forget.
    private static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(ICopycatBlock.wrappedColor(),
            ModBlocks.BLOCKS.getEntries().stream()
                .map(DeferredHolder::get)
                .toArray(Block[]::new));
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        swapModelCore(models, ModBlocks.ADV_SLOPE_LAYER.get(), ModBlocks.ADV_SLOPE_LAYER.getId(),
            CopycatVerticalSlopeLayerModelCore::new);
        swapModelCore(models, ModBlocks.INNER_CORNER_SLOPE.get(), ModBlocks.INNER_CORNER_SLOPE.getId(),
            CopycatInnerCornerSlopeModelCore::new);
        swapModelCore(models, ModBlocks.INNER_CORNER_SLOPE_LAYER.get(), ModBlocks.INNER_CORNER_SLOPE_LAYER.getId(),
            CopycatInnerCornerSlopeLayerModelCore::new);
        swapModelCore(models, ModBlocks.CORNER_SLOPE.get(), ModBlocks.CORNER_SLOPE.getId(),
            CopycatCornerSlopeModelCore::new);
        swapModelCore(models, ModBlocks.CORNER_SLOPE_LAYER.get(), ModBlocks.CORNER_SLOPE_LAYER.getId(),
            CopycatCornerSlopeLayerModelCore::new);
    }

    private static void swapModelCore(
        Map<ModelResourceLocation, BakedModel> models,
        Block block,
        ResourceLocation blockId,
        Supplier<? extends CopycatModelCore> coreFactory
    ) {
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation location = BlockModelShaper.stateToModelLocation(blockId, state);
            BakedModel original = models.get(location);
            if (original != null) {
                models.put(location, CopycatModelCore.createModel(original, coreFactory.get()));
            }
        }
    }
}

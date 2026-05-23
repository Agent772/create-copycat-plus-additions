package com.agent772.copycatplusadditions.registry;

import com.agent772.copycatplusadditions.CopycatPlusAdditions;
import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * BlockEntity types registered by this mod.
 *
 * <p>{@code CCCopycatBlock} (the supertype of every Copycats+ block) implements Create's
 * {@code IBE<CCCopycatBlockEntity>}: placement calls {@code getBlockEntityType().create(pos, state)},
 * and {@link BlockEntityType#create} returns {@code null} when the placed block isn't in that
 * type's {@code validBlocks} set. Copycats+ registers its own {@code copycat} BE type only with
 * its own blocks, so a block from this mod needs its own type whose {@code validBlocks} include
 * {@code VERTICAL_SLOPE_LAYER} — otherwise the BE is silently null on placement and every later
 * read of material / consumed item NPEs.
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CopycatPlusAdditions.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CCCopycatBlockEntity>> VERTICAL_SLOPE_LAYER =
        BLOCK_ENTITIES.register("vertical_slope_layer", () ->
            BlockEntityType.Builder
                .of(
                    (pos, state) -> new CCCopycatBlockEntity(ModBlockEntities.VERTICAL_SLOPE_LAYER.get(), pos, state),
                    ModBlocks.VERTICAL_SLOPE_LAYER.get())
                .build(null));

    private ModBlockEntities() {
    }
}

package com.agent772.copycatplusadditions.gametest;

import java.util.Optional;

import com.agent772.copycatplusadditions.blocks.CopycatVerticalSlopeLayerBlock;
import com.agent772.copycatplusadditions.blocks.SlopeCTBlocking;
import com.agent772.copycatplusadditions.registry.ModBlocks;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.ICustomCTBlocking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side tests for corner-slope connected textures (issue #61). Covers the
 * two behaviours the fix relies on:
 * <ul>
 *   <li>the inherited CT toggle actually flips {@code isCTEnabled} on our corners
 *       and their layer variants;</li>
 *   <li>{@link SlopeCTBlocking} unblocks CT between our corners and upstream
 *       {@code copycat_slope}s (both dispatch directions) while still blocking
 *       non-family neighbours and layers;</li>
 *   <li>{@link com.agent772.copycatplusadditions.blocks.FlushMaterialCT} connects our
 *       corners and the upstream slopes to a real block of their material on flush
 *       full faces only.</li>
 * </ul>
 *
 * <p>All tests share the empty {@code copycatplusadditions:empty} template.
 * {@code @PrefixGameTestTemplate(false)} keeps the template id un-prefixed so
 * every method resolves to that one structure.
 */
@GameTestHolder("copycatplusadditions")
@PrefixGameTestTemplate(false)
public class CornerSlopeCTGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    /** Upstream slope, resolved by id to avoid a compile dependency on Registrate types. */
    private static Block copycatSlope() {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("copycats", "copycat_slope"));
    }

    @GameTest(template = "empty")
    public void cornerSlopeCtToggles(GameTestHelper helper) {
        assertCtToggles(helper, ModBlocks.CORNER_SLOPE.get());
    }

    @GameTest(template = "empty")
    public void innerCornerSlopeCtToggles(GameTestHelper helper) {
        assertCtToggles(helper, ModBlocks.INNER_CORNER_SLOPE.get());
    }

    @GameTest(template = "empty")
    public void cornerSlopeLayerCtToggles(GameTestHelper helper) {
        assertCtToggles(helper, ModBlocks.CORNER_SLOPE_LAYER.get());
    }

    @GameTest(template = "empty")
    public void innerCornerSlopeLayerCtToggles(GameTestHelper helper) {
        assertCtToggles(helper, ModBlocks.INNER_CORNER_SLOPE_LAYER.get());
    }

    /**
     * Places {@code block} with a material, then sneak-uses it with an empty hand
     * twice: CT must go on→off→on. This is the upstream toggle we inherit; the
     * test guards against a future override accidentally suppressing it.
     */
    private void assertCtToggles(GameTestHelper helper, Block block) {
        helper.setBlock(POS, block.defaultBlockState());
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(POS);
        if (!(level.getBlockEntity(abs) instanceof ICopycatBlockEntity be)) {
            throw new GameTestAssertException("no copycat block entity for " + block);
        }
        be.setMaterial(Blocks.OAK_PLANKS.defaultBlockState());
        if (!be.isCTEnabled()) {
            throw new GameTestAssertException("CT should default to on for " + block);
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        ICopycatBlock copycat = (ICopycatBlock) block;

        copycat.useWithoutItem(level.getBlockState(abs), level, abs, player, hit);
        if (be.isCTEnabled()) {
            throw new GameTestAssertException("CT should be off after first sneak-use on " + block);
        }
        copycat.useWithoutItem(level.getBlockState(abs), level, abs, player, hit);
        if (!be.isCTEnabled()) {
            throw new GameTestAssertException("CT should be back on after second sneak-use on " + block);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void cornerConnectsToUpstreamSlope(GameTestHelper helper) {
        // Path 1: our corner, being rendered, does not block CT toward an upstream slope.
        BlockPos other = placeOther(helper, copycatSlope());
        Block corner = ModBlocks.CORNER_SLOPE.get();
        assertUnblocked(isCTBlocked(helper, corner, other), "corner.isCTBlocked toward copycat_slope");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void upstreamSlopeConnectsToCorner(GameTestHelper helper) {
        // Path 2: an upstream slope is being rendered; our corner sits in front and
        // answers blockCTTowards for it.
        BlockPos other = placeOther(helper, copycatSlope());
        Block corner = ModBlocks.CORNER_SLOPE.get();
        assertUnblocked(blockCTTowards(helper, corner, other), "corner.blockCTTowards from copycat_slope");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void cornerConnectsToInnerCorner(GameTestHelper helper) {
        BlockPos other = placeOther(helper, ModBlocks.INNER_CORNER_SLOPE.get());
        assertUnblocked(isCTBlocked(helper, ModBlocks.CORNER_SLOPE.get(), other),
            "corner.isCTBlocked toward inner_corner_slope");
        // and the reverse role
        BlockPos otherCorner = placeOther(helper, ModBlocks.CORNER_SLOPE.get());
        assertUnblocked(isCTBlocked(helper, ModBlocks.INNER_CORNER_SLOPE.get(), otherCorner),
            "inner_corner.isCTBlocked toward corner_slope");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void cornerBlocksNonFamily(GameTestHelper helper) {
        Block corner = ModBlocks.CORNER_SLOPE.get();
        assertDefaulted(isCTBlocked(helper, corner, placeOther(helper, Blocks.STONE)),
            "corner vs stone");
        assertDefaulted(isCTBlocked(helper, corner, placeOther(helper, ModBlocks.CORNER_SLOPE_LAYER.get())),
            "corner vs corner_slope_layer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void cornerLayerNeverUnblocks(GameTestHelper helper) {
        // A layer inherits SlopeCTBlocking but its own state is not in the family,
        // so it must defer to the default logic even toward a full slope.
        BlockPos slope = placeOther(helper, copycatSlope());
        assertDefaulted(isCTBlocked(helper, ModBlocks.CORNER_SLOPE_LAYER.get(), slope),
            "corner_slope_layer vs copycat_slope");
        helper.succeed();
    }

    // --- flush CT toward a real block of the material (FlushMaterialCT) -----

    @GameTest(template = "empty")
    public void cornerConnectsFlushToMaterial(GameTestHelper helper) {
        assertFlushMaterialCT(helper, ModBlocks.CORNER_SLOPE.get().defaultBlockState());
    }

    @GameTest(template = "empty")
    public void innerCornerConnectsFlushToMaterial(GameTestHelper helper) {
        assertFlushMaterialCT(helper, ModBlocks.INNER_CORNER_SLOPE.get().defaultBlockState());
    }

    @GameTest(template = "empty")
    public void cornerLayerConnectsFlushToMaterial(GameTestHelper helper) {
        assertFlushMaterialCT(helper, ModBlocks.CORNER_SLOPE_LAYER.get().defaultBlockState());
    }

    @GameTest(template = "empty")
    public void advSlopeLayerConnectsFlushToMaterial(GameTestHelper helper) {
        // Inherits the hooks UpstreamSlopeFlushCTMixin adds to the upstream slope layer.
        // Its default state is wall-mounted, so ask for the floor variant explicitly.
        assertFlushMaterialCT(helper, ModBlocks.ADV_SLOPE_LAYER.get().defaultBlockState()
            .setValue(CopycatVerticalSlopeLayerBlock.IN_WALL, false));
    }

    @GameTest(template = "empty")
    public void upstreamSlopeConnectsFlushToMaterial(GameTestHelper helper) {
        // Only passes with UpstreamSlopeFlushCTMixin and UpstreamSlopeCTBlockedMixin applied.
        assertFlushMaterialCT(helper, copycatSlope().defaultBlockState());
    }

    /**
     * {@code placed} with an oak material next to real oak planks, on a side where
     * upstream's own rule (matching faces between the two) does not connect them.
     * A face full on both, in the neighbour's plane, must connect; a face that is not
     * full must not. Stone next to it, or CT toggled off, keeps the upstream result.
     */
    private void assertFlushMaterialCT(GameTestHelper helper, BlockState placed) {
        ServerLevel level = helper.getLevel();
        BlockPos copycatPos = helper.absolutePos(POS);
        helper.setBlock(POS, placed);
        if (!(level.getBlockEntity(copycatPos) instanceof ICopycatBlockEntity be)) {
            throw new GameTestAssertException("no copycat block entity for " + placed);
        }
        be.setMaterial(Blocks.OAK_PLANKS.defaultBlockState());
        BlockState state = level.getBlockState(copycatPos);
        ICopycatBlock copycat = (ICopycatBlock) state.getBlock();
        ICustomCTBlocking blocking = (ICustomCTBlocking) state.getBlock();
        VoxelShape shape = state.getShape(level, copycatPos);

        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos otherPos = copycatPos.relative(side);
            level.setBlock(otherPos, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
            if (copycat.checkConnection(level, copycatPos, otherPos, state)) {
                // Upstream already connects on this side; nothing new to test here.
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                continue;
            }
            Direction flush = null;
            Direction notFlush = null;
            for (Direction face : Direction.values()) {
                if (face.getAxis() == side.getAxis()) {
                    continue;
                }
                if (Block.isFaceFull(shape, face)) {
                    flush = flush == null ? face : flush;
                } else {
                    notFlush = notFlush == null ? face : notFlush;
                }
            }
            check(flush != null && notFlush != null,
                placed + " needs a full and a partial face across its " + side + " side");

            check(copycat.canConnectTexturesToward(level, copycatPos, otherPos, state),
                placed + " should let its material model see the oak neighbour");
            check(!copycat.isIgnoredConnectivitySide(level, state, flush, copycatPos, otherPos, null),
                placed + " should show its material to the oak neighbour on the flush " + flush + " face");
            check(copycat.isIgnoredConnectivitySide(level, state, notFlush, copycatPos, otherPos, null),
                placed + " should not connect its partial " + notFlush + " face to the oak neighbour");
            check(blocking.isCTBlocked(level, state, copycatPos, otherPos, otherPos.relative(notFlush), notFlush)
                    .equals(Optional.of(true)),
                placed + " should block " + notFlush + "-face CT toward the oak neighbour");
            check(blocking.isCTBlocked(level, state, copycatPos, otherPos, otherPos.relative(flush), flush)
                    .equals(Optional.empty()),
                placed + " should leave " + flush + "-face CT toward the oak neighbour to the default rules");

            level.setBlock(otherPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            check(copycat.isIgnoredConnectivitySide(level, state, flush, copycatPos, otherPos, null),
                placed + " should not connect to a block of another material");

            level.setBlock(otherPos, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
            be.setCTEnabled(false);
            check(copycat.isIgnoredConnectivitySide(level, state, flush, copycatPos, otherPos, null),
                placed + " should not connect with its CT toggled off");
            helper.succeed();
            return;
        }
        throw new GameTestAssertException(placed + " matches a full block on every side; nothing to test");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    // --- helpers -----------------------------------------------------------

    private BlockPos placeOther(GameTestHelper helper, Block block) {
        helper.setBlock(POS, block.defaultBlockState());
        return helper.absolutePos(POS);
    }

    private Optional<Boolean> isCTBlocked(GameTestHelper helper, Block block, BlockPos blockingPos) {
        ServerLevel level = helper.getLevel();
        BlockState state = block.defaultBlockState();
        // pos/otherPos are unused by the family check; only blockingPos is read.
        return ((SlopeCTBlocking) block).isCTBlocked(level, state, blockingPos, blockingPos, blockingPos, Direction.NORTH);
    }

    private Optional<Boolean> blockCTTowards(GameTestHelper helper, Block block, BlockPos posOfOther) {
        ServerLevel level = helper.getLevel();
        BlockState state = block.defaultBlockState();
        // Only posOfOther (the block trying to connect) is read by the family check.
        return ((SlopeCTBlocking) block).blockCTTowards(level, state, posOfOther, posOfOther, posOfOther, Direction.NORTH);
    }

    private void assertUnblocked(Optional<Boolean> result, String what) {
        if (!result.equals(Optional.of(false))) {
            throw new GameTestAssertException(what + " expected Optional.of(false) but was " + result);
        }
    }

    private void assertDefaulted(Optional<Boolean> result, String what) {
        if (!result.isEmpty()) {
            throw new GameTestAssertException(what + " expected Optional.empty() but was " + result);
        }
    }
}

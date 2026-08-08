package com.agent772.copycatplusadditions.compat.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The only class that imports Sable. Loaded lazily by {@link SableClientCompat} after the
 * mod-present + client-side guard, so its {@code dev.ryanhcode.sable.*} and
 * {@code net.minecraft.client.*} references are never linked on a dedicated server or when
 * Sable is absent.
 *
 * <p><b>Issue #30.</b> Every block in this addon uses a {@code minecraft:block/air}
 * blockstate; the real geometry is emitted at render time by {@code CopycatModelCore}, which
 * needs the block entity's NeoForge {@code ModelData}. Sable stores a sub-level's chunks in the
 * main client level at "plot" coordinates and compiles its render mesh via the vanilla section
 * dispatcher. On world join the sub-level mesh is compiled <em>before</em> the client block
 * entities publish their {@code ModelData}, so {@code CopycatModelCore} sees
 * {@code ModelData.EMPTY}, falls through to the wrapped air model and emits zero quads → the
 * copycat is invisible (its outline still draws, since the {@code VoxelShape} comes from the
 * blockstate alone). Nothing re-meshes the section once the model data arrives, so it stays
 * invisible until a manual block update dirties it. Sable already reroutes any vanilla
 * section-dirty on plot coordinates into the sub-level's own render data (its
 * {@code ViewAreaMixin}, or the Sodium/Embeddium equivalent), so we heal this by re-issuing that
 * same dirty ourselves — the exact path a manual block update takes.
 *
 * <p><b>Why the dirty is deferred to the next client tick.</b> NeoForge's
 * {@code BlockEntity#onLoad()} only <em>queues</em> a {@code ModelData} refresh
 * ({@code requestModelDataUpdate}); the data is not published by the time {@code onLoad} returns.
 * On join the section is also very likely already dirty for its first compile, and
 * {@code setSectionDirty} on an already-dirty section is absorbed — it does not schedule a second
 * rebuild. Dirtying synchronously here would therefore risk being consumed by the same
 * model-data-less compile. Queuing the section and re-issuing the dirty on the next client tick
 * lets the initial compile and the {@code ModelDataManager} snapshot run first, so our dirty
 * provably schedules a fresh rebuild with the model data present.
 *
 * <p><b>Dedup / thread affinity.</b> Sections are collected in a {@code long} set keyed by
 * {@link SectionPos#asLong}, so a densely-decorated sub-level (thousands of copycats spanning a
 * handful of sections) issues one dirty per section on the next tick rather than one per block.
 * {@code LevelRenderer#setSectionDirty} is render-thread-only; both the enqueue (guarded by
 * {@link Minecraft#isSameThread()}) and the tick drain run on the render thread, so the set needs
 * no synchronization and the dirty never races Sable's sub-level chunk path.
 *
 * <p>The plot lookup is deliberately kept to companion-free Sable types
 * ({@link SubLevelContainer}, its {@code getPlot}); {@code ClientSubLevel} /
 * {@code SubLevelRenderData} implement interfaces from Sable's jar-in-jar'd
 * {@code sable-companion} module, which is present at runtime but not on our compile classpath.
 */
final class SableClientHandler {

    // Section positions (SectionPos.asLong) queued this tick, drained on the next client tick.
    // Single-threaded: only ever touched on the render thread (see the isSameThread guard in
    // markSubLevelSectionDirty and onClientTick), so no synchronization is required.
    private static final LongOpenHashSet PENDING_SECTIONS = new LongOpenHashSet();
    private static boolean tickListenerRegistered = false;

    private SableClientHandler() {
    }

    static void markSubLevelSectionDirty(Level level, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            return;
        }
        // The block entity's level must be the level whose renderer we are about to dirty. This
        // BE type descends from Create's SmartBlockEntity (a Ponder VirtualBlockEntity), so it
        // also lives in Create/Ponder virtual levels (SchematicWorld, VirtualRenderWorld, ponder
        // scenes) that report isClientSide == true but are not Minecraft.level. Those are never
        // Sable sub-levels, and dirtying mc.levelRenderer for them would rebuild an unrelated
        // main-level section.
        if (level != mc.level) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        // Section coordinate == chunk coordinate on X/Z, which is how Sable indexes plots.
        int sectionX = SectionPos.blockToSectionCoord(pos.getX());
        int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
        if (container.getPlot(sectionX, sectionZ) == null) {
            // Not part of any sub-level: a normal-level block, which NeoForge already re-meshes
            // after the block entity syncs. Skip so we don't force a redundant main-level rebuild.
            return;
        }
        int sectionY = SectionPos.blockToSectionCoord(pos.getY());
        PENDING_SECTIONS.add(SectionPos.asLong(sectionX, sectionY, sectionZ));
        if (!tickListenerRegistered) {
            NeoForge.EVENT_BUS.addListener(SableClientHandler::onClientTick);
            tickListenerRegistered = true;
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (PENDING_SECTIONS.isEmpty()) {
            return;
        }
        // Re-issue the vanilla section-dirty that Sable reroutes into the sub-level's render data.
        // The render data is created when the client starts tracking the sub-level, before its
        // chunks (and these block entities) load, so it is present by now.
        LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
        LongIterator it = PENDING_SECTIONS.iterator();
        while (it.hasNext()) {
            long section = it.nextLong();
            renderer.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
        }
        PENDING_SECTIONS.clear();
    }
}

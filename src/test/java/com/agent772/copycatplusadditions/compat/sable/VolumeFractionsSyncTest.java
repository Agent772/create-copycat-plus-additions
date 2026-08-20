package com.agent772.copycatplusadditions.compat.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntToDoubleFunction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Guards the hand-maintained sync between {@link LayerVolumes} (the code source of
 * truth) and the shipped {@code physics_block_properties/*.json} files: for every
 * layer count the JSON {@code sable:volume} must equal the computed fraction, and
 * {@code sable:mass} must be {@code volume * 0.5} (the density the whole dataset
 * uses). Prevents the two from silently drifting apart (reviewer item #3 on #44).
 */
class VolumeFractionsSyncTest {

    /** Density factor relating mass to volume across all shipped properties. */
    private static final double DENSITY = 0.5;

    /** JSON volumes/masses are rounded to 5 decimals; allow a hair more. */
    private static final double TOL = 1.0e-4;

    private static final Path DATA_DIR = Path.of(
        "src", "main", "resources", "data", "copycatplusadditions", "physics_block_properties");

    @Test
    void cornerSlopeLayerMatchesJson() throws IOException {
        assertLayersMatch("corner_slope_layer.json", LayerVolumes::cornerLayer);
    }

    @Test
    void innerCornerSlopeLayerMatchesJson() throws IOException {
        assertLayersMatch("inner_corner_slope_layer.json", LayerVolumes::innerCornerLayer);
    }

    @Test
    void advSlopeLayerMatchesJson() throws IOException {
        assertLayersMatch("adv_slope_layer.json", LayerVolumes::advSlopeLayer);
    }

    private void assertLayersMatch(String fileName, IntToDoubleFunction expectedVolume) throws IOException {
        JsonObject root = readJson(fileName);
        JsonObject base = root.getAsJsonObject("properties");
        JsonObject overrides = root.getAsJsonObject("overrides");

        for (int layers = 1; layers <= 8; layers++) {
            // Layers 1-7 are explicit overrides; layer 8 (the max/default state) falls
            // through to the base "properties" block, so it is validated there too.
            JsonObject props = layers <= 7 ? overrides.getAsJsonObject("layers=" + layers) : base;
            assertTrue(props != null, fileName + " is missing properties for layer " + layers);

            double expected = expectedVolume.applyAsDouble(layers);
            double jsonVolume = props.get("sable:volume").getAsDouble();
            double jsonMass = props.get("sable:mass").getAsDouble();

            assertEquals(expected, jsonVolume, TOL,
                fileName + " layer " + layers + " volume out of sync with LayerVolumes");
            assertEquals(expected * DENSITY, jsonMass, TOL,
                fileName + " layer " + layers + " mass is not volume * " + DENSITY);
        }
    }

    private static JsonObject readJson(String fileName) throws IOException {
        Path path = DATA_DIR.resolve(fileName);
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}

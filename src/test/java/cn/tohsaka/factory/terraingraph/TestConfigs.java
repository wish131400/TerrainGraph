package cn.tohsaka.factory.terraingraph;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.nio.file.Path;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

final class TestConfigs {
    private TestConfigs() {}

    static void load(CommentedConfig values) {
        TerrainGraphConfig.SPEC.correct(values);
        try {
            // NeoForge seals ILoadedConfig; use its in-memory implementation in tests.
            var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
                    .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
            constructor.setAccessible(true);
            TerrainGraphConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(values, null, null));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    static void unload() {
        TerrainGraphConfig.SPEC.acceptConfig(null);
    }
}

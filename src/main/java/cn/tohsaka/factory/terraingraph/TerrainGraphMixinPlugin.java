package cn.tohsaka.factory.terraingraph;

import java.util.List;
import java.util.Set;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class TerrainGraphMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("TerrainGraph");
    private static volatile String finalDensityStatus = "not initialized";

    public static String finalDensityStatus() {
        return finalDensityStatus;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !mixinClassName.endsWith(".NoiseGeneratorAccess") || Boolean.getBoolean("terraingraph.verify");
    }

    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith(".NoiseChunkMixin")) return;
        LoadingModList mods = LoadingModList.get();
        if (mods == null) {
            finalDensityStatus = "skipped: mod list unavailable";
        } else {
            String version = mods.getMods().stream().filter(mod -> mod.getModId().equals("byepregen"))
                    .map(mod -> mod.getVersion().toString()).findFirst().orElse(null);
            finalDensityStatus = FinalDensityTransformer.attach(node, version);
        }
        LOGGER.info("TerrainGraph final-density integration {}", finalDensityStatus);
    }
}

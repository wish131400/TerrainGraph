package cn.tohsaka.factory.terraingraph;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(TerrainGraph.MOD_ID)
public final class TerrainGraph {
    public static final String MOD_ID = "terraingraph";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TerrainGraph(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, TerrainGraphConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("terraingraph").requires(source -> source.hasPermission(2))
                .then(Commands.literal("stats").executes(context -> {
                    String result = "TerrainGraph enabled=" + TerrainGraphConfig.enabled() + ", metrics="
                            + TerrainGraphConfig.metrics() + ", reuseFinalDensity=" + TerrainGraphConfig.finalDensityEnabled()
                            + ", finalDensityIntegration=" + TerrainGraphMixinPlugin.finalDensityStatus()
                            + ", " + GraphMetrics.snapshot();
                    context.getSource().sendSuccess(() -> Component.literal(result), false);
                    LOGGER.info(result);
                    return 1;
                })));
        if (Boolean.getBoolean("terraingraph.verify")) GraphVerifier.register(event.getDispatcher());
    }
}

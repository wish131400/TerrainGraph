package cn.tohsaka.factory.terraingraph.mixin;

import cn.tohsaka.factory.terraingraph.MemoizingVisitor;
import cn.tohsaka.factory.terraingraph.TerrainGraphConfig;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = NoiseChunk.class, priority = 50)
public abstract class NoiseChunkMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"), index = 0)
    private DensityFunction.Visitor terraingraph$memoizeRouter(DensityFunction.Visitor visitor) {
        return TerrainGraphConfig.enabled() ? MemoizingVisitor.wrap(visitor) : visitor;
    }
}

package cn.tohsaka.factory.terraingraph.mixin;

import java.util.function.Supplier;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access used only by the opt-in, in-memory equivalence diagnostic. */
@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseGeneratorAccess {
    @Accessor("globalFluidPicker")
    Supplier<Aquifer.FluidPicker> terraingraph$fluidPicker();

    @Invoker("doCreateBiomes")
    void terraingraph$createBiomes(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk);

    @Invoker("doFill")
    ChunkAccess terraingraph$fill(Blender blender, StructureManager structures, RandomState random,
                                ChunkAccess chunk, int minCellY, int cellCountY);
}

package cn.tohsaka.factory.terraingraph.mixin;

import cn.tohsaka.factory.terraingraph.MemoizingVisitor;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {DensityFunctions.HolderHolder.class, DensityFunctions.Spline.class}, targets = {
        "net.minecraft.world.level.levelgen.DensityFunctions$Ap2",
        "net.minecraft.world.level.levelgen.DensityFunctions$BlendDensity",
        "net.minecraft.world.level.levelgen.DensityFunctions$Clamp",
        "net.minecraft.world.level.levelgen.DensityFunctions$MulOrAdd",
        "net.minecraft.world.level.levelgen.DensityFunctions$Noise",
        "net.minecraft.world.level.levelgen.DensityFunctions$RangeChoice",
        "net.minecraft.world.level.levelgen.DensityFunctions$Shift",
        "net.minecraft.world.level.levelgen.DensityFunctions$ShiftA",
        "net.minecraft.world.level.levelgen.DensityFunctions$ShiftB",
        "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise",
        "net.minecraft.world.level.levelgen.DensityFunctions$WeirdScaledSampler"
}, priority = 50)
public abstract class DensityMapMixin {
    @Inject(method = "mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/DensityFunction;",
            at = @At("HEAD"), cancellable = true)
    private void terraingraph$reuse(DensityFunction.Visitor visitor, CallbackInfoReturnable<DensityFunction> callback) {
        if (visitor instanceof MemoizingVisitor memo) {
            DensityFunction mapped = memo.find((DensityFunction) (Object) this);
            if (mapped != null) callback.setReturnValue(mapped);
        }
    }

    @Inject(method = "mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/DensityFunction;",
            at = @At("RETURN"))
    private void terraingraph$remember(DensityFunction.Visitor visitor, CallbackInfoReturnable<DensityFunction> callback) {
        if (visitor instanceof MemoizingVisitor memo) {
            memo.remember((DensityFunction) (Object) this, callback.getReturnValue());
        }
    }
}

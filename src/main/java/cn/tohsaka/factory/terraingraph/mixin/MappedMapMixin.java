package cn.tohsaka.factory.terraingraph.mixin;

import cn.tohsaka.factory.terraingraph.MemoizingVisitor;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Mapped", priority = 50)
public abstract class MappedMapMixin {
    @Inject(method = "mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/DensityFunctions$Mapped;",
            at = @At("HEAD"), cancellable = true)
    private void terraingraph$reuse(DensityFunction.Visitor visitor, CallbackInfoReturnable<DensityFunction> callback) {
        if (visitor instanceof MemoizingVisitor memo) {
            DensityFunction mapped = memo.find((DensityFunction) (Object) this);
            if (mapped != null) callback.setReturnValue(mapped);
        }
    }

    @Inject(method = "mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/DensityFunctions$Mapped;",
            at = @At("RETURN"))
    private void terraingraph$remember(DensityFunction.Visitor visitor, CallbackInfoReturnable<DensityFunction> callback) {
        if (visitor instanceof MemoizingVisitor memo) {
            memo.remember((DensityFunction) (Object) this, callback.getReturnValue());
        }
    }
}

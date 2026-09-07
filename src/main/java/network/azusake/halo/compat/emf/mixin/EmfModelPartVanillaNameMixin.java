package network.azusake.halo.compat.emf.mixin;

import network.azusake.halo.compat.emf.Emf261Symbols;
import network.azusake.halo.compat.emf.EmfPartNameAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes EMFModelPartVanilla.name without a compile-time EMF dependency. */
@Pseudo
@Mixin(targets = Emf261Symbols.VANILLA_MODEL_PART, remap = false)
public abstract class EmfModelPartVanillaNameMixin implements EmfPartNameAccess {

    @Shadow
    @Final
    private String name;

    @Override
    public String halo$getEmfPartName() {
        return name;
    }
}

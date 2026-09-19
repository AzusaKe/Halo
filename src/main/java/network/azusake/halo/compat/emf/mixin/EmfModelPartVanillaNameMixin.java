package network.azusake.halo.compat.emf.mixin;

import network.azusake.halo.compat.emf.EmfPartNameAccess;
import network.azusake.halo.compat.emf.Emf263Symbols;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

/** Exposes the EMF 1.21.1 ABI family's vanilla-part name without depending on EMF. */
@Pseudo
@Mixin(targets = Emf263Symbols.VANILLA_MODEL_PART, remap = false)
public abstract class EmfModelPartVanillaNameMixin implements EmfPartNameAccess {

    @Shadow
    @Final
    private String name;

    @Override
    public String halo$getEmfPartName() {
        return name;
    }
}

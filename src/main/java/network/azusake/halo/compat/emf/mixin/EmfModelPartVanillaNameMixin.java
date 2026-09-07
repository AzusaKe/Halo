package network.azusake.halo.compat.emf.mixin;

import network.azusake.halo.compat.emf.Emf1211Symbols;
import network.azusake.halo.compat.emf.EmfPartNameAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes the EMF 1.21.1 vanilla-part name without depending on EMF. */
@Pseudo
@Mixin(targets = Emf1211Symbols.VANILLA_MODEL_PART, remap = false)
public abstract class EmfModelPartVanillaNameMixin implements EmfPartNameAccess {

    @Shadow
    @Final
    private String name;

    @Override
    public String halo$getEmfPartName() {
        return name;
    }
}

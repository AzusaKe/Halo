package network.azusake.halo.compat.emf;

import net.minecraft.client.model.geom.ModelPart;

/** EMF-specific part identification kept separate from shared pose math. */
public final class EmfHeadMath {
    private EmfHeadMath() {}

    public static boolean isHeadPart(ModelPart part) {
        Object candidate = part;
        return candidate instanceof EmfPartNameAccess namedPart
            && "head".equals(namedPart.halo$getEmfPartName());
    }
}


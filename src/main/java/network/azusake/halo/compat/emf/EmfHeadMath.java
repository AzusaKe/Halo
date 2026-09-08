package network.azusake.halo.compat.emf;

import net.minecraft.client.model.geom.ModelPart;

/** EMF-specific part identification kept separate from the shared pose math. */
public final class EmfHeadMath {

    private EmfHeadMath() {
    }

    /**
     * EMF 3.1.1+ does not expose a stable getPartName ABI.  The verified
     * 26.2 ABI keeps the vanilla part name on EMFModelPartVanilla, so the
     * optional field-access mixin is the authoritative head anchor here.
     */
    public static boolean isHeadPart(ModelPart part) {
        // 26.2's official ModelPart mapping is final, so a direct instanceof
        // check against an optional mixin interface is rejected by javac even
        // though the runtime EMF replacement is a distinct transformed type.
        Object candidate = part;
        if (!(candidate instanceof EmfPartNameAccess namedPart)) {
            return false;
        }
        return "head".equals(namedPart.halo$getEmfPartName());
    }
}



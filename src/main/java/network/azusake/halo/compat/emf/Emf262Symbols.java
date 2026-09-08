package network.azusake.halo.compat.emf;

/** Runtime symbols for the 26.2 EMF render ABI family. */
public final class Emf262Symbols {

    public static final String MOD_ID = "entity_model_features";
    public static final String MIN_SUPPORTED_VERSION = "3.1.1";

    public static final String MODEL_PART =
        "traben.entity_model_features.models.parts.EMFModelPart";
    public static final String VANILLA_MODEL_PART =
        "traben.entity_model_features.models.parts.EMFModelPartVanilla";

    /** Mojang-named render method used by the 26.2 development runtime. */
    public static final String RENDER_METHOD_NAMED = "render";

    /** Fabric production name retained by the 26.2 intermediary ABI. */
    public static final String RENDER_METHOD_INTERMEDIARY = "method_22699";

    /** 26.2 Mojang-named render overload with packed colour. */
    public static final String RENDER_DESCRIPTOR_NAMED =
        "(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V";

    /** 26.2 intermediary render overload with packed colour. */
    public static final String RENDER_DESCRIPTOR_INTERMEDIARY =
        "(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;III)V";

    private Emf262Symbols() {
    }
}



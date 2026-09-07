package network.azusake.halo.compat.emf;

/** Runtime symbols shared by the verified EMF 1.21.1 ABI family. */
public final class Emf1211Symbols {

    public static final String MOD_ID = "entity_model_features";
    public static final String MIN_SUPPORTED_VERSION = "3.1.1";

    public static final String MODEL_PART =
        "traben.entity_model_features.models.parts.EMFModelPart";
    public static final String VANILLA_MODEL_PART =
        "traben.entity_model_features.models.parts.EMFModelPartVanilla";

    /** Named method used by Loom's 1.21.1 development runtime. */
    public static final String RENDER_METHOD_NAMED = "render";

    /** Intermediary method used by the published Fabric runtime. */
    public static final String RENDER_METHOD_INTERMEDIARY = "method_22699";

    /** 1.21.1 production/intermediary render signature. */
    public static final String RENDER_DESCRIPTOR_INTERMEDIARY =
        "(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;III)V";

    /** 1.21.1 named development render signature. */
    public static final String RENDER_DESCRIPTOR_NAMED =
        "(Lnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumer;III)V";

    private Emf1211Symbols() {
    }
}

package network.azusake.halo.compat.emf;

/** Runtime symbols shared by the verified EMF 1.21.1 NeoForge ABI family. */
public final class Emf1211Symbols {

    public static final String MOD_ID = "entity_model_features";
    public static final String MIN_SUPPORTED_VERSION = "3.1.1";

    public static final String MODEL_PART =
        "traben.entity_model_features.models.parts.EMFModelPart";
    public static final String VANILLA_MODEL_PART =
        "traben.entity_model_features.models.parts.EMFModelPartVanilla";

    /** Official/Mojmap method name used by the NeoForge runtime. */
    public static final String RENDER_METHOD = "render";

    /** 1.21.1 NeoForge render signature with packed colour. */
    public static final String RENDER_DESCRIPTOR =
        "(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V";

    private Emf1211Symbols() {
    }
}

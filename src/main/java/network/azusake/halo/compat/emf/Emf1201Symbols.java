package network.azusake.halo.compat.emf;

/** Runtime symbols shared by the verified EMF 1.20.1 ABI family. */
public final class Emf1201Symbols {

    public static final String MOD_ID = "entity_model_features";
    public static final String MIN_SUPPORTED_VERSION = "3.1.1";

    public static final String MODEL_PART =
        "traben.entity_model_features.models.parts.EMFModelPart";
    public static final String VANILLA_MODEL_PART =
        "traben.entity_model_features.models.parts.EMFModelPartVanilla";

    /** Named namespace method name used by Loom development runs. */
    public static final String RENDER_METHOD_NAMED = "render";

    /** SRG method name used by the production Forge 1.20.1 runtime. */
    public static final String RENDER_METHOD_FORGE = "m_104306_";

    /** 1.20.1 named development render signature. */
    public static final String RENDER_DESCRIPTOR_NAMED =
        "(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V";

    public static final String RENDER_DESCRIPTOR_FORGE = "(Leij;Lein;IIFFFF)V";

    private Emf1201Symbols() {
    }
}

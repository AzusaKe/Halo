package network.azusake.halo.compat.emf;

/** Runtime symbols shared by the verified EMF 1.20.1 ABI family. */
public final class Emf1201Symbols {

    public static final String MOD_ID = "entity_model_features";
    public static final String MIN_SUPPORTED_VERSION = "3.1.1";

    public static final String MODEL_PART =
        "traben.entity_model_features.models.parts.EMFModelPart";
    public static final String VANILLA_MODEL_PART =
        "traben.entity_model_features.models.parts.EMFModelPartVanilla";

    /** Named Forge userdev method name. */
    public static final String RENDER_METHOD_NAMED = "render";

    /** SRG method name in a production Forge 1.20.1 runtime. */
    public static final String RENDER_METHOD_FORGE = "m_104306_";

    /** Official/Mojmap descriptor used by Forge userdev and deobfuscated jars. */
    public static final String RENDER_DESCRIPTOR_NAMED =
        "(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V";

    /** Obfuscated Minecraft descriptor used by the production Forge runtime. */
    public static final String RENDER_DESCRIPTOR_FORGE =
        "(Leij;Lein;IIFFFF)V";

    private Emf1201Symbols() {
    }
}

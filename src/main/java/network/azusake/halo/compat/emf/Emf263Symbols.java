package network.azusake.halo.compat.emf;

/** Runtime symbols verified against EMF 3.3.6 for Minecraft 26.3. */
public final class Emf263Symbols {

    public static final String MOD_ID = "entity_model_features";
    public static final String MIN_SUPPORTED_VERSION = "3.3.6";

    public static final String MODEL_PART =
        "traben.entity_model_features.models.parts.EMFModelPart";
    public static final String VANILLA_MODEL_PART =
        "traben.entity_model_features.models.parts.EMFModelPartVanilla";

    /** Mojang-named render method used by the unobfuscated 26.3 runtime. */
    public static final String RENDER_METHOD_NAMED = "render";

    /** Legacy intermediary name retained as a defensive optional-ABI fallback. */
    public static final String RENDER_METHOD_INTERMEDIARY = "method_22699";

    /** EMF 3.3.6 render overload with packed colour. */
    public static final String RENDER_DESCRIPTOR_NAMED =
        "(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V";

    /** Equivalent intermediary descriptor for transformed compatibility jars. */
    public static final String RENDER_DESCRIPTOR_INTERMEDIARY =
        "(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;III)V";

    private Emf263Symbols() {
    }
}

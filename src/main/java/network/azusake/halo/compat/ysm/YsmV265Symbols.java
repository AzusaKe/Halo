package network.azusake.halo.compat.ysm;

/**
 * Version-pinned symbols verified against the official
 * {@code 2.6.5-forge+mc1.20.1} release jar.
 *
 * <p>Keeping every obfuscated name here makes upgrades auditable and prevents
 * YSM internals from leaking through the rest of Halo.</p>
 */
public final class YsmV265Symbols {

    public static final String MOD_ID = "yes_steve_model";
    public static final String SUPPORTED_VERSION = "2.6.5-forge+mc1.20.1";

    public static final String GEO_RENDERER =
        "com.elfmcys.yesstevemodel.oo0OOoOOOOO0Oo0O0oo0O0OO";
    public static final String LIVING_GEO_RENDERER =
        "com.elfmcys.yesstevemodel.OOoo0o0oO000ooO0Oo00OoOo";
    public static final String ENTITY_GEO_RENDERER =
        "com.elfmcys.yesstevemodel.oOoOOO0OOOoo000O0o0oo0OO";
    public static final String ANIMATED_GEO_MODEL =
        "com.elfmcys.yesstevemodel.OOOO0O0O000O000000oOOO0o";
    public static final String ANIMATABLE =
        "com.elfmcys.yesstevemodel.o0000OoOooO0oo0o0oooo0Oo";
    public static final String LIVING_ANIMATABLE =
        "com.elfmcys.yesstevemodel.o0O0oOooOo0OoOo0oOo00O00";
    public static final String BONE =
        "com.elfmcys.yesstevemodel.Oo0o00oOOo0OO000000O0oO0";

    public static final String RENDER_METHOD = "Oo0Oo0o00O00Oo0OOoOOoooo";
    public static final String HEAD_BONES_GETTER = "oOOOooO00oo0oOooooOO0Oo0";
    public static final String HEAD_BONES_DESCRIPTOR = "()Ljava/util/List;";
    public static final String BONE_FLOAT_GETTER_DESCRIPTOR = "()F";

    public static final String ROTATION_X = "Oo0Oo0o00O00Oo0OOoOOoooo";
    public static final String ROTATION_Y = "o0OOooo0o0OO00OoOOOo0o0O";
    public static final String ROTATION_Z = "O00OOOooOoooOoo0o0o0oO0O";
    public static final String POSITION_X = "oOOOo0OOO0ooooo0O00OO0o0";
    public static final String POSITION_Y = "OOOOo0O0oO0OOo0O0O0Oo0O0";
    public static final String POSITION_Z = "Ooooo0oooO0oooOOOoO0000O";
    public static final String SCALE_X = "oo0OoO00oOoo000O0000o0oo";
    public static final String SCALE_Y = "oooooooOOoOOoO00OooOo00O";
    public static final String SCALE_Z = "Oo00o0OooOOo0ooOoo0oO0o0";
    public static final String PIVOT_X = "o0OOO0o0o0OOo000oO00o00O";
    public static final String PIVOT_Y = "O0OooOo0oOOoOoOoOooO000o";
    public static final String PIVOT_Z = "ooOO000o0O0OOOoO0Oo0o0Oo";

    /** Production descriptor present in the official Forge release jar. */
    public static final String RENDER_DESCRIPTOR_FORGE =
        "(Lcom/elfmcys/yesstevemodel/OOOO0O0O000O000000oOOO0o;"
            + "Lcom/elfmcys/yesstevemodel/o0000OoOooO0oo0o0oooo0Oo;"
            + "FLnet/minecraft/client/renderer/RenderType;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V";

    /** Argument slots of {@link #RENDER_DESCRIPTOR_FORGE}, used by the ModifyArgs hook. */
    public static final int RENDER_MODEL_ARGUMENT = 0;
    public static final int RENDER_POSE_STACK_ARGUMENT = 4;

    /** Living renderer entrypoint containing the final base-model invocation. */
    public static final String LIVING_RENDER_DESCRIPTOR =
        "(Lcom/elfmcys/yesstevemodel/o0O0oOooOo0OoOo0oOo00O00;"
            + "Lnet/minecraft/resources/ResourceLocation;FF"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    /** Generic entity replacement entrypoint used for non-player YSM models. */
    public static final String ENTITY_RENDER_DESCRIPTOR =
        "(Lcom/elfmcys/yesstevemodel/o0000OoOooO0oo0o0oooo0Oo;FF"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    public static final String LIVING_BASE_RENDER_INVOKE =
        "Lcom/elfmcys/yesstevemodel/OOoo0o0oO000ooO0Oo00OoOo;"
            + RENDER_METHOD + RENDER_DESCRIPTOR_FORGE;

    public static final String ENTITY_BASE_RENDER_INVOKE =
        "Lcom/elfmcys/yesstevemodel/oOoOOO0OOOoo000O0o0oo0OO;"
            + RENDER_METHOD + RENDER_DESCRIPTOR_FORGE;

    private YsmV265Symbols() {
    }
}

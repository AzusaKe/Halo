package network.azusake.halo.compat.ysm;

/**
 * Version-pinned symbols verified against the official
 * {@code 2.6.5-fabric+mc1.21.1} release jar.
 *
 * <p>Keeping every obfuscated name here makes upgrades auditable and prevents
 * YSM internals from leaking through the rest of Halo.</p>
 */
public final class YsmV265Symbols {

    public static final String MOD_ID = "yes_steve_model";
    public static final String SUPPORTED_VERSION = "2.6.5-fabric+mc1.21.1";

    public static final String GEO_RENDERER =
        "com.elfmcys.yesstevemodel.Oo0o000oOOooO0O0oOo0O00o";
    public static final String ANIMATED_GEO_MODEL =
        "com.elfmcys.yesstevemodel.Ooo0ooOoO00o0oooo0o0OO0O";
    public static final String ANIMATABLE =
        "com.elfmcys.yesstevemodel.ooOOOOO0O0Oo0OO0000OOO0o";
    public static final String BONE =
        "com.elfmcys.yesstevemodel.o000O00oooOoo0Oooo00o0oO";

    public static final String RENDER_METHOD = "OO0ooo0OooOoO0OoO0ooOO0o";
    public static final String HEAD_BONES_GETTER = "OOooO00Ooo00oO00OoOOOooO";
    public static final String HEAD_BONES_DESCRIPTOR = "()Ljava/util/List;";
    public static final String BONE_FLOAT_GETTER_DESCRIPTOR = "()F";

    public static final String ROTATION_X = "OO0ooo0OooOoO0OoO0ooOO0o";
    public static final String ROTATION_Y = "Ooo0OOOOO00O0Oo0oo0OOOo0";
    public static final String ROTATION_Z = "ooOoOOO00Oo0o0oO0Ooo0oO0";
    public static final String POSITION_X = "ooOo0o0o0oOoOoOOOo0o0o00";
    public static final String POSITION_Y = "O0O0Ooo0OO0o00O0OO0ooOOo";
    public static final String POSITION_Z = "OooOO0000OoO00o00OoOooOo";
    public static final String SCALE_X = "oOoO0OOO000OooO0OoooOOO0";
    public static final String SCALE_Y = "Oo0OO0o0Ooo0OO0o0oOo00oo";
    public static final String SCALE_Z = "o0o0oO0o00o0ooOooO00O0OO";
    public static final String PIVOT_X = "oo0ooOoOO00ooO0OoOOoOoOo";
    public static final String PIVOT_Y = "O0O00O0000OoOooO0OO00Ooo";
    public static final String PIVOT_Z = "OoOoOo0O0oo0o0OOOooOo000";

    /** Production/intermediary descriptor present in the published YSM jar. */
    public static final String RENDER_DESCRIPTOR_INTERMEDIARY =
        "(Lcom/elfmcys/yesstevemodel/Ooo0ooOoO00o0oooo0o0OO0O;"
            + "Lcom/elfmcys/yesstevemodel/ooOOOOO0O0Oo0OO0000OOO0o;"
            + "FLnet/minecraft/class_1921;Lnet/minecraft/class_4587;"
            + "Lnet/minecraft/class_4597;ILnet/minecraft/class_4588;IIFFFF)V";

    /** Named descriptor used by Loom's remapped development runtime. */
    public static final String RENDER_DESCRIPTOR_NAMED =
        "(Lcom/elfmcys/yesstevemodel/Ooo0ooOoO00o0oooo0o0OO0O;"
            + "Lcom/elfmcys/yesstevemodel/ooOOOOO0O0Oo0OO0000OOO0o;"
            + "FLnet/minecraft/client/render/RenderLayer;"
            + "Lnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider;I"
            + "Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V";

    private YsmV265Symbols() {
    }
}

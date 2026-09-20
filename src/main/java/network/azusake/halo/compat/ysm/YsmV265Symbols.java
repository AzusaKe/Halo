package network.azusake.halo.compat.ysm;

/** Symbols verified from the official 2.6.5 Minecraft 1.21.1 NeoForge jar. */
public final class YsmV265Symbols {
    public static final String MOD_ID = "yes_steve_model";
    public static final String SUPPORTED_VERSION = "2.6.5-neoforge+mc1.21.1";
    public static final String EXPECTED_SHA1 = "18bec74a5b7f3778ce162ddbbbd5555aff0eda2e";

    public static final String GEO_RENDERER =
        "com.elfmcys.yesstevemodel.ooo0000oO0O0ooOO0ooOO0o0";
    public static final String ANIMATED_GEO_MODEL =
        "com.elfmcys.yesstevemodel.o0ooO0ooO00oo0o00Oo00000";
    public static final String ANIMATABLE =
        "com.elfmcys.yesstevemodel.OoO0oo0o0o0oOoo0oOOO0Ooo";
    public static final String BONE =
        "com.elfmcys.yesstevemodel.ooOO0OoOoO0o0o00oO0oo00o";

    public static final String RENDER_METHOD = "oOo0OO0O0o000OO0O000oo0o";
    public static final String HEAD_BONES_GETTER = "oO0O000o0oooOOO0O0oooOO0";
    public static final String HEAD_BONES_DESCRIPTOR = "()Ljava/util/List;";
    public static final String BONE_FLOAT_GETTER_DESCRIPTOR = "()F";

    public static final String ROTATION_X = "oOo0OO0O0o000OO0O000oo0o";
    public static final String ROTATION_Y = "oOoo00O0o0oO0o0oO00OO0O0";
    public static final String ROTATION_Z = "OO000o0ooOooooOOOOO0Ooo0";
    public static final String POSITION_X = "OOo0o0000Ooo0o00OO0oOOoO";
    public static final String POSITION_Y = "Oo0O0OoOo0O0oOoo0000O0oO";
    public static final String POSITION_Z = "O0o0OoOOooOo0O0OOoo0Oo00";
    public static final String SCALE_X = "OoooO0OO0000O00oo0Oo00OO";
    public static final String SCALE_Y = "oOOO00ooO0oOOoOOo0OoOOOo";
    public static final String SCALE_Z = "ooOooOO0oO00o00o0o0oOOoO";
    public static final String PIVOT_X = "OOo0O00Ooo00O0Ooo0OoOo0o";
    public static final String PIVOT_Y = "OO0Oo0O00OOOo0oOo0oooooO";
    public static final String PIVOT_Z = "O0O0Oo0Oooo0OOoOOO0ooo0O";

    public static final String RENDER_DESCRIPTOR_NEOFORGE =
        "(Lcom/elfmcys/yesstevemodel/o0ooO0ooO00oo0o00Oo00000;"
            + "Lcom/elfmcys/yesstevemodel/OoO0oo0o0o0oOoo0oOOO0Ooo;"
            + "FLnet/minecraft/client/renderer/RenderType;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V";

    private YsmV265Symbols() {}
}

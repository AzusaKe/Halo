package network.azusake.halo.compat.ysm;

/**
 * Version-pinned symbols verified against the official
 * {@code 2.6.5-fabric+mc1.20.1} release jar.
 *
 * <p>Keeping every obfuscated name here makes upgrades auditable and prevents
 * YSM internals from leaking through the rest of Halo.</p>
 */
public final class YsmV265Symbols {

    public static final String MOD_ID = "yes_steve_model";
    public static final String SUPPORTED_VERSION = "2.6.5-fabric+mc1.20.1";

    public static final String GEO_RENDERER =
        "com.elfmcys.yesstevemodel.OoOO00o00Oo00Oo0OOOOoO00";
    public static final String ANIMATED_GEO_MODEL =
        "com.elfmcys.yesstevemodel.o00oo00o000oo00o00OOooOO";
    public static final String ANIMATABLE =
        "com.elfmcys.yesstevemodel.O0OOo00o00Oo0OO0oO0O0oOo";
    public static final String BONE =
        "com.elfmcys.yesstevemodel.ooOooo0OOOoOo00o0OO0oOoO";

    public static final String RENDER_METHOD = "OoOo0OooO0OOO0Oo00000o00";
    public static final String HEAD_BONES_GETTER = "oO0ooOOOooo00oOo0OO00Oo0";
    public static final String HEAD_BONES_DESCRIPTOR = "()Ljava/util/List;";
    public static final String BONE_FLOAT_GETTER_DESCRIPTOR = "()F";

    public static final String ROTATION_X = "OoOo0OooO0OOO0Oo00000o00";
    public static final String ROTATION_Y = "OoooO000OO0OoO0o0o0ooooO";
    public static final String ROTATION_Z = "o0OOoooO000O0O0oo0O0o00o";
    public static final String POSITION_X = "Ooo0OOoO0oooOoO0OoOo0Oo0";
    public static final String POSITION_Y = "o00O00o0Oo0O0oOO00oOOOO0";
    public static final String POSITION_Z = "OOO0OOOoO0oOooO00Oo00oOO";
    public static final String SCALE_X = "OO0OOo0o0oOOOOoOO00O0o0O";
    public static final String SCALE_Y = "o0OO00OoOo0oOoOOo0OOOO00";
    public static final String SCALE_Z = "O00oOOOo0ooOoo0o00o00OoO";
    public static final String PIVOT_X = "ooO0OOoo0o0OoO00oOO0o0o0";
    public static final String PIVOT_Y = "O0Oo000000o0OO0OOoooOoo0";
    public static final String PIVOT_Z = "Ooo0ooO0oo00O00Ooo000oOo";

    /** Production/intermediary descriptor present in the published YSM jar. */
    public static final String RENDER_DESCRIPTOR_INTERMEDIARY =
        "(Lcom/elfmcys/yesstevemodel/o00oo00o000oo00o00OOooOO;"
            + "Lcom/elfmcys/yesstevemodel/O0OOo00o00Oo0OO0oO0O0oOo;"
            + "FLnet/minecraft/class_1921;Lnet/minecraft/class_4587;"
            + "Lnet/minecraft/class_4597;ILnet/minecraft/class_4588;IIFFFF)V";

    /** Named descriptor used by Loom's remapped development runtime. */
    public static final String RENDER_DESCRIPTOR_NAMED =
        "(Lcom/elfmcys/yesstevemodel/o00oo00o000oo00o00OOooOO;"
            + "Lcom/elfmcys/yesstevemodel/O0OOo00o00Oo0OO0oO0O0oOo;"
            + "FLnet/minecraft/client/render/RenderLayer;"
            + "Lnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider;I"
            + "Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V";

    private YsmV265Symbols() {
    }
}

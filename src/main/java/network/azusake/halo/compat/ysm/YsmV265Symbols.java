package network.azusake.halo.compat.ysm;

/** Symbols verified against ysm-2.6.5-neoforge+mc26.1-hotfix.jar. */
public final class YsmV265Symbols {
    public static final String MOD_ID = "yes_steve_model";
    /** The hotfix artifact reports this value in NeoForge mod metadata. */
    public static final String SUPPORTED_VERSION = "2.6.5-neoforge+mc26.1";
    public static final String RELEASE_FILE = "ysm-2.6.5-neoforge+mc26.1-hotfix.jar";

    public static final String GEO_RENDERER =
        "com.elfmcys.yesstevemodel.o0o00OO0oOo0o0O00o00oooO";
    public static final String RENDER_DATA =
        "com.elfmcys.yesstevemodel.oo0oo0O0O0oo00OO00000Oo0";
    public static final String RAW_MODEL =
        "com.elfmcys.yesstevemodel.oooOoOoo000OoOo0OOoO0oO0";
    public static final String RAW_BONE =
        "com.elfmcys.yesstevemodel.Oo0ooO0oOooo000oOo0Ooooo";
    public static final String ANIMATED_BONE =
        "com.elfmcys.yesstevemodel.oo0Oooo0o00OOOoooOOo0oOO";

    public static final String RENDER_METHOD = "OO0OoO00ooOOo0o00O000OoO";
    public static final String RENDER_DESCRIPTOR =
        "(Lcom/elfmcys/yesstevemodel/oo0oo0O0O0oo00OO00000Oo0;"
            + "Lcom/elfmcys/yesstevemodel/O0o0O0O00ooo0ooo000o0ooo;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/rendertype/RenderType;)V";

    public static final String DATA_MODEL_FIELD = "o0Oo00O0Ooo00oOo0O000Ooo";
    /** Direct native-order snapshot of the animated model's 12 floats per bone. */
    public static final String DATA_ATTRIBUTES_BUFFER_FIELD = "O000O0000O00000oo0o0OooO";
    public static final String MODEL_BONES_GETTER = "OO0OoO00ooOOo0o00O000OoO";
    public static final String BONE_NAME_GETTER = "OO0OoO00ooOOo0o00O000OoO";
    public static final String BONE_ID_GETTER = "O0oo00O0OoooOOOO00ooO000";
    /**
     * The hotfix raw-bone constructor stores pivot first and initial rotation second.
     * AnimatedBone copies the latter into attribute slots 0..2 while retaining the former as
     * immutable locator pivots. Keep these mappings explicit: all six methods return float and a
     * descriptor-only signature test cannot distinguish their semantics.
     */
    public static final String PIVOT_X = "o0oo0000oooOo0ooOOOo0OOo";
    public static final String PIVOT_Y = "O0OoOoO0oo0O00O0ooo0o0OO";
    public static final String PIVOT_Z = "OOOOoooo000OO0Ooo0o00ooO";
    public static final String INITIAL_ROTATION_X = "OOoO0ooOO0OOoo0OOOoOo000";
    public static final String INITIAL_ROTATION_Y = "o0Oo00O0Ooo00oOo0O000Ooo";
    public static final String INITIAL_ROTATION_Z = "O000O0000O00000oo0o0OooO";

    /**
     * Raw chain used by AnimatedGeoModel's head getter in the 26.1 hotfix.
     *
     * <p>This is intentionally pinned instead of inferred from bone names. YSM models commonly
     * end this locator at a control bone such as MHead, so requiring the terminal bone to be named
     * Head incorrectly rejects otherwise valid models.</p>
     */
    public static final String HEAD_LOCATOR_FIELD = "O00o00ooOo0O0ooOoOO0o0OO";

    private YsmV265Symbols() {}
}

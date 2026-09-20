package network.azusake.halo.compat.ysm;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Reads YSM 2.6.5's compact bone attributes and composes its Head locator. */
public final class YsmV265Adapter {
    private static final int ATTRIBUTE_STRIDE = 12;
    private static final int POSITION_OFFSET = 3;
    private static final int SCALE_OFFSET = 6;
    private static final Map<Object, HeadLayout> LAYOUTS = new WeakHashMap<>();
    private static final ThreadLocal<String> LAST_FAILURE = new ThreadLocal<>();
    private static volatile Accessors accessors;

    private YsmV265Adapter() {}

    public static Matrix4f captureHeadMatrix(Object renderData, Matrix4f root) throws Throwable {
        LAST_FAILURE.remove();
        if (renderData == null || !isFinite(root)) {
            return fail("missing render data or non-finite render root");
        }
        Accessors a = accessorsFor(renderData);
        Object model = a.dataModel.invoke(renderData);
        Object attributesValue = a.dataAttributes.invoke(renderData);
        if (model == null || !(attributesValue instanceof ByteBuffer attributesBuffer)) {
            return fail("missing raw model or animated attribute buffer");
        }
        FloatBuffer attributes = attributesBuffer.duplicate()
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        HeadLayout layout;
        synchronized (LAYOUTS) {
            layout = LAYOUTS.get(model);
            if (layout == null) {
                layout = resolveHeadLayout(a, model);
                if (layout != null) {
                    LAYOUTS.put(model, layout);
                }
            }
        }
        if (layout == null) return null;
        if (attributes.capacity() < layout.requiredAttributeLength()) {
            return fail("animated attribute buffer is shorter than the Head chain requires ("
                + attributes.capacity() + " < " + layout.requiredAttributeLength() + ")");
        }

        List<BonePose> poses = new ArrayList<>(layout.bones().length);
        for (BoneLayout bone : layout.bones()) {
            int base = bone.index() * ATTRIBUTE_STRIDE;
            poses.add(new BonePose(
                attributes.get(base), attributes.get(base + 1), attributes.get(base + 2),
                attributes.get(base + POSITION_OFFSET), attributes.get(base + POSITION_OFFSET + 1),
                attributes.get(base + POSITION_OFFSET + 2),
                attributes.get(base + SCALE_OFFSET), attributes.get(base + SCALE_OFFSET + 1),
                attributes.get(base + SCALE_OFFSET + 2),
                bone.pivotX(), bone.pivotY(), bone.pivotZ()
            ));
        }
        Matrix4f result = composeHeadMatrix(root, poses);
        return result != null ? result : fail("Head chain contains a non-finite pose or zero scale");
    }

    public static String lastFailureDetail() {
        String detail = LAST_FAILURE.get();
        return detail == null ? "unknown adapter rejection" : detail;
    }

    private static HeadLayout resolveHeadLayout(Accessors a, Object model) throws Throwable {
        Object bonesValue = a.modelBones.invoke(model);
        if (!(bonesValue instanceof List<?> rawBones) || rawBones.isEmpty()) {
            return layoutFail("raw model has no bones");
        }
        Map<Integer, BoneLayout> byId = new HashMap<>();
        for (int index = 0; index < rawBones.size(); index++) {
            Object bone = rawBones.get(index);
            if (bone == null || !a.boneClass.isInstance(bone)) {
                return layoutFail("raw model contains an unexpected bone value at index " + index);
            }
            int id = ((Number) a.boneId.invoke(bone)).intValue();
            String name = String.valueOf(a.boneName.invoke(bone));
            byId.put(id, new BoneLayout(index, name,
                number(a.pivotX.invoke(bone)), number(a.pivotY.invoke(bone)),
                number(a.pivotZ.invoke(bone))));
        }

        Object chainValue = a.headLocator.invoke(model);
        if (!(chainValue instanceof List<?> chain) || chain.isEmpty()) {
            return layoutFail("YSM's fixed Head locator chain is empty");
        }
        BoneLayout[] layout = new BoneLayout[chain.size()];
        for (int i = 0; i < chain.size(); i++) {
            Object idValue = chain.get(i);
            if (!(idValue instanceof Number number)) {
                return layoutFail("Head locator contains a non-numeric bone id at index " + i);
            }
            layout[i] = byId.get(number.intValue());
            if (layout[i] == null) {
                return layoutFail("Head locator references missing bone id " + number.intValue());
            }
        }
        return new HeadLayout(layout);
    }

    private static Matrix4f fail(String detail) {
        LAST_FAILURE.set(detail);
        return null;
    }

    private static HeadLayout layoutFail(String detail) {
        LAST_FAILURE.set(detail);
        return null;
    }

    /** Exact YSM locator composition: position, pivot, ZYX rotation, scale. */
    public static Matrix4f composeHeadMatrix(Matrix4f root, List<BonePose> hierarchy) {
        if (!isFinite(root) || hierarchy == null || hierarchy.isEmpty()) {
            return null;
        }
        Matrix4f matrix = new Matrix4f(root);
        for (int i = 0; i < hierarchy.size(); i++) {
            BonePose bone = hierarchy.get(i);
            if (bone == null || !bone.isUsable()) {
                return null;
            }
            matrix.translate(-bone.positionX / 16f, bone.positionY / 16f, bone.positionZ / 16f);
            matrix.translate(bone.pivotX / 16f, bone.pivotY / 16f, bone.pivotZ / 16f);
            matrix.rotate(new Quaternionf().rotationZYX(
                bone.rotationZ, bone.rotationY, bone.rotationX));
            matrix.scale(bone.scaleX, bone.scaleY, bone.scaleZ);
            if (i + 1 < hierarchy.size()) {
                matrix.translate(-bone.pivotX / 16f, -bone.pivotY / 16f, -bone.pivotZ / 16f);
            }
        }
        return isFinite(matrix) ? matrix : null;
    }

    public static boolean isFinite(Matrix4f m) {
        return m != null && Float.isFinite(m.m00()) && Float.isFinite(m.m01())
            && Float.isFinite(m.m02()) && Float.isFinite(m.m03())
            && Float.isFinite(m.m10()) && Float.isFinite(m.m11())
            && Float.isFinite(m.m12()) && Float.isFinite(m.m13())
            && Float.isFinite(m.m20()) && Float.isFinite(m.m21())
            && Float.isFinite(m.m22()) && Float.isFinite(m.m23())
            && Float.isFinite(m.m30()) && Float.isFinite(m.m31())
            && Float.isFinite(m.m32()) && Float.isFinite(m.m33());
    }

    private static Accessors accessorsFor(Object data) throws ReflectiveOperationException {
        Accessors current = accessors;
        if (current == null) {
            synchronized (YsmV265Adapter.class) {
                if (accessors == null) {
                    accessors = Accessors.resolve(data.getClass().getClassLoader());
                }
                current = accessors;
            }
        }
        if (!current.dataClass.isInstance(data)) {
            throw new IllegalArgumentException("unexpected YSM render-data class " + data.getClass().getName());
        }
        return current;
    }

    private static float number(Object value) {
        return ((Number) value).floatValue();
    }

    public record BonePose(
        float rotationX, float rotationY, float rotationZ,
        float positionX, float positionY, float positionZ,
        float scaleX, float scaleY, float scaleZ,
        float pivotX, float pivotY, float pivotZ
    ) {
        boolean isUsable() {
            return Float.isFinite(rotationX) && Float.isFinite(rotationY) && Float.isFinite(rotationZ)
                && Float.isFinite(positionX) && Float.isFinite(positionY) && Float.isFinite(positionZ)
                && Float.isFinite(scaleX) && Float.isFinite(scaleY) && Float.isFinite(scaleZ)
                && Float.isFinite(pivotX) && Float.isFinite(pivotY) && Float.isFinite(pivotZ)
                && scaleX != 0f && scaleY != 0f && scaleZ != 0f;
        }
    }

    private record BoneLayout(int index, String name, float pivotX, float pivotY, float pivotZ) {}
    private record HeadLayout(BoneLayout[] bones) {
        int requiredAttributeLength() {
            int maximum = 0;
            for (BoneLayout bone : bones) maximum = Math.max(maximum, bone.index());
            return (maximum + 1) * ATTRIBUTE_STRIDE;
        }
    }

    private record Accessors(
        Class<?> dataClass, Class<?> boneClass, MethodHandle dataModel,
        MethodHandle dataAttributes, MethodHandle modelBones, MethodHandle headLocator,
        MethodHandle boneName, MethodHandle boneId,
        MethodHandle pivotX, MethodHandle pivotY, MethodHandle pivotZ
    ) {
        static Accessors resolve(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> data = Class.forName(YsmV265Symbols.RENDER_DATA, false, loader);
            Class<?> model = Class.forName(YsmV265Symbols.RAW_MODEL, false, loader);
            Class<?> bone = Class.forName(YsmV265Symbols.RAW_BONE, false, loader);
            Class<?> intList = Class.forName("it.unimi.dsi.fastutil.ints.IntList", false, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType noArgList = MethodType.methodType(List.class);
            MethodType noArgFloat = MethodType.methodType(float.class);
            return new Accessors(data, bone,
                lookup.findGetter(data, YsmV265Symbols.DATA_MODEL_FIELD, model),
                lookup.findGetter(data, YsmV265Symbols.DATA_ATTRIBUTES_BUFFER_FIELD,
                    ByteBuffer.class),
                lookup.findVirtual(model, YsmV265Symbols.MODEL_BONES_GETTER, noArgList),
                lookup.findGetter(model, YsmV265Symbols.HEAD_LOCATOR_FIELD, intList),
                lookup.findVirtual(bone, YsmV265Symbols.BONE_NAME_GETTER,
                    MethodType.methodType(String.class)),
                lookup.findVirtual(bone, YsmV265Symbols.BONE_ID_GETTER,
                    MethodType.methodType(int.class)),
                lookup.findVirtual(bone, YsmV265Symbols.PIVOT_X, noArgFloat),
                lookup.findVirtual(bone, YsmV265Symbols.PIVOT_Y, noArgFloat),
                lookup.findVirtual(bone, YsmV265Symbols.PIVOT_Z, noArgFloat));
        }
    }
}

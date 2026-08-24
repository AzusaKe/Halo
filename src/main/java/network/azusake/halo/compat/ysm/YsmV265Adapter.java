package network.azusake.halo.compat.ysm;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

/** Reads and composes YSM 2.6.5's obfuscated Head locator hierarchy. */
public final class YsmV265Adapter {

    private static volatile Accessors accessors;

    private YsmV265Adapter() {
    }

    /**
     * Capture the final Head locator matrix.  A {@code null} result means the
     * rendered model has no usable Head hierarchy; symbol failures throw and
     * are handled once by {@link YsmHeadCapture}.
     */
    public static Matrix4f captureHeadMatrix(Object model, Matrix4f rootMatrix) throws Throwable {
        Accessors a = accessorsFor(model);
        Object value = a.headBones.invoke(model);
        if (!(value instanceof List<?> bones) || bones.isEmpty()) {
            return null;
        }

        List<BonePose> poses = new ArrayList<>(bones.size());
        for (Object bone : bones) {
            if (bone == null || !a.boneClass.isInstance(bone)) {
                return null;
            }
            BonePose pose = new BonePose(
                floatValue(a.rotationX.invoke(bone)),
                floatValue(a.rotationY.invoke(bone)),
                floatValue(a.rotationZ.invoke(bone)),
                floatValue(a.positionX.invoke(bone)),
                floatValue(a.positionY.invoke(bone)),
                floatValue(a.positionZ.invoke(bone)),
                floatValue(a.scaleX.invoke(bone)),
                floatValue(a.scaleY.invoke(bone)),
                floatValue(a.scaleZ.invoke(bone)),
                floatValue(a.pivotX.invoke(bone)),
                floatValue(a.pivotY.invoke(bone)),
                floatValue(a.pivotZ.invoke(bone))
            );
            if (!pose.isUsable()) {
                return null;
            }
            poses.add(pose);
        }
        return composeHeadMatrix(rootMatrix, poses);
    }

    /** Pure implementation of YSM 2.6.5 RenderUtils.prepMatrixForLocator. */
    public static Matrix4f composeHeadMatrix(Matrix4f rootMatrix, List<BonePose> hierarchy) {
        if (rootMatrix == null || hierarchy == null || hierarchy.isEmpty() || !isFinite(rootMatrix)) {
            return null;
        }

        Matrix4f matrix = new Matrix4f(rootMatrix);
        for (int i = 0; i < hierarchy.size(); i++) {
            BonePose bone = hierarchy.get(i);
            if (bone == null || !bone.isUsable()) {
                return null;
            }

            matrix.translate(-bone.positionX / 16f, bone.positionY / 16f, bone.positionZ / 16f);
            matrix.translate(bone.pivotX / 16f, bone.pivotY / 16f, bone.pivotZ / 16f);
            if (bone.rotationX != 0f || bone.rotationY != 0f || bone.rotationZ != 0f) {
                matrix.rotate(new Quaternionf().rotationZYX(
                    bone.rotationZ, bone.rotationY, bone.rotationX));
            }
            matrix.scale(bone.scaleX, bone.scaleY, bone.scaleZ);

            // prepMatrixForLocator intentionally remains at the final Head
            // pivot, while intermediate bones translate back out of theirs.
            if (i + 1 < hierarchy.size()) {
                matrix.translate(-bone.pivotX / 16f, -bone.pivotY / 16f, -bone.pivotZ / 16f);
            }
        }
        return isFinite(matrix) ? matrix : null;
    }

    public static boolean isFinite(Matrix4f matrix) {
        return matrix != null
            && Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01())
            && Float.isFinite(matrix.m02()) && Float.isFinite(matrix.m03())
            && Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11())
            && Float.isFinite(matrix.m12()) && Float.isFinite(matrix.m13())
            && Float.isFinite(matrix.m20()) && Float.isFinite(matrix.m21())
            && Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m23())
            && Float.isFinite(matrix.m30()) && Float.isFinite(matrix.m31())
            && Float.isFinite(matrix.m32()) && Float.isFinite(matrix.m33());
    }

    private static Accessors accessorsFor(Object model) throws ReflectiveOperationException {
        Accessors current = accessors;
        if (current != null) {
            if (!current.modelClass.isInstance(model)) {
                throw new IllegalArgumentException("unexpected YSM model class " + model.getClass().getName());
            }
            return current;
        }
        synchronized (YsmV265Adapter.class) {
            if (accessors == null) {
                accessors = Accessors.resolve(model.getClass().getClassLoader());
            }
            return accessorsFor(model);
        }
    }

    private static float floatValue(Object value) {
        return ((Number) value).floatValue();
    }

    /** Snapshot of the twelve IBone values used by locator composition. */
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

    private record Accessors(
        Class<?> modelClass,
        Class<?> boneClass,
        MethodHandle headBones,
        MethodHandle rotationX,
        MethodHandle rotationY,
        MethodHandle rotationZ,
        MethodHandle positionX,
        MethodHandle positionY,
        MethodHandle positionZ,
        MethodHandle scaleX,
        MethodHandle scaleY,
        MethodHandle scaleZ,
        MethodHandle pivotX,
        MethodHandle pivotY,
        MethodHandle pivotZ
    ) {
        private static Accessors resolve(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> modelClass = Class.forName(YsmV265Symbols.ANIMATED_GEO_MODEL, false, loader);
            Class<?> boneClass = Class.forName(YsmV265Symbols.BONE, false, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType listGetter = MethodType.fromMethodDescriptorString(
                YsmV265Symbols.HEAD_BONES_DESCRIPTOR, loader);
            MethodType floatGetter = MethodType.fromMethodDescriptorString(
                YsmV265Symbols.BONE_FLOAT_GETTER_DESCRIPTOR, loader);
            return new Accessors(
                modelClass,
                boneClass,
                lookup.findVirtual(modelClass, YsmV265Symbols.HEAD_BONES_GETTER, listGetter),
                lookup.findVirtual(boneClass, YsmV265Symbols.ROTATION_X, floatGetter),
                lookup.findVirtual(boneClass, YsmV265Symbols.ROTATION_Y, floatGetter),
                lookup.findVirtual(boneClass, YsmV265Symbols.ROTATION_Z, floatGetter),
                lookup.findVirtual(boneClass, YsmV265Symbols.POSITION_X, floatGetter),
                lookup.findVirtual(boneClass, YsmV265Symbols.POSITION_Y, floatGetter),
                lookup.findVirtual(boneClass, YsmV265Symbols.POSITION_Z, floatGetter),
                lookup.findVirtual(boneClass, YsmV265Symbols.SCALE_X, floatGetter),
                lookup.findVirtual(boneClass, YsmV265Symbols.SCALE_Y, floatGetter),
                lookup.findVirtual(boneClass, YsmV265Symbols.SCALE_Z, floatGetter),
                lookup.findVirtual(boneClass, YsmV265Symbols.PIVOT_X, floatGetter),
                lookup.findVirtual(boneClass, YsmV265Symbols.PIVOT_Y, floatGetter),
                lookup.findVirtual(boneClass, YsmV265Symbols.PIVOT_Z, floatGetter)
            );
        }
    }
}

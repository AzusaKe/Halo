package network.azusake.halo.physics;
import network.azusake.halo.api.v2.AnchorRotation;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
/** Conversion of the engine's camera-local -Z forward to core's head-local +Z forward. */
public final class CameraAnchorMath {
    private CameraAnchorMath(){}
    public static AnchorRotation rotation(Quaternionfc camera){
        var rotation=new Quaternionf(camera).rotateY((float)Math.PI).normalize();
        return new AnchorRotation(rotation.x,rotation.y,rotation.z,rotation.w);
    }
}

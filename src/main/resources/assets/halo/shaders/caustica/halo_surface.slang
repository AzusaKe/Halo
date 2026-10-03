module halo_surface;
import caustica_surface;
import caustica_types;
import caustica_color;
import halo_data;
namespace halo {
public static const float3 METAL_N[8]={float3(2.9114,2.9497,2.5845),float3(.18299,.42108,1.3734),float3(1.3456,.96521,.61722),float3(3.1071,3.1812,2.3230),float3(.27105,.67693,1.3164),float3(1.91,1.83,1.44),float3(2.3757,2.0847,1.8453),float3(.15943,.14512,.13547)};
public static const float3 METAL_K[8]={float3(3.0893,2.9318,2.7670),float3(3.4242,2.3459,1.7704),float3(7.4746,6.3995,5.3031),float3(3.3314,3.3291,3.1350),float3(3.6092,2.6248,2.2921),float3(3.51,3.4,3.18),float3(4.2655,3.7153,3.1365),float3(3.9291,3.19,2.3808)};
public float3 mappedNormal(float3 n,AffineTransform transform,float3 tangent,float3 bitangent,float2 xy,float z) {
    float3 t=transformDirection(transform,tangent);t-=n*dot(t,n);
    float3 b=transformDirection(transform,bitangent);b-=n*dot(b,n);
    if(dot(t,t)<1e-20 || dot(b,b)<1e-20)return n;
    t=normalize(t);b-=t*dot(b,t);if(dot(b,b)<1e-20)return n;
    return normalize(t*xy.x+normalize(b)*xy.y+n*z);
}
public float3 reflectionSafeNormal(float3 n,float3 ng,float3 v,out float2 weights) {
    weights=float2(1,0);
    float nz=clamp(dot(n,ng),-1.0,1.0),vz=max(dot(v,ng),0.0);
    float clearance=.05*vz;
    if(nz>0 && 2*dot(n,v)*nz-vz>=clearance)return n;
    float3 tangent=n-nz*ng;float tangentSquared=dot(tangent,tangent);
    // Only the degenerate antiparallel normal has no tangent variation to retain.
    if(tangentSquared<1e-20){weights=float2(0,1);return ng;}
    float tangentLength=sqrt(tangentSquared);float3 x=tangent/tangentLength;
    float vx=dot(v,x),a=vx*vx+vz*vz;
    if(a<1e-20){weights=float2(0,1);return ng;}
    // Rotate towards Ng by the smallest angle that keeps specular reflection
    // above the triangle. With n'=sin(theta)*x+cos(theta)*Ng, the boundary is
    // vz*cos(2*theta)+vx*sin(2*theta)=clearance. Solve its upper-angle root.
    // This is continuous at the boundary and preserves tangent azimuth rather
    // than replacing an entire region by a constant triangle normal.
    float cosDouble=(clearance*vz-vx*sqrt(max(0.0,a-clearance*clearance)))/a;
    float correctedZ=sqrt(saturate(.5*(1+cosDouble)));
    float correctedX=sqrt(max(0.0,1-correctedZ*correctedZ));
    weights.x=correctedX/tangentLength;weights.y=correctedZ-weights.x*nz;
    return normalize(weights.x*n+weights.y*ng);
}
public struct HaloSurface : ISurfaceModel {
    public override OpenPbrSurface evaluateSurface(SurfaceInput input) {
        Material m=ShaderDataPtr<Material>(input.instanceData)[0];Hit h=hit(input.bindingData,input.primitiveIndex,input.barycentrics);
        float3 worldNormal=transformNormal(input.normalToScene,explicitNormal(m,h.normal));
        bool inward=(m.flags&4u)!=0u && dot(worldNormal,input.outgoingDirection)<0;
        uint base=inward?m.innerTexture:m.baseTexture;
        uint normal=inward?m.innerNormal:m.normalTexture;
        uint spec=inward?m.innerSpec:m.specTexture;
        bool hasNormal=(m.flags&(inward?128u:16u))!=0u;
        bool hasSpec=(m.flags&(inward?256u:32u))!=0u;
        OpenPbrSurface surface={};surface.geometry_thin_walled=true;
        float3 color=(m.flags&1u)!=0u?bt709ToAcesCg(texel(base,h.uv).rgb):float3(1);
        surface.base_color=color;
        float emission=0;
        if(hasSpec) {
            float4 s=texel(spec,h.uv);surface.specular_roughness=1-s.r;
            float g=s.g*255;
            if(g<229.5) {float amplitude=min(sqrt(s.g),254.0/255.0);surface.specular_ior=(1+amplitude)/(1-amplitude);}
            else {surface.base_metalness=1;if(g<237.5){uint metal=uint(round(g))-230;float3 n=METAL_N[metal],k=METAL_K[metal];surface.base_color=color*saturate(bt709ToAcesCg(((n-1)*(n-1)+k*k)/((n+1)*(n+1)+k*k)));}}
            emission=s.a<254.5/255.0?s.a*255.0/254.0:0;
        }
        if((m.flags&2u)!=0u)emission=1;
        surface.emission_luminance=emission*m.nits*m.brightness;surface.emission_color=color;
        float3 objectNormal=inward?-h.normal:h.normal;
        float3 correctedObject=explicitNormal(m,objectNormal);
        float3 current=normalize(transformNormal(input.normalToScene,correctedObject));
        float3 previous=normalize(transformNormal(input.previousNormalToScene,correctedObject));
        if(hasNormal) {
            float2 uvU=float2(h.b.u-h.a.u,h.b.v-h.a.v),uvV=float2(h.c.u-h.a.u,h.c.v-h.a.v);
            float det=uvU.x*uvV.y-uvU.y*uvV.x;
            if(abs(det)>1e-12) {
                float3 tangent=(input.objectEdgeU*uvV.y-input.objectEdgeV*uvU.y)/det;
                float3 bitangent=(input.objectEdgeV*uvU.x-input.objectEdgeU*uvV.x)/det;
                // Halo UVs already use top-left V. DirectX green points along dP/dV (down),
                // so there is no second Y/V flip. Tangents follow A; authored base normals follow N.
                float2 xy=texel(normal,h.uv).rg*2-1;
                float lengthSquared=dot(xy,xy);if(lengthSquared>1)xy*=rsqrt(lengthSquared);
                float z=sqrt(max(0.0,1-dot(xy,xy)));
                current=mappedNormal(current,input.objectToScene,tangent,bitangent,xy,z);
                previous=mappedNormal(previous,input.previousObjectToScene,tangent,bitangent,xy,z);
            }
        }
        // Orient the whole triangle once. Testing the interpolated shading normal
        // instead can flip it midway across a face with extreme imported normals.
        float3 objectGeometric=cross(input.objectEdgeU,input.objectEdgeV);
        bool reverse=dot(transformNormal(input.normalToScene,objectGeometric),input.geometricNormal)<0;
        if(reverse!=inward){current=-current;previous=-previous;}
        float2 normalWeights;
        current=reflectionSafeNormal(current,input.geometricNormal,input.outgoingDirection,normalWeights);
        // Transport the same correction through the previous normal frame. Using
        // the current view independently with a previous transform gives false
        // normal motion when the instance rotates or moves.
        previous=normalize(normalWeights.x*previous+normalWeights.y*input.previousGeometricNormal);
        surface.geometry_normal=current;surface.previous_geometry_normal=previous;
        return surface;
    }
};
}

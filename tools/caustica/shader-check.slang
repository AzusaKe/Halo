module caustica_shader_check;
import halo_surface;
import halo_coverage;
import caustica_types;
import caustica_coverage;
RWStructuredBuffer<float4> result;
[shader("compute")] [numthreads(1,1,1)]
void main(uint3 id:SV_DispatchThreadID) {
    SurfaceInput input={};input.instanceData=uint64_t(id.x)*128;input.bindingData=uint64_t(id.y)*16;
    input.primitiveIndex=id.z;input.barycentrics=float2(.2,.3);input.outgoingDirection=float3(0,1,0);
    input.objectToScene.row0=float4(1,0,0,0);input.objectToScene.row1=float4(0,2,0,0);input.objectToScene.row2=float4(0,0,-1,0);
    input.previousObjectToScene=input.objectToScene;
    input.normalToScene.row0=float3(1,0,0);input.normalToScene.row1=float3(0,.5,0);input.normalToScene.row2=float3(0,0,-1);
    input.previousNormalToScene=input.normalToScene;
    input.objectEdgeU=float3(1,0,0);input.objectEdgeV=float3(0,0,1);
    input.geometricNormal=float3(0,1,0);input.previousGeometricNormal=input.geometricNormal;
    halo.HaloSurface surface;OpenPbrSurface p=surface.evaluateSurface(input);
    CoverageInput ci={};ci.instanceData=input.instanceData;ci.bindingData=input.bindingData;
    ci.primitiveIndex=id.z;ci.barycentrics=input.barycentrics;
    // Consume every material path; otherwise compilation can remove normals and
    // emission as unused when checking only base color and coverage.
    halo.HaloCoverage coverage;result[id.x*4]=float4(p.base_color,coverage.evaluateCoverage(ci));
    result[id.x*4+1]=float4(p.geometry_normal,p.specular_roughness);
    result[id.x*4+2]=float4(p.previous_geometry_normal,p.specular_ior);
    result[id.x*4+3]=float4(p.emission_color,p.emission_luminance);
}

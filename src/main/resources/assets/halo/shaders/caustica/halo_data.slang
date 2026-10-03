module halo_data;
import caustica_types;
import caustica_resources;
namespace halo {
public struct Vertex { public float x,y,z,u,v,nx,ny,nz; };
public struct Geometry { public uint64_t vertices,indices; };
public struct Material {
    public uint baseTexture,innerTexture,normalTexture,specTexture,maskTexture,reservedSampler,flags,mode;
    public float alpha,brightness,threshold,offsetU,offsetV,nits,reserved0,reserved1;
    public float4 normal0,normal1,normal2;
    public uint innerNormal,innerSpec,reserved2,reserved3;
};
public struct Hit { public Vertex a,b,c; public float2 uv; public float3 normal; };
public Hit hit(uint64_t binding,uint primitive,float2 bary) {
    Geometry g=ShaderDataPtr<Geometry>(binding)[0];
    ShaderDataPtr<uint> indices=ShaderDataPtr<uint>(g.indices);
    ShaderDataPtr<Vertex> vertices=ShaderDataPtr<Vertex>(g.vertices);
    Hit h;h.a=vertices[indices[primitive*3]];h.b=vertices[indices[primitive*3+1]];h.c=vertices[indices[primitive*3+2]];
    float3 w=float3(1-bary.x-bary.y,bary);
    h.uv=float2(h.a.u,h.a.v)*w.x+float2(h.b.u,h.b.v)*w.y+float2(h.c.u,h.c.v)*w.z;
    h.normal=float3(h.a.nx,h.a.ny,h.a.nz)*w.x+float3(h.b.nx,h.b.ny,h.b.nz)*w.y+float3(h.c.nx,h.c.ny,h.c.nz)*w.z;
    return h;
}
/** Exact nearest/repeat, LOD zero; texture alpha and data are never filtered or gamma decoded twice. */
public float4 texel(uint index,float2 uv) {
    SampledTexture2DIndex slot;slot.value=index;
    Texture2D<float4> image=sampledTexture2D(slot);
    uint width,height;image.GetDimensions(width,height);
    return image.Load(int3(int2(floor(frac(uv)*float2(width,height))),0));
}
public float coverage(Material m,Hit h) {
    float alpha=m.alpha;
    if((m.flags&1u)!=0u)alpha*=texel(m.baseTexture,h.uv).a;
    if((m.flags&64u)!=0u) {
        float red=texel(m.maskTexture,h.uv+float2(m.offsetU,m.offsetV)).r;
        alpha*=m.mode==1u?(red>=m.threshold?1.0:0.0):red;
    }
    return alpha<=0 || ((m.flags&8u)!=0u && alpha<0.1)?0:saturate(alpha);
}
public float3 explicitNormal(Material m,float3 n){return float3(dot(m.normal0.xyz,n),dot(m.normal1.xyz,n),dot(m.normal2.xyz,n));}
}

// Halo BA contract v1. Raw companion RGBA; scene-linear color; no LabPBR decoding.
uniform int HaloBA_type;
uniform int HaloBA_hasMask;
uniform int HaloBA_hasSpec;
uniform sampler2D HaloBA_mask;
uniform sampler2D HaloBA_spec;
uniform mat3 HaloBA_viewToReference;
uniform vec3 HaloBA_objectDirection;
uniform float HaloBA_fixedLight;
// Parameter declarations are generated from the exact Blender socket table.
/* HALO_BA_PARAMETERS */

float baClamp(float x) { return clamp(x, 0.0, 1.0); }
vec3 baSRGB(vec3 c) {
    return mix(c/12.92,pow((max(c,vec3(0.0))+0.055)/1.055,vec3(2.4)),step(vec3(0.04045),c));
}
float baMap(float x, float a, float b, float c, float d, bool clamped) {
    float t = a == b ? 0.0 : (x-a)/(b-a);
    return mix(c,d,clamped ? baClamp(t) : t);
}
vec3 baSafeNormal(vec3 v) { float q = dot(v,v); return q > 1e-12 ? v*inversesqrt(q) : vec3(0,0,1); }
float baFresnel(float c) {
    c = abs(c); float g = sqrt(1.5*1.5 - 1.0 + c*c);
    float a = (g-c)/(g+c), b = (c*(g+c)-1.0)/(c*(g-c)+1.0);
    return 0.5*a*a*(1.0+b*b);
}
float baRamp(float x) {
    if(x<0.0072727273) return baMap(x,0.0,0.0072727273,0.0,0.05,true);
    if(x<0.58909091) return baMap(x,0.0072727273,0.58909091,0.05,0.58184,true);
    return baMap(x,0.58909091,0.74909091,0.58184,1.0,true);
}
vec3 baBody(vec3 base, vec4 mask, float light, vec3 normal, vec3 incoming) {
    float a = mask.a;
    vec3 shadow = HaloBA_shadow_color0.rgb;
    if(a>HaloBA_shadow_color1_clamp) shadow=HaloBA_shadow_color1.rgb;
    if(a>HaloBA_shadow_color2_clamp) shadow=HaloBA_shadow_color2.rgb;
    if(a>HaloBA_shadow_color3_clamp) shadow=HaloBA_shadow_color3.rgb;
    if(a>HaloBA_shadow_color4_clamp) shadow=HaloBA_shadow_color4.rgb;
    if(a>HaloBA_shadow_color5_clamp) shadow=HaloBA_shadow_color5.rgb;
    vec3 dark=mix(base, base.b>shadow.b ? shadow : base, baClamp(HaloBA_shadowfactor));
    float m=1.0-2.0*mask.g;
    float edge=HaloBA_shadow_value+HaloBA_Value-1.35;
    float adjust=m>HaloBA_Value-0.3 ? m : baMap(m,edge,-edge,-1.0,1.0,true);
    float shade=baMap(light,HaloBA_From_Min,HaloBA_From_Max,0.0,HaloBA_To_Max,true)+adjust;
    vec3 result=mix(dark,base,baClamp(shade));
    float rim=baFresnel(dot(normal,incoming))>HaloBA_Fresnel && a<=HaloBA_shadow_color2_clamp ? 1.0 : 0.0;
    return mix(result,vec3(1.0,0.90406698,0.82423109),rim);
}
vec3 baHair(vec3 base, vec4 mask, vec4 spec, float light, vec3 normal, vec3 incoming) {
    float k=HaloBA_MaskGSensitivity;
    float shadow=baMap(light,HaloBA_From_Min,HaloBA_From_Max,-k,k,false)
        -(mask.g*2.0-1.0-HaloBA_AdjustiveHairShadow*mask.r)*k;
    // TRUNC is toward zero, unlike floor for negative values.
    float shadowMask=sign(-20.0*shadow)*floor(abs(-20.0*shadow));
    vec3 dark=mix(base,base*0.5,baClamp(HaloBA_shadowfactor));
    dark=mix(dark,mix(base,HaloBA_shadowcolor.rgb,baClamp(HaloBA_shadowfactor)),baClamp(HaloBA_shadowcolor_switch));
    vec3 shaded=mix(base,dark,baClamp(shadowMask));
    vec3 highlight=mix(shaded*2.0,HaloBA_highlightcolor.rgb,baClamp(HaloBA_highlightcolor_switch));
    vec3 direction=baSafeNormal(spec.rgb*2.0-1.0);
    vec3 halfDirection=baSafeNormal(baSafeNormal(HaloBA_viewToReference*incoming)*HaloBA_view_vector
        +baSafeNormal(HaloBA_objectDirection)*HaloBA_object_vector);
    float d=dot(direction,halfDirection)+dot(direction,vec3(HaloBA_SpecDirMultiplier_X,HaloBA_SpecDirMultiplier_Y,HaloBA_SpecDirMultiplier_Z));
    float shape=d>0.0 ? (1.0-d)*HaloBA_SpecTopMultiplier-HaloBA_SpecTopLeveler
        : pow(max(d,-HaloBA_SpecBotArea)+HaloBA_SpecBotArea,2.0)*HaloBA_SpecBotMultiplier;
    shape-=1.0-spec.a;
    float strength=mix(shape,shape>HaloBA_SpecClamp ? 1.0 : 0.0,baClamp(HaloBA_Hardspec))*HaloBA_SpecStrength;
    if(HaloBA_hasSpec==0 || spec.a<0.07) strength=0.0;
    float factor=baRamp(shadow+0.3)*baClamp(strength)*2.0;
    vec3 result=mix(shaded,highlight,baClamp(factor));
    float rim=baFresnel(dot(normal,incoming))>HaloBA_Fresnel ? HaloBA_SpecStrength : 0.0;
    return mix(result,highlight,baClamp(rim));
}
vec3 baHalo(vec3 base) {
    vec3 delta=base-HaloBA_slect_color.rgb;
    float selection=baClamp((1.0-baClamp(dot(delta,delta)*HaloBA_replace_clamp))*HaloBA_replace_multiply);
    vec3 mixed=mix(base,HaloBA_mix_color.rgb,baClamp(HaloBA_mix_fac));
    mixed=mix(mixed,HaloBA_replace_color.rgb,selection);
    float strength=mix(HaloBA_Emission_Strength2+HaloBA_Emission_Strength,HaloBA_Emission_Strength1+HaloBA_Emission_Strength,selection);
    return mixed*max(strength,0.0);
}
vec3 baWeapon(vec3 base, vec4 mask, float light, vec3 normal, vec3 incoming) {
    float z=mask.b*2.0-1.0;
    vec3 ao=mix(base,vec3(0),baClamp(HaloBA_AO_Value));
    vec3 color=mix(ao,base,baClamp(z));
    // Math.009 is muted: Blender bypasses its subtract-(1-z) input.
    float shade=baMap(light,0.0,0.25000006,-1.0,1.0,true)-(2.0*mask.g-1.0);
    color=mix(mix(color,vec3(0),baClamp(HaloBA_shadow_factor)),color,baClamp(shade));
    float inv=mix(mask.r+HaloBA_spec_clamp,1.0-(mask.r+HaloBA_spec_clamp),baClamp(HaloBA_spec_invert));
    float spec=baClamp(((1.0-dot(normal,incoming))*inv-0.2)*20.0)*HaloBA_spec_multiply;
    return mix(color,HaloBA_spec_color.rgb,baClamp(spec*baClamp(shade+HaloBA_spec_mask)));
}
vec3 baWeaponNormal(vec3 n, vec3 viewPos, vec2 uv, vec4 mask) {
    if(HaloBA_type!=5 || HaloBA_hasMask==0) return n;
    // Blender Bump height R, Strength=1, Distance=.001, using screen derivatives.
    vec3 dx=dFdx(viewPos), dy=dFdy(viewPos), r1=cross(dy,n), r2=cross(n,dx);
    float det=dot(dx,r1);
    vec3 gradient=(dFdx(mask.r)*r1+dFdy(mask.r)*r2)*0.001;
    return baSafeNormal(abs(det)*n+sign(det)*gradient); // active Bump invert=true
}
vec3 baEvaluate(vec3 base, vec4 mask, vec4 spec, vec3 illumination, vec3 ambient, float exposure, vec3 n, vec3 incoming) {
    float light=HaloBA_fixedLight>=0.0 ? HaloBA_fixedLight : dot(illumination*0.8,vec3(0.2126,0.7152,0.0722));
    // Toon thresholds choose the artist palette; the world irradiance supplies HDR brightness.
    // Without this conversion the palette acts as fixed emission and daylight exposure darkens it.
    // Fixed-light reference mode deliberately uses unit irradiance for Blender comparisons.
    vec3 environment=HaloBA_fixedLight>=0.0 ? vec3(1.0) : max(illumination,vec3(0.0));
    vec3 ambientEnvironment=HaloBA_fixedLight>=0.0 ? vec3(1.0) : max(ambient,vec3(0.0));
    if(HaloBA_type>=1 && HaloBA_type<=3) return baBody(base,mask,light,n,incoming)*environment;
    if(HaloBA_type==4) return baHair(base,mask,spec,light,n,incoming)*environment;
    if(HaloBA_type==5) return baWeapon(base,mask,light,n,incoming)*environment;
    // Convert halo emission to the host HDR scale. Keep a small exposure-compensated
    // emission floor; the original .5 strength is still applied by baHalo.
    if(HaloBA_type==6) return baHalo(base)*(HaloBA_fixedLight>=0.0 ? vec3(1.0)
        : max(ambientEnvironment,vec3(0.04/max(exposure,0.0001))));
    // No-shadow means independent of normal and cast shadows, not fixed HDR emission.
    // Eyes/mouth and outline retain their color while following local ambient brightness.
    return base*HaloBA_Strength*ambientEnvironment;
}

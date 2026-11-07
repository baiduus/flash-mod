#version 150
// Fix5: distinct world ribbon marker (1,.25,0); first-person/frost code preserved.
// Green=1 marks a short travelling brightness packet (UV.x spans 0..1).
// Fix42 (laser workspace override): in the (1,.25,b) ribbon branch, blue .15-.5 marks the Laser Blast (BeamLook5.drawBlast;
// blue - .25 = eye x .1). Its WHITE CORE is held much steadier (no lateral sway, a tenth of its width shimmer and
// flicker), while its RED-PINK HALO shakes harder and on its own: width pulsing (2.5x the old ribbon wobble and ripple),
// a noisy edge running along the beam, brightness flicker. Every term keeps its old mean, so size, colour and falloff are
// the fix40 blast's. Normal heat vision and the ultimate (blue 0) and every other branch are the original file unchanged.
uniform float Phase;
in vec4 vertexColor;
in vec2 beamCoord;
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
float noise(vec2 p) {
    vec2 cell=floor(p),f=fract(p);
    f=f*f*(3.0-2.0*f);
    return mix(mix(hash(cell),hash(cell+vec2(1,0)),f.x),
               mix(hash(cell+vec2(0,1)),hash(cell+vec2(1,1)),f.x),f.y);
}
float flow(vec2 p) { return .58*noise(p)+.28*noise(p*2.07)+.14*noise(p*4.13); }
float gaussian(float x,float width) {
    float filtered=sqrt(width*width+fwidth(x)*fwidth(x)/12.0);
    return (width/filtered)*exp(-x*x/(filtered*filtered));
}
void main() {
    float distance=beamCoord.x;
    float across=beamCoord.y*2.0-1.0;
    float edge=1.0-smoothstep(.66,1.0,abs(across));
    float travel=distance-Phase*8.4;
    float turbulence=flow(vec2(travel*2.6,across*3.1));
    float drift=(noise(vec2(distance*1.8-Phase*8.0,17.3))-.5)*.055;
    float radius=across-drift;
    vec3 color;
    float alpha;
    if (distance < 0.0) {
        // Existing eye flare UV was incorrectly being interpreted as a beam segment.
        vec2 q=vec2(-1.0-distance,beamCoord.y)*2.0-1.0;
        float r=length(q);
        float hot=1.0-smoothstep(.085,.24,r);
        float halo=exp(-r*r/.26);
        float radialEdge=1.0-smoothstep(.65,1.0,r);
        color=mix(vec3(1.0,.003,.009),vec3(1.0,.995,.985),hot);
        alpha=max(.98*hot,.68*halo)*radialEdge;
    } else if (vertexColor.r>.5 && vertexColor.g>.20 && vertexColor.g<.35 && vertexColor.b<.5) {
        // White-hot light falls off continuously into a scarlet mantle.
        // A narrow Gaussian filament replaces the old flat opaque white stripe;
        // HeatGlow23 supplies its separate soft additive light spill.
        float n=noise(vec2(distance*1.45-Phase*17.0,19.7));
        float packet=.5+.5*sin(distance*7.0-Phase*45.0);
        float radialEdge=1.0-smoothstep(.70,1.0,abs(across));
        if (vertexColor.b>.15) {
            // Fix42 Laser Blast: the core's width shimmer and flicker at a tenth, around the same means, and no lateral sway
            // at all (neither the centre nor the inner filament moves sideways)
            float k=.1;
            float eyePh=(vertexColor.b-.25)*17.0;
            float hot=gaussian(across,.067+k*(.004*(n-.5)+.002*(packet-.5)));
            float whiteGlow=gaussian(across,.171+k*.012*(n-.5));
            float filament=gaussian(across,.020);
            // the halo on its own: pulsing (2.5x the old 3.5 % ribbon wobble and 1.5 % travelling ripple), a noisy edge
            // boiling along the beam, its old width noise at 2.5x, and a brightness flicker
            float pulse=.0875*sin(Phase*37.0+eyePh)+.0375*sin(distance*3.1-Phase*24.0+eyePh*.18);
            float edgeN=noise(vec2(distance*2.2-Phase*31.0,7.3+eyePh*3.0))-.5;
            float flick=noise(vec2(Phase*23.0+eyePh*4.1,3.7))-.5;
            float redHalo=gaussian(across,(.5125+.0625*(n-.5))*(1.0+pulse+.14*edgeN));
            float haloEnergy=1.0+.16*flick+.10*edgeN;
            float light=clamp(hot*1.14+whiteGlow*.34,0.0,1.0);
            color=mix(vec3(1.0,.009,.025),vec3(1.0,.997,.98),light);
            float energy=3.25*hot+.82*whiteGlow+.31*redHalo*haloEnergy+.055*filament;
            alpha=(1.0-exp(-energy))*(.98+k*.04*(n-.5))*radialEdge;
        } else {
            float center=.004*sin(distance*3.7-Phase*16.0);
            float r=across-center;
            float hot=gaussian(r,.064+.004*n+.002*packet);
            float whiteGlow=gaussian(r,.165+.012*n);
            float redHalo=gaussian(r,.50+.025*n);
            float filament=gaussian(r-.026*sin(distance*2.4-Phase*9.0),.020);
            float light=clamp(hot*1.14+whiteGlow*.34,0.0,1.0);
            color=mix(vec3(1.0,.009,.025),vec3(1.0,.997,.98),light);
            float energy=3.25*hot+.82*whiteGlow+.31*redHalo+.055*filament;
            alpha=(1.0-exp(-energy))*(.96+.04*n)*radialEdge;
        }
    } else if(vertexColor.r<.5) {
        float mist=gaussian(radius,.62)*(.30+.42*turbulence);
        float curl=.20*sin(distance*3.3-Phase*6.0);
        float thread=gaussian(radius-curl,.16)*(.07+.17*turbulence);
        color=mix(vec3(.10,.51,1.0),vec3(.66,.91,1.0),clamp(thread+turbulence*.35,0.0,1.0));
        alpha=(mist+thread)*edge;
    } else if(vertexColor.g>.5) {
        float tail=smoothstep(0.0,.60,distance)*(1.0-smoothstep(.72,1.0,distance));
        float hot=gaussian(radius,.18);
        color=mix(vec3(1.0,.012,.04),vec3(1.0,.71,.65),hot*.8);
        alpha=(.22*gaussian(radius,.57)+.68*hot)*tail*edge;
    } else {
        // A narrow white-hot center inside a red turbulent sheath. Light advects along
        // the ray; its actual aim and eye origins do not shake with the shader noise.
        float wave=.5+.5*sin(travel*4.8);
        float packet=pow(wave,4.0);
        float halo=gaussian(radius,.62+.07*turbulence);
        float body=gaussian(radius,.29+.04*turbulence);
        float hot=gaussian(radius,.080+.026*turbulence+.014*packet);
        float curl=.20+.035*sin(travel*2.2);
        float wisps=gaussian(radius-curl,.030)+gaussian(radius+curl,.026);
        float fine=.5+.5*sin(travel*19.0+across*4.0);
        float detail=1.0/(1.0+fwidth(distance)*4.0);
        color=mix(vec3(1.0,.006,.028),vec3(1.0,.09,.10),body*.72);
        color=mix(color,vec3(1.0,.91,.87),clamp(hot*(.75+.22*packet),0.0,1.0));
        alpha=(.17*halo+.37*body*(.78+.22*turbulence)
                +hot*(.45+.25*packet)+.20*wisps*turbulence*detail
                +.08*body*fine*detail)*edge;
    }
    fragColor=vec4(color,clamp(alpha*vertexColor.a,0.0,1.0));
}

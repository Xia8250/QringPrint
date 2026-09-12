package com.thisko.qringprint.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

internal const val AGSL_LIQUID_GLASS = """
uniform shader content;
uniform float2  iResolution;
uniform float   ior;
uniform float   fresnelPower;
uniform float   dispersion;
uniform float   specular;
uniform float   sss;
uniform float   grain;
uniform float   tintR;
uniform float   tintG;
uniform float   tintB;
uniform float   tintA;

float2 toCentered(float2 uv) {
    return (uv * iResolution - iResolution * 0.5) / min(iResolution.x, iResolution.y);
}

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float2 boxNormal(float2 p, float2 halfSize, float edgeWidth) {
    float2 d = halfSize - abs(p);
    float2 n = float2(0.0);
    if (d.x < edgeWidth && d.y < edgeWidth) {
        n = (d.x < d.y) ? float2(sign(p.x), 0.0) : float2(0.0, sign(p.y));
    } else if (d.x < edgeWidth) {
        n = float2(sign(p.x), 0.0);
    } else if (d.y < edgeWidth) {
        n = float2(0.0, sign(p.y));
    }
    return n;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;
    float2 p = toCentered(fragCoord);
    float2 halfSize = iResolution * 0.5 / min(iResolution.x, iResolution.y);

    float edgeWidth = 0.04;
    float2 N2 = boxNormal(p, halfSize, edgeWidth);
    float Nmag = length(N2);

    float2 refrOff = N2 * ior * 0.012;
    float disp = dispersion;
    float2 offR = refrOff + N2 * disp *  1.0;
    float2 offG = refrOff + N2 * disp *  0.0;
    float2 offB = refrOff + N2 * disp * -1.0;

    float2 sampleR = fragCoord + offR * iResolution;
    float2 sampleG = fragCoord + offG * iResolution;
    float2 sampleB = fragCoord + offB * iResolution;
    float2 sampleA = fragCoord + refrOff * iResolution;
    half r = content.eval(sampleR).r;
    half g = content.eval(sampleG).g;
    half b = content.eval(sampleB).b;
    half a = content.eval(sampleA).a;
    half3 refrColor = half3(r, g, b);

    half3 tint = half3(tintR, tintG, tintB);
    half3 glassColor = mix(refrColor, tint, half(tintA * 0.78));

    float fresnel = pow(Nmag, fresnelPower);
    glassColor += half3(0.95, 0.97, 1.0) * fresnel * 0.80;

    float topHi = smoothstep(0.95, 0.55, uv.y) * (1.0 - smoothstep(0.95, 0.85, uv.y));
    glassColor += half3(1.0) * topHi * specular * 0.7;

    float leftHi = smoothstep(0.0, 0.06, uv.x) * (1.0 - smoothstep(0.0, 0.04, uv.x));
    glassColor += half3(1.0) * leftHi * specular * 0.25;

    half3 sssColor = half3(1.0, 0.78, 0.55);
    glassColor += sssColor * fresnel * sss * 0.35;

    if (grain > 0.0) {
        float n = hash21(fragCoord) - 0.5;
        glassColor += half3(n) * grain;
    }

    float bodyAlpha = 0.40 + tintA * 0.62;
    float rimAlpha = Nmag * 0.26 + topHi * 0.10;
    half outAlpha = half(clamp(max(bodyAlpha, a * 0.68) + rimAlpha, 0.42, 0.86));
    return half4(glassColor, outAlpha);
}
"""

internal const val AGSL_AURORA_BG = """
uniform float2 iResolution;
uniform float  time;
uniform float  hueA;
uniform float  hueB;
uniform float  hueC;
uniform float  darkMode;

half3 hsv2rgb(half3 c) {
    half4 K = half4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
    half3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float blob(float2 uv, float2 c, float r, float t) {
    float2 d = uv - c;
    d.x += sin(t * 0.3 + c.y * 4.0) * 0.15;
    d.y += cos(t * 0.25 + c.x * 3.5) * 0.15;
    return exp(-dot(d, d) / (r * r));
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution;
    float2 p = uv * 2.0 - 1.0;
    p.x *= iResolution.x / iResolution.y;

    half saturation = mix(0.42, 0.64, half(darkMode));
    half3 cA = hsv2rgb(half3(hueA, saturation, 0.98));
    half3 cB = hsv2rgb(half3(hueB, saturation, 0.94));
    half3 cC = hsv2rgb(half3(hueC, saturation * 0.92, 1.0));

    float a = blob(p, float2(-0.6,  0.4), 0.9, time);
    float b = blob(p, float2( 0.5, -0.3), 1.0, time + 1.7);
    float c = blob(p, float2( 0.0,  0.7), 1.2, time + 3.1);
    float d = blob(p, float2(-0.4, -0.6), 0.8, time + 4.5);

    half3 lightBg = half3(0.74, 0.79, 0.90);
    half3 darkBg = half3(0.025, 0.025, 0.07);
    half3 bg = mix(lightBg, darkBg, half(darkMode));
    half strength = mix(0.44, 0.66, half(darkMode));
    half3 col = bg;
    col += cA * half(a) * strength * 0.62;
    col += cB * half(b) * strength * 0.54;
    col += cC * half(c + d) * strength * 0.36;

    float vignette = 1.0 - smoothstep(0.25, 1.45, length(p));
    col *= mix(0.91, 1.04, half(vignette));

    float n = hash21(fragCoord) - 0.5;
    col += half3(n) * 0.012;

    return half4(clamp(col, 0.0, 1.0), 1.0);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun buildLiquidGlassShader(): RuntimeShader = RuntimeShader(AGSL_LIQUID_GLASS)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun buildAuroraShader(): RuntimeShader = RuntimeShader(AGSL_AURORA_BG)




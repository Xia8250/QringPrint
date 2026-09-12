package com.thisko.qringprint.ui.glass

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

object GlassLayer {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun configureLiquidGlass(
        shader: RuntimeShader,
        size: Size,
        ior: Float = GlassTokens.Ior,
        fresnel: Float = GlassTokens.FresnelPower,
        dispersion: Float = GlassTokens.Dispersion,
        specular: Float = GlassTokens.Specular,
        sss: Float = GlassTokens.SSS,
        grain: Float = GlassTokens.Grain,
        tint: Color = GlassTokens.TintLight,
    ) {
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("ior", ior)
        shader.setFloatUniform("fresnelPower", fresnel)
        shader.setFloatUniform("dispersion", dispersion)
        shader.setFloatUniform("specular", specular)
        shader.setFloatUniform("sss", sss)
        shader.setFloatUniform("grain", grain)
        shader.setFloatUniform("tintR", tint.red)
        shader.setFloatUniform("tintG", tint.green)
        shader.setFloatUniform("tintB", tint.blue)
        shader.setFloatUniform("tintA", tint.alpha)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun configureAurora(
        shader: RuntimeShader,
        size: Size,
        time: Float,
        dark: Boolean,
    ) {
        val hues = GlassTokens.auroraHues(dark)
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("hueA", hues[0])
        shader.setFloatUniform("hueB", hues[1])
        shader.setFloatUniform("hueC", hues[2])
        shader.setFloatUniform("darkMode", if (dark) 1f else 0f)
    }
}

/**
 * Liquid Glass layer Modifier 工厂.
 *  - API 33+: AGSL 真实物理 (色散 + 折射 + 菲涅尔 + SSS + 颗粒)
 *  - API 26-32: 半透明 + 描边 + 阴影 降级
 */
fun Modifier.glassLayer(
    blurRadius: Dp,
    cornerRadius: Dp,
    tint: Color = GlassTokens.TintLight,
    dark: Boolean = false,
    ior: Float = GlassTokens.Ior,
    fresnel: Float = GlassTokens.FresnelPower,
    dispersion: Float = GlassTokens.Dispersion,
    specular: Float = GlassTokens.Specular,
    sss: Float = GlassTokens.SSS,
    grain: Float = GlassTokens.Grain,
): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.then(
            Modifier.graphicsLayer {
                val shader = buildLiquidGlassShader()
                val effTint = tint.copy(alpha = if (dark) 0.18f else 0.22f)
                GlassLayer.configureLiquidGlass(
                    shader,
                    Size(size.width, size.height),
                    ior = ior,
                    fresnel = fresnel,
                    dispersion = dispersion,
                    specular = specular,
                    sss = sss,
                    grain = grain,
                    tint = effTint,
                )
                val shaderEffect = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
                val blur = AndroidRenderEffect.createBlurEffect(
                    blurRadius.toPx(),
                    blurRadius.toPx(),
                    Shader.TileMode.CLAMP,
                )
                val chain = AndroidRenderEffect.createChainEffect(blur, shaderEffect)
                renderEffect = chain.asComposeRenderEffect()
                clip = true
                shape = RoundedCornerShape(cornerRadius)
            }
        )
    } else {
        this.then(
            Modifier.background(
                if (dark) GlassTokens.TintDark.copy(alpha = 0.32f)
                else GlassTokens.TintLight.copy(alpha = 0.18f),
                RoundedCornerShape(cornerRadius),
            )
        )
    }
}




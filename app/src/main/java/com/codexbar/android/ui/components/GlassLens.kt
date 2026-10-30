package com.codexbar.android.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

/** Native backdrop lens inspired by Glisse. Foreground labels are drawn outside this effect. */
@RequiresApi(33)
internal class GlassLens {
    private val shader = RuntimeShader(SOURCE)

    fun effect(width: Float, height: Float, corner: Float, blur: Float, bend: Float, density: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("panelSize", width, height)
        shader.setFloatUniform("corner", corner)
        shader.setFloatUniform("bend", bend)
        shader.setFloatUniform("density", density)
        val lens = RenderEffect.createRuntimeShaderEffect(shader, "backdrop")
        return if (blur > 0f) {
            RenderEffect.createChainEffect(
                lens,
                RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
            ).asComposeRenderEffect()
        } else {
            lens.asComposeRenderEffect()
        }
    }

    private companion object {
        // Rounded-rectangle distance and normal give the rim a lens profile. Three nearby
        // samples split colours subtly at the edge; the middle stays clear and undistorted.
        const val SOURCE = """
            uniform shader backdrop;
            uniform float2 panelSize;
            uniform float corner;
            uniform float bend;
            uniform float density;

            float2 bounded(float2 p) {
                return clamp(p, float2(0.5), panelSize - float2(0.5));
            }

            half4 main(float2 coord) {
                float2 halfSize = panelSize * 0.5;
                float2 p = coord - halfSize;
                float r = min(corner, min(halfSize.x, halfSize.y));
                float2 q = abs(p) - halfSize + r;
                float distance = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
                float2 normal;
                if (q.x > 0.0 && q.y > 0.0) {
                    normal = normalize(q) * sign(p);
                } else {
                    normal = q.x > q.y ? float2(sign(p.x), 0.0) : float2(0.0, sign(p.y));
                }
                float band = max(1.0, min(halfSize.x, halfSize.y) * 0.5);
                float edge = 1.0 - smoothstep(0.0, band, max(0.0, -distance));
                float2 shift = -normal * edge * edge * band * bend;
                half4 middle = backdrop.eval(bounded(coord + shift));
                half red = backdrop.eval(bounded(coord + shift * 1.04)).r;
                half blue = backdrop.eval(bounded(coord + shift * 0.96)).b;
                half3 colour = half3(red, middle.g, blue);
                half luminance = dot(colour, half3(0.2126, 0.7152, 0.0722));
                colour = mix(half3(luminance), colour, 1.12);
                float gleam = (1.0 - smoothstep(0.0, 1.2 * density, abs(distance)))
                    * pow(abs(dot(normal, normalize(float2(-1.0, -1.0)))), 2.0);
                colour = mix(colour, half3(middle.a), half(gleam * 0.14));
                return half4(clamp(colour, half3(0.0), half3(middle.a)), middle.a);
            }
        """
    }
}

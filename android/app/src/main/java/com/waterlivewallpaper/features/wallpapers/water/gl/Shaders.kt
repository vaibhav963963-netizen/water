package com.waterlivewallpaper.features.wallpapers.water.gl

object Shaders {

    // Pass 1: Splat smooth, compact particle densities into FBO
    val DENSITY_VERTEX_SHADER = """
        #version 100
        attribute vec2 a_Position;
        attribute float a_Radius;

        uniform vec2 u_Resolution;

        void main() {
            vec2 zeroToOne = a_Position / u_Resolution;
            vec2 clipSpace = zeroToOne * 2.0 - 1.0;
            // Invert Y so (0,0) in screen coordinates is at the top of the viewport
            gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
            // Splat size in FBO pixel units: covers adjacent particles smoothly to form continuous fluid
            gl_PointSize = a_Radius * 3.2;
        }
    """.trimIndent()

    val DENSITY_FRAGMENT_SHADER = """
        #version 100
        precision mediump float;

        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            float r2 = dot(coord, coord) * 4.0;
            if (r2 >= 1.0) {
                discard;
            }
            // Smooth Gaussian-like quadratic kernel (1 - r^2)^2 ensures seamless overlap between particles
            float falloff = 1.0 - r2;
            float density = falloff * falloff;
            gl_FragColor = vec4(density, density, density, 1.0);
        }
    """.trimIndent()

    // Pass 2: Fullscreen Translucent Isosurface Reconstruction with Optical Wave Refraction
    val ISOSURFACE_VERTEX_SHADER = """
        #version 100
        attribute vec2 a_QuadPosition;
        varying vec2 v_TexCoord;

        void main() {
            v_TexCoord = (a_QuadPosition + 1.0) * 0.5;
            gl_Position = vec4(a_QuadPosition, 0.0, 1.0);
        }
    """.trimIndent()

    val ISOSURFACE_FRAGMENT_SHADER = """
        #version 100
        precision mediump float;

        uniform sampler2D u_DensityTexture;
        uniform sampler2D u_BackgroundTexture;
        uniform int u_HasCustomBackground;
        uniform vec2 u_ScreenResolution;
        uniform vec2 u_BgResolution;
        uniform vec4 u_WaterColor;
        uniform vec4 u_SecondaryColor;
        uniform vec4 u_BackgroundColor;
        uniform float u_GlowIntensity;

        varying vec2 v_TexCoord;

        void main() {
            vec2 uv = v_TexCoord;
            float density = texture2D(u_DensityTexture, uv).r;

            // Calibrated thresholds for smooth continuous water isosurface
            float liquidThreshold = 0.16;
            float glowThreshold = 0.03;

            // Direct background coordinate (aspect-ratio preserved cover image or solid color)
            vec2 rawUv = vec2(uv.x, 1.0 - uv.y);
            float screenAR = u_ScreenResolution.x / max(u_ScreenResolution.y, 1.0);
            float bgAR = u_BgResolution.x / max(u_BgResolution.y, 1.0);
            float r = screenAR / bgAR;
            vec2 bgUv = (r > 1.0) ? vec2(rawUv.x, 0.5 + (rawUv.y - 0.5) / r)
                                  : vec2(0.5 + (rawUv.x - 0.5) * r, rawUv.y);
            vec4 baseBgColor = (u_HasCustomBackground == 1) ? texture2D(u_BackgroundTexture, bgUv) : u_BackgroundColor;

            if (density < glowThreshold) {
                gl_FragColor = baseBgColor;
                return;
            }

            // Normal reconstruction using central differences of the density field
            vec2 texel = vec2(1.0 / u_ScreenResolution.x, 1.0 / u_ScreenResolution.y) * 2.5;
            float dL = texture2D(u_DensityTexture, uv - vec2(texel.x, 0.0)).r;
            float dR = texture2D(u_DensityTexture, uv + vec2(texel.x, 0.0)).r;
            float dD = texture2D(u_DensityTexture, uv - vec2(0.0, texel.y)).r;
            float dU = texture2D(u_DensityTexture, uv + vec2(0.0, texel.y)).r;

            vec2 normal2D = vec2(dL - dR, dD - dU);
            float normalLen = length(normal2D);
            vec2 normDir = normalLen > 0.001 ? normal2D / normalLen : vec2(0.0);

            // Refract background texture through moving water surface
            vec2 refractOffset = normDir * min(normalLen * 0.035, 0.022);
            vec2 refractUv = clamp(uv + refractOffset, 0.001, 0.999);
            vec2 refractRawUv = vec2(refractUv.x, 1.0 - refractUv.y);
            vec2 refrBgUv = (r > 1.0) ? vec2(refractRawUv.x, 0.5 + (refractRawUv.y - 0.5) / r)
                                      : vec2(0.5 + (refractRawUv.x - 0.5) * r, refractRawUv.y);
            vec4 refractBgColor = (u_HasCustomBackground == 1) ? texture2D(u_BackgroundTexture, refrBgUv) : u_BackgroundColor;

            if (density >= liquidThreshold) {
                // Smooth antialiased boundary edge
                float edgeAlpha = smoothstep(liquidThreshold - 0.02, liquidThreshold + 0.03, density);

                // Depth factor: deeper water has richer ocean hue
                float depthFactor = clamp((density - liquidThreshold) * 0.45, 0.0, 1.0);
                vec3 waterColor = mix(u_WaterColor.rgb, u_SecondaryColor.rgb, depthFactor);

                // Translucent water: wallpaper is clearly visible through the water
                float fluidAlpha = mix(0.28, 0.58, depthFactor) * edgeAlpha;
                vec3 finalRgb = mix(refractBgColor.rgb, waterColor, fluidAlpha);

                // Natural sunlight specular gleam on wave crests and droplet surface curvature
                vec3 lightDir = normalize(vec3(0.35, 0.75, 0.55));
                vec3 surfNormal3D = normalize(vec3(normDir * 0.75, 1.0 - normalLen * 0.5));
                float specular = pow(max(dot(surfNormal3D, lightDir), 0.0), 14.0) * 0.45;

                // Luminous meniscus water line along the outer surface boundary
                float meniscus = smoothstep(liquidThreshold - 0.02, liquidThreshold + 0.03, density) * 
                                 (1.0 - smoothstep(liquidThreshold + 0.03, liquidThreshold + 0.20, density));
                vec3 meniscusColor = vec3(0.92, 0.98, 1.0);
                finalRgb += meniscusColor * ((meniscus * 0.55 + specular) * u_GlowIntensity);

                gl_FragColor = vec4(finalRgb, 1.0);
            } else {
                // Soft luminous glow along the outer liquid meniscus
                float glowFactor = smoothstep(glowThreshold, liquidThreshold, density);
                float glowAlpha = pow(glowFactor, 1.4) * u_GlowIntensity * 0.42;
                vec3 finalRgb = mix(baseBgColor.rgb, u_WaterColor.rgb, glowAlpha);
                gl_FragColor = vec4(finalRgb, 1.0);
            }
        }
    """.trimIndent()
}

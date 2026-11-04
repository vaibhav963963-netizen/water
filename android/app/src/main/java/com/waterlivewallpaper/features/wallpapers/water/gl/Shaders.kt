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
            // Compact splat size for high-density smooth fluid (no giant blobs)
            gl_PointSize = a_Radius * 3.4;
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
            // Smooth Gaussian-like kernel: (1 - r^2)^2
            float density = 1.0 - r2;
            density = density * density;
            gl_FragColor = vec4(density, density, density, 1.0);
        }
    """.trimIndent()

    // Pass 2: Fullscreen Translucent Isosurface & Luminous Meniscus reconstruction
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

            // Resolve background color: aspect-ratio preserved cover image or solid background
            vec4 bgColor;
            if (u_HasCustomBackground == 1) {
                vec2 rawUv = vec2(uv.x, 1.0 - uv.y);
                float screenAR = u_ScreenResolution.x / max(u_ScreenResolution.y, 1.0);
                float bgAR = u_BgResolution.x / max(u_BgResolution.y, 1.0);
                float r = screenAR / bgAR;
                vec2 bgUv;
                if (r > 1.0) {
                    bgUv = vec2(rawUv.x, 0.5 + (rawUv.y - 0.5) / r);
                } else {
                    bgUv = vec2(0.5 + (rawUv.x - 0.5) * r, rawUv.y);
                }
                bgColor = texture2D(u_BackgroundTexture, bgUv);
            } else {
                bgColor = u_BackgroundColor;
            }

            // Calibrated thresholds for smooth water isosurface
            float liquidThreshold = 0.15;
            float glowThreshold = 0.03;

            if (density < glowThreshold) {
                gl_FragColor = bgColor;
                return;
            }

            if (density >= liquidThreshold) {
                // Smooth antialiased boundary edge
                float edgeAlpha = smoothstep(liquidThreshold - 0.02, liquidThreshold + 0.02, density);

                // Depth gradient: deeper water has rich blue/cyan body
                float depthFactor = clamp((density - liquidThreshold) * 0.45, 0.0, 1.0);
                vec3 waterTint = mix(u_WaterColor.rgb, u_SecondaryColor.rgb, depthFactor);

                // Translucent water: wallpaper is clearly visible through the water
                float fluidAlpha = mix(0.32, 0.62, depthFactor) * edgeAlpha;
                vec3 finalRgb = mix(bgColor.rgb, waterTint, fluidAlpha);

                // Luminous meniscus water line along the outer surface boundary
                float meniscus = smoothstep(liquidThreshold - 0.01, liquidThreshold + 0.03, density) * 
                                 (1.0 - smoothstep(liquidThreshold + 0.03, liquidThreshold + 0.15, density));
                vec3 meniscusColor = vec3(0.88, 0.96, 1.0);
                finalRgb += meniscusColor * (meniscus * 0.55 * u_GlowIntensity);

                gl_FragColor = vec4(finalRgb, 1.0);
            } else {
                // Soft luminous glow along the outer liquid meniscus
                float glowFactor = smoothstep(glowThreshold, liquidThreshold, density);
                float glowAlpha = pow(glowFactor, 1.5) * u_GlowIntensity * 0.50;
                vec3 finalRgb = mix(bgColor.rgb, u_WaterColor.rgb, glowAlpha);
                gl_FragColor = vec4(finalRgb, 1.0);
            }
        }
    """.trimIndent()
}

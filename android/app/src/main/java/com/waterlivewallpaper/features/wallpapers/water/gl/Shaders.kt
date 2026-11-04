package com.waterlivewallpaper.features.wallpapers.water.gl

object Shaders {

    // 1. Fullscreen Background Program (Aspect-ratio preserved cover mode, zero stretching)
    val BG_VERTEX_SHADER = """
        #version 100
        attribute vec2 a_QuadPosition;
        varying vec2 v_TexCoord;

        void main() {
            v_TexCoord = (a_QuadPosition + 1.0) * 0.5;
            gl_Position = vec4(a_QuadPosition, 0.0, 1.0);
        }
    """.trimIndent()

    val BG_FRAGMENT_SHADER = """
        #version 100
        precision mediump float;

        uniform sampler2D u_BackgroundTexture;
        uniform int u_HasCustomBackground;
        uniform vec2 u_ScreenResolution;
        uniform vec2 u_BgResolution;
        uniform vec4 u_BackgroundColor;

        varying vec2 v_TexCoord;

        void main() {
            vec2 uv = vec2(v_TexCoord.x, 1.0 - v_TexCoord.y);

            if (u_HasCustomBackground == 1) {
                float screenAR = u_ScreenResolution.x / max(u_ScreenResolution.y, 1.0);
                float bgAR = u_BgResolution.x / max(u_BgResolution.y, 1.0);
                float r = screenAR / bgAR;
                vec2 bgUv;
                if (r > 1.0) {
                    bgUv = vec2(uv.x, 0.5 + (uv.y - 0.5) / r);
                } else {
                    bgUv = vec2(0.5 + (uv.x - 0.5) * r, uv.y);
                }
                gl_FragColor = texture2D(u_BackgroundTexture, bgUv);
            } else {
                gl_FragColor = u_BackgroundColor;
            }
        }
    """.trimIndent()

    // 2. Continuous Container Fluid Body Program (Triangle Strip Mesh)
    val FLUID_VERTEX_SHADER = """
        #version 100
        attribute vec2 a_Position;
        attribute float a_Depth;
        attribute float a_Slope;

        uniform vec2 u_Resolution;

        varying vec2 v_TexCoord;
        varying float v_Depth;
        varying float v_Slope;

        void main() {
            vec2 zeroToOne = a_Position / u_Resolution;
            vec2 clipSpace = zeroToOne * 2.0 - 1.0;
            // Invert Y so (0,0) in screen coordinates is at the top of the viewport
            gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
            v_TexCoord = zeroToOne;
            v_Depth = a_Depth;
            v_Slope = a_Slope;
        }
    """.trimIndent()

    val FLUID_FRAGMENT_SHADER = """
        #version 100
        precision mediump float;

        uniform sampler2D u_BackgroundTexture;
        uniform int u_HasCustomBackground;
        uniform vec2 u_ScreenResolution;
        uniform vec2 u_BgResolution;
        uniform vec4 u_WaterColor;
        uniform vec4 u_SecondaryColor;
        uniform vec4 u_BackgroundColor;
        uniform float u_GlowIntensity;

        varying vec2 v_TexCoord;
        varying float v_Depth;
        varying float v_Slope;

        void main() {
            vec2 uv = v_TexCoord;

            // Subtle optical wave refraction through moving water surface
            vec2 refractUv = uv + vec2(-v_Slope * 0.015 * (1.0 - v_Depth), 0.0);
            refractUv = clamp(refractUv, 0.001, 0.999);

            vec4 bgColor;
            if (u_HasCustomBackground == 1) {
                float screenAR = u_ScreenResolution.x / max(u_ScreenResolution.y, 1.0);
                float bgAR = u_BgResolution.x / max(u_BgResolution.y, 1.0);
                float r = screenAR / bgAR;
                vec2 bgUv;
                if (r > 1.0) {
                    bgUv = vec2(refractUv.x, 0.5 + (refractUv.y - 0.5) / r);
                } else {
                    bgUv = vec2(0.5 + (refractUv.x - 0.5) * r, refractUv.y);
                }
                bgColor = texture2D(u_BackgroundTexture, bgUv);
            } else {
                bgColor = u_BackgroundColor;
            }

            // Translucent depth shading:
            // Near surface (v_Depth = 0.0): crystal clear transparent aquatic tint (28% tint)
            // Deep water (v_Depth = 1.0): richer cyan/blue gradient (60% tint)
            // Wallpaper background is clearly visible through the water!
            vec3 liquidColor = mix(u_WaterColor.rgb, u_SecondaryColor.rgb, v_Depth);
            float waterAlpha = mix(0.28, 0.60, v_Depth);
            vec3 finalRgb = mix(bgColor.rgb, liquidColor, waterAlpha);

            // Luminous meniscus water line along top surface (v_Depth < 0.035)
            float meniscus = 1.0 - smoothstep(0.0, 0.035, v_Depth);
            // Specular sunlight glint along wave crests
            float glint = pow(clamp(1.0 - abs(v_Slope) * 1.2, 0.0, 1.0), 3.0) * 0.40;
            vec3 meniscusHighlight = vec3(0.88, 0.96, 1.0);
            finalRgb += meniscusHighlight * (meniscus * (0.40 + glint) * u_GlowIntensity);

            gl_FragColor = vec4(finalRgb, 1.0);
        }
    """.trimIndent()

    // 3. Fine Spray Droplet Program (Realistic 4-8px antialiased spherical droplets)
    val DROPLET_VERTEX_SHADER = """
        #version 100
        attribute vec2 a_Position;
        attribute float a_Radius;

        uniform vec2 u_Resolution;

        void main() {
            vec2 zeroToOne = a_Position / u_Resolution;
            vec2 clipSpace = zeroToOne * 2.0 - 1.0;
            gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
            gl_PointSize = a_Radius * 2.4;
        }
    """.trimIndent()

    val DROPLET_FRAGMENT_SHADER = """
        #version 100
        precision mediump float;

        uniform vec4 u_WaterColor;

        void main() {
            vec2 p = gl_PointCoord - vec2(0.5);
            float r = length(p);
            if (r > 0.5) {
                discard;
            }
            // Antialiased circular edge
            float alpha = smoothstep(0.5, 0.40, r);
            // Specular highlight glint near upper-left
            float specular = smoothstep(0.18, 0.0, length(p - vec2(-0.16, -0.16)));
            vec3 dropRgb = mix(u_WaterColor.rgb, vec3(1.0), specular * 0.85);
            gl_FragColor = vec4(dropRgb, alpha * 0.88);
        }
    """.trimIndent()
}

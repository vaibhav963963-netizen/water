package com.waterlivewallpaper.gl

object Shaders {

    // Pass 1: Splat smooth particle density into FBO
    val DENSITY_VERTEX_SHADER = """
        #version 100
        attribute vec2 a_Position;
        attribute float a_Radius;

        uniform vec2 u_Resolution;

        varying vec2 v_PointCoord;

        void main() {
            vec2 zeroToOne = a_Position / u_Resolution;
            vec2 clipSpace = zeroToOne * 2.0 - 1.0;
            // Invert Y so (0,0) in screen coordinates is at the top of the viewport
            gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
            gl_PointSize = a_Radius * 4.2;
        }
    """.trimIndent()

    val DENSITY_FRAGMENT_SHADER = """
        #version 100
        precision mediump float;

        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            float dist = length(coord) * 2.0;
            if (dist >= 1.0) {
                discard;
            }
            // Smooth bell-shaped Gaussian-like kernel: (1 - r^2)^2
            float density = 1.0 - dist * dist;
            density = density * density;
            gl_FragColor = vec4(density, density, density, 1.0);
        }
    """.trimIndent()

    // Pass 2: Fullscreen Isosurface & Luminous Glow reconstruction
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
        uniform vec4 u_WaterColor;
        uniform vec4 u_SecondaryColor;
        uniform vec4 u_BackgroundColor;
        uniform float u_GlowIntensity;

        varying vec2 v_TexCoord;

        void main() {
            // Direct texture coordinate (Y=0 is bottom, Y=1 is top in OpenGL viewport)
            vec2 uv = v_TexCoord;
            float density = texture2D(u_DensityTexture, uv).r;

            // Resolve background color: either custom bitmap texture (with inverted Y for Bitmap orientation) or solid background color
            vec4 bgColor;
            if (u_HasCustomBackground == 1) {
                bgColor = texture2D(u_BackgroundTexture, vec2(uv.x, 1.0 - uv.y));
            } else {
                bgColor = u_BackgroundColor;
            }

            // Thresholds for metaball isosurface
            float liquidThreshold = 0.28;
            float glowThreshold = 0.05;

            if (density < glowThreshold) {
                // Background
                gl_FragColor = bgColor;
                return;
            }

            if (density >= liquidThreshold) {
                // Inside liquid body: smooth organic boundary with anti-aliasing
                float edgeAlpha = smoothstep(liquidThreshold - 0.03, liquidThreshold + 0.03, density);

                // Organic depth gradient: deeper water is richer, surface has slight specular highlight
                float depthFactor = clamp((density - liquidThreshold) * 0.9, 0.0, 1.0);
                vec3 liquidRgb = mix(u_SecondaryColor.rgb, u_WaterColor.rgb, depthFactor);

                // Subtle luminous surface highlight
                float surfaceHighlight = smoothstep(liquidThreshold, liquidThreshold + 0.12, density) * (1.0 - smoothstep(liquidThreshold + 0.12, liquidThreshold + 0.28, density));
                liquidRgb += vec3(0.12, 0.22, 0.25) * surfaceHighlight;

                vec3 finalRgb = mix(bgColor.rgb, liquidRgb, edgeAlpha);
                gl_FragColor = vec4(finalRgb, 1.0);
            } else {
                // Soft luminous cyan glow along the outer liquid meniscus
                float glowFactor = smoothstep(glowThreshold, liquidThreshold, density);
                float glowAlpha = pow(glowFactor, 1.4) * u_GlowIntensity * 0.85;

                vec3 finalRgb = mix(bgColor.rgb, u_WaterColor.rgb, glowAlpha);
                gl_FragColor = vec4(finalRgb, 1.0);
            }
        }
    """.trimIndent()
}

package com.waterlivewallpaper.features.wallpapers.water.gl

object Shaders {

    // Background Shader (Aspect-ratio preserving cover mode, zero stretching)
    val BG_VERTEX_SHADER = """
        #version 100
        attribute vec4 a_Position;
        attribute vec2 a_TexCoord;
        varying vec2 v_TexCoord;

        void main() {
            gl_Position = a_Position;
            v_TexCoord = a_TexCoord;
        }
    """.trimIndent()

    val BG_FRAGMENT_SHADER = """
        #version 100
        precision mediump float;

        uniform sampler2D u_Texture;
        uniform vec2 u_ScreenResolution;
        uniform vec2 u_BgResolution;
        uniform int u_HasTexture;
        uniform vec4 u_SolidColor;

        varying vec2 v_TexCoord;

        void main() {
            if (u_HasTexture == 1) {
                vec2 rawUv = vec2(v_TexCoord.x, 1.0 - v_TexCoord.y);
                float screenAR = u_ScreenResolution.x / max(u_ScreenResolution.y, 1.0);
                float bgAR = u_BgResolution.x / max(u_BgResolution.y, 1.0);
                float r = screenAR / bgAR;
                vec2 bgUv;
                if (r > 1.0) {
                    bgUv = vec2(rawUv.x, 0.5 + (rawUv.y - 0.5) / r);
                } else {
                    bgUv = vec2(0.5 + (rawUv.x - 0.5) * r, rawUv.y);
                }
                gl_FragColor = texture2D(u_Texture, bgUv);
            } else {
                gl_FragColor = u_SolidColor;
            }
        }
    """.trimIndent()

    // Container Water Mesh Shader (Renders crystal-clear liquid filling container up to wave surface)
    val WATER_MESH_VERTEX_SHADER = """
        #version 100
        attribute vec2 a_Position; // Screen-space coords [0, width] x [0, height]
        attribute float a_Depth;    // 0.0 at surface crest, 1.0 at container bottom

        uniform vec2 u_Resolution;

        varying float v_Depth;
        varying vec2 v_ScreenUv;

        void main() {
            vec2 zeroToOne = a_Position / u_Resolution;
            vec2 clipSpace = zeroToOne * 2.0 - 1.0;
            // Invert Y so (0, 0) is top-left
            gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
            v_Depth = a_Depth;
            v_ScreenUv = zeroToOne;
        }
    """.trimIndent()

    val WATER_MESH_FRAGMENT_SHADER = """
        #version 100
        precision mediump float;

        uniform vec4 u_WaterColor;
        uniform vec4 u_SecondaryColor;
        uniform float u_GlowIntensity;

        varying float v_Depth;
        varying vec2 v_ScreenUv;

        void main() {
            // Liquid depth gradient: surface is lighter & more translucent, deep water is richer
            vec3 waterRgb = mix(u_WaterColor.rgb, u_SecondaryColor.rgb, clamp(v_Depth * 0.85, 0.0, 1.0));

            // Specular luminous water meniscus highlight along the top wave crest (top 5% of depth)
            float surfaceHighlight = smoothstep(0.06, 0.0, v_Depth);
            vec3 crestColor = vec3(0.90, 0.98, 1.0);
            vec3 finalRgb = mix(waterRgb, crestColor, surfaceHighlight * 0.70 * u_GlowIntensity);

            // Water transparency: background image shows through realistically
            float alpha = mix(0.72, 0.92, clamp(v_Depth * 1.15, 0.0, 1.0));
            gl_FragColor = vec4(finalRgb, alpha);
        }
    """.trimIndent()

    // Splash Spray Droplet Shader
    val DROPLET_VERTEX_SHADER = """
        #version 100
        attribute vec2 a_Position;
        attribute float a_Radius;

        uniform vec2 u_Resolution;

        void main() {
            vec2 zeroToOne = a_Position / u_Resolution;
            vec2 clipSpace = zeroToOne * 2.0 - 1.0;
            gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
            gl_PointSize = a_Radius * 2.5;
        }
    """.trimIndent()

    val DROPLET_FRAGMENT_SHADER = """
        #version 100
        precision mediump float;

        uniform vec4 u_Color;

        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            float r2 = dot(coord, coord) * 4.0;
            if (r2 > 1.0) discard;
            float alpha = (1.0 - r2 * r2) * 0.88;
            gl_FragColor = vec4(u_Color.rgb, alpha);
        }
    """.trimIndent()

    // Legacy Density & Isosurface Shaders preserved for backward-compatibility
    val DENSITY_VERTEX_SHADER = """
        #version 100
        attribute vec2 a_Position;
        attribute float a_Radius;
        uniform vec2 u_Resolution;
        void main() {
            vec2 zeroToOne = a_Position / u_Resolution;
            vec2 clipSpace = zeroToOne * 2.0 - 1.0;
            gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
            gl_PointSize = a_Radius * 3.6;
        }
    """.trimIndent()

    val DENSITY_FRAGMENT_SHADER = """
        #version 100
        precision mediump float;
        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            float r2 = dot(coord, coord) * 4.0;
            if (r2 >= 1.0) discard;
            float density = 1.0 - r2;
            density = density * density;
            gl_FragColor = vec4(density, density, density, 1.0);
        }
    """.trimIndent()

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
        uniform vec4 u_WaterColor;
        varying vec2 v_TexCoord;
        void main() {
            float density = texture2D(u_DensityTexture, v_TexCoord).r;
            if (density < 0.1) discard;
            gl_FragColor = u_WaterColor;
        }
    """.trimIndent()
}

package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.gl.texture.InternalTextureFormat;
import com.bdmajora.impetus.umbra.shaderpack.ProgramSource;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The colortex directives (<target>Format, <target>Clear, <target>ClearColor, legacy GAUX4FORMAT, per-program <target>MipmapEnabled); they may sit in ANY stage and Umbra/OptiFine scan every one, so Sildur's HDR formats in gbuffers_textured.fsh are honoured
public final class TargetDirectives {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // The three target directives in one pass; the suffix decides which type and value shape is valid
    private static final Pattern DIRECTIVE = Pattern.compile(
            "const\\s+(int|bool|vec4)\\s+(\\w+?)(Format|ClearColor|Clear)\\s*=\\s*([^;]*?)\\s*;");
    private static final Pattern IDENTIFIER = Pattern.compile("\\w+");
    private static final Pattern VEC4 = Pattern.compile("vec4\\s*\\(([^)]*)\\)");
    // OptiFine's pre-colortexNFormat way of upgrading gaux4/colortex7 (GAUX4FORMAT:RGB32F)
    private static final Pattern GAUX4_FORMAT = Pattern.compile("/\\*\\s*GAUX4FORMAT\\s*:\\s*(\\w+)\\s*\\*/");
    private static final Pattern MIPMAP = Pattern.compile("const\\s+bool\\s+(\\w+?)MipmapEnabled\\s*=\\s*(true|false)\\s*;");
    private static final int GAUX4 = 7;

    // Umbra defaults every colortex to clear=true
    private final boolean[] clears = new boolean[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    // Explicit colortexNClearColor values; null means the default colour for that buffer
    private final float[][] clearColors = new float[UmbraRenderTargets.MAX_COLOR_BUFFERS][];

    public TargetDirectives() {
        Arrays.fill(this.clears, true);
    }

    // Applies every format directive to the targets and records the clears, later declarations winning; must precede target materialisation or HDR packs clamp and blow out
    public void apply(PackSources sources, UmbraRenderTargets targets) {
        for (ProgramSource source : sources.all()) {
            for (String stage : sources.activeStages(source)) {
                if (stage.contains("GAUX4FORMAT")) {
                    applyLegacyGaux4(stage, source.getName(), targets);
                }
            }
        }
        Map<String, Integer> names = SamplerLayout.colorTargetsByName();
        for (ProgramSource source : sources.all()) {
            for (String stage : sources.activeStages(source)) {
                Matcher matcher = DIRECTIVE.matcher(stage);
                while (matcher.find()) {
                    // Not a colour-target name (shadowcolor0Format belongs to the shadow pass)
                    Integer index = names.get(matcher.group(2));
                    if (index != null) {
                        applyOne(matcher.group(1), matcher.group(3), matcher.group(4), index, source.getName(), targets);
                    }
                }
            }
        }
    }

    private void applyOne(String type, String suffix, String value, int index, String source, UmbraRenderTargets targets) {
        switch (suffix) {
            case "Format" -> {
                if (type.equals("int") && IDENTIFIER.matcher(value).matches()) {
                    applyFormat(index, value, source, targets);
                }
            }
            case "Clear" -> {
                if (type.equals("bool") && (value.equals("true") || value.equals("false"))) {
                    this.clears[index] = Boolean.parseBoolean(value);
                }
            }
            default -> {
                Matcher vec4 = VEC4.matcher(value);
                // A malformed colour keeps the previous one, as Umbra ignores a directive it cannot parse
                float[] color = type.equals("vec4") && vec4.matches() ? parseVec4(vec4.group(1)) : null;
                if (color != null) {
                    this.clearColors[index] = color;
                }
            }
        }
    }

    private static void applyFormat(int index, String name, String source, UmbraRenderTargets targets) {
        Optional<InternalTextureFormat> format = InternalTextureFormat.fromString(name);
        if (format.isEmpty()) {
            LOGGER.warn("[Umbra] '{}' requests unknown format {} for colortex{}; keeping RGBA8", source, name, index);
            return;
        }
        try {
            targets.setColorFormat(index, format.get());
        } catch (IllegalStateException e) {
            LOGGER.warn("[Umbra] Format directive for colortex{} came after the target was created", index);
        }
    }

    // Only OptiFine's three formats are honoured, matching Umbra's PackRenderTargetDirectives
    private static void applyLegacyGaux4(String stage, String source, UmbraRenderTargets targets) {
        Matcher matcher = GAUX4_FORMAT.matcher(stage);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!name.equals("RGBA32F") && !name.equals("RGB32F") && !name.equals("RGB16")) {
                LOGGER.warn("[Umbra] Ignoring GAUX4FORMAT:{} in '{}' — only RGBA32F, RGB32F and RGB16 are valid; "
                        + "use `const int colortex7Format = {};` instead", name, source, name);
                continue;
            }
            applyFormat(GAUX4, name, source, targets);
        }
    }

    // Four comma-separated floats, or one splatted; null for anything malformed or non-finite
    static float[] parseVec4(String value) {
        String[] parts = value.split(",");
        if (parts.length != 1 && parts.length != 4) {
            return null;
        }
        float[] color = new float[4];
        try {
            for (int i = 0; i < 4; i++) {
                color[i] = ComputeDirectives.floatLiteral(parts[parts.length == 1 ? 0 : i]);
                if (!Float.isFinite(color[i])) {
                    return null;
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return color;
    }

    // Whether a target clears every frame
    public boolean clears(int index) {
        return this.clears[index];
    }

    // colortex0 clears to the fog colour (null), colortex1 to white, the rest to transparent black, unless the pack says otherwise
    public float[] clearColor(int index) {
        if (this.clearColors[index] != null) {
            return this.clearColors[index];
        }
        if (index == 0) {
            return null;
        }
        return index == 1 ? new float[]{1.0f, 1.0f, 1.0f, 1.0f} : new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    }

    // One program's colortexNMipmapEnabled set, read like Umbra's ProgramDirectives from the resolved fragment stage (Body Camera declares it both ways in one #if and the dead branch broke its auto-exposure)
    public static BitSet mipmappedBuffers(ProgramSource source, PackSources sources) {
        BitSet mipmapped = new BitSet(UmbraRenderTargets.MAX_COLOR_BUFFERS);
        String fragment = sources.activeFragment(source);
        if (fragment == null || !fragment.contains("MipmapEnabled")) {
            return mipmapped;
        }
        Matcher matcher = MIPMAP.matcher(fragment);
        while (matcher.find()) {
            Integer index = SamplerLayout.colorTargetsByName().get(matcher.group(1));
            if (index != null) {
                mipmapped.set(index, Boolean.parseBoolean(matcher.group(2)));
            }
        }
        return mipmapped;
    }
}

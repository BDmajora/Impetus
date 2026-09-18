package com.bdmajora.impetus.umbra.pipeline;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.gl.texture.PngTexture;
import com.bdmajora.impetus.umbra.shaderpack.ShaderPack;
import com.bdmajora.impetus.umbra.shaderpack.texture.CustomTextureData;
import com.bdmajora.impetus.umbra.shaderpack.texture.TextureStage;
import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL12;
import com.bdmajora.impetus.lwjgl.GL13;
import com.bdmajora.impetus.lwjgl.GL30;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntSupplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The GL textures behind the pack's custom-texture directives plus per-stage sampler-unit overrides; each gets a dedicated unit above the reserved range, texture.<stage>.<sampler> is per stage, customTexture.<name> global, texture.noise replaces noisetex
public class CustomTextureManager {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // One sampler-unit override: programs belonging to the stage point that sampler's uniform at this unit
    public static final class Override {
        // The dedicated texture unit the custom texture is bound to for the whole frame
        public final int unit;
        // The colortex index this override shadows, or -1 for a non-colour sampler; needed for Iris's flip deactivation, so once a composite pass has written that buffer later passes read the chain's content rather than the stand-in
        public final int colorTarget;

        Override(int unit, int colorTarget) {
            this.unit = unit;
            this.colorTarget = colorTarget;
        }
    }

    // One dedicated unit and its texture, resolved per frame because a resource reload can recreate the texture and leave the old GL id pointing at nothing
    private static final class Binding {
        final int unit;
        final int target;
        final IntSupplier texture;

        Binding(int unit, int target, IntSupplier texture) {
            this.unit = unit;
            this.target = target;
            this.texture = texture;
        }
    }

    private static final class TextureRef {
        final int target;
        final IntSupplier texture;

        TextureRef(int target, IntSupplier texture) {
            this.target = target;
            this.texture = texture;
        }
    }

    private final Map<TextureStage, Map<String, Override>> overrides = new EnumMap<>(TextureStage.class);
    private final List<Binding> bindings = new ArrayList<>();
    private final List<PngTexture> ownedTextures = new ArrayList<>();
    private final List<Integer> ownedRawTextures = new ArrayList<>();
    // The pack's own noisetex, or null to keep the generated one
    private final PngTexture noiseTexture;

    private final int firstUnit;
    private final int lastUnit;
    private int nextUnit;

    // Cached EntityRenderer.lightmapTexture field, located by TYPE rather than name so it works under both MCP and SRG mappings
    private static Field lightmapTextureField;

    // samplerUnitsByStage expands a directive's sampler name to every ALIAS sharing its unit (overriding gaux4 also overrides colortex7), colorTargetsByName drives flip deactivation, and a directive past the inclusive lastUnit is skipped with an error rather than wrapping onto someone else's unit
    public CustomTextureManager(ShaderPack pack, Map<TextureStage, Map<String, Integer>> samplerUnitsByStage,
                                Map<String, Integer> colorTargetsByName, int firstUnit, int lastUnit) {
        this.firstUnit = firstUnit;
        this.lastUnit = lastUnit;
        this.nextUnit = firstUnit;

        PngTexture noise = null;
        CustomTextureData noiseData = pack.getCustomNoiseTexture();
        if (noiseData != null) {
            if (noiseData instanceof CustomTextureData.PngData) {
                try {
                    noise = new PngTexture((CustomTextureData.PngData) noiseData);
                    this.ownedTextures.add(noise);
                } catch (IOException e) {
                    LOGGER.error("[Umbra] Unable to parse the image data for the custom noise texture: {}", e.getMessage());
                }
            } else {
                LOGGER.warn("[Umbra] texture.noise only supports pack PNG paths; keeping the generated noisetex");
            }
        }
        this.noiseTexture = noise;

        pack.getCustomTextureDataMap().forEach((stage, stageTextures) ->
                stageTextures.forEach((samplerName, data) ->
                        addOverride(stage, samplerName,
                                samplerUnitsByStage.getOrDefault(stage, Collections.<String, Integer>emptyMap()),
                                colorTargetsByName, data)));

        // customTexture.<name> samplers exist in every stage, like Umbra passing getUmbraCustomTextures() to each stage; one texture/unit shared by all
        pack.getUmbraCustomTextureDataMap().forEach((name, data) -> {
            TextureRef texture = createTexture(name, data);
            if (texture == null) {
                return;
            }
            int unit = allocateUnit(name);
            if (unit < 0) {
                return;
            }
            this.bindings.add(new Binding(unit, texture.target, texture.texture));
            Override override = new Override(unit, -1);
            for (TextureStage stage : TextureStage.values()) {
                this.overrides.computeIfAbsent(stage, s -> new LinkedHashMap<>()).put(name, override);
            }
        });
    }

    private void addOverride(TextureStage stage, String samplerName, Map<String, Integer> samplerUnits,
                             Map<String, Integer> colorTargetsByName, CustomTextureData data) {
        TextureRef texture = createTexture(stage + "." + samplerName, data);
        if (texture == null) {
            return;
        }
        int unit = allocateUnit(samplerName);
        if (unit < 0) {
            return;
        }
        this.bindings.add(new Binding(unit, texture.target, texture.texture));

        Map<String, Override> stageOverrides = this.overrides.computeIfAbsent(stage, s -> new LinkedHashMap<>());
        Integer standardUnit = samplerUnits.get(samplerName);
        if (standardUnit == null) {
            // Not a standard sampler name: override by name only (a pack-declared sampler of that exact name).
            stageOverrides.put(samplerName, new Override(unit, -1));
        } else {
            // Override the name and every alias sharing its unit (gaux4 <-> colortex7, composite <-> colortex3), matching Umbra's getOverride(names...)
            Integer colorTargetIndex = colorTargetsByName.get(samplerName);
            int colorTarget = colorTargetIndex == null ? -1 : colorTargetIndex;
            Override override = new Override(unit, colorTarget);
            for (Map.Entry<String, Integer> entry : samplerUnits.entrySet()) {
                if (entry.getValue().intValue() == standardUnit.intValue()) {
                    stageOverrides.put(entry.getKey(), override);
                }
            }
        }
    }

    // Next free unit in the fixed layout
    private int allocateUnit(String samplerName) {
        if (this.nextUnit > this.lastUnit) {
            LOGGER.error("[Umbra] Out of texture units for custom texture '{}' (units {}..{} exhausted); ignoring it",
                    samplerName, this.firstUnit, this.lastUnit);
            return -1;
        }
        return this.nextUnit++;
    }

    // The first texture unit still free once every custom-texture directive has been assigned
    public int getNextAvailableUnit() {
        return this.nextUnit;
    }

    // Turns parsed texture data into a per-frame texture-id supplier: PNG data becomes an owned GL texture, a resource location a supplier that re-resolves each frame
    private TextureRef createTexture(String name, CustomTextureData data) {
        if (data instanceof CustomTextureData.PngData) {
            try {
                PngTexture texture = new PngTexture((CustomTextureData.PngData) data);
                this.ownedTextures.add(texture);
                int id = texture.getTextureId();
                return new TextureRef(GL11.GL_TEXTURE_2D, () -> id);
            } catch (IOException e) {
                LOGGER.error("[Umbra] Unable to parse the image data for the custom texture '{}': {}", name, e.getMessage());
                return null;
            }
        }
        if (data instanceof CustomTextureData.LightmapMarker) {
            return new TextureRef(GL11.GL_TEXTURE_2D, CustomTextureManager::resolveLightmap);
        }
        if (data instanceof CustomTextureData.ResourceData) {
            CustomTextureData.ResourceData resource = (CustomTextureData.ResourceData) data;
            return new TextureRef(GL11.GL_TEXTURE_2D,
                    () -> resolveResource(resource.getNamespace(), resource.getLocation()));
        }
        if (data instanceof CustomTextureData.RawData) {
            return createRawTexture(name, (CustomTextureData.RawData) data);
        }
        LOGGER.warn("[Umbra] Unsupported custom texture type for '{}'; ignoring it", name);
        return null;
    }

    // A texture from raw bytes with the declared dimensions and format
    private TextureRef createRawTexture(String name, CustomTextureData.RawData data) {
        try {
            int target = textureTarget(data.getTextureType());
            int internalFormat = internalFormat(data.getInternalFormat());
            int pixelFormat = pixelFormat(data.getPixelFormat());
            int pixelType = pixelType(data.getPixelType());
            int texture = LWJGL.glGenTextures();

            ByteBuffer pixels = ByteBuffer.allocateDirect(data.getContent().length).order(ByteOrder.nativeOrder());
            pixels.put(data.getContent());
            pixels.flip();

            LWJGL.glBindTexture(target, texture);
            LWJGL.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            LWJGL.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            LWJGL.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            LWJGL.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            if (target == GL12.GL_TEXTURE_3D) {
                LWJGL.glTexParameteri(target, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
            }
            LWJGL.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            if (target == GL12.GL_TEXTURE_3D) {
                LWJGL.glTexImage3D(target, 0, internalFormat, data.getWidth(), data.getHeight(), data.getDepth(),
                        0, pixelFormat, pixelType, pixels);
            } else {
                LWJGL.glTexImage2D(target, 0, internalFormat, data.getWidth(), data.getHeight(),
                        0, pixelFormat, pixelType, pixels);
            }
            LWJGL.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            LWJGL.glBindTexture(target, 0);

            this.ownedRawTextures.add(texture);
            return new TextureRef(target, () -> texture);
        } catch (IllegalArgumentException e) {
            LOGGER.error("[Umbra] Unsupported raw custom texture '{}': {}", name, e.getMessage());
            return null;
        }
    }

    // 1D, 2D or 3D from the directive
    private static int textureTarget(String value) {
        switch (value.toUpperCase(Locale.ROOT)) {
            case "TEXTURE_2D":
                return GL11.GL_TEXTURE_2D;
            case "TEXTURE_3D":
                return GL12.GL_TEXTURE_3D;
            default:
                throw new IllegalArgumentException("target " + value);
        }
    }

    // Pack format name to GL
    private static int internalFormat(String value) {
        switch (value.toUpperCase(Locale.ROOT)) {
            case "R8": return GL30.GL_R8;
            case "RG8": return GL30.GL_RG8;
            case "RGB8": return GL11.GL_RGB8;
            case "RGBA8": return GL11.GL_RGBA8;
            case "R16F": return GL30.GL_R16F;
            case "RGB16F": return GL30.GL_RGB16F;
            case "RGBA16F": return GL30.GL_RGBA16F;
            case "R32F": return GL30.GL_R32F;
            case "RGBA32F": return GL30.GL_RGBA32F;
            default:
                throw new IllegalArgumentException("internal format " + value);
        }
    }

    // Pack format name to GL client format
    private static int pixelFormat(String value) {
        switch (value.toUpperCase(Locale.ROOT)) {
            case "RED": return GL11.GL_RED;
            case "RG": return GL30.GL_RG;
            case "RGB": return GL11.GL_RGB;
            case "RGBA": return GL11.GL_RGBA;
            default:
                throw new IllegalArgumentException("pixel format " + value);
        }
    }

    // Pack type name to GL
    private static int pixelType(String value) {
        switch (value.toUpperCase(Locale.ROOT)) {
            case "BYTE": return GL11.GL_BYTE;
            case "UNSIGNED_BYTE": return GL11.GL_UNSIGNED_BYTE;
            case "SHORT": return GL11.GL_SHORT;
            case "UNSIGNED_SHORT": return GL11.GL_UNSIGNED_SHORT;
            case "FLOAT": return GL11.GL_FLOAT;
            case "HALF_FLOAT": return GL30.GL_HALF_FLOAT;
            default:
                throw new IllegalArgumentException("pixel type " + value);
        }
    }

    // Resolves a namespace:path texture through the TextureManager every frame like Iris, since a reload replaces the texture; 1.12.2 registers atlases WITHOUT the .png modern packs write, so the extension is retried stripped
    private static int resolveResource(String namespace, String location) {
        ITextureObject texture = Minecraft.getMinecraft().getTextureManager()
                .getTexture(new ResourceLocation(namespace, location));
        if (texture == null && location.endsWith(".png")) {
            texture = Minecraft.getMinecraft().getTextureManager()
                    .getTexture(new ResourceLocation(namespace, location.substring(0, location.length() - 4)));
        }
        return texture != null ? texture.getGlTextureId() : TextureUtil.MISSING_TEXTURE.getGlTextureId();
    }

    // The lightmap's GL id for callers binding it on a unit of their own (the DH compat puts it on the pack's `lightmap` unit)
    public static int getLightmapTextureId() {
        return resolveLightmap();
    }

    // The live lightmap texture, EntityRenderer.lightmapTexture, found by FIELD TYPE since the class has exactly one DynamicTexture field and that works under MCP and SRG alike
    private static int resolveLightmap() {
        try {
            if (lightmapTextureField == null) {
                for (Field field : EntityRenderer.class.getDeclaredFields()) {
                    if (field.getType() == DynamicTexture.class) {
                        field.setAccessible(true);
                        lightmapTextureField = field;
                        break;
                    }
                }
            }
            if (lightmapTextureField != null) {
                DynamicTexture lightmap = (DynamicTexture) lightmapTextureField.get(Minecraft.getMinecraft().entityRenderer);
                if (lightmap != null) {
                    return lightmap.getGlTextureId();
                }
            }
        } catch (ReflectiveOperationException e) {
            // fall through to the missing texture
        }
        return TextureUtil.MISSING_TEXTURE.getGlTextureId();
    }

    // The sampler-unit overrides for one stage keyed by sampler name, the directive's own plus every alias sharing that unit
    public Map<String, Override> getOverrides(TextureStage stage) {
        return this.overrides.containsKey(stage)
                ? Collections.unmodifiableMap(this.overrides.get(stage))
                : Collections.emptyMap();
    }

    // The pack's texture.noise GL id, or -1 meaning "use the generated noise texture"
    public int getNoiseTextureId() {
        return this.noiseTexture != null ? this.noiseTexture.getTextureId() : -1;
    }

    // Whether the pack declared any custom textures
    public boolean isEmpty() {
        return this.bindings.isEmpty();
    }

    // Binds every custom texture on its dedicated unit; raw binds are correct because every unit sits above GlStateManager's 8-slot cache, and unit 0 is left active afterwards
    public void bindAll() {
        for (Binding binding : this.bindings) {
            GlTextureUnits.selectScratch(binding.unit);
            LWJGL.glBindTexture(binding.target, binding.texture.getAsInt());
        }
        GlTextureUnits.resetToUnit0();
    }

    // Unbinds the custom-texture units at frame teardown, mirroring the pipeline's other unit restores
    public void unbindAll() {
        for (Binding binding : this.bindings) {
            GlTextureUnits.selectScratch(binding.unit);
            LWJGL.glBindTexture(binding.target, 0);
        }
        GlTextureUnits.resetToUnit0();
    }

    // Frees every texture
    public void destroy() {
        for (PngTexture texture : this.ownedTextures) {
            texture.destroy();
        }
        for (Integer texture : this.ownedRawTextures) {
            LWJGL.glDeleteTextures(texture);
        }
        this.ownedTextures.clear();
        this.ownedRawTextures.clear();
        this.bindings.clear();
        this.overrides.clear();
    }
}

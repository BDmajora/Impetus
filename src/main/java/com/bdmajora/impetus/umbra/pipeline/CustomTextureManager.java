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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntSupplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Custom textures of a shader pack with the texture units they bind to.
public class CustomTextureManager {
    // Logger of texture failures.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Unit that replaces a sampler and the color target it stands for.
    public static final class Override {
        // Texture unit of the replacement.
        public final int unit;
        // Color target index or minus one.
        public final int colorTarget;

        // Creates an override.
        public Override(int unit, int colorTarget) {
            this.unit = unit;
            this.colorTarget = colorTarget;
        }
    }

    // Texture bound to a unit before drawing.
    private static final class Binding {
        // Texture unit.
        final int unit;
        // GL texture target.
        final int target;
        // Source of the GL texture name.
        final IntSupplier texture;

        // Creates a binding.
        Binding(int unit, int target, IntSupplier texture) {
            this.unit = unit;
            this.target = target;
            this.texture = texture;
        }
    }

    // GL target and name source of a created texture.
    private static final class TextureRef {
        // GL texture target.
        final int target;
        // Source of the GL texture name.
        final IntSupplier texture;

        // Creates a reference.
        TextureRef(int target, IntSupplier texture) {
            this.target = target;
            this.texture = texture;
        }
    }

    // Sampler overrides by stage.
    private final Map<TextureStage, Map<String, Override>> overrides = new EnumMap<>(TextureStage.class);
    // Bindings in creation order.
    private final List<Binding> bindings = new ArrayList<>();
    // Image textures to delete.
    private final List<PngTexture> ownedTextures = new ArrayList<>();
    // Raw textures to delete.
    private final List<Integer> ownedRawTextures = new ArrayList<>();
    // Custom noise texture or null.
    private final PngTexture noiseTexture;

    // First unit custom textures may use.
    private final int firstUnit;
    // Last unit custom textures may use.
    private final int lastUnit;
    // Next free unit.
    private int nextUnit;

    // Lightmap texture field of the entity renderer or null.
    private static Field lightmapTextureField;
    // Searched state of the lightmap field.
    private static boolean lightmapFieldSearched;

    // Creates every custom texture of a pack and deletes them all when one fails.
    public CustomTextureManager(ShaderPack pack, Map<TextureStage, Map<String, Integer>> samplerUnitsByStage,
                                Map<String, Integer> colorTargetsByName, int firstUnit, int lastUnit) {
        this.firstUnit = firstUnit;
        this.lastUnit = lastUnit;
        this.nextUnit = firstUnit;

        // Releases every created texture when a later texture fails
        try {
            this.noiseTexture = loadNoise(pack);
            this.loadTextures(pack, samplerUnitsByStage, colorTargetsByName);
        } catch (RuntimeException | Error e) {
            this.destroy();
            throw e;
        }
    }

    // Returns the custom noise texture of a pack or null.
    private PngTexture loadNoise(ShaderPack pack) {
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
        return noise;
    }

    // Creates the stage textures and the named textures of a pack.
    private void loadTextures(ShaderPack pack, Map<TextureStage, Map<String, Integer>> samplerUnitsByStage,
                              Map<String, Integer> colorTargetsByName) {
        pack.getCustomTextureDataMap().forEach((stage, stageTextures) ->
                stageTextures.forEach((samplerName, data) ->
                        addOverride(stage, samplerName,
                                samplerUnitsByStage.getOrDefault(stage, Collections.<String, Integer>emptyMap()),
                                colorTargetsByName, data)));

        pack.getUmbraCustomTextureDataMap().forEach((name, data) -> {
            if (!this.hasFreeUnit(name)) {
                return;
            }
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

    // Creates a stage texture and overrides every sampler that shares its standard unit.
    private void addOverride(TextureStage stage, String samplerName, Map<String, Integer> samplerUnits,
                             Map<String, Integer> colorTargetsByName, CustomTextureData data) {
        if (!this.hasFreeUnit(samplerName)) {
            return;
        }
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
            stageOverrides.put(samplerName, new Override(unit, -1));
        } else {
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

    // Returns whether a unit is left and reports the texture that has none.
    private boolean hasFreeUnit(String samplerName) {
        if (this.nextUnit > this.lastUnit) {
            LOGGER.error("[Umbra] Out of texture units for custom texture '{}' (units {} to {} exhausted); ignoring it",
                    samplerName, this.firstUnit, this.lastUnit);
            return false;
        }
        return true;
    }

    // Returns the next unit or minus one when none is left.
    private int allocateUnit(String samplerName) {
        return this.hasFreeUnit(samplerName) ? this.nextUnit++ : -1;
    }

    // Returns the next free unit.
    public int getNextAvailableUnit() {
        return this.nextUnit;
    }

    // Creates a texture from pack data or returns null for unsupported data.
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

    // Uploads a raw texture or returns null for an unsupported layout.
    private TextureRef createRawTexture(String name, CustomTextureData.RawData data) {
        try {
            int target = textureTarget(data.getTextureType());
            int internalFormat = internalFormat(data.getInternalFormat());
            int pixelFormat = pixelFormat(data.getPixelFormat());
            int pixelType = pixelType(data.getPixelType());
            requireEnoughPixels(data, target);
            int texture = LWJGL.glGenTextures();
            // Adds the texture to the owned list before the upload.
            this.ownedRawTextures.add(texture);

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
            try {
                if (target == GL12.GL_TEXTURE_3D) {
                    LWJGL.glTexImage3D(target, 0, internalFormat, data.getWidth(), data.getHeight(), data.getDepth(),
                            0, pixelFormat, pixelType, pixels);
                } else {
                    LWJGL.glTexImage2D(target, 0, internalFormat, data.getWidth(), data.getHeight(),
                            0, pixelFormat, pixelType, pixels);
                }
            } finally {
                LWJGL.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                LWJGL.glBindTexture(target, 0);
            }

            return new TextureRef(target, () -> texture);
        } catch (IllegalArgumentException e) {
            LOGGER.error("[Umbra] Unsupported raw custom texture '{}': {}", name, e.getMessage());
            return null;
        }
    }

    // Returns the bytes a raw texture needs and rejects a size that is not positive.
    static long requiredBytes(CustomTextureData.RawData data, int target) {
        int depth = target == GL12.GL_TEXTURE_3D ? data.getDepth() : 1;
        if (data.getWidth() <= 0 || data.getHeight() <= 0 || depth <= 0) {
            throw new IllegalArgumentException("size " + data.getWidth() + "x" + data.getHeight() + "x" + depth);
        }
        int components;
        switch (data.getPixelFormat().toUpperCase(Locale.ROOT)) {
            case "RED": components = 1; break;
            case "RG": components = 2; break;
            case "RGB": components = 3; break;
            default: components = 4; break;
        }
        int typeSize;
        switch (data.getPixelType().toUpperCase(Locale.ROOT)) {
            case "BYTE":
            case "UNSIGNED_BYTE": typeSize = 1; break;
            case "SHORT":
            case "UNSIGNED_SHORT":
            case "HALF_FLOAT": typeSize = 2; break;
            default: typeSize = 4; break;
        }
        return (long) data.getWidth() * data.getHeight() * depth * components * typeSize;
    }

    // Rejects raw content shorter than its size needs.
    private static void requireEnoughPixels(CustomTextureData.RawData data, int target) {
        long required = requiredBytes(data, target);
        if (data.getContent() == null || data.getContent().length < required) {
            throw new IllegalArgumentException("content is shorter than the " + required + " bytes the size needs");
        }
    }

    // Returns the GL target of a texture type.
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

    // Returns the GL internal format of a format name.
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

    // Returns the GL pixel format of a format name.
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

    // Returns the GL pixel type of a type name.
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

    // Returns the GL name of a game texture or the missing texture.
    private static int resolveResource(String namespace, String location) {
        ITextureObject texture = Minecraft.getMinecraft().getTextureManager()
                .getTexture(new ResourceLocation(namespace, location));
        if (texture == null && location.endsWith(".png")) {
            texture = Minecraft.getMinecraft().getTextureManager()
                    .getTexture(new ResourceLocation(namespace, location.substring(0, location.length() - 4)));
        }
        return texture != null ? texture.getGlTextureId() : TextureUtil.MISSING_TEXTURE.getGlTextureId();
    }

    // Returns the GL name of the lightmap.
    public static int getLightmapTextureId() {
        return resolveLightmap();
    }

    // Returns the GL name of the lightmap and looks up its field once.
    private static int resolveLightmap() {
        try {
            if (!lightmapFieldSearched) {
                lightmapFieldSearched = true;
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
            // Falls back to the missing texture when the field cannot be read
            return TextureUtil.MISSING_TEXTURE.getGlTextureId();
        }
        return TextureUtil.MISSING_TEXTURE.getGlTextureId();
    }

    // Returns a read only view of the overrides of a stage.
    public Map<String, Override> getOverrides(TextureStage stage) {
        Map<String, Override> stageOverrides = this.overrides.get(stage);
        return stageOverrides != null ? Collections.unmodifiableMap(stageOverrides) : Collections.emptyMap();
    }

    // Returns the GL name of the noise texture or minus one.
    public int getNoiseTextureId() {
        return this.noiseTexture != null ? this.noiseTexture.getTextureId() : -1;
    }

    // Returns whether no texture is bound.
    public boolean isEmpty() {
        return this.bindings.isEmpty();
    }

    // Binds every texture to its unit.
    public void bindAll() {
        for (Binding binding : this.bindings) {
            GlTextureUnits.selectScratch(binding.unit);
            LWJGL.glBindTexture(binding.target, binding.texture.getAsInt());
        }
        GlTextureUnits.resetToUnit0();
    }

    // Unbinds every texture from its unit.
    public void unbindAll() {
        for (Binding binding : this.bindings) {
            GlTextureUnits.selectScratch(binding.unit);
            LWJGL.glBindTexture(binding.target, 0);
        }
        GlTextureUnits.resetToUnit0();
    }

    // Deletes every owned texture and forgets every binding.
    public void destroy() {
        for (PngTexture texture : this.ownedTextures) {
            texture.destroy();
        }
        for (int i = 0; i < this.ownedRawTextures.size(); i++) {
            LWJGL.glDeleteTextures(this.ownedRawTextures.get(i));
        }
        this.ownedTextures.clear();
        this.ownedRawTextures.clear();
        this.bindings.clear();
        this.overrides.clear();
    }
}

package com.bdmajora.impetus.umbra.uniforms;

import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.ImpetusVintage;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeBeach;
import net.minecraft.world.biome.BiomeDesert;
import net.minecraft.world.biome.BiomeEnd;
import net.minecraft.world.biome.BiomeForest;
import net.minecraft.world.biome.BiomeHell;
import net.minecraft.world.biome.BiomeHills;
import net.minecraft.world.biome.BiomeJungle;
import net.minecraft.world.biome.BiomeMesa;
import net.minecraft.world.biome.BiomeMushroomIsland;
import net.minecraft.world.biome.BiomeOcean;
import net.minecraft.world.biome.BiomeRiver;
import net.minecraft.world.biome.BiomeSavanna;
import net.minecraft.world.biome.BiomeSnow;
import net.minecraft.world.biome.BiomeSwamp;
import net.minecraft.world.biome.BiomeTaiga;
import net.minecraft.world.biome.BiomeVoid;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4i;
import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.gl.uniform.UniformCollector;
import com.bdmajora.impetus.umbra.gl.uniform.UniformUpdateFrequency;
import com.bdmajora.impetus.umbra.material.WorldRenderingSettings;
import com.bdmajora.impetus.umbra.pipeline.ColorSpaceConverter;
import com.bdmajora.impetus.umbra.vertices.UmbraChunkVertexType;
import org.lwjgl.opengl.GL11;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Uniforms shared by every pack program.
public final class CommonUniforms {
    // Reusable two component vector.
    private static final Vector2f SCRATCH_2 = new Vector2f();
    // Reusable three component vector.
    private static final Vector3f SCRATCH_3 = new Vector3f();
    // Reusable four component vector.
    private static final Vector4f SCRATCH_4 = new Vector4f();
    // Scratch 2 i.
    private static final Vector2i SCRATCH_2I = new Vector2i();
    // Scratch 4 i.
    private static final Vector4i SCRATCH_4I = new Vector4i();
    // Zero vector.
    private static final Vector3f ZERO_3 = new Vector3f();
    // White color.
    private static final Vector3f WHITE_3 = new Vector3f(1.0f, 1.0f, 1.0f);
    // Position reported when no block is selected.
    private static final Vector3f NO_SELECTED_BLOCK = new Vector3f(-256.0f, -256.0f, -256.0f);
    // Value reported when there is no lightning.
    private static final Vector4f NO_LIGHTNING = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);

    // Frame time used before the first frame.
    private static final float DEFAULT_FRAME_TIME = 1.0f / 60.0f;
    // Id of the darkness effect.
    private static final ResourceLocation DARKNESS_EFFECT_ID = new ResourceLocation("darkness");
    // GL enum of the fog density.
    private static final int GL_FOG_DENSITY = 0x0B62;
    // GL enum of the active texture unit.
    private static final int GL_ACTIVE_TEXTURE = 0x84E0;
    // GL enum of the first texture unit.
    private static final int GL_TEXTURE0 = 0x84C0;
    // GL enum of the source color blend factor.
    private static final int GL_BLEND_SRC_RGB = 0x80C9;
    // GL enum of the destination color blend factor.
    private static final int GL_BLEND_DST_RGB = 0x80C8;
    // GL enum of the source alpha blend factor.
    private static final int GL_BLEND_SRC_ALPHA = 0x80CB;
    // GL enum of the destination alpha blend factor.
    private static final int GL_BLEND_DST_ALPHA = 0x80CA;
    // Gl texture binding 2 d.
    private static final int GL_TEXTURE_BINDING_2D = 0x8069;
    // GL enum of the texture width.
    private static final int GL_TEXTURE_WIDTH = 0x1000;
    // GL enum of the texture height.
    private static final int GL_TEXTURE_HEIGHT = 0x1001;

    // Smoothed cave value.
    private static final SmoothedValue eyeInCave = new SmoothedValue();
    // Smoothed dry biome value.
    private static final SmoothedValue inDry = new SmoothedValue();
    // Smoothed rainy biome value.
    private static final SmoothedValue inRainy = new SmoothedValue();
    // Smoothed snowy biome value.
    private static final SmoothedValue inSnowy = new SmoothedValue();
    // Smoothed state of player movement.
    private static final SmoothedValue moved = new SmoothedValue();
    // Smoothed value that ramps up after a world load.
    private static final SmoothedValue starter = new SmoothedValue();
    // Smoothed frame time.
    private static final SmoothedValue frameTimeSmooth = new SmoothedValue(DEFAULT_FRAME_TIME);
    // Smoothed eye brightness.
    private static final SmoothedValue eyeBrightnessM = new SmoothedValue();
    // Second smoothed eye brightness.
    private static final SmoothedValue eyeBrightnessM2 = new SmoothedValue();
    // Smoothed rain factor.
    private static final SmoothedValue rainFactor = new SmoothedValue();
    // Smoothed rain strength.
    private static final SmoothedValue rainStrengthS = new SmoothedValue();
    // Smoothed rain strength for stars.
    private static final SmoothedValue rainStrengthShiningStars = new SmoothedValue();
    // Second smoothed rain strength.
    private static final SmoothedValue rainStrengthS2 = new SmoothedValue();
    // Smoothed rain precipitation.
    private static final SmoothedValue precipitationRain = new SmoothedValue();
    // Smoothed water contact value.
    private static final SmoothedValue touchMyBody = new SmoothedValue();
    // Smoothed sneak value.
    private static final SmoothedValue sneakSmooth = new SmoothedValue();
    // Smoothed burning value.
    private static final SmoothedValue burningSmooth = new SmoothedValue();
    // Smoothed speed.
    private static final SmoothedValue smoothSpeed = new SmoothedValue();

    // Frame of the last pack uniform update.
    private static int complementaryUniformFrame = Integer.MIN_VALUE;
    // Cached cave value.
    private static float cachedEyeInCave;
    // Cached dry biome value.
    private static float cachedInDry;
    // Cached rainy biome value.
    private static float cachedInRainy;
    // Cached snowy biome value.
    private static float cachedInSnowy;
    // Cached starter value.
    private static float cachedStarter;
    // Cached smoothed frame time.
    private static float cachedFrameTimeSmooth = DEFAULT_FRAME_TIME;
    // Cached smoothed eye brightness.
    private static float cachedEyeBrightnessM;
    // Cached second smoothed eye brightness.
    private static float cachedEyeBrightnessM2;
    // Cached rain factor.
    private static float cachedRainFactor;
    // Cached speed.
    private static float cachedVelocity;
    // Cached smoothed rain strength.
    private static float cachedRainStrengthS;
    // Cached rain strength for stars.
    private static float cachedRainStrengthShiningStars;
    // Cached second smoothed rain strength.
    private static float cachedRainStrengthS2;
    // Cached rain precipitation.
    private static float cachedPrecipitationRain;
    // Cached water contact value.
    private static float cachedTouchMyBody;
    // Cached sneak value.
    private static float cachedSneakSmooth;
    // Cached burning value.
    private static float cachedBurningSmooth;
    // Cached effect strength.
    private static float cachedEffectStrength;
    // Cached end flash intensity.
    private static float cachedEndFlashIntensity;
    // End flash intensity of the last frame.
    private static float cachedPreviousEndFlashIntensity;

    // Prevents instantiation
    private CommonUniforms() {
    }

    // Marks the start of a frame.
    public static void beginFrame() {
        updateComplementaryCustomUniforms();
    }

    // Adds the common uniforms.
    public static void addCommonUniforms(UniformCollector uniforms) {
        CelestialUniforms.addCelestialUniforms(uniforms);
        SystemTimeUniforms.addSystemTimeUniforms(uniforms);

        uniforms
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "centerDepthSmooth",
                        com.bdmajora.impetus.umbra.pipeline.CenterDepthSampler::getCenterDepthSmooth)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "rainStrength", CommonUniforms::getRainStrength)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "eyeAltitude", CommonUniforms::getEyeAltitude)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "isEyeInWater", CommonUniforms::isEyeInWater)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "blindness", CommonUniforms::getBlindness)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "darknessFactor", CommonUniforms::getDarknessFactor)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "darknessLightFactor",
                        CommonUniforms::getDarknessLightFactor)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "nightVision", CommonUniforms::getNightVision)
                .uniform1i(UniformUpdateFrequency.DYNAMIC, "renderStage",
                        CapturedRenderingState.INSTANCE::getRenderStage)
                .uniform1i(UniformUpdateFrequency.DYNAMIC, "entityId",
                        CapturedRenderingState.INSTANCE::getCurrentRenderedEntity)
                .uniform1i(UniformUpdateFrequency.DYNAMIC, "blockEntityId",
                        CapturedRenderingState.INSTANCE::getCurrentRenderedBlockEntity)
                .uniform1i(UniformUpdateFrequency.DYNAMIC, "currentRenderedItemId",
                        CapturedRenderingState.INSTANCE::getCurrentRenderedItem)
                .uniform4f(UniformUpdateFrequency.DYNAMIC, "iris_ColorModulator",
                        CapturedRenderingState.INSTANCE::getColorModulator)
                .uniform1f(UniformUpdateFrequency.DYNAMIC, "iris_GlintAlpha", CommonUniforms::getUmbraGlintAlpha)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "iris_TextureScale",
                        CommonUniforms::getUmbraTextureScale)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "iris_ModelScale",
                        CommonUniforms::getUmbraModelScale)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "iris_ModelOffset",
                        CommonUniforms::getUmbraModelOffset)
                .uniform2f(UniformUpdateFrequency.PER_FRAME, "u_TextureScale",
                        CommonUniforms::getTextureScaleVector)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "u_ModelScale",
                        CommonUniforms::getModelScaleVector)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "iris_CameraTranslation",
                        CommonUniforms::getUmbraCameraTranslation)
                .uniform4f(UniformUpdateFrequency.DYNAMIC, "entityColor",
                        CapturedRenderingState.INSTANCE::getEntityColor)
                .uniform1i(UniformUpdateFrequency.DYNAMIC, "gtextureId", CommonUniforms::getGtextureId)
                .uniform1i(UniformUpdateFrequency.DYNAMIC, "textureReloadCount",
                        CapturedRenderingState.INSTANCE::getTextureReloadCount)
                .uniform2i(UniformUpdateFrequency.DYNAMIC, "gtextureSize", CommonUniforms::getGtextureSize)
                .uniform4i(UniformUpdateFrequency.DYNAMIC, "blendFunc", CommonUniforms::getBlendFunc)
                .uniform1f(UniformUpdateFrequency.DYNAMIC, "iris_currentAlphaTest",
                        CapturedRenderingState.INSTANCE::getCurrentAlphaTest)
                .uniform1f(UniformUpdateFrequency.DYNAMIC, "alphaTestRef",
                        CapturedRenderingState.INSTANCE::getCurrentAlphaTest)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "isRightHanded", CommonUniforms::isRightHanded)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "is_sneaking", CommonUniforms::isSneaking)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "is_sprinting", CommonUniforms::isSprinting)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "is_hurt", CommonUniforms::isHurt)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "is_invisible", CommonUniforms::isInvisible)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "is_burning", CommonUniforms::isBurning)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "is_on_ground", CommonUniforms::isOnGround)
                .uniform1i(UniformUpdateFrequency.PER_TICK, "feetInWater", CommonUniforms::isFeetInWater)
                .uniform1i(UniformUpdateFrequency.PER_TICK, "inSwimmingAnimation", CommonUniforms::isInSwimmingAnimation)
                .uniform1i(UniformUpdateFrequency.PER_TICK, "isRiding", CommonUniforms::isRiding)
                .uniform1i(UniformUpdateFrequency.PER_TICK, "isElytraFlying", CommonUniforms::isElytraFlying)
                .uniform1i(UniformUpdateFrequency.PER_TICK, "vehicleInWater", CommonUniforms::isVehicleInWater)
                .uniform1i(UniformUpdateFrequency.PER_TICK, "vehicleId", CommonUniforms::getVehicleId)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "vehicleLookVector", CommonUniforms::getVehicleLookVector)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "relativeVehiclePosition",
                        CommonUniforms::getRelativeVehiclePosition)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "currentColorSpace",
                        () -> ColorSpaceConverter.getColorSpace().ordinal())
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "chunkFadeTimeInv",
                        CommonUniforms::getChunkFadeTimeInv)
                .uniform1i(UniformUpdateFrequency.ONCE, "textureFilteringMode", () -> 0)
                .uniform1i(UniformUpdateFrequency.ONCE, "anisotropicFiltering", () -> 0)
                .uniform1f(UniformUpdateFrequency.ONCE, "pi", () -> (float) Math.PI)
                .uniform1f(UniformUpdateFrequency.PER_TICK, "playerMood", CommonUniforms::getPlayerMood)
                .uniform1f(UniformUpdateFrequency.PER_TICK, "constantMood", CommonUniforms::getConstantMood)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "thunderStrength", CommonUniforms::getThunderStrength)
                .uniform1i(UniformUpdateFrequency.PER_TICK, "heavyFog", CommonUniforms::isHeavyFog)
                .uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHealth",
                        CommonUniforms::getCurrentPlayerHealth)
                .uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHealth", CommonUniforms::getMaxPlayerHealth)
                .uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHunger",
                        CommonUniforms::getCurrentPlayerHunger)
                .uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHunger", () -> 20.0f)
                .uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerArmor",
                        CommonUniforms::getCurrentPlayerArmor)
                .uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerArmor", () -> 50.0f)
                .uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerAir", CommonUniforms::getCurrentPlayerAir)
                .uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerAir", CommonUniforms::getMaxPlayerAir)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "firstPersonCamera", CommonUniforms::isFirstPersonCamera)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "isSpectator", CommonUniforms::isSpectator)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "currentSelectedBlockId",
                        CommonUniforms::getCurrentSelectedBlockId)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "seaLevel", CommonUniforms::getSeaLevel)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "currentSelectedBlockPos",
                        CommonUniforms::getCurrentSelectedBlockPos)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "eyePosition", CommonUniforms::getEyePosition)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "relativeEyePosition",
                        CommonUniforms::getRelativeEyePosition)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "playerLookVector", CommonUniforms::getPlayerLookVector)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "playerBodyVector", CommonUniforms::getPlayerBodyVector)
                .uniform4f(UniformUpdateFrequency.PER_TICK, "lightningBoltPosition",
                        CommonUniforms::getLightningBoltPosition)
                .uniform1f(UniformUpdateFrequency.PER_TICK, "cloudTime", CommonUniforms::getCloudTime)
                .uniform1f(UniformUpdateFrequency.PER_TICK, "endFlashIntensity",
                        CommonUniforms::getEndFlashIntensity)
                .uniform1f(UniformUpdateFrequency.PER_TICK, "previousEndFlashIntensity",
                        CommonUniforms::getPreviousEndFlashIntensity)
                .uniform2f(UniformUpdateFrequency.PER_FRAME, "iris_ScreenSize", CommonUniforms::getScreenSize)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "framemod2",
                        () -> (float) (SystemTimeUniforms.COUNTER.getFrameCounter() & 1))
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "framemod4",
                        () -> (float) (SystemTimeUniforms.COUNTER.getFrameCounter() & 3))
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "framemod8",
                        () -> (float) (SystemTimeUniforms.COUNTER.getFrameCounter() & 7))
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "framemod600",
                        () -> (float) (SystemTimeUniforms.COUNTER.getFrameCounter() % 600))
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "biome", CommonUniforms::getBiomeId)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "biome_category", CommonUniforms::getBiomeCategory)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "biome_precipitation",
                        CommonUniforms::getBiomePrecipitation)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "rainfall", CommonUniforms::getRainfall)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "temperature", CommonUniforms::getTemperature)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "BiomeTemp", CommonUniforms::getTemperature)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "isEyeInCave", CommonUniforms::getIsEyeInCave)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "inDry", CommonUniforms::getInDry)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "inRainy", CommonUniforms::getInRainy)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "inSnowy", CommonUniforms::getInSnowy)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "isDry", CommonUniforms::getInDry)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "isRainy", CommonUniforms::getInRainy)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "isSnowy", CommonUniforms::getInSnowy)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "velocity", CommonUniforms::getVelocity)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "starter", CommonUniforms::getStarter)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "frameTimeSmooth",
                        CommonUniforms::getFrameTimeSmooth)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "eyeBrightnessM", CommonUniforms::getEyeBrightnessM)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "eyeBrightnessM2", CommonUniforms::getEyeBrightnessM2)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "rainFactor", CommonUniforms::getRainFactor)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "timeAngle", CommonUniforms::getTimeAngle)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "timeBrightness", CommonUniforms::getTimeBrightness)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "moonBrightness", CommonUniforms::getMoonBrightness)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "shadowFade", CommonUniforms::getShadowFade)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "shdFade", CommonUniforms::getShdFade)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "rainStrengthS", CommonUniforms::getRainStrengthS)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "rainStrengthShiningStars",
                        CommonUniforms::getRainStrengthShiningStars)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "rainStrengthS2", CommonUniforms::getRainStrengthS2)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "blindFactor", CommonUniforms::getBlindFactor)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "day", CommonUniforms::getDay)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "night", CommonUniforms::getNight)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "dawnDusk", CommonUniforms::getDawnDusk)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "isPrecipitationRain",
                        CommonUniforms::getIsPrecipitationRain)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "touchmybody", CommonUniforms::getTouchMyBody)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "sneakSmooth", CommonUniforms::getSneakSmooth)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "burningSmooth", CommonUniforms::getBurningSmooth)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "effectStrength", CommonUniforms::getEffectStrength)
                .uniform1i(UniformUpdateFrequency.PER_TICK, "worldTime", CommonUniforms::getWorldTime)
                .uniform1i(UniformUpdateFrequency.PER_TICK, "worldDay", CommonUniforms::getWorldDay)
                .uniform1i(UniformUpdateFrequency.PER_TICK, "moonPhase", CommonUniforms::getMoonPhase)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "bedrockLevel", CommonUniforms::getBedrockLevel)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "cloudHeight", CommonUniforms::getCloudHeight)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "heightLimit", CommonUniforms::getHeightLimit)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "logicalHeightLimit",
                        CommonUniforms::getLogicalHeightLimit)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "hasCeiling", CommonUniforms::hasCeiling)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "hasSkylight", CommonUniforms::hasSkylight)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "ambientLight", CommonUniforms::getAmbientLight)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "aspectRatio", CommonUniforms::getAspectRatio)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "viewWidth", CommonUniforms::getViewWidth)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "viewHeight", CommonUniforms::getViewHeight)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "pixelSizeX", CommonUniforms::getPixelSizeX)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "pixelSizeY", CommonUniforms::getPixelSizeY)
                .uniform1f(UniformUpdateFrequency.ONCE, "near", () -> 0.05f)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "far", CommonUniforms::getFar)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "dhFarPlane", CommonUniforms::getDhFarPlane)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "dhNearPlane", CommonUniforms::getDhNearPlane)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "dhRenderDistance", CommonUniforms::getDhRenderDistance)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "vxRenderDistance", CommonUniforms::getVxRenderDistance)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "fogColor", CapturedRenderingState.INSTANCE::getFogColor)
                .uniform4f(UniformUpdateFrequency.DYNAMIC, "iris_FogColor", CommonUniforms::getUmbraFogColor)
                .uniform1f(UniformUpdateFrequency.DYNAMIC, "iris_FogDensity", CommonUniforms::getFogDensity)
                .uniform1f(UniformUpdateFrequency.DYNAMIC, "iris_FogStart", CommonUniforms::getFogStart)
                .uniform1f(UniformUpdateFrequency.DYNAMIC, "iris_FogEnd", CommonUniforms::getFogEnd)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "skyColor", CommonUniforms::getSkyColor)
                .uniform2i(UniformUpdateFrequency.PER_FRAME, "eyeBrightness", EyeBrightnessTracker::getEyeBrightness)
                .uniform2i(UniformUpdateFrequency.PER_FRAME, "eyeBrightnessSmooth", EyeBrightnessTracker::getEyeBrightnessSmooth)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "wetness", EyeBrightnessTracker::getWetness)
                .uniform1i(UniformUpdateFrequency.DYNAMIC, "fogMode", CommonUniforms::getFogMode)
                .uniform1f(UniformUpdateFrequency.DYNAMIC, "fogDensity", CommonUniforms::getFogDensity)
                .uniform1f(UniformUpdateFrequency.DYNAMIC, "fogStart", CommonUniforms::getFogStart)
                .uniform1f(UniformUpdateFrequency.DYNAMIC, "fogEnd", CommonUniforms::getFogEnd)
                .uniform1i(UniformUpdateFrequency.DYNAMIC, "fogShape", CommonUniforms::getFogShape)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "heldItemId", CommonUniforms::getHeldItemId)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "heldBlockLightValue", CommonUniforms::getHeldBlockLightValue)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "heldItemId2", CommonUniforms::getHeldItemId2)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "heldBlockLightValue2", CommonUniforms::getHeldBlockLightValue2)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "heldBlockLightColor",
                        CommonUniforms::getHeldBlockLightColor)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "heldBlockLightColor2",
                        CommonUniforms::getHeldBlockLightColor2)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "hideGUI",
                        () -> Minecraft.getMinecraft().gameSettings.hideGUI ? 1 : 0)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "screenBrightness",
                        () -> Minecraft.getMinecraft().gameSettings.gammaSetting)
                .uniform2i(UniformUpdateFrequency.PER_FRAME, "atlasSize", CapturedRenderingState.INSTANCE::getAtlasSize)
                .uniform2i(UniformUpdateFrequency.PER_FRAME, "terrainTextureSize", CapturedRenderingState.INSTANCE::getAtlasSize);
    }

    // Returns the is eye in cave.
    private static float getIsEyeInCave() {
        updateComplementaryCustomUniforms();
        return isEyeInWater() == 0 ? cachedEyeInCave : 0.0f;
    }

    // Returns the in dry.
    private static float getInDry() {
        updateComplementaryCustomUniforms();
        return cachedInDry;
    }

    // Returns the in rainy.
    private static float getInRainy() {
        updateComplementaryCustomUniforms();
        return cachedInRainy;
    }

    // Returns the in snowy.
    private static float getInSnowy() {
        updateComplementaryCustomUniforms();
        return cachedInSnowy;
    }

    // Returns the starter.
    private static float getStarter() {
        updateComplementaryCustomUniforms();
        return cachedStarter;
    }

    // Returns the frame time smooth.
    private static float getFrameTimeSmooth() {
        updateComplementaryCustomUniforms();
        return cachedFrameTimeSmooth;
    }

    // Returns the eye brightness m.
    public static float getEyeBrightnessM() {
        updateComplementaryCustomUniforms();
        return cachedEyeBrightnessM;
    }

    // Returns the second smoothed eye brightness.
    private static float getEyeBrightnessM2() {
        updateComplementaryCustomUniforms();
        return cachedEyeBrightnessM2;
    }

    // Returns the rain factor.
    private static float getRainFactor() {
        updateComplementaryCustomUniforms();
        return cachedRainFactor;
    }

    // Returns the velocity.
    private static float getVelocity() {
        updateComplementaryCustomUniforms();
        return cachedVelocity;
    }

    // Returns the rain strength s.
    private static float getRainStrengthS() {
        updateComplementaryCustomUniforms();
        return cachedRainStrengthS;
    }

    // Returns the rain strength shining stars.
    private static float getRainStrengthShiningStars() {
        updateComplementaryCustomUniforms();
        return cachedRainStrengthShiningStars;
    }

    // Returns the second smoothed rain strength.
    private static float getRainStrengthS2() {
        updateComplementaryCustomUniforms();
        return cachedRainStrengthS2;
    }

    // Returns the is precipitation rain.
    private static float getIsPrecipitationRain() {
        updateComplementaryCustomUniforms();
        return cachedPrecipitationRain;
    }

    // Returns the touch my body.
    private static float getTouchMyBody() {
        updateComplementaryCustomUniforms();
        return cachedTouchMyBody;
    }

    // Returns the sneak smooth.
    private static float getSneakSmooth() {
        updateComplementaryCustomUniforms();
        return cachedSneakSmooth;
    }

    // Returns the burning smooth.
    private static float getBurningSmooth() {
        updateComplementaryCustomUniforms();
        return cachedBurningSmooth;
    }

    // Returns the effect strength.
    private static float getEffectStrength() {
        updateComplementaryCustomUniforms();
        return cachedEffectStrength;
    }

    // Updates the smoothed values that packs define for themselves.
    private static void updateComplementaryCustomUniforms() {
        int frame = SystemTimeUniforms.COUNTER.getFrameCounter();
        if (frame == complementaryUniformFrame) {
            return;
        }
        complementaryUniformFrame = frame;

        float deltaSeconds = Math.max(getSafeFrameTime(), 0.0f);
        float skyBrightness = getEyeSkyBrightness();
        int precipitation = getBiomePrecipitation();

        cachedEyeInCave = 1.0f - eyeInCave.update(getRawEyeInCave(skyBrightness), 6.0f, 12.0f, deltaSeconds);
        cachedInDry = inDry.update(precipitation == 0 ? 1.0f : 0.0f, 20.0f, 10.0f, deltaSeconds);
        cachedInRainy = inRainy.update(precipitation == 1 ? 1.0f : 0.0f, 20.0f, 10.0f, deltaSeconds);
        cachedInSnowy = inSnowy.update(precipitation == 2 ? 1.0f : 0.0f, 20.0f, 10.0f, deltaSeconds);

        float moving = getMoving();
        float movedValue = moved.update(moving, 0.0f, 31536000.0f, deltaSeconds);
        cachedStarter = starter.update(movedValue, 20.0f, 20.0f, deltaSeconds);

        cachedFrameTimeSmooth = Math.max(DEFAULT_FRAME_TIME / 4.0f,
                frameTimeSmooth.update(getSafeFrameTime(), 5.0f, 5.0f, deltaSeconds));
        cachedEyeBrightnessM = eyeBrightnessM.update(skyBrightness, 5.0f, 5.0f, deltaSeconds);
        cachedEyeBrightnessM2 = eyeBrightnessM2.update(skyBrightness > 239.0f / 240.0f ? 1.0f : 0.0f,
                2.0f, 2.0f, deltaSeconds);
        float rainStrength = getRainStrength();
        cachedRainFactor = rainFactor.update(rainStrength, 3.0f, 3.0f, deltaSeconds);
        cachedRainStrengthS = rainStrengthS.update(rainStrength, 15.0f, 15.0f, deltaSeconds);
        cachedRainStrengthShiningStars = rainStrengthShiningStars.update(rainStrength, 10.0f, 11.0f, deltaSeconds);
        cachedRainStrengthS2 = rainStrengthS2.update(rainStrength, 70.0f, 1.0f, deltaSeconds);

        cachedVelocity = getRawVelocity();
        float frameTime = Math.max(getSafeFrameTime(), 1.0e-6f);
        float smoothedSpeed = smoothSpeed.update(cachedVelocity / frameTime, 1.0f, 1.5f, deltaSeconds);
        cachedEffectStrength = (float) (1.0f - Math.exp(-smoothedSpeed * 0.003906f));
        cachedPrecipitationRain = precipitationRain.update(
                precipitation == 1 && getEyeAltitude() < 96.0f ? 1.0f : 0.0f, 6.0f, 6.0f, deltaSeconds);
        cachedTouchMyBody = touchMyBody.update(getRawHurtFactor(), 0.0f, 0.1f, deltaSeconds);
        cachedSneakSmooth = sneakSmooth.update(isSneaking(), 2.0f, 0.9f, deltaSeconds);
        cachedBurningSmooth = burningSmooth.update(isBurning(), 1.0f, 2.0f, deltaSeconds);
        cachedPreviousEndFlashIntensity = cachedEndFlashIntensity;
        cachedEndFlashIntensity = getRawEndFlashIntensity();
    }

    // Returns the cave value before smoothing.
    private static float getRawEyeInCave(float skyBrightness) {
        return getEyeAltitude() < 5.0f ? skyBrightness : 1.0f;
    }

    // Returns the eye sky brightness.
    private static float getEyeSkyBrightness() {
        Vector2i brightness = EyeBrightnessTracker.getEyeBrightness();
        return MathUtil.clamp(brightness.y / 240.0f, 0.0f, 1.0f);
    }

    // Returns the safe frame time.
    private static float getSafeFrameTime() {
        float frameTime = SystemTimeUniforms.COUNTER.getLastFrameTime();
        return frameTime > 0.0f ? Math.min(frameTime, 0.25f) : DEFAULT_FRAME_TIME;
    }

    // Returns the moving.
    private static float getMoving() {
        Vector3d current = CameraUniforms.getCurrentCameraPosition();
        Vector3d previous = CameraUniforms.getPreviousCameraPosition();
        double diffSum = Math.abs(current.x - previous.x)
                + Math.abs(current.y - previous.y)
                + Math.abs(current.z - previous.z);
        return diffSum > 0.0 && diffSum < 1.0 ? 1.0f : 0.0f;
    }

    // Returns the raw velocity.
    private static float getRawVelocity() {
        Vector3d current = CameraUniforms.getCurrentCameraPosition();
        Vector3d previous = CameraUniforms.getPreviousCameraPosition();
        double dx = current.x - previous.x;
        double dy = current.y - previous.y;
        double dz = current.z - previous.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // Returns the biome precipitation.
    private static int getBiomePrecipitation() {
        Biome biome = getCameraBiome();
        if (biome == null) {
            return 0;
        }
        if (biome.getEnableSnow()) {
            return 2;
        }
        return biome.canRain() ? 1 : 0;
    }

    // Returns the biome id.
    private static int getBiomeId() {
        Biome biome = getCameraBiome();
        return biome == null ? -1 : Biome.getIdForBiome(biome);
    }

    // Returns the biome category.
    private static int getBiomeCategory() {
        Biome biome = getCameraBiome();
        if (biome == null) {
            return 0;
        }
        if (biome instanceof BiomeTaiga) {
            return 1;
        }
        if (biome instanceof BiomeHills) {
            return 2;
        }
        if (biome instanceof BiomeJungle) {
            return 3;
        }
        if (biome instanceof BiomeMesa) {
            return 4;
        }
        if (biome instanceof BiomeSavanna) {
            return 6;
        }
        if (biome instanceof BiomeSnow) {
            return 7;
        }
        if (biome instanceof BiomeEnd) {
            return 8;
        }
        if (biome instanceof BiomeBeach) {
            return 9;
        }
        if (biome instanceof BiomeForest) {
            return 10;
        }
        if (biome instanceof BiomeOcean) {
            return 11;
        }
        if (biome instanceof BiomeDesert) {
            return 12;
        }
        if (biome instanceof BiomeRiver) {
            return 13;
        }
        if (biome instanceof BiomeSwamp) {
            return 14;
        }
        if (biome instanceof BiomeMushroomIsland) {
            return 15;
        }
        if (biome instanceof BiomeHell) {
            return 16;
        }
        if (biome instanceof BiomeVoid) {
            return 0;
        }
        return 5;
    }

    // Returns the rainfall.
    private static float getRainfall() {
        Biome biome = getCameraBiome();
        return biome == null ? 0.0f : biome.getRainfall();
    }

    // Returns the temperature.
    private static float getTemperature() {
        World world = world();
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        if (world == null || camera == null) {
            return 0.0f;
        }
        BlockPos pos = CAMERA_POS.setPos(camera);
        return world.getBiome(pos).getTemperature(pos);
    }

    // Returns the camera biome.
    private static Biome getCameraBiome() {
        World world = world();
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        if (world == null || camera == null) {
            return null;
        }
        return world.getBiome(CAMERA_POS.setPos(camera));
    }

    // Reusable camera position.
    private static final BlockPos.MutableBlockPos CAMERA_POS = new BlockPos.MutableBlockPos();

    // Returns the player or null.
    private static EntityPlayer player() {
        return Minecraft.getMinecraft().player;
    }

    // Returns one for true and zero for false.
    private static int bool(boolean value) {
        return value ? 1 : 0;
    }

    // Returns the umbra glint alpha.
    private static float getUmbraGlintAlpha() {
        return 1.0f;
    }

    // Returns the umbra texture scale.
    private static float getUmbraTextureScale() {
        return UmbraChunkVertexType.INSTANCE.getTextureScale();
    }

    // Returns the umbra model scale.
    private static float getUmbraModelScale() {
        return UmbraChunkVertexType.INSTANCE.getPositionScale();
    }

    // Returns the umbra model offset.
    private static Vector3f getUmbraModelOffset() {
        float offset = UmbraChunkVertexType.INSTANCE.getPositionOffset();
        return SCRATCH_3.set(offset, offset, offset);
    }

    // Returns the texture scale vector.
    private static Vector2f getTextureScaleVector() {
        float scale = getUmbraTextureScale();
        return SCRATCH_2.set(scale, scale);
    }

    // Returns the model scale vector.
    private static Vector3f getModelScaleVector() {
        float scale = getUmbraModelScale();
        return SCRATCH_3.set(scale, scale, scale);
    }

    // Returns the umbra camera translation.
    private static Vector3f getUmbraCameraTranslation() {
        return CameraUniforms.getCameraPositionFract(CameraUniforms.getCurrentRenderOriginUnshifted()).negate();
    }

    // Returns the gtexture id.
    private static int getGtextureId() {
        return getTextureUnit0Integer(GL_TEXTURE_BINDING_2D);
    }

    // Returns the gtexture size.
    private static Vector2i getGtextureSize() {
        int activeTexture = LWJGL.glGetInteger(GL_ACTIVE_TEXTURE);
        LWJGL.glActiveTexture(GL_TEXTURE0);
        try {
            return SCRATCH_2I.set(
                    LWJGL.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH),
                    LWJGL.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT));
        } finally {
            LWJGL.glActiveTexture(activeTexture);
        }
    }

    // Returns an int parameter of the texture on unit zero.
    private static int getTextureUnit0Integer(int pname) {
        int activeTexture = LWJGL.glGetInteger(GL_ACTIVE_TEXTURE);
        LWJGL.glActiveTexture(GL_TEXTURE0);
        try {
            return LWJGL.glGetInteger(pname);
        } finally {
            LWJGL.glActiveTexture(activeTexture);
        }
    }

    // Returns the blend func.
    private static Vector4i getBlendFunc() {
        if (LWJGL.glGetInteger(GL11.GL_BLEND) == 0) {
            return SCRATCH_4I.set(0, 0, 0, 0);
        }
        return SCRATCH_4I.set(
                LWJGL.glGetInteger(GL_BLEND_SRC_RGB),
                LWJGL.glGetInteger(GL_BLEND_DST_RGB),
                LWJGL.glGetInteger(GL_BLEND_SRC_ALPHA),
                LWJGL.glGetInteger(GL_BLEND_DST_ALPHA));
    }

    // Returns whether right handed.
    private static int isRightHanded() {
        EntityPlayer player = player();
        return bool(player == null || player.getPrimaryHand() == EnumHandSide.RIGHT);
    }

    // Returns whether sneaking.
    private static int isSneaking() {
        EntityPlayer player = player();
        return bool(player != null && player.isSneaking());
    }

    // Returns whether sprinting.
    private static int isSprinting() {
        EntityPlayer player = player();
        return bool(player != null && player.isSprinting());
    }

    // Returns whether hurt.
    private static int isHurt() {
        EntityPlayer player = player();
        return bool(player != null && player.hurtTime > 0);
    }

    // Returns the raw hurt factor.
    private static float getRawHurtFactor() {
        EntityPlayer player = player();
        return player != null && (player.hurtTime > 0 || player.deathTime > 0) ? 0.4f : 0.0f;
    }

    // Returns whether invisible.
    private static int isInvisible() {
        EntityPlayer player = player();
        return bool(player != null && player.isInvisible());
    }

    // Returns whether burning.
    private static int isBurning() {
        EntityPlayer player = player();
        return bool(player != null && player.isBurning());
    }

    // Returns whether on ground.
    private static int isOnGround() {
        EntityPlayer player = player();
        return bool(player != null && player.onGround);
    }

    // Returns whether feet in water.
    private static int isFeetInWater() {
        EntityPlayer player = player();
        World world = world();
        if (player == null || world == null) {
            return 0;
        }
        BlockPos feet = new BlockPos(player.posX, player.posY + 0.01D, player.posZ);
        return bool(world.isBlockLoaded(feet) && world.getBlockState(feet).getMaterial() == Material.WATER);
    }

    // Returns whether in swimming animation.
    private static int isInSwimmingAnimation() {
        EntityPlayer player = player();
        return bool(player != null && player.isInWater());
    }

    // Returns whether riding.
    private static int isRiding() {
        EntityPlayer player = player();
        return bool(player != null && player.isRiding());
    }

    // Returns whether elytra flying.
    private static int isElytraFlying() {
        EntityPlayer player = player();
        return bool(player != null && player.isElytraFlying());
    }

    // Returns whether vehicle in water.
    private static int isVehicleInWater() {
        Entity vehicle = vehicle();
        return bool(vehicle != null && vehicle.isInWater());
    }

    // Returns the vehicle id.
    private static int getVehicleId() {
        Entity vehicle = vehicle();
        return vehicle == null ? 0 : WorldRenderingSettings.getEntityId(vehicle);
    }

    // Returns the vehicle look vector.
    private static Vector3f getVehicleLookVector() {
        Entity vehicle = vehicle();
        return vehicle == null ? ZERO_3 : toVector3f(vehicle.getLook(CapturedRenderingState.INSTANCE.getTickDelta()));
    }

    // Returns the relative vehicle position.
    private static Vector3f getRelativeVehiclePosition() {
        Entity vehicle = vehicle();
        if (vehicle == null) {
            return ZERO_3;
        }
        float tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();
        double x = vehicle.lastTickPosX + (vehicle.posX - vehicle.lastTickPosX) * tickDelta;
        double y = vehicle.lastTickPosY + (vehicle.posY - vehicle.lastTickPosY) * tickDelta;
        double z = vehicle.lastTickPosZ + (vehicle.posZ - vehicle.lastTickPosZ) * tickDelta;
        Vector3d camera = CameraUniforms.getCurrentCameraPositionUnshifted();
        return SCRATCH_3.set((float) (camera.x - x), (float) (camera.y - y), (float) (camera.z - z));
    }

    // Returns the ridden entity or null.
    private static Entity vehicle() {
        EntityPlayer player = player();
        return player == null ? null : player.getRidingEntity();
    }

    // Returns whether survival like.
    private static boolean isSurvivalLike() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.player != null && mc.playerController != null && mc.playerController.isNotCreative()
                && !mc.playerController.isSpectator();
    }

    // Returns the chunk fade time inv.
    private static float getChunkFadeTimeInv() {
        int durationMs = ImpetusVintage.options().quality.chunkFadeInDuration;
        return durationMs > 0 ? 1.0f / durationMs : 0.0f;
    }

    // Returns the player mood.
    private static float getPlayerMood() {
        return getIsEyeInCave();
    }

    // Returns the constant mood.
    private static float getConstantMood() {
        return getIsEyeInCave();
    }

    // Returns the current player health.
    private static float getCurrentPlayerHealth() {
        EntityPlayer player = player();
        return player != null && isSurvivalLike() ? player.getHealth() / player.getMaxHealth() : -1.0f;
    }

    // Returns the max player health.
    private static float getMaxPlayerHealth() {
        EntityPlayer player = player();
        return player != null && isSurvivalLike() ? player.getMaxHealth() : -1.0f;
    }

    // Returns the current player hunger.
    private static float getCurrentPlayerHunger() {
        EntityPlayer player = player();
        return player != null && isSurvivalLike() ? player.getFoodStats().getFoodLevel() / 20.0f : -1.0f;
    }

    // Returns the current player armor.
    private static float getCurrentPlayerArmor() {
        EntityPlayer player = player();
        return player != null && isSurvivalLike() ? player.getTotalArmorValue() / 50.0f : -1.0f;
    }

    // Returns the current player air.
    private static float getCurrentPlayerAir() {
        EntityPlayer player = player();
        return player != null && isSurvivalLike() ? player.getAir() / 300.0f : -1.0f;
    }

    // Returns the max player air.
    private static float getMaxPlayerAir() {
        return isSurvivalLike() ? 300.0f : -1.0f;
    }

    // Returns whether first person camera.
    private static int isFirstPersonCamera() {
        return bool(Minecraft.getMinecraft().gameSettings.thirdPersonView == 0);
    }

    // Returns whether spectator.
    private static int isSpectator() {
        Minecraft mc = Minecraft.getMinecraft();
        return bool(mc.playerController != null && mc.playerController.isSpectator());
    }

    // Returns the thunder strength.
    private static float getThunderStrength() {
        World world = world();
        return world == null ? 0.0f : MathUtil.clamp(world.getThunderStrength(CapturedRenderingState.INSTANCE.getTickDelta()), 0.0f, 1.0f);
    }

    // Returns whether heavy fog.
    private static int isHeavyFog() {
        return bool(getBlindness() > 0.0f || isEyeInWater() == 2);
    }

    // Returns the sea level.
    private static int getSeaLevel() {
        World world = world();
        return world == null ? 0 : world.getSeaLevel();
    }

    // Returns the current selected block id.
    private static int getCurrentSelectedBlockId() {
        Minecraft mc = Minecraft.getMinecraft();
        RayTraceResult hit = mc.objectMouseOver;
        if (mc.world == null || hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return 0;
        }
        BlockPos pos = hit.getBlockPos();
        if (pos == null || !mc.world.isBlockLoaded(pos)) {
            return 0;
        }
        return WorldRenderingSettings.getBlockStateId(mc.world.getBlockState(pos));
    }

    // Returns the current selected block pos.
    private static Vector3f getCurrentSelectedBlockPos() {
        Minecraft mc = Minecraft.getMinecraft();
        RayTraceResult hit = mc.objectMouseOver;
        Entity camera = mc.getRenderViewEntity();
        if (mc.world == null || hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || camera == null) {
            return NO_SELECTED_BLOCK;
        }
        BlockPos pos = hit.getBlockPos();
        Vec3d eye = camera.getPositionEyes(CapturedRenderingState.INSTANCE.getTickDelta());
        double cx = pos.getX() + 0.5 - eye.x;
        double cy = pos.getY() + 0.5 - eye.y;
        double cz = pos.getZ() + 0.5 - eye.z;
        return SCRATCH_3.set((float) cx, (float) cy, (float) cz);
    }

    // Returns the eye position.
    private static Vector3f getEyePosition() {
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        if (camera == null) {
            return ZERO_3;
        }
        Vec3d eye = camera.getPositionEyes(CapturedRenderingState.INSTANCE.getTickDelta());
        return toVector3f(eye);
    }

    // Returns the relative eye position.
    private static Vector3f getRelativeEyePosition() {
        Vector3d camera = CameraUniforms.getCurrentCameraPositionUnshifted();
        Vector3f eye = getEyePosition();
        return SCRATCH_3.set((float) (camera.x - eye.x), (float) (camera.y - eye.y), (float) (camera.z - eye.z));
    }

    // Returns the player look vector.
    private static Vector3f getPlayerLookVector() {
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        return camera == null ? ZERO_3 : toVector3f(camera.getLook(CapturedRenderingState.INSTANCE.getTickDelta()));
    }

    // Returns the player body vector.
    private static Vector3f getPlayerBodyVector() {
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        if (camera == null) {
            return ZERO_3;
        }
        float yaw = (float) Math.toRadians(camera.rotationYaw);
        return SCRATCH_3.set(-MathHelper.sin(yaw), 0.0f, MathHelper.cos(yaw));
    }

    // Returns the lightning bolt position.
    private static Vector4f getLightningBoltPosition() {
        World world = world();
        if (world == null) {
            return NO_LIGHTNING;
        }
        float tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();
        Vector3d camera = CameraUniforms.getCurrentCameraPositionUnshifted();
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityLightningBolt) {
                double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * tickDelta;
                double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * tickDelta;
                double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * tickDelta;
                return SCRATCH_4.set((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), 1.0f);
            }
        }
        return NO_LIGHTNING;
    }

    // Returns the cloud time.
    private static float getCloudTime() {
        World world = world();
        return world == null ? 0.0f : (world.getTotalWorldTime() + CapturedRenderingState.INSTANCE.getTickDelta()) * 0.03f;
    }

    // Returns the end flash intensity.
    private static float getEndFlashIntensity() {
        updateComplementaryCustomUniforms();
        return cachedEndFlashIntensity;
    }

    // Returns the previous end flash intensity.
    private static float getPreviousEndFlashIntensity() {
        updateComplementaryCustomUniforms();
        return cachedPreviousEndFlashIntensity;
    }

    // Returns the raw end flash intensity.
    private static float getRawEndFlashIntensity() {
        return 0.0f;
    }

    // Returns the screen size.
    private static Vector2f getScreenSize() {
        Minecraft mc = Minecraft.getMinecraft();
        return SCRATCH_2.set(mc.displayWidth, mc.displayHeight);
    }

    // Returns the fog mode.
    private static int getFogMode() {
        return isFogEnabled() ? LWJGL.glGetInteger(GL11.GL_FOG_MODE) : 0;
    }

    // Returns the fog density.
    private static float getFogDensity() {
        return isFogEnabled() ? finiteNonNegative(LWJGL.glGetFloat(GL_FOG_DENSITY), 0.0f) : 0.0f;
    }

    // Fraction of the far plane where fog starts.
    private static final float FOG_START_FRACTION = 0.8f;
    // Fraction of the far plane where thick fog starts.
    private static final float FOG_START_FRACTION_THICK = 0.05f;

    // Returns the fog start.
    private static float getFogStart() {
        return getFar() * (showsThickFog() ? FOG_START_FRACTION_THICK : FOG_START_FRACTION);
    }

    // Returns the fog end.
    private static float getFogEnd() {
        return getFar();
    }

    // Returns whether the dimension has thick fog.
    private static boolean showsThickFog() {
        World world = world();
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        if (world == null || camera == null) {
            return false;
        }
        boolean bossFog = Minecraft.getMinecraft().ingameGUI != null
                && Minecraft.getMinecraft().ingameGUI.getBossOverlay() != null
                && Minecraft.getMinecraft().ingameGUI.getBossOverlay().shouldCreateFog();
        return world.provider.doesXZShowFog((int) camera.posX, (int) camera.posZ) || bossFog;
    }

    // Returns the fog shape.
    private static int getFogShape() {
        return 1;
    }

    // Returns the umbra fog color.
    private static Vector4f getUmbraFogColor() {
        Vector3f fogColor = CapturedRenderingState.INSTANCE.getFogColor();
        return SCRATCH_4.set(fogColor.x, fogColor.y, fogColor.z, 1.0f);
    }

    // Returns the dh render distance.
    private static int getDhRenderDistance() {
        return DhCompat.getRenderDistance();
    }

    // Returns the dh far plane.
    private static float getDhFarPlane() {
        return DhCompat.hasRenderingEnabled() ? DhCompat.getFarPlane() : getFar();
    }

    // Returns the dh near plane.
    private static float getDhNearPlane() {
        return DhCompat.hasRenderingEnabled() ? DhCompat.getNearPlane() : 0.05f;
    }

    // Returns the vx render distance.
    private static int getVxRenderDistance() {
        int chunks = WorldRenderingSettings.getVoxelRenderDistanceChunks();
        return chunks > 0 ? chunks : Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
    }

    // Returns whether fog enabled.
    private static boolean isFogEnabled() {
        return LWJGL.glGetInteger(GL11.GL_FOG) != 0;
    }

    // Returns a finite value that is not negative or a fallback.
    private static float finiteNonNegative(float value, float fallback) {
        return Float.isFinite(value) && value >= 0.0f ? value : fallback;
    }

    // Returns the main hand item.
    private static ItemStack heldItem() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        return player == null ? ItemStack.EMPTY : player.getHeldItemMainhand();
    }

    // Returns the held item with the brightest light.
    private static ItemStack brightestHeldItem() {
        ItemStack main = heldItem();
        if (!WorldRenderingSettings.isOldHandLight()) {
            return main;
        }
        ItemStack off = offhandItem();
        return blockLightValue(off) > blockLightValue(main) ? off : main;
    }

    // Returns the held item id.
    private static int getHeldItemId() {
        return WorldRenderingSettings.getItemId(brightestHeldItem());
    }

    // Returns the held block light value.
    private static int getHeldBlockLightValue() {
        return WorldRenderingSettings.isDynamicHandLight() ? blockLightValue(brightestHeldItem()) : 0;
    }

    // Returns the held block light color.
    private static Vector3f getHeldBlockLightColor() {
        return WorldRenderingSettings.isDynamicHandLight()
                ? heldLightColor(brightestHeldItem()) : ZERO_3;
    }

    // Returns the off hand item.
    private static ItemStack offhandItem() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        return player == null ? ItemStack.EMPTY : player.getHeldItemOffhand();
    }

    // Returns the id of the off hand item.
    private static int getHeldItemId2() {
        return WorldRenderingSettings.getItemId(offhandItem());
    }

    // Returns the light of the off hand item.
    private static int getHeldBlockLightValue2() {
        return WorldRenderingSettings.isDynamicHandLight() ? blockLightValue(offhandItem()) : 0;
    }

    // Returns the light color of the off hand item.
    private static Vector3f getHeldBlockLightColor2() {
        if (!WorldRenderingSettings.isDynamicHandLight()) {
            return ZERO_3;
        }
        return heldLightColor(offhandItem());
    }

    // Returns the light value of a held stack.
    @SuppressWarnings("deprecation")
    private static int blockLightValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Block block = Block.getBlockFromItem(stack.getItem());
        return block.getDefaultState().getLightValue();
    }

    // Returns the light color of a held stack.
    private static Vector3f heldLightColor(ItemStack stack) {
        return WHITE_3;
    }

    // Returns a JOML copy of a vector.
    private static Vector3f toVector3f(Vec3d vector) {
        return SCRATCH_3.set((float) vector.x, (float) vector.y, (float) vector.z);
    }

    // Returns the sky color.
    private static Vector3f getSkyColor() {
        World world = world();
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        if (world == null || camera == null) {
            return ZERO_3;
        }
        Vec3d sky = world.getSkyColor(camera, CapturedRenderingState.INSTANCE.getTickDelta());
        return SCRATCH_3.set((float) sky.x, (float) sky.y, (float) sky.z);
    }

    // Returns the client world or null.
    private static World world() {
        return Minecraft.getMinecraft().world;
    }

    // Returns the rain strength.
    private static float getRainStrength() {
        World world = world();
        return world == null ? 0.0f : world.getRainStrength(CapturedRenderingState.INSTANCE.getTickDelta());
    }

    // Returns the eye altitude.
    private static float getEyeAltitude() {
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        return camera == null ? 0.0f : (float) (camera.posY + camera.getEyeHeight());
    }

    // Returns whether eye in water.
    private static int isEyeInWater() {
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        World world = world();
        if (camera == null || world == null) {
            return 0;
        }
        Material material = ActiveRenderInfo.getBlockStateAtEntityViewpoint(
                world, camera, CapturedRenderingState.INSTANCE.getTickDelta()).getMaterial();
        if (material == Material.WATER) {
            return 1;
        }
        if (material == Material.LAVA) {
            return 2;
        }
        return 0;
    }

    // Returns the blindness.
    private static float getBlindness() {
        EntityLivingBase player = livingCamera();
        return (player != null && player.isPotionActive(MobEffects.BLINDNESS)) ? 1.0f : 0.0f;
    }

    // Returns the darkness factor.
    private static float getDarknessFactor() {
        EntityLivingBase player = livingCamera();
        if (player == null) {
            return 0.0f;
        }
        Potion darkness = Potion.REGISTRY.getObject(DARKNESS_EFFECT_ID);
        if (darkness == null) {
            return 0.0f;
        }
        PotionEffect effect = player.getActivePotionEffect(darkness);
        if (effect == null) {
            return 0.0f;
        }
        return MathUtil.clamp(effect.getDuration() / 20.0f, 0.0f, 1.0f);
    }

    // Returns the darkness light factor.
    private static float getDarknessLightFactor() {
        return getDarknessFactor();
    }

    // Returns the night vision.
    private static float getNightVision() {
        EntityLivingBase player = livingCamera();
        if (player == null || !player.isPotionActive(MobEffects.NIGHT_VISION)) {
            return 0.0f;
        }
        int duration = player.getActivePotionEffect(MobEffects.NIGHT_VISION).getDuration();
        if (duration > 200) {
            return 1.0f;
        }
        return 0.7f + (float) Math.sin(duration * Math.PI * 0.2) * 0.3f;
    }

    // Returns the camera entity when it is living or null.
    private static EntityLivingBase livingCamera() {
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        return camera instanceof EntityLivingBase ? (EntityLivingBase) camera : null;
    }

    // Returns the world time.
    private static int getWorldTime() {
        World world = world();
        return world == null ? 0 : (int) (world.getWorldTime() % 24000L);
    }

    // Returns the world day.
    private static int getWorldDay() {
        World world = world();
        return world == null ? 0 : (int) (world.getWorldTime() / 24000L);
    }

    // Returns the moon phase.
    private static int getMoonPhase() {
        World world = world();
        return world == null ? 0 : world.getMoonPhase();
    }

    // Returns the time angle.
    private static float getTimeAngle() {
        return getWorldTime() / 24000.0f;
    }

    // Returns the time brightness.
    private static float getTimeBrightness() {
        return (float) Math.max(Math.sin(getTimeAngle() * Math.PI * 2.0), 0.0);
    }

    // Returns the moon brightness.
    private static float getMoonBrightness() {
        return (float) Math.max(Math.sin(getTimeAngle() * Math.PI * -2.0), 0.0);
    }

    // Returns the shadow fade.
    private static float getShadowFade() {
        return MathUtil.clamp(1.0f - (Math.abs(Math.abs(CelestialUniforms.getSunAngle() - 0.5f) - 0.25f) - 0.23f)
                * 100.0f, 0.0f, 1.0f);
    }

    // Returns the shd fade.
    private static float getShdFade() {
        return MathUtil.clamp(1.0f - (Math.abs(Math.abs(CelestialUniforms.getSunAngle() - 0.5f) - 0.25f) - 0.225f)
                * 40.0f, 0.0f, 1.0f);
    }

    // Returns the blind factor.
    private static float getBlindFactor() {
        float blindFactorSqrt = MathUtil.clamp(getBlindness() * 2.0f - 1.0f, 0.0f, 1.0f);
        return blindFactorSqrt * blindFactorSqrt;
    }

    // Returns the adjusted time.
    private static float getAdjustedTime() {
        return Math.abs((((getWorldTime() / 1000.0f) + 6.0f) % 24.0f) - 12.0f);
    }

    // Returns the day.
    private static float getDay() {
        return MathUtil.clamp(5.4f - getAdjustedTime(), 0.0f, 1.0f);
    }

    // Returns the night.
    private static float getNight() {
        return MathUtil.clamp(getAdjustedTime() - 6.0f, 0.0f, 1.0f);
    }

    // Returns the dawn dusk.
    private static float getDawnDusk() {
        return (1.0f - getDay()) - getNight();
    }

    // Returns the bedrock level.
    private static int getBedrockLevel() {
        return 0;
    }

    // Returns the cloud height.
    private static float getCloudHeight() {
        return ImpetusVintage.options().quality.cloudHeight;
    }

    // Returns the height limit.
    private static int getHeightLimit() {
        World world = world();
        return world == null ? 256 : world.getHeight();
    }

    // Returns the logical height limit.
    private static int getLogicalHeightLimit() {
        World world = world();
        return world == null ? 256 : world.getActualHeight();
    }

    // Returns whether ceiling.
    private static int hasCeiling() {
        World world = world();
        return bool(world != null && !world.provider.isSurfaceWorld());
    }

    // Returns whether skylight.
    private static int hasSkylight() {
        World world = world();
        return bool(world == null || world.provider.isSurfaceWorld());
    }

    // Returns the ambient light.
    private static float getAmbientLight() {
        World world = world();
        if (world == null) {
            return 0.0f;
        }
        return world.provider.isSurfaceWorld() ? 0.0f : 0.1f;
    }

    // Returns the render target width.
    private static int getRenderTargetWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        return OpenGlHelper.isFramebufferEnabled() && mc.getFramebuffer() != null
                ? mc.getFramebuffer().framebufferWidth : mc.displayWidth;
    }

    // Returns the render target height.
    private static int getRenderTargetHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        return OpenGlHelper.isFramebufferEnabled() && mc.getFramebuffer() != null
                ? mc.getFramebuffer().framebufferHeight : mc.displayHeight;
    }

    // Returns the view width.
    private static float getViewWidth() {
        return getRenderTargetWidth();
    }

    // Returns the view height.
    private static float getViewHeight() {
        return getRenderTargetHeight();
    }

    // Returns the pixel size x.
    private static float getPixelSizeX() {
        int width = getRenderTargetWidth();
        return width > 0 ? 1.0f / width : 0.0f;
    }

    // Returns the pixel size y.
    private static float getPixelSizeY() {
        int height = getRenderTargetHeight();
        return height > 0 ? 1.0f / height : 0.0f;
    }

    // Returns the aspect ratio.
    private static float getAspectRatio() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.displayHeight == 0) {
            return 1.0f;
        }
        return (float) mc.displayWidth / (float) mc.displayHeight;
    }

    // Returns the far.
    private static float getFar() {
        int renderDistanceChunks = Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
        return renderDistanceChunks * 16.0f;
    }

    // Value that approaches its target with separate half lives up and down.
    private static final class SmoothedValue {
        // Natural logarithm of two.
        private static final double LOG_2 = Math.log(2.0);

        // First update state.
        private boolean initialized;
        // Current smoothed value.
        private float accumulator;

        // Creates a value that starts at its first target.
        private SmoothedValue() {
        }

        // Creates a value that starts at a given value.
        private SmoothedValue(float initialValue) {
            this.initialized = true;
            this.accumulator = initialValue;
        }

        // Moves toward a target over the elapsed time and returns the new value.
        private float update(float target, float halfLifeUp, float halfLifeDown, float deltaSeconds) {
            if (!this.initialized) {
                this.initialized = true;
                this.accumulator = target;
                return target;
            }

            float halfLife = target > this.accumulator ? halfLifeUp : halfLifeDown;
            if (halfLife <= 0.0f) {
                this.accumulator = target;
                return target;
            }

            float scaledHalfLife = halfLife / 20.0f;
            float decay = (float) (LOG_2 / scaledHalfLife);
            float smoothingFactor = 1.0f - (float) Math.exp(-decay * deltaSeconds);
            this.accumulator += (target - this.accumulator) * smoothingFactor;
            return this.accumulator;
        }
    }
}

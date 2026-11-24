package com.bdmajora.extras.mixin;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.LightmapGammaAccess;
import com.bdmajora.extras.mixin.lightmap.EntityRendererLightmapMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.world.WorldProviderSurface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// The lightmap cache key against Thaumcraft: Return of the Ancients' gamma hook and gamma values past 1.
class LightmapGammaKeyTest {
    private static final String FILE = LightmapGammaAccess.ROTA_HOOKS.replace('.', '/') + ".class";

    private Minecraft client;
    private GameSettings settings;
    private WorldClient world;
    private EntityPlayerSP player;
    private MethodHandle previousHook;

    // Shows an ASMHookRotA class file and defines the given bytes for it.
    private static final class RotALoader extends ClassLoader {
        private final byte[] hooks;

        RotALoader(byte[] hooks) {
            super(LightmapGammaKeyTest.class.getClassLoader());
            this.hooks = hooks;
        }

        @Override
        public URL getResource(String name) {
            return name.equals(FILE) ? LightmapGammaKeyTest.class.getResource("LightmapGammaKeyTest.class") : super.getResource(name);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.equals(LightmapGammaAccess.ROTA_HOOKS) || this.hooks == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, this.hooks, 0, this.hooks.length);
        }
    }

    // ASMHookRotA with public static float value and hookGammaSetting() returning it, or throwing.
    private static byte[] hooks(boolean throwing, String descriptor) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String name = LightmapGammaAccess.ROTA_HOOKS.replace('.', '/');
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "F", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calls", "I", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, LightmapGammaAccess.ROTA_METHOD, descriptor, null, null);
        method.visitCode();
        if (throwing) {
            method.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
            method.visitInsn(Opcodes.DUP);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false);
            method.visitInsn(Opcodes.ATHROW);
        } else if (descriptor.equals("()F")) {
            method.visitFieldInsn(Opcodes.GETSTATIC, name, "calls", "I");
            method.visitInsn(Opcodes.ICONST_1);
            method.visitInsn(Opcodes.IADD);
            method.visitFieldInsn(Opcodes.PUTSTATIC, name, "calls", "I");
            method.visitFieldInsn(Opcodes.GETSTATIC, name, "value", "F");
            method.visitInsn(Opcodes.FRETURN);
        } else {
            method.visitInsn(Opcodes.RETURN);
        }
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    // Installs a working RotA hook and returns its loader so the test can move its answer.
    private static RotALoader installRotA() {
        RotALoader loader = new RotALoader(hooks(false, "()F"));
        MethodHandle handle = LightmapGammaAccess.bind(loader);
        assertNotNull(handle);
        LightmapGammaAccess.install(handle);
        return loader;
    }

    private static void answer(RotALoader loader, float value) throws Exception {
        Class.forName(LightmapGammaAccess.ROTA_HOOKS, false, loader).getField("value").setFloat(null, value);
    }

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void setUp() {
        Mixins.set(Extras.class, "config", new ExtrasConfig());
        client = Mc.client();
        settings = Mc.uninitialized(GameSettings.class);
        settings.gammaSetting = 1.0F;
        Mixins.set(client, "gameSettings", settings);
        world = mock(WorldClient.class);
        Mixins.set(world, "provider", new WorldProviderSurface());
        when(world.getSunBrightness(anyFloat())).thenReturn(0.75F);
        player = mock(EntityPlayerSP.class);
        Mixins.set(client, "world", world);
        Mixins.set(client, "player", player);
        previousHook = LightmapGammaAccess.installed();
    }

    @AfterEach
    void tearDown() {
        LightmapGammaAccess.install(previousHook);
        Mixins.set(Extras.class, "config", null);
    }

    private long key() {
        return Statics.call(EntityRendererLightmapMixin.class, "impetus$key", world, player);
    }

    // Runs the HEAD handler once for a frame that wants a lightmap update; true when it skipped the rebuild.
    private static boolean skips(EntityRendererLightmapMixin renderer) {
        Mixins.set(renderer, "lightmapUpdateNeeded", true);
        CallbackInfo ci = Mixins.ci();
        Mixins.call(renderer, "impetus$skipUnchanged", 0F, ci);
        return ci.isCancelled();
    }

    private EntityRendererLightmapMixin renderer() {
        EntityRendererLightmapMixin renderer = Mixins.instance(EntityRendererLightmapMixin.class);
        Mixins.set(renderer, "mc", client);
        Mixins.set(renderer, "impetus$lightmapKey", Long.MIN_VALUE);
        return renderer;
    }

    @Test
    void withoutRotATheSettingIsTheGamma() {
        LightmapGammaAccess.install(null);
        assertEquals(1.0F, LightmapGammaAccess.read(client));
        settings.gammaSetting = 0.25F;
        assertEquals(0.25F, LightmapGammaAccess.read(client));
        // The test class path has no RotA
        assertNull(LightmapGammaAccess.bind(LightmapGammaKeyTest.class.getClassLoader()));
        assertNull(LightmapGammaAccess.bind(null));
    }

    @Test
    void walkingIntoATaintBiomeRebuildsTheLightmap() throws Exception {
        RotALoader rota = installRotA();
        answer(rota, 1.0F);
        EntityRendererLightmapMixin renderer = renderer();
        assertFalse(skips(renderer));
        assertTrue(skips(renderer));
        // RotA answers 0 inside the biome while the setting stays at 1: the old key read the setting and kept skipping
        answer(rota, 0.0F);
        assertEquals(1.0F, settings.gammaSetting);
        assertFalse(skips(renderer));
        assertTrue(skips(renderer));
        // Walking back out rebuilds once more
        answer(rota, 1.0F);
        assertFalse(skips(renderer));
        assertTrue(skips(renderer));
    }

    @Test
    void rotAsAnswerWinsOverTheSetting() throws Exception {
        RotALoader rota = installRotA();
        answer(rota, 0.0F);
        settings.gammaSetting = 0.5F;
        long inBiome = key();
        // A setting change RotA hides changes nothing updateLightmap would read
        settings.gammaSetting = 0.9F;
        assertEquals(inBiome, key());
        assertEquals(0.0F, LightmapGammaAccess.read(client));
    }

    @Test
    void aFailingRotAHookFallsBackToTheSettingForGood() throws Exception {
        MethodHandle broken = LightmapGammaAccess.bind(new RotALoader(hooks(true, "()F")));
        assertNotNull(broken);
        LightmapGammaAccess.install(broken);
        settings.gammaSetting = 0.6F;
        assertEquals(0.6F, LightmapGammaAccess.read(client));
        assertNull(LightmapGammaAccess.installed());
        // Still answers after the disable, and the key still builds
        assertEquals(0.6F, LightmapGammaAccess.read(client));
        assertDoesNotThrow(this::key);
    }

    @Test
    void anUnusableRotAHookIsNotBound() {
        // Wrong signature, class that cannot be defined, garbage bytes
        assertNull(LightmapGammaAccess.bind(new RotALoader(hooks(false, "()V"))));
        assertNull(LightmapGammaAccess.bind(new RotALoader(null)));
        assertNull(LightmapGammaAccess.bind(new RotALoader(new byte[] {1, 2, 3})));
    }

    @Test
    void gammaPastOneNeverCollidesWithADarkerValue() {
        LightmapGammaAccess.install(null);
        // 512 / 255 wrapped onto 0 in the old 9 bit field, 2.5 onto ~0.49
        float[] pairs = {0.0F, 512.0F / 255.0F, 0.49F, 2.5F, 1.0F, 100.0F, 0.0F, 1000.0F};
        for (int i = 0; i < pairs.length; i += 2) {
            settings.gammaSetting = pairs[i];
            long low = key();
            settings.gammaSetting = pairs[i + 1];
            assertNotEquals(low, key(), pairs[i] + " vs " + pairs[i + 1]);
        }
        // Every bit of the float takes part: neighbouring floats differ
        settings.gammaSetting = 1.0F;
        long one = key();
        settings.gammaSetting = Math.nextUp(1.0F);
        assertNotEquals(one, key());
    }

    @Test
    void extremeGammaKeepsTheOtherFieldsAndTheSentinelApart() {
        LightmapGammaAccess.install(null);
        for (float gamma : new float[] {-1.0F, -0.0F, Float.MAX_VALUE, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            settings.gammaSetting = gamma;
            long key = key();
            // The "no lightmap yet" sentinel can never be produced, so the first frame always rebuilds
            assertNotEquals(Long.MIN_VALUE, key);
            assertTrue(key >= 0, "bit 63 stays clear for " + gamma);
            // Sun brightness still sits in the low byte, untouched by the gamma bits
            assertEquals((long) (0.75F * 255.0F), key & 0xFF);
            assertEquals(Float.floatToIntBits(gamma) & 0xFFFFFFFFL, key >>> 20);
        }
    }

    @Test
    void sunLightningAndDimensionStillChangeTheKey() {
        LightmapGammaAccess.install(null);
        long base = key();
        when(world.getSunBrightness(anyFloat())).thenReturn(0.25F);
        assertNotEquals(base, key());
        when(world.getSunBrightness(anyFloat())).thenReturn(0.75F);
        assertEquals(base, key());
        when(world.getLastLightningBolt()).thenReturn(2);
        long lit = key();
        assertNotEquals(base, lit);
        assertEquals(1L, (lit >>> 8) & 1L);
        when(world.getLastLightningBolt()).thenReturn(0);
        Mixins.set(world, "provider", new net.minecraft.world.WorldProviderEnd());
        assertEquals(1L, (key() >>> 9) & 1L);
    }

    @Test
    void aNanGammaIsStableSoItDoesNotRebuildEveryFrame() throws Exception {
        // NaN != NaN as a float; the key compares bits, so a hook stuck on NaN still caches
        RotALoader rota = installRotA();
        answer(rota, Float.NaN);
        EntityRendererLightmapMixin renderer = renderer();
        assertFalse(skips(renderer));
        assertTrue(skips(renderer));
        assertTrue(skips(renderer));
        // Any non canonical NaN the hook might produce collapses to the same bits as well
        answer(rota, Float.intBitsToFloat(0x7FC00001));
        assertTrue(skips(renderer));
    }

    @Test
    void signedZeroCostsAtMostOneExtraRebuild() {
        LightmapGammaAccess.install(null);
        settings.gammaSetting = 0.0F;
        EntityRendererLightmapMixin renderer = renderer();
        assertFalse(skips(renderer));
        settings.gammaSetting = -0.0F;
        // Different bits, so one rebuild; then it caches again
        assertFalse(skips(renderer));
        assertTrue(skips(renderer));
    }

    @Test
    void aRotAHookThatFlipsEveryFrameRebuildsEveryFrame() throws Exception {
        // Standing on a taint biome border: each change of answer is a real lightmap change
        RotALoader rota = installRotA();
        EntityRendererLightmapMixin renderer = renderer();
        for (int frame = 0; frame < 20; frame++) {
            answer(rota, (frame & 1) == 0 ? 0.0F : 1.0F);
            assertFalse(skips(renderer), "frame " + frame);
        }
        assertTrue(skips(renderer));
    }

    @Test
    void aNewWorldWithTheSameInputsStillRebuilds() {
        LightmapGammaAccess.install(null);
        EntityRendererLightmapMixin renderer = renderer();
        assertFalse(skips(renderer));
        assertTrue(skips(renderer));
        WorldClient other = mock(WorldClient.class);
        Mixins.set(other, "provider", new WorldProviderSurface());
        when(other.getSunBrightness(anyFloat())).thenReturn(0.75F);
        Mixins.set(client, "world", other);
        world = other;
        assertFalse(skips(renderer));
        assertTrue(skips(renderer));
    }

    @Test
    void noUpdateRequestedLeavesTheKeyAlone() {
        LightmapGammaAccess.install(null);
        EntityRendererLightmapMixin renderer = renderer();
        Mixins.set(renderer, "lightmapUpdateNeeded", false);
        CallbackInfo ci = Mixins.ci();
        Mixins.call(renderer, "impetus$skipUnchanged", 0F, ci);
        assertFalse(ci.isCancelled());
        assertEquals(Long.MIN_VALUE, (long) Mixins.<Long>get(renderer, "impetus$lightmapKey"));
    }

    @Test
    void togglingCachingMidSessionNeverSkipsAChange() {
        LightmapGammaAccess.install(null);
        EntityRendererLightmapMixin renderer = renderer();
        assertFalse(skips(renderer));
        ExtrasConfig config = Extras.options();
        config.clientTick.lightmapCaching = false;
        settings.gammaSetting = 0.3F;
        // Vanilla rebuilds while caching is off
        assertFalse(skips(renderer));
        config.clientTick.lightmapCaching = true;
        // The key still holds the old gamma, so the first frame back rebuilds
        assertFalse(skips(renderer));
        assertTrue(skips(renderer));
    }

    @Test
    void aBossFadeOrCustomDimensionForcesARebuildAfterwards() {
        LightmapGammaAccess.install(null);
        EntityRendererLightmapMixin renderer = renderer();
        assertFalse(skips(renderer));
        assertTrue(skips(renderer));
        // The boss fade changes the lightmap without changing the key, so it clears the key
        Mixins.set(renderer, "bossColorModifier", 0.4F);
        assertFalse(skips(renderer));
        Mixins.set(renderer, "bossColorModifier", 0.0F);
        Mixins.set(renderer, "bossColorModifierPrev", 0.1F);
        assertFalse(skips(renderer));
        Mixins.set(renderer, "bossColorModifierPrev", 0.0F);
        // Same inputs as before the fade, yet the fade's lightmap is on screen: it must rebuild once
        assertFalse(skips(renderer));
        assertTrue(skips(renderer));
    }

    @Test
    void nightVisionBoundariesMatchVanillaFlicker() {
        LightmapGammaAccess.install(null);
        when(player.getActivePotionEffect(net.minecraft.init.MobEffects.NIGHT_VISION))
                .thenReturn(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.NIGHT_VISION, 201));
        long steady = key();
        // Past 200 ticks vanilla holds night vision at full, so longer durations share the key
        when(player.getActivePotionEffect(net.minecraft.init.MobEffects.NIGHT_VISION))
                .thenReturn(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.NIGHT_VISION, 100000));
        assertEquals(steady, key());
        // At 200 and below it flickers per tick, so each duration is its own key
        long[] keys = new long[201];
        for (int duration = 1; duration <= 200; duration++) {
            when(player.getActivePotionEffect(net.minecraft.init.MobEffects.NIGHT_VISION))
                    .thenReturn(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.NIGHT_VISION, duration));
            keys[duration] = key();
            assertNotEquals(steady, keys[duration], "duration " + duration);
        }
        assertEquals(200, java.util.Arrays.stream(keys, 1, 201).distinct().count());
        when(player.getActivePotionEffect(net.minecraft.init.MobEffects.NIGHT_VISION)).thenReturn(null);
        long none = key();
        for (int duration = 1; duration <= 200; duration++) {
            assertNotEquals(none, keys[duration]);
        }
        // Night vision bits never reach the gamma field
        assertEquals(key() >>> 20, steady >>> 20);
    }

    private static int calls(RotALoader loader) throws Exception {
        return Class.forName(LightmapGammaAccess.ROTA_HOOKS, false, loader).getField("calls").getInt(null);
    }

    @Test
    void rotAIsAskedOncePerFrameThatChecksTheCache() throws Exception {
        RotALoader rota = installRotA();
        answer(rota, 1.0F);
        EntityRendererLightmapMixin renderer = renderer();
        for (int frame = 1; frame <= 10; frame++) {
            skips(renderer);
            assertEquals(frame, calls(rota));
        }
    }

    @Test
    void rotAIsNotAskedWhenNothingWouldUseTheAnswer() throws Exception {
        RotALoader rota = installRotA();
        EntityRendererLightmapMixin renderer = renderer();
        // No update wanted this frame
        Mixins.set(renderer, "lightmapUpdateNeeded", false);
        Mixins.call(renderer, "impetus$skipUnchanged", 0F, Mixins.ci());
        // Caching switched off
        Extras.options().clientTick.lightmapCaching = false;
        skips(renderer);
        Extras.options().clientTick.lightmapCaching = true;
        // Main menu: no world or no player
        Mixins.set(client, "world", null);
        skips(renderer);
        Mixins.set(client, "world", world);
        Mixins.set(client, "player", null);
        skips(renderer);
        // A boss fade or a dimension with its own colours rebuilds anyway
        Mixins.set(client, "player", player);
        Mixins.set(renderer, "bossColorModifier", 0.5F);
        skips(renderer);
        assertEquals(0, calls(rota));
    }

    @Test
    void inMenusNothingIsSkipped() {
        LightmapGammaAccess.install(null);
        EntityRendererLightmapMixin renderer = renderer();
        Mixins.set(client, "world", null);
        assertFalse(skips(renderer));
        assertFalse(skips(renderer));
        // The key is untouched, so the first in-game frame still rebuilds
        assertEquals(Long.MIN_VALUE, (long) Mixins.<Long>get(renderer, "impetus$lightmapKey"));
        Mixins.set(client, "world", world);
        assertFalse(skips(renderer));
    }
}

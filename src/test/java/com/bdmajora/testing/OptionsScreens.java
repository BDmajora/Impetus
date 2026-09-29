package com.bdmajora.testing;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.DynamicLightsConfig;
import com.bdmajora.equilibrium.Equilibrium;
import com.bdmajora.extras.Extras;
import com.bdmajora.extras.mixin.particle.ParticleManagerAccessor;
import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.Locale;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.mockito.Mockito;

import java.nio.file.Path;

// What the Impetus video settings screen reaches for while it gathers every module's pages and draws them: each
// module's config under a scratch game directory, Impetus's own options, a font and texture manager on the client,
// and a lang table
public final class OptionsScreens {
    // The options ImpetusVintage loaded for itself, handed back by forget
    private static ImpetusGameOptions previous;

    private OptionsScreens() {}

    // The Impetus options now in place, whose config file lives under home
    public static ImpetusGameOptions prepare(Minecraft client, Path home) {
        Statics.set(FMLInjectionData.class, "minecraftHome", home.toFile());
        Statics.set(Launch.class, "minecraftHome", home.toFile());
        Statics.set(FMLLaunchHandler.class, "side", Side.CLIENT);
        Statics.set(I18n.class, "i18nLocale", new Locale());
        Mixins.set(Extras.class, "config", null);
        Mixins.set(CoarctatioConfig.class, "instance", null);
        Mixins.set(FulgorConfig.class, "instance", null);
        Mixins.set(Equilibrium.class, "config", null);
        Mixins.set(DynamicLights.class, "config", new DynamicLightsConfig());
        Mixins.set(client, "effectRenderer", Mc.mock(ParticleManager.class, ParticleManagerAccessor.class));
        // Six pixels a character, trimming and wrapping to nothing shorter than the text itself
        FontRenderer font = Mockito.mock(FontRenderer.class);
        font.FONT_HEIGHT = 9;
        Mockito.when(font.getStringWidth(Mockito.anyString())).thenAnswer(invocation -> invocation.<String>getArgument(0).length() * 6);
        Mockito.when(font.trimStringToWidth(Mockito.anyString(), Mockito.anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(font.listFormattedStringToWidth(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(invocation -> java.util.List.of(invocation.<String>getArgument(0)));
        Mixins.set(client, "fontRenderer", font);
        Mockito.when(client.getTextureManager()).thenReturn(Mockito.mock(TextureManager.class));
        // The GUI scale slider sizes itself from the window's framebuffer
        Framebuffer framebuffer = Mockito.mock(Framebuffer.class);
        framebuffer.framebufferWidth = client.displayWidth;
        framebuffer.framebufferHeight = client.displayHeight;
        Mockito.when(client.getFramebuffer()).thenReturn(framebuffer);
        // ImpetusVintage loads its own options as it initialises, so it has to have done so before they are swapped
        try {
            Class.forName(ImpetusVintage.class.getName());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
        previous = Statics.get(ImpetusVintage.class, "CONFIG");
        ImpetusGameOptions options = ImpetusGameOptions.defaults();
        Mixins.set(options, "configPath", home.resolve("config").resolve("impetus-options.json"));
        Statics.set(ImpetusVintage.class, "CONFIG", options);
        return options;
    }

    // Drops the configs prepare left behind, so the next test loads its own
    public static void forget() {
        Mixins.set(Extras.class, "config", null);
        Mixins.set(CoarctatioConfig.class, "instance", null);
        Mixins.set(FulgorConfig.class, "instance", null);
        Mixins.set(Equilibrium.class, "config", null);
        Mixins.set(DynamicLights.class, "config", null);
        Statics.set(ImpetusVintage.class, "CONFIG", previous);
    }
}

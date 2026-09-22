package com.bdmajora.impetus.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.Loader;
import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Implements the engine's abstract DrawContext on top of 1.12.2's GuiScreen/GlStateManager calls
public class VintageDrawContext implements DrawContext {
    private final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
    // caches compiled ITextComponents since TextComponent objects are treated as immutable keys
    private final Map<TextComponent, ITextComponent> componentCache;

    // caches generated dynamic texture locations for mod logos, keyed by mod ID; a null value records "no logo" so the container and pack lookups do not repeat every frame
    private static final Map<String, String> MOD_LOGOS = new HashMap<>();
    // ResourceLocation parses its string on construction, and the sidebar blits the same few icons every frame
    private static final Map<String, ResourceLocation> ICON_LOCATIONS = new HashMap<>();

    // Impetus and its subsystems ship sidebar icons as textures under assets/impetus/textures/gui, bypassing the mcmod.info lookup below which needs a Forge container; keyed by the mod id the option pages register under
    private static final Map<String, String> BUNDLED_LOGOS = new HashMap<>();

    static {
        BUNDLED_LOGOS.put("impetus", "impetus:textures/gui/impetus.png");
        // The Extras pages are Impetus' own, so they share its mark
        BUNDLED_LOGOS.put("extras", "impetus:textures/gui/impetus.png");
        BUNDLED_LOGOS.put("coarctatio", "impetus:textures/gui/coarctatio.png");
        BUNDLED_LOGOS.put("equilibrium", "impetus:textures/gui/equilibrium.png");
        BUNDLED_LOGOS.put("fulgor", "impetus:textures/gui/fulgor.png");
        BUNDLED_LOGOS.put("umbra", "impetus:textures/gui/umbra.png");
    }

    public VintageDrawContext() {
        this.componentCache = new HashMap<>();
    }

    // Maps Sodium's style set onto vanilla's Style object
    private ITextComponent applyStyles(ITextComponent c, Set<TextFormattingStyle> styles) {
        if (styles.isEmpty()) {
            return c;
        }
        var mutable = new TextComponentString("").appendSibling(c);
        var vanillaStyle = mutable.getStyle();
        for (var style : styles) {
            if (style.isColor()) {
                vanillaStyle = vanillaStyle.setColor(TextFormatting.fromColorIndex(style.ordinal()));
            } else {
                vanillaStyle = switch (style) {
                    case STRIKETHROUGH -> vanillaStyle.setStrikethrough(true);
                    case UNDERLINE -> vanillaStyle.setUnderlined(true);
                    case ITALIC -> vanillaStyle.setItalic(true);
                    default -> throw new IllegalArgumentException("Unknown TextFormattingStyle: " + style.name());
                };
            }
        }
        mutable.setStyle(vanillaStyle);
        return mutable;
    }

    // First key with a translation, so fallbacks work
    private static String findKey(List<String> keys) {
        for (var str : keys) {
            if (I18n.hasKey(str)) {
                return str;
            }
        }
        return keys.get(0);
    }

    // Translates one Sodium component into a vanilla one
    private ITextComponent convertComponent(TextComponent component) {
        if (component instanceof TextComponent.Literal literal) {
            return new TextComponentString(literal.text());
        } else if (component instanceof TextComponent.Translatable translatable) {
            return new TextComponentTranslation(findKey(translatable.keys()), translatable.args().stream().map(a -> {
                if (a instanceof TextComponent c) {
                    return compile(c);
                } else {
                    return a;
                }
            }).toArray());
        } else if (component instanceof TextComponent.Styled styled) {
            var innerComponent = compile(styled.inner());
            return applyStyles(innerComponent, styled.styles());
        } else {
            throw new IllegalArgumentException("Unexpected component class: " + component.getClass().getName());
        }
    }

    // Converts and caches, since the same components are drawn every frame
    private ITextComponent compile(TextComponent component) {
        var compiled = this.componentCache.get(component);
        if (compiled == null) {
            compiled = this.convertComponent(component);
            this.componentCache.put(component, compiled);
        }
        return compiled;
    }

    // Vanilla's Gui.drawRect
    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y2, color);
    }

    // Through the font renderer, with or without shadow
    @Override
    public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        int len = font.drawString(compile(str).getFormattedText(), x, y, color, shadow);
        GlStateManager.disableBlend();
        return len;
    }

    // Binds and draws a full texture; the GL colour is reset to opaque white first because Gui.drawRect leaves the last fill colour (e.g. translucent black from a widget background) in place, which would tint and fade the icon by whatever was drawn before it
    @Override
    public void blitWholeImage(String icon, int x, int y, int width, int height) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        Minecraft.getMinecraft().getTextureManager().bindTexture(ICON_LOCATIONS.computeIfAbsent(icon, ResourceLocation::new));
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, width, height, (float)width, (float)height);
        GlStateManager.disableBlend();
    }

    // GlStateManager
    @Override
    public void pushMatrix() {
        GlStateManager.pushMatrix();
    }

    // GlStateManager
    @Override
    public void translate(double x, double y, double z) {
        GlStateManager.translate(x, y, z);
    }

    // GlStateManager
    @Override
    public void popMatrix() {
        GlStateManager.popMatrix();
    }

    // Converts GUI coordinates to window pixels, flipping y since GL's origin is bottom-left
    @Override
    public void enableScissor(int x, int y, int x2, int y2) {
        int width = x2 - x + 1;
        int height = y2 - y + 1;
        var mc = Minecraft.getMinecraft();
        ScaledResolution scaledresolution = new ScaledResolution(mc);
        int scale = scaledresolution.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, mc.displayHeight - (y + height) * scale, width * scale, height * scale);
    }

    // Ends the clip
    @Override
    public void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // Font renderer width of the compiled component
    @Override
    public int getStringWidth(TextComponent component) {
        return font.getStringWidth(compile(component).getUnformattedText());
    }

    // Truncates to fit, for tooltips
    @Override
    public String substrByWidth(String str, int maxWidth) {
        return font.trimStringToWidth(str, maxWidth);
    }

    // Word-wraps through the font renderer and converts each line back
    @Override
    public List<TextComponent> split(TextComponent component, int maxWidth) {
        return font.listFormattedStringToWidth(compile(component).getFormattedText(), maxWidth).stream().map(TextComponent::literal).collect(Collectors.toList());
    }

    // Plain text with formatting codes stripped
    @Override
    public String extractString(TextComponent component) {
        return compile(component).getUnformattedText();
    }

    // Font renderer's line height
    @Override
    public int lineHeight() {
        return font.FONT_HEIGHT;
    }

    // Sidebar heading for a group of pages; Impetus' subsystems are not registered Forge mods so their raw lowercase id is capitalised to match "Impetus", while a third-party mod's own display name wins
    @Override
    public TextComponent getFriendlyModName(String modId) {
        var container = Loader.instance().getIndexedModList().get(modId);
        if (container != null) {
            return TextComponent.literal(container.getName());
        }

        return TextComponent.literal(capitalize(modId));
    }

    // First character upper-cased via Character.toUpperCase, not String.toUpperCase, which applies the default locale and on a Turkish install maps "i" to the dotted capital
    private static String capitalize(String modId) {
        if (modId == null || modId.isEmpty()) {
            return modId;
        }

        return Character.toUpperCase(modId.charAt(0)) + modId.substring(1);
    }

    // Bundled icons first, then the mod's own logo file, resolved once per mod id
    @Override
    public @Nullable String getModLogoPath(String modId) {
        String bundled = BUNDLED_LOGOS.get(modId);
        if (bundled != null) {
            return bundled;
        }

        if (MOD_LOGOS.containsKey(modId)) {
            return MOD_LOGOS.get(modId);
        }

        String path = loadModLogo(modId);
        MOD_LOGOS.put(modId, path);
        return path;
    }

    // Uploads the mod's logo as a dynamic texture and returns its location, or null when the mod has none
    private String loadModLogo(String modId) {
        var container = Loader.instance().getIndexedModList().get(modId);
        if (container == null) {
            return null;
        }

        String file = container.getMetadata().logoFile;
        if (file == null || file.isEmpty()) {
            return null;
        }

        IResourcePack pack = FMLClientHandler.instance().getResourcePackFor(container.getModId());
        BufferedImage logo = null;

        try {
            if (pack != null) {
                logo = pack.getPackImage();
            } else {
                InputStream logoResource = this.getClass().getResourceAsStream(file);
                if (logoResource != null) {
                    logo = TextureUtil.readBufferedImage(logoResource);
                }
            }
        } catch (IOException ignored) {
        }

        if (logo == null) {
            return null;
        }

        return Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("modlogo", new DynamicTexture(logo)).toString();
    }
}

package com.bdmajora.impetus.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Draw context of the option screens on the vanilla font and tessellator.
public class VintageDrawContext implements DrawContext {
    // Font of the game.
    private final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
    // Largest number of cached components before the cache is cleared.
    private static final int COMPONENT_CACHE_LIMIT = 1024;
    // Formatted and plain text of each component.
    private final Map<TextComponent, Compiled> componentCache;
    // Language version of the cached text.
    private long cacheVersion;

    // Logo paths by mod id.
    private static final Map<String, String> MOD_LOGOS = new HashMap<>();
    // Loaded logo textures by path.
    private static final Map<String, ResourceLocation> ICON_LOCATIONS = new HashMap<>();

    // Logos shipped with the mod by module id.
    private static final Map<String, String> BUNDLED_LOGOS = new HashMap<>();

    // Fills the bundled logos
    static {
        BUNDLED_LOGOS.put("impetus", "impetus:textures/gui/impetus.png");
        BUNDLED_LOGOS.put("extras", "impetus:textures/gui/impetus.png");
        BUNDLED_LOGOS.put("coarctatio", "impetus:textures/gui/coarctatio.png");
        BUNDLED_LOGOS.put("equilibrium", "impetus:textures/gui/equilibrium.png");
        BUNDLED_LOGOS.put("fulgor", "impetus:textures/gui/fulgor.png");
        BUNDLED_LOGOS.put("umbra", "impetus:textures/gui/umbra.png");
    }

    // Number of pushed matrices.
    private int matrixDepth;
    // Stack of scissor rectangles in groups of four.
    private int[] scissors = new int[16];
    // Number of pushed scissor rectangles.
    private int scissorDepth;

    // Creates a context with an empty text cache.
    public VintageDrawContext() {
        this.componentCache = new HashMap<>();
    }

    // Returns a vanilla component with the styles applied.
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

    // Returns the first translated key or the first key.
    private static String findKey(List<String> keys) {
        for (var str : keys) {
            if (I18n.hasKey(str)) {
                return str;
            }
        }
        return keys.get(0);
    }

    // Returns the vanilla form of a component.
    private ITextComponent convertComponent(TextComponent component) {
        if (component instanceof TextComponent.Literal literal) {
            return new TextComponentString(literal.text());
        } else if (component instanceof TextComponent.Translatable translatable) {
            return new TextComponentTranslation(findKey(translatable.keys()), translatable.args().stream().map(a -> {
                if (a instanceof TextComponent c) {
                    return convertComponent(c);
                } else {
                    return a;
                }
            }).toArray());
        } else if (component instanceof TextComponent.Styled styled) {
            var innerComponent = convertComponent(styled.inner());
            return applyStyles(innerComponent, styled.styles());
        } else {
            throw new IllegalArgumentException("Unexpected component class: " + component.getClass().getName());
        }
    }

    // Formatted and plain text of a component.
    private record Compiled(String formatted, String plain) {
    }

    // Returns the cached text of a component and clears the cache after a language change or when full.
    private Compiled compile(TextComponent component) {
        long version = this.textVersion();
        if (version != this.cacheVersion) {
            this.componentCache.clear();
            this.cacheVersion = version;
        }
        Compiled compiled = this.componentCache.get(component);
        if (compiled == null) {
            ITextComponent vanilla = this.convertComponent(component);
            compiled = new Compiled(vanilla.getFormattedText(), vanilla.getUnformattedText());
            if (this.componentCache.size() >= COMPONENT_CACHE_LIMIT) {
                this.componentCache.clear();
            }
            this.componentCache.put(component, compiled);
        }
        return compiled;
    }

    // Fills a rectangle.
    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y2, color);
    }

    // Draws a one pixel border.
    @Override
    public void drawBorder(int x1, int y1, int x2, int y2, int color) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        GlStateManager.color((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F, (color >>> 24) / 255.0F);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        quad(buffer, x1, y1, x2, y1 + 1);
        quad(buffer, x1, y2 - 1, x2, y2);
        quad(buffer, x1, y1, x1 + 1, y2);
        quad(buffer, x2 - 1, y1, x2, y2);
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    // Adds a rectangle to the buffer.
    private static void quad(BufferBuilder buffer, int x1, int y1, int x2, int y2) {
        int left = Math.max(x1, x2);
        int right = Math.min(x1, x2);
        int top = Math.max(y1, y2);
        int bottom = Math.min(y1, y2);
        buffer.pos(left, bottom, 0.0D).endVertex();
        buffer.pos(right, bottom, 0.0D).endVertex();
        buffer.pos(right, top, 0.0D).endVertex();
        buffer.pos(left, top, 0.0D).endVertex();
    }

    // Draws a component and returns the end position.
    @Override
    public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        int len = font.drawString(compile(str).formatted(), x, y, color, shadow);
        GlStateManager.disableBlend();
        return len;
    }

    // Draws a whole image into a rectangle.
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

    // Pushes the model view matrix.
    @Override
    public void pushMatrix() {
        GlStateManager.pushMatrix();
        this.matrixDepth++;
    }

    // Translates the model view matrix.
    @Override
    public void translate(double x, double y, double z) {
        GlStateManager.translate(x, y, z);
    }

    // Pops the model view matrix.
    @Override
    public void popMatrix() {
        GlStateManager.popMatrix();
        if (this.matrixDepth > 0) {
            this.matrixDepth--;
        }
    }

    // Pushes a scissor rectangle intersected with the current one.
    @Override
    public void enableScissor(int x, int y, int x2, int y2) {
        int width = x2 - x + 1;
        int height = y2 - y + 1;
        var mc = Minecraft.getMinecraft();
        ScaledResolution scaledresolution = new ScaledResolution(mc);
        int scale = scaledresolution.getScaleFactor();
        int boxX = x * scale;
        int boxY = mc.displayHeight - (y + height) * scale;
        int boxWidth = width * scale;
        int boxHeight = height * scale;
        if (this.scissorDepth > 0) {
            int outer = (this.scissorDepth - 1) * 4;
            int right = Math.min(boxX + boxWidth, this.scissors[outer] + this.scissors[outer + 2]);
            int top = Math.min(boxY + boxHeight, this.scissors[outer + 1] + this.scissors[outer + 3]);
            boxX = Math.max(boxX, this.scissors[outer]);
            boxY = Math.max(boxY, this.scissors[outer + 1]);
            boxWidth = Math.max(0, right - boxX);
            boxHeight = Math.max(0, top - boxY);
        }
        if ((this.scissorDepth + 1) * 4 > this.scissors.length) {
            this.scissors = Arrays.copyOf(this.scissors, this.scissors.length * 2);
        }
        int slot = this.scissorDepth++ * 4;
        this.scissors[slot] = boxX;
        this.scissors[slot + 1] = boxY;
        this.scissors[slot + 2] = boxWidth;
        this.scissors[slot + 3] = boxHeight;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(boxX, boxY, boxWidth, boxHeight);
    }

    // Pops a scissor rectangle and restores the one below.
    @Override
    public void disableScissor() {
        if (this.scissorDepth > 0) {
            this.scissorDepth--;
        }
        if (this.scissorDepth == 0) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }
        int outer = (this.scissorDepth - 1) * 4;
        GL11.glScissor(this.scissors[outer], this.scissors[outer + 1], this.scissors[outer + 2], this.scissors[outer + 3]);
    }

    // Returns the number of pushed matrices.
    @Override
    public int matrixDepth() {
        return this.matrixDepth;
    }

    // Returns the number of pushed scissor rectangles.
    @Override
    public int scissorDepth() {
        return this.scissorDepth;
    }

    // Returns the width of a component.
    @Override
    public int getStringWidth(TextComponent component) {
        return font.getStringWidth(compile(component).plain());
    }

    // Returns the longest start of a text that fits a width.
    @Override
    public String substrByWidth(String str, int maxWidth) {
        return font.trimStringToWidth(str, maxWidth);
    }

    // Splits a component into lines that fit a width.
    @Override
    public List<TextComponent> split(TextComponent component, int maxWidth) {
        return font.listFormattedStringToWidth(compile(component).formatted(), maxWidth).stream().map(TextComponent::literal).collect(Collectors.toList());
    }

    // Returns the plain text of a component.
    @Override
    public String extractString(TextComponent component) {
        return compile(component).plain();
    }

    // Returns the height of a line.
    @Override
    public int lineHeight() {
        return font.FONT_HEIGHT;
    }

    // Returns the language version of the text.
    @Override
    @SuppressWarnings("deprecation")
    public long textVersion() {
        return net.minecraft.util.text.translation.I18n.getLastTranslationUpdateTimeInMilliseconds() << 2 | (font.getUnicodeFlag() ? 1 : 0) | (font.getBidiFlag() ? 2 : 0);
    }

    // Returns the display name of a mod.
    @Override
    public TextComponent getFriendlyModName(String modId) {
        var container = Loader.instance().getIndexedModList().get(modId);
        if (container != null) {
            return TextComponent.literal(container.getName());
        }

        return TextComponent.literal(capitalize(modId));
    }

    // Returns a text with its first letter in upper case.
    private static String capitalize(String modId) {
        if (modId == null || modId.isEmpty()) {
            return modId;
        }

        return Character.toUpperCase(modId.charAt(0)) + modId.substring(1);
    }

    // Returns the logo path of a mod or null.
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

    // Loads the logo of a mod once.
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

package com.bdmajora.impetus.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusVideoOptionsController;
import com.bdmajora.impetus.engine.impl.gui.options.CommonOptionPages;
import com.bdmajora.impetus.engine.impl.render.ShaderModBridge;
import com.bdmajora.coarctatio.gui.CoarctatioOptionPages;
import com.bdmajora.equilibrium.gui.EquilibriumOptionPages;
import com.bdmajora.dynamiclights.gui.DynamicLightsOptionPages;
import com.bdmajora.extras.gui.ExtrasOptionPages;
import com.bdmajora.fulgor.gui.FulgorOptionPages;
import com.bdmajora.impetus.umbra.gui.modern.UmbraOptionPages;
import org.lwjgl.input.Mouse;
import com.bdmajora.impetus.ImpetusVintage;

import java.io.IOException;
import java.util.*;

public class ImpetusVideoOptionsScreen extends GuiScreen {
    private final GuiScreen prevScreen;
    private final ImpetusVideoOptionsController controller;
    // One context for the screen's lifetime, so its compiled-component cache survives between frames
    private final VintageDrawContext drawContext = new VintageDrawContext();

    private int lastMouseX, lastMouseY;

    public ImpetusVideoOptionsScreen(GuiScreen prevScreen) {
        super();
        this.prevScreen = prevScreen;
        this.controller = new ImpetusVideoOptionsController(() -> this.mc.displayGuiScreen(this.prevScreen), createPages(this), this.drawContext) {
            // Reloads the renderer, textures or world as the changed options demand
            @Override
            protected void applyFlagSideEffects(Set<OptionFlag> flags) {
                super.applyFlagSideEffects(flags);

                Minecraft client = Minecraft.getMinecraft();

                if (client.world != null) {
                    if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
                        client.renderGlobal.loadRenderers();
                    } else if (flags.contains(OptionFlag.REQUIRES_RENDERER_UPDATE)) {
                        client.renderGlobal.setDisplayListEntitiesDirty();
                    }
                }

                if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
                    client.getTextureMapBlocks().setMipmapLevels(mc.gameSettings.mipmapLevels);
                    client.refreshResources();
                    // Re-apply the atlas sampler state in case the atlas was not fully restitched.
                    com.bdmajora.impetus.impl.render.texture.BlockAtlasFiltering.reapplyToBlockAtlas();
                }

                // Push hot-path option values into the engine's runtime snapshot.
                com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions.apply(ImpetusVintage.options());

                // The General page's VSync tickbox writes the swap interval directly, clearing adaptive sync; re-assert after any apply, no-op unless it is on
                com.bdmajora.extras.client.AdaptiveSync.reapply();
            }
        };
        resetDrag();
    }

    // Assembles every subsystem's pages in sidebar order; pages group under their namespace, so Extras and its nine topic pages fold as one section beneath Impetus
    private static List<OptionPage> createPages(GuiScreen parent) {
        List<OptionPage> pages = new ArrayList<>();
        pages.add(ImpetusGameOptionPages.general());
        pages.add(ImpetusGameOptionPages.quality());
        pages.add(CommonOptionPages.performance(ImpetusVintage.options()));
        pages.addAll(ExtrasOptionPages.pages());
        pages.addAll(CoarctatioOptionPages.pages());
        pages.add(FulgorOptionPages.lighting());
        pages.addAll(EquilibriumOptionPages.pages());

        // The Umbra group: shader packs (only when a shader mod actually resolved) and, beneath them, dynamic lights, which share the namespace
        if (ShaderModBridge.isShaderModPresent()) {
            pages.add(UmbraOptionPages.shaderPacks(parent));
        }
        pages.addAll(DynamicLightsOptionPages.pages());

        return pages;
    }

    // Forwards to the Sodium screen and starts drag tracking
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        this.controller.getFrame().mouseClicked(VintageInteractionContext.INSTANCE, mouseX, mouseY, mouseButton);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    // Forwards and ends drag tracking
    @Override
    protected void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        this.controller.getFrame().mouseReleased(VintageInteractionContext.INSTANCE, mouseX, mouseY, mouseButton);
        resetDrag();
    }

    // Clears the drag origin
    private void resetDrag() {
        lastMouseX = -1;
        lastMouseY = -1;
    }

    // Synthesises drag events, which vanilla does not deliver as such
    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        if (lastMouseY != -1 && lastMouseX != -1) {
            int dx = mouseX - lastMouseX;
            int dy = mouseY - lastMouseY;

            this.controller.getFrame().mouseDragged(VintageInteractionContext.INSTANCE, mouseX, mouseY, clickedMouseButton, dx, dy);
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    // Forwards keys; Escape is handled by the parent
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Give the framework (e.g. the search bar) first refusal; fall back to vanilla handling (ESC-to-close).
        if (this.controller.getFrame().keyTyped(typedChar, keyCode)) {
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    // Forwards scroll wheel events, which vanilla reports outside the click callbacks
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            double scrollDelta = dWheel > 0 ? 1 : -1;
            this.controller.getFrame().mouseScrolled(VintageInteractionContext.INSTANCE, mouseX, mouseY, 0, scrollDelta);
        }
    }

    // Draws the Sodium screen through the vintage draw context
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.controller.render(this.drawContext, mouseX, mouseY, partialTicks);
    }

    // In a world the frame behind the screen is blurred and darkened so the options read against it; out of one vanilla's dirt background stays
    @Override
    public void drawWorldBackground(int tint) {
        if (this.mc.world != null) {
            ScreenBlurBackdrop.draw(this.width, this.height);
            return;
        }

        super.drawWorldBackground(tint);
    }

    // Rebuilds layout for the current resolution
    @Override
    public void initGui() {
        this.controller.init(this.width, this.height);
    }
}

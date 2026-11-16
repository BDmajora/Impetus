package com.bdmajora.impetus.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusVideoOptionsController;
import com.bdmajora.impetus.engine.impl.gui.options.CommonOptionPages;
import com.bdmajora.impetus.engine.impl.render.ShaderModBridge;
import com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggedCompat;
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

// Video options screen of Impetus.
public class ImpetusVideoOptionsScreen extends GuiScreen {
    // Screen to return to.
    private final GuiScreen prevScreen;
    // Controller of the screen.
    private final ImpetusVideoOptionsController controller;
    // Draw context of the screen.
    private final VintageDrawContext drawContext = new VintageDrawContext();

    // Last cursor position.
    private int lastMouseX, lastMouseY;

    // Creates an impetus video options screen from its prev screen.
    public ImpetusVideoOptionsScreen(GuiScreen prevScreen) {
        super();
        this.prevScreen = prevScreen;
        this.controller = new ImpetusVideoOptionsController(() -> this.mc.displayGuiScreen(this.prevScreen), createPages(this), this.drawContext) {
            @Override
            @SuppressWarnings("deprecation")
            protected void applyFlagSideEffects(Set<OptionFlag> flags) {
                super.applyFlagSideEffects(flags);

                Minecraft client = Minecraft.getMinecraft();

                boolean pipeline = flags.contains(OptionFlag.REQUIRES_SHADER_PIPELINE_RELOAD);
                boolean rebuild = pipeline || flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD);
                if (pipeline) {
                    com.bdmajora.impetus.umbra.Umbra.loadCurrentShaderpack();
                } else if (rebuild) {
                    com.bdmajora.impetus.umbra.Umbra.reloadIfTerrainBackendChanged();
                }

                if (client.world != null) {
                    if (rebuild) {
                        client.renderGlobal.loadRenderers();
                    } else if (flags.contains(OptionFlag.REQUIRES_RENDERER_UPDATE)) {
                        client.renderGlobal.setDisplayListEntitiesDirty();
                    }
                }

                if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
                    client.getTextureMapBlocks().setMipmapLevels(mc.gameSettings.mipmapLevels);
                    client.refreshResources();
                    com.bdmajora.impetus.impl.render.texture.BlockAtlasFiltering.reapplyToBlockAtlas();
                }

                com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions.apply(ImpetusVintage.options());

                com.bdmajora.extras.client.AdaptiveSync.reapply();
            }
        };
        resetDrag();
    }

    // Returns every options page.
    private static List<OptionPage> createPages(GuiScreen parent) {
        List<OptionPage> pages = new ArrayList<>();
        var graphicsApi = CommonOptionPages.graphicsApi(ImpetusVintage.options());
        pages.add(ImpetusGameOptionPages.general(graphicsApi));
        pages.add(ImpetusGameOptionPages.quality());
        pages.add(CommonOptionPages.performance(ImpetusVintage.options()));
        pages.add(CommonOptionPages.nvidium(ImpetusVintage.options(), graphicsApi));
        if (FluidloggedCompat.IS_LOADED) {
            pages.add(ImpetusGameOptionPages.fluidlogging());
        }
        pages.addAll(ExtrasOptionPages.pages());
        pages.addAll(CoarctatioOptionPages.pages());
        pages.add(FulgorOptionPages.lighting());
        pages.addAll(EquilibriumOptionPages.pages());

        if (ShaderModBridge.isShaderModPresent()) {
            pages.add(UmbraOptionPages.shaderPacks(parent));
        }
        pages.addAll(DynamicLightsOptionPages.pages());

        return pages;
    }

    // Forwards a click to the controller.
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        this.controller.getFrame().mouseClicked(VintageInteractionContext.INSTANCE, mouseX, mouseY, mouseButton);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    // Forwards a release to the controller.
    @Override
    protected void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        this.controller.getFrame().mouseReleased(VintageInteractionContext.INSTANCE, mouseX, mouseY, mouseButton);
        resetDrag();
    }

    // Ends any drag.
    private void resetDrag() {
        lastMouseX = -1;
        lastMouseY = -1;
    }

    // Forwards a drag to the controller.
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

    // Forwards a key to the controller.
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.controller.getFrame().dispatchKeyTyped(VintageInteractionContext.INSTANCE, typedChar, keyCode)) {
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    // Forwards scrolling to the controller.
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

    // Draws the screen.
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.controller.render(this.drawContext, mouseX, mouseY, partialTicks);
    }

    // Draws the blurred backdrop.
    @Override
    public void drawWorldBackground(int tint) {
        if (this.mc.world != null) {
            ScreenBlurBackdrop.draw(this.width, this.height);
            return;
        }

        super.drawWorldBackground(tint);
    }

    // Initializes the gui.
    @Override
    public void initGui() {
        this.controller.init(this.width, this.height);
    }
}

package net.irisshaders.iris.api.v0;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.gui.modern.ShaderPackSelectScreen;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

// Implements the public Iris API without exposing Umbra's internals to other mods.
final class IrisApiInternal implements IrisApi {
    static final IrisApi INSTANCE = new IrisApiInternal();

    private final IrisApiConfig config = new IrisApiConfig() {
        @Override
        public boolean areShadersEnabled() {
            return Umbra.areShadersEnabled();
        }

        @Override
        public void setShadersEnabledAndApply(boolean enabled) {
            Umbra.setShadersEnabled(enabled);
        }
    };

    private IrisApiInternal() { }

    @Override
    public int getMinorApiRevision() {
        return 0;
    }

    @Override
    public boolean isShaderPackInUse() {
        return Umbra.isShaderPackInUse();
    }

    @Override
    public boolean isRenderingShadowPass() {
        return UmbraShadowRenderer.isShadowPass();
    }

    @Override
    public Object openMainIrisScreenObj(Object parent) {
        GuiScreen parentScreen = parent instanceof GuiScreen
                ? (GuiScreen) parent
                : Minecraft.getMinecraft().currentScreen;
        return new ShaderPackSelectScreen(parentScreen);
    }

    @Override
    public String getMainScreenLanguageKey() {
        return "options.umbra.shaderPackSelection";
    }

    @Override
    public IrisApiConfig getConfig() {
        return this.config;
    }
}

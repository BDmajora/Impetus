package net.irisshaders.iris.api.v0;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.gui.modern.ShaderPackSelectScreen;

// The Iris v0 public API surface on top of Umbra; deliberately outside com.bdmajora because mods probe this exact package/class/method spelling by reflection, so the whole file is a naming contract (ShaderModBridge is our own such prober)
public class IrisApi {
    // The API is handed out as a singleton because callers cache a bound MethodHandle against this instance
    private static final IrisApi INSTANCE = new IrisApi();

    // The singleton other mods reach by reflection
    public static IrisApi getInstance() {
        return INSTANCE;
    }

    // "In use" means a pack is loaded and rendering, not merely that the mod is installed
    public boolean isShaderPackInUse() {
        return Umbra.isShaderPackInUse();
    }

    // True while the shadow map is being drawn; Distant Horizons reads this to keep its frustum, viewport and camera zoom off the shadow pass
    public boolean isRenderingShadowPass() {
        return UmbraShadowRenderer.isShadowPass();
    }

    // Builds the pack selection screen so a caller can push it without linking against our GUI classes; Object in and out to keep the API free of Minecraft types, and a non-GuiScreen parent falls back to whatever is on screen
    public Object openMainIrisScreenObj(Object parent) {
        GuiScreen parentScreen = parent instanceof GuiScreen
                ? (GuiScreen) parent
                : Minecraft.getMinecraft().currentScreen;
        return new ShaderPackSelectScreen(parentScreen);
    }
}

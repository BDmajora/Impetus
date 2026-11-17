package net.irisshaders.iris.api.v0;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.gui.modern.ShaderPackSelectScreen;

// Iris API answered by Impetus.
public class IrisApi {
    // Shared instance.
    private static final IrisApi INSTANCE = new IrisApi();

    // Returns the instance.
    public static IrisApi getInstance() {
        return INSTANCE;
    }

    // Returns whether shader pack in use.
    public boolean isShaderPackInUse() {
        return Umbra.isShaderPackInUse();
    }

    // Returns whether the shadow pass is drawing.
    public boolean isRenderingShadowPass() {
        return UmbraShadowRenderer.isShadowPass();
    }

    // Opens the shader pack screen.
    public Object openMainIrisScreenObj(Object parent) {
        GuiScreen parentScreen = parent instanceof GuiScreen
                ? (GuiScreen) parent
                : Minecraft.getMinecraft().currentScreen;
        return new ShaderPackSelectScreen(parentScreen);
    }
}

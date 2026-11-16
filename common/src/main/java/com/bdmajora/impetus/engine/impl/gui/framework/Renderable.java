package com.bdmajora.impetus.engine.impl.gui.framework;

// Element that draws itself.
public interface Renderable {
    // Draws the element.
    void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks);
}

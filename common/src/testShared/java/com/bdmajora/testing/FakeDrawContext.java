package com.bdmajora.testing;

import com.bdmajora.impetus.engine.impl.gui.framework.DrawContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;

import java.util.ArrayList;
import java.util.List;

// Records every draw call; strings measure six pixels per character so layout maths is checkable
public class FakeDrawContext implements DrawContext {
    public final List<String> calls = new ArrayList<>();
    public final List<String> strings = new ArrayList<>();
    public int scissorDepth;
    public int matrixDepth;

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        calls.add("fill(" + x1 + "," + y1 + "," + x2 + "," + y2 + "," + Integer.toHexString(color) + ")");
    }

    @Override
    public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
        String text = extractString(str);
        strings.add(text);
        calls.add("text(" + text + "," + x + "," + y + "," + Integer.toHexString(color) + "," + shadow + ")");
        return x + getStringWidth(str);
    }

    @Override
    public void blitWholeImage(String icon, int x, int y, int width, int height) {
        calls.add("blit(" + icon + "," + x + "," + y + "," + width + "," + height + ")");
    }

    @Override
    public void pushMatrix() {
        matrixDepth++;
        calls.add("push");
    }

    @Override
    public void translate(double x, double y, double z) {
        calls.add("translate(" + x + "," + y + "," + z + ")");
    }

    @Override
    public void popMatrix() {
        matrixDepth--;
        calls.add("pop");
    }

    @Override
    public void enableScissor(int x1, int y1, int x2, int y2) {
        scissorDepth++;
        calls.add("scissor(" + x1 + "," + y1 + "," + x2 + "," + y2 + ")");
    }

    @Override
    public void disableScissor() {
        scissorDepth--;
        calls.add("unscissor");
    }

    @Override
    public int getStringWidth(TextComponent component) {
        return extractString(component).length() * 6;
    }

    @Override
    public String substrByWidth(String str, int maxWidth) {
        return str.substring(0, Math.min(str.length(), Math.max(0, maxWidth / 6)));
    }

    @Override
    public List<TextComponent> split(TextComponent component, int maxWidth) {
        List<TextComponent> lines = new ArrayList<>();
        String text = extractString(component);
        int perLine = Math.max(1, maxWidth / 6);
        for (int i = 0; i < text.length(); i += perLine) {
            lines.add(TextComponent.literal(text.substring(i, Math.min(text.length(), i + perLine))));
        }
        if (lines.isEmpty()) {
            lines.add(TextComponent.literal(""));
        }
        return lines;
    }

    // Literal text, translation keys and styled wrappers flatten to their plain text
    @Override
    public String extractString(TextComponent component) {
        if (component instanceof TextComponent.Literal literal) {
            return literal.text();
        }
        if (component instanceof TextComponent.Styled styled) {
            return extractString(styled.inner());
        }
        if (component instanceof TextComponent.Translatable translatable) {
            return translatable.keys().getFirst() + (translatable.args().isEmpty() ? "" : translatable.args().toString());
        }
        return component.toString();
    }

    @Override
    public int lineHeight() {
        return 9;
    }

    public boolean drew(String fragment) {
        return calls.stream().anyMatch(c -> c.contains(fragment));
    }
}

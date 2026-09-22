package com.bdmajora.impetus.impl.gui;

import net.minecraft.client.resources.I18n;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

// Enumerates real fullscreen video modes from LWJGL; index 0 is always "Current" (desktop resolution), 1..N are distinct modes sorted by resolution
public final class FullscreenResolutions {
    private static List<DisplayMode> modes;

    private FullscreenResolutions() {
    }

    // Fullscreen-capable display modes, sorted and deduplicated by size
    private static List<DisplayMode> modes() {
        if (modes == null) {
            var seen = new LinkedHashSet<String>();
            var list = new ArrayList<DisplayMode>();

            try {
                DisplayMode[] available = Display.getAvailableDisplayModes();
                // Highest resolution / refresh first.
                Arrays.sort(available, (a, b) -> {
                    int byArea = Integer.compare(b.getWidth() * b.getHeight(), a.getWidth() * a.getHeight());
                    return byArea != 0 ? byArea : Integer.compare(b.getFrequency(), a.getFrequency());
                });

                for (DisplayMode mode : available) {
                    if (!mode.isFullscreenCapable()) {
                        continue;
                    }
                    // Collapse duplicate resolutions that differ only by bit depth/refresh.
                    if (seen.add(mode.getWidth() + "x" + mode.getHeight())) {
                        list.add(mode);
                    }
                }
            } catch (LWJGLException e) {
                // Leave the list empty; only "Current" will be offered.
            }

            modes = list;
        }

        return modes;
    }

    // number of selectable entries, including "Current" at index 0
    public static int count() {
        return modes().size() + 1;
    }

    // WxH text for the cycler; index zero is the desktop resolution
    public static String label(int index) {
        if (index <= 0 || index > modes().size()) {
            return I18n.format("impetus.options.fullscreen_resolution.current");
        }

        DisplayMode mode = modes().get(index - 1);
        return mode.getWidth() + "x" + mode.getHeight();
    }

    // applies the mode only if already fullscreen; otherwise it's just recorded for the next fullscreen switch
    public static void apply(int index) {
        if (index <= 0 || index > modes().size()) {
            return;
        }

        try {
            if (Display.isFullscreen()) {
                Display.setDisplayModeAndFullscreen(modes().get(index - 1));
            }
        } catch (Throwable t) {
            // Never let a resolution change take the game down.
        }
    }
}

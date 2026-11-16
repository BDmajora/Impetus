package com.bdmajora.impetus.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import com.bdmajora.impetus.impl.platform.GameWindow;
import com.cleanroommc.client.sdl.video.DisplayMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

// List of the fullscreen display modes.
public final class FullscreenResolutions {
    // Display modes or null before the first read.
    private static List<DisplayMode> modes;

    // Prevents instantiation
    private FullscreenResolutions() {
    }

    // Returns the display modes and reads them on first use.
    private static List<DisplayMode> modes() {
        if (modes == null) {
            var seen = new LinkedHashSet<String>();
            var list = new ArrayList<DisplayMode>();

            try {
                DisplayMode[] available = GameWindow.fullscreenModes();
                Arrays.sort(available, (a, b) -> {
                    int byArea = Long.compare((long) b.width() * b.height(), (long) a.width() * a.height());
                    return byArea != 0 ? byArea : Float.compare(b.refreshRate(), a.refreshRate());
                });

                for (DisplayMode mode : available) {
                    if (seen.add(mode.width() + "x" + mode.height())) {
                        list.add(mode);
                    }
                }
            } catch (RuntimeException e) {
            }

            modes = list;
        }

        return modes;
    }

    // Returns the count.
    public static int count() {
        return modes().size() + 1;
    }

    // Returns the text of a display mode.
    public static String label(int index) {
        if (index <= 0 || index > modes().size()) {
            return I18n.format("impetus.options.fullscreen_resolution.current");
        }

        DisplayMode mode = modes().get(index - 1);
        return mode.width() + "x" + mode.height();
    }

    // Switches to the display mode at an index.
    public static void apply(int index) {
        try {
            DisplayMode mode = index > 0 && index <= modes().size() ? modes().get(index - 1) : GameWindow.desktopMode();
            if (mode != null && GameWindow.setFullscreenMode(mode)) {
                Minecraft.getMinecraft().resize(mode.width(), mode.height());
            }
        } catch (Throwable t) {
        }
    }
}

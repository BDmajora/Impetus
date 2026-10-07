package com.bdmajora.impetus.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import com.bdmajora.impetus.impl.platform.GameWindow;
import org.lwjgl.opengl.DisplayMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

// Enumerates the fullscreen monitor's video modes through Cleanroom's window; index 0 is always "Current" (desktop resolution), 1..N are distinct modes sorted by resolution
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
                DisplayMode[] available = GameWindow.fullscreenModes();
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
            } catch (RuntimeException e) {
                // No monitor answered (headless, or the window is not up yet); only "Current" will be offered.
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

    // Switches an exclusive-fullscreen window to the chosen mode, "Current" being the desktop's; a windowed or borderless window just keeps the choice for its next exclusive switch
    public static void apply(int index) {
        try {
            DisplayMode mode = index > 0 && index <= modes().size() ? modes().get(index - 1) : GameWindow.desktopMode();
            // Vanilla only follows window resizes while windowed, so the new fullscreen size is handed to it here, as Cleanroom does on entering fullscreen
            if (mode != null && GameWindow.setFullscreenMode(mode)) {
                Minecraft.getMinecraft().resize(mode.getWidth(), mode.getHeight());
            }
        } catch (Throwable t) {
            // Never let a resolution change take the game down.
        }
    }
}

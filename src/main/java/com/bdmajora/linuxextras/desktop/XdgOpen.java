package com.bdmajora.linuxextras.desktop;

import com.bdmajora.linuxextras.LinuxExtras;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

// Links and folders go to xdg-open, the desktop's default handler, as modern Minecraft does on Linux; AWT's Desktop API is unsupported on most Linux desktops, which left only Copy to Clipboard, and inside Flatpak xdg-open reaches the host through the portal
public final class XdgOpen {
    // Swapped by tests so nothing is really launched
    static Starter starter = command -> new ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();

    private XdgOpen() {
    }

    @FunctionalInterface
    interface Starter {
        Process start(List<String> command) throws IOException;
    }

    // True once xdg-open is running; false leaves the caller's own path to run and report, as when the switch is off or xdg-open is missing
    public static boolean open(String target) {
        if (!LinuxExtras.options().xdgOpen) {
            return false;
        }
        try {
            // One argument, never a shell line, so nothing in the link is interpreted
            Process process = starter.start(Arrays.asList("xdg-open", target));
            process.getOutputStream().close();
            return true;
        } catch (IOException e) {
            LinuxExtras.LOGGER.warn("Couldn't hand {} to xdg-open: {}", target, e.toString());
            return false;
        }
    }
}

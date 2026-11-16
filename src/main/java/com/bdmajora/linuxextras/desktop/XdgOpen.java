package com.bdmajora.linuxextras.desktop;

import com.bdmajora.linuxextras.LinuxExtras;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

// Opener of files and links with the desktop handler.
public final class XdgOpen {
    // Process starter.
    static Starter starter = command -> new ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();

    // Prevents instantiation
    private XdgOpen() {
    }

    // Contract of the starter.
    @FunctionalInterface
    interface Starter {
        // Starts a command.
        Process start(List<String> command) throws IOException;
    }

    // Opens a target and returns whether the handler started.
    public static boolean open(String target) {
        if (target == null || target.isBlank() || target.startsWith("-") || !LinuxExtras.options().xdgOpen) {
            return false;
        }
        try {
            Process process = starter.start(Arrays.asList("xdg-open", target));
            process.getOutputStream().close();
            return true;
        } catch (IOException e) {
            LinuxExtras.LOGGER.warn("Couldn't hand {} to xdg-open: {}", target, e.toString());
            return false;
        }
    }
}

package com.bdmajora.impetus.impl.compat;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

// Scanner of the active resource packs for features.
public final class ResourcePackScanner {
    // Logger of this class.
    private static final Logger LOGGER = ImpetusVintage.logger();
    // Ticks between rescans.
    private static final int RESCAN_INTERVAL_TICKS = 100;

    // Core shader programs looked for.
    private static final String[] PROGRAM_NAMES = {
            "antialias", "bits", "blit", "blobs", "blobs2", "blur", "bumpy", "color_convolve",
            "deconverge", "downscale", "entity_outline", "entity_sobel", "flip", "fxaa", "invert",
            "notch", "ntsc_decode", "ntsc_encode", "outline", "outline_combine", "outline_soft",
            "outline_watercolor", "overlay", "phosphor", "rotscale", "scan_pincushion", "sobel",
            "spider", "spiderclip", "wobble"
    };

    // Post shaders looked for.
    private static final String[] POST_NAMES = {
            "antialias", "art", "bits", "blobs", "blobs2", "blur", "bumpy", "color_convolve",
            "creeper", "deconverge", "desaturate", "entity_outline", "flip", "fxaa", "green",
            "invert", "notch", "ntsc", "outline", "pencil", "phosphor", "scan_pincushion",
            "sobel", "spider", "wobble"
    };

    // Ticks left before the next scan.
    private static int ticksUntilScan;
    // Signature of the last scanned pack list.
    private static String lastPackSignature = "";

    // Prevents instantiation
    private ResourcePackScanner() {
    }

    // Rescans when the active packs change.
    public static void tick(Minecraft client) {
        if (ticksUntilScan-- > 0) {
            return;
        }

        ticksUntilScan = RESCAN_INTERVAL_TICKS;
        scanIfChanged(client);
    }

    // Scans when the pack list changed.
    public static void scanIfChanged(Minecraft client) {
        if (client == null || client.getResourcePackRepository() == null) {
            return;
        }

        if (ImpetusVintage.options().advanced.disableIncompatibleModWarnings) {
            return;
        }

        List<ResourcePackRepository.Entry> activePacks = new ArrayList<>(client.getResourcePackRepository().getRepositoryEntries());

        String signature = activePacks.stream()
                .map(ResourcePackRepository.Entry::getResourcePackName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("|"));

        if (signature.equals(lastPackSignature)) {
            return;
        }

        lastPackSignature = signature;
        scan(activePacks);
    }

    // Scans the active packs.
    private static void scan(List<ResourcePackRepository.Entry> activePacks) {
        List<String> riskyPacks = new ArrayList<>();

        for (ResourcePackRepository.Entry entry : activePacks) {
            IResourcePack pack = entry.getResourcePack();

            if (pack == null || !hasMinecraftDomain(pack)) {
                continue;
            }

            if (containsShaderAssets(pack)) {
                riskyPacks.add(entry.getResourcePackName());
            }
        }

        if (riskyPacks.isEmpty()) {
            return;
        }

        LOGGER.warn("The following resource packs contain Minecraft shader program assets that may conflict with Impetus/Umbra rendering: {}",
                String.join(", ", riskyPacks));

        List<String> lines = new ArrayList<>();
        int maxShown = Math.min(riskyPacks.size(), 3);

        for (int i = 0; i < maxShown; i++) {
            lines.add("- " + riskyPacks.get(i));
        }

        if (riskyPacks.size() > maxShown) {
            lines.add("...and " + (riskyPacks.size() - maxShown) + " more");
        }

        lines.add("Check the game log for details.");

        ImpetusNotifications.warn("Resource pack compatibility", lines.toArray(new String[0]));
    }

    // Returns whether it contains the shader assets.
    private static boolean containsShaderAssets(IResourcePack pack) {
        for (String name : PROGRAM_NAMES) {
            if (exists(pack, "shaders/program/" + name + ".json")
                    || exists(pack, "shaders/program/" + name + ".vsh")
                    || exists(pack, "shaders/program/" + name + ".fsh")) {
                return true;
            }
        }

        for (String name : POST_NAMES) {
            if (exists(pack, "shaders/post/" + name + ".json")) {
                return true;
            }
        }

        return false;
    }

    // Returns whether a pack has the minecraft domain.
    private static boolean hasMinecraftDomain(IResourcePack pack) {
        try {
            return pack.getResourceDomains().contains("minecraft");
        } catch (RuntimeException e) {
            return false;
        }
    }

    // Returns whether a pack has a file.
    private static boolean exists(IResourcePack pack, String path) {
        try {
            return pack.resourceExists(new ResourceLocation("minecraft", path.toLowerCase(Locale.ROOT)));
        } catch (RuntimeException e) {
            return false;
        }
    }
}

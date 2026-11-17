package com.bdmajora.extras.mixin.loading;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ScreenshotEvent;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;

// Mixin that changes ScreenShotHelper.
@Mixin(ScreenShotHelper.class)
public abstract class ScreenShotHelperMixin {
    // Reference to the field logger of the target.
    @Shadow
    @Final
    private static Logger LOGGER;

    // Reference to the field pixel buffer of the target.
    @Shadow
    private static IntBuffer pixelBuffer;

    // Reference to the field pixel values of the target.
    @Shadow
    private static int[] pixelValues;

    // Reference to the method create screenshot of the target.
    @Shadow
    public static BufferedImage createScreenshot(int width, int height, Framebuffer framebuffer) {
        throw new AssertionError();
    }

    // Reference to the method get timestamped png file for directory of the target.
    @Shadow
    private static File getTimestampedPNGFileForDirectory(File directory) {
        throw new AssertionError();
    }

    // Runs before every return of createScreenshot.
    @Inject(method = "createScreenshot", at = @At("RETURN"))
    private static void impetus$releaseReadbackBuffers(int width, int height, Framebuffer framebuffer, CallbackInfoReturnable<BufferedImage> cir) {
        if (Extras.options().loading.releaseScreenshotBuffers) {
            pixelBuffer = null;
            pixelValues = null;
        }
    }

    // Runs at the start of saveScreenshot and can cancel it.
    @Inject(method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/shader/Framebuffer;)Lnet/minecraft/util/text/ITextComponent;", at = @At("HEAD"), cancellable = true)
    private static void impetus$saveAsync(File gameDirectory, @Nullable String screenshotName, int width, int height, Framebuffer framebuffer, CallbackInfoReturnable<ITextComponent> cir) {
        ExtrasConfig.LoadingSettings settings = Extras.options().loading;
        if (!settings.asyncScreenshots) {
            return;
        }
        try {
            File directory = new File(gameDirectory, "screenshots");
            directory.mkdir();
            BufferedImage image = createScreenshot(width, height, framebuffer);
            File target = screenshotName == null ? getTimestampedPNGFileForDirectory(directory) : new File(directory, screenshotName);
            target = target.getCanonicalFile();
            ScreenshotEvent event = ForgeHooksClient.onScreenshot(image, target);
            if (event.isCanceled()) {
                cir.setReturnValue(event.getCancelMessage());
                return;
            }
            final File file = event.getScreenshotFile();
            // Claims the file name before writing.
            file.createNewFile();
            Thread.ofVirtual().name("Impetus Screenshot Writer").start(() -> {
                // Writes the image into the claimed file in place.
                try (RandomAccessFile out = new RandomAccessFile(file, "rw")) {
                    out.setLength(0L);
                    try (ImageOutputStream stream = new FileImageOutputStream(out)) {
                        ImageIO.write(image, "png", stream);
                    }
                } catch (Exception e) {
                    if (file.length() == 0L) {
                        file.delete();
                    }
                    LOGGER.warn("Couldn't save screenshot", e);
                    Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().ingameGUI.getChatGUI()
                            .printChatMessage(new TextComponentTranslation("screenshot.failure", e.getMessage())));
                }
            });
            ITextComponent link = new TextComponentString(file.getName());
            link.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath()));
            link.getStyle().setUnderlined(true);
            cir.setReturnValue(event.getResultMessage() != null ? event.getResultMessage() : new TextComponentTranslation("screenshot.success", link));
        } catch (Exception e) {
            LOGGER.warn("Couldn't save screenshot", e);
            cir.setReturnValue(new TextComponentTranslation("screenshot.failure", e.getMessage()));
        }
    }
}

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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;

// Two things about F2 (Chibi's releaseScreenshotCache and asyncScreenshot): the pixel readback buffers, two copies of the framebuffer, are kept as statics forever after the first screenshot, and the PNG encode runs on the render thread, a visible hitch at high resolutions. The buffers are dropped once the image is built, and the encode moves to a background thread while the chat line is posted immediately
@Mixin(ScreenShotHelper.class)
public abstract class ScreenShotHelperMixin {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    private static IntBuffer pixelBuffer;

    @Shadow
    private static int[] pixelValues;

    @Shadow
    public static BufferedImage createScreenshot(int width, int height, Framebuffer framebuffer) {
        throw new AssertionError();
    }

    @Shadow
    private static File getTimestampedPNGFileForDirectory(File directory) {
        throw new AssertionError();
    }

    @Inject(method = "createScreenshot", at = @At("RETURN"))
    private static void impetus$releaseReadbackBuffers(int width, int height, Framebuffer framebuffer, CallbackInfoReturnable<BufferedImage> cir) {
        if (Extras.options().loading.releaseScreenshotBuffers) {
            pixelBuffer = null;
            pixelValues = null;
        }
    }

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
            Thread writer = new Thread(() -> {
                try {
                    ImageIO.write(image, "png", file);
                } catch (Exception e) {
                    LOGGER.warn("Couldn't save screenshot", e);
                    Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().ingameGUI.getChatGUI()
                            .printChatMessage(new TextComponentTranslation("screenshot.failure", e.getMessage())));
                }
            }, "Impetus Screenshot Writer");
            writer.setDaemon(true);
            writer.start();
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

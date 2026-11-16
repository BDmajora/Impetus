package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.pipeline.CustomImageManager;
import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTarget;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL15;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Bindings of the pack images to image units.
public final class ImageUnits {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Shadow color indices.
    private static final int SHADOW_COLOR_INDICES = 8;

    // Render target units.
    private final int[] renderTargetUnits = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    // Shadow color units.
    private final int[] shadowColorUnits = new int[SHADOW_COLOR_INDICES];
    // Render target count.
    private int renderTargetCount;
    // Shadow color count.
    private int shadowColorCount;

    // Creates an image units with default state.
    public ImageUnits() {
        Arrays.fill(this.renderTargetUnits, -1);
        Arrays.fill(this.shadowColorUnits, -1);
    }

    // Assigns image units to the referenced render targets.
    public void allocateRenderTargets(BitSet referenced, CustomImageManager images, UmbraRenderTargets targets) {
        int unit = images.getNextAvailableImageUnit();
        int limit = images.getHardwareImageUnits();
        for (int index = referenced.nextSetBit(0); index >= 0 && index < this.renderTargetUnits.length;
             index = referenced.nextSetBit(index + 1)) {
            if (unit >= limit) {
                LOGGER.error("[Umbra] Out of image units for colorimg{} (max {}); ignoring it", index, limit);
                continue;
            }
            targets.getOrCreate(index);
            this.renderTargetUnits[index] = unit++;
            this.renderTargetCount++;
        }
    }

    // Assigns image units to the referenced shadow colors.
    public void allocateShadowColors(BitSet referenced, CustomImageManager images, UmbraShadowRenderer shadow) {
        if (shadow == null) {
            return;
        }
        int unit = images.getNextAvailableImageUnit() + this.renderTargetCount;
        int limit = images.getHardwareImageUnits();
        for (int index = referenced.nextSetBit(0); index >= 0 && index < SHADOW_COLOR_INDICES;
             index = referenced.nextSetBit(index + 1)) {
            if (shadow.getColorTextureId(index) == 0) {
                continue;
            }
            if (unit >= limit) {
                LOGGER.error("[Umbra] Out of image units for shadowcolorimg{} (max {}); ignoring it", index, limit);
                continue;
            }
            this.shadowColorUnits[index] = unit++;
            this.shadowColorCount++;
        }
    }

    // Binds the custom image overrides.
    public void addOverrides(Map<String, CustomTextureManager.Override> into) {
        for (int i = 0; i < this.renderTargetUnits.length; i++) {
            if (this.renderTargetUnits[i] >= 0) {
                into.put("colorimg" + i, new CustomTextureManager.Override(this.renderTargetUnits[i], -1));
            }
        }
        for (int i = 0; i < SHADOW_COLOR_INDICES; i++) {
            if (this.shadowColorUnits[i] >= 0) {
                into.put("shadowcolorimg" + i, new CustomTextureManager.Override(this.shadowColorUnits[i], -1));
            }
        }
    }

    // Returns whether empty.
    public boolean isEmpty() {
        return this.renderTargetCount == 0 && this.shadowColorCount == 0;
    }

    // Binds every image for the current flip state.
    public void bind(BitSet flipsBefore, UmbraRenderTargets targets, UmbraShadowRenderer shadow) {
        if (this.renderTargetCount > 0) {
            for (int i = 0; i < this.renderTargetUnits.length; i++) {
                UmbraRenderTarget target = this.renderTargetUnits[i] < 0 ? null : targets.get(i);
                if (target == null) {
                    continue;
                }
                int texture = flipsBefore.get(i) ? target.getAltTexture() : target.getMainTexture();
                LWJGL.glBindImageTexture(this.renderTargetUnits[i], texture, 0, false, 0, GL15.GL_READ_WRITE,
                        target.getInternalFormat().getInternalFormat());
            }
        }
        if (this.shadowColorCount > 0 && shadow != null) {
            for (int i = 0; i < SHADOW_COLOR_INDICES; i++) {
                if (this.shadowColorUnits[i] >= 0) {
                    LWJGL.glBindImageTexture(this.shadowColorUnits[i], shadow.getColorTextureId(i), 0, false, 0,
                            GL15.GL_READ_WRITE, shadow.getColorInternalFormat(i));
                }
            }
        }
    }
}

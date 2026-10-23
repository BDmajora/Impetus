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

// Image units for colour targets written through the image API (colorimgN, Umbra's addRenderTargetImages) and for the shadow colour attachments (shadowcolorimgN), above the image.<name> units in one ascending allocation; Umbra assigns per program, but with fixed units one global unit per target is equivalent since the binding is re-established per pass
public final class ImageUnits {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    private static final int SHADOW_COLOR_INDICES = 8;

    // Image unit per colortex / per shadowcolor, -1 when unreferenced or out of units
    private final int[] renderTargetUnits = new int[UmbraRenderTargets.MAX_COLOR_BUFFERS];
    private final int[] shadowColorUnits = new int[SHADOW_COLOR_INDICES];
    private int renderTargetCount;
    private int shadowColorCount;

    public ImageUnits() {
        Arrays.fill(this.renderTargetUnits, -1);
        Arrays.fill(this.shadowColorUnits, -1);
    }

    // Reserves a unit per referenced colorimgN; Umbra createIfUnsure()s the target, since a buffer nothing samples but a compute writes still has to exist
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

    // Reserves units for shadowcolorimgN after the render-target images, only for buffers the shadow renderer actually owns
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

    // The image uniforms as sampler-assignment overrides; image entries never deactivate (colorTarget -1)
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

    // Whether there is anything to bind
    public boolean isEmpty() {
        return this.renderTargetCount == 0 && this.shadowColorCount == 0;
    }

    // Binds every image for one pass: colour targets flip-aware like Umbra, so a compute writing colorimg4 hits the exact texture later programs sample as colortex4; shadow colours do not ping-pong. The two halves are independent, a pack using only shadowcolorimg used to get no binding at all
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

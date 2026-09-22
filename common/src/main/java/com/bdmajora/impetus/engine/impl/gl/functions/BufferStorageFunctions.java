package com.bdmajora.impetus.engine.impl.gl.functions;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferStorageFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.lwjgl.GLExtension;

public enum BufferStorageFunctions {
    NONE {
        // Implementation for this GL level
        @Override
        public void createBufferStorage(GlBufferTarget target, long length, EnumBitField<GlBufferStorageFlags> flags) {
            throw new UnsupportedOperationException();
        }
    },
    // GL 4.4 core or ARB_buffer_storage; the binding resolves the same entry point either way
    CORE {
        @Override
        public void createBufferStorage(GlBufferTarget target, long length, EnumBitField<GlBufferStorageFlags> flags) {
            LWJGL.glBufferStorage(target.getTargetParameter(), length, flags.getBitField());
        }
    };

    // Core 4.4 or the ARB extension, else none
    public static BufferStorageFunctions pickBest(RenderDevice device) {
        return LWJGL.isOpenGLVersionSupported(4, 4) || LWJGL.isExtensionSupported(GLExtension.ARB_buffer_storage) ? CORE : NONE;
    }

    public abstract void createBufferStorage(GlBufferTarget target, long length, EnumBitField<GlBufferStorageFlags> flags);
}

package com.bdmajora.impetus.shaders;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Logger of the shaders.
public class ImpetusShaders {
    // Logger of the shader code.
    public static final Logger logger = LogManager.getLogger();

    // Returns the logger.
    public static Logger logger() {
        return logger;
    }
}

package com.seibel.distanthorizons.core.dependencyInjection;

import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModAccessor;
import com.seibel.distanthorizons.coreapi.DependencyInjection.DependencyInjector;

// Stand-in for Distant Horizons' accessor injector.
public class ModAccessorInjector extends DependencyInjector<IModAccessor> {
    public static final ModAccessorInjector INSTANCE = new ModAccessorInjector();

    public ModAccessorInjector() {
        super(IModAccessor.class, false);
    }
}

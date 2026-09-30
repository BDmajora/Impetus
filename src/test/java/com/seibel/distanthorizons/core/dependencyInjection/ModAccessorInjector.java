package com.seibel.distanthorizons.core.dependencyInjection;

import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModAccessor;
import com.seibel.distanthorizons.coreapi.DependencyInjection.DependencyInjector;

// Stands in for Distant Horizons' internal mod accessor injector; the binding rules are the API jar's real DependencyInjector
public class ModAccessorInjector extends DependencyInjector<IModAccessor> {
    public static final ModAccessorInjector INSTANCE = new ModAccessorInjector();

    public ModAccessorInjector() {
        super(IModAccessor.class, false);
    }
}

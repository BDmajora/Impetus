package com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor;

import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

// Stands in for Distant Horizons' internal root of every mod accessor
public interface IModAccessor extends IBindable {
    String getModName();
}

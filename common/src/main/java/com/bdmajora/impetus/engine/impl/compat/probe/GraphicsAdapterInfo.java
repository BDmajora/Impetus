package com.bdmajora.impetus.engine.impl.compat.probe;

// Display adapter as the operating system reports it
public record GraphicsAdapterInfo(
        // Vendor classified from the PCI vendor id or the adapter name
        GraphicsVendor vendor,
        // Name of the adapter or its PCI vendor and device ids
        String name,
        // Driver version or kernel driver name with an empty string when the probe finds neither
        String driverVersion) {
}

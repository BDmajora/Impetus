package slimeknights.tconstruct.library.client;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

// Test stand-in for TConstruct's texture creator.
@SuppressWarnings("deprecation")
public class CustomTextureCreator implements IResourceManagerReloadListener {
    public int reloads;

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        this.reloads++;
    }
}

package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.coarctatio.Coarctatio;
import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.ItemModelMesherForgeAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.ItemModelMesherForge;
import net.minecraftforge.fml.client.FMLClientHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// Bakes every registered item model on a low-priority thread after a reload and pins the results, so opening an inventory does not bake on the render thread; blocks stay on demand since only what is in view is ever needed
public final class ItemModelPrebake extends Thread {
    private static volatile ItemModelPrebake running;

    private final List<ModelResourceLocation> locations;
    private final BakedModelProvider provider;
    private volatile boolean stop;

    private ItemModelPrebake(List<ModelResourceLocation> locations, BakedModelProvider provider) {
        this.locations = locations;
        this.provider = provider;
        this.setName("Coarctatio item model prebake");
        this.setPriority(Thread.MIN_PRIORITY);
        this.setDaemon(true);
    }

    // Skipped in-world, where a reload is a pack change and baking everything would compete with the game
    public static void restart() {
        stopAndJoin();
        BakedModelProvider provider = DynamicModels.baked();
        if (provider == null || !CoarctatioConfig.get().dynamicModelsPrebakeItems || FMLClientHandler.instance().hasError() || Minecraft.getMinecraft().world != null) {
            return;
        }
        ItemModelMesherForge mesher = (ItemModelMesherForge) Minecraft.getMinecraft().getRenderItem().getItemModelMesher();
        List<ModelResourceLocation> locations = new ArrayList<>();
        for (Int2ObjectMap<ModelResourceLocation> byMeta : ((ItemModelMesherForgeAccessor) mesher).coarctatio$locations().values()) {
            locations.addAll(byMeta.values());
        }
        ItemModelPrebake prebake = new ItemModelPrebake(locations, provider);
        running = prebake;
        prebake.start();
    }

    public static void stopAndJoin() {
        ItemModelPrebake prebake = running;
        if (prebake == null) {
            return;
        }
        prebake.stop = true;
        while (prebake.isAlive()) {
            try {
                prebake.join();
            } catch (InterruptedException ignored) {
                // Keep waiting; the reload must not race the baker
            }
        }
        running = null;
    }

    @Override
    public void run() {
        long start = System.nanoTime();
        int total = this.locations.size();
        int done = 0;
        long lastReport = start;
        Coarctatio.LOGGER.info("Baking {} item models in the background", total);
        for (ModelResourceLocation location : this.locations) {
            if (this.stop) {
                return;
            }
            try {
                IBakedModel model = this.provider.getObject(location);
                if (model != null) {
                    this.provider.putObject(location, model);
                }
            } catch (Throwable e) {
                Coarctatio.LOGGER.error("Error baking {}: {}", location, e.toString());
            }
            done++;
            if (done % 10 == 0 && System.nanoTime() - lastReport >= TimeUnit.SECONDS.toNanos(5)) {
                lastReport = System.nanoTime();
                Coarctatio.LOGGER.info("Item model prebake at {}%", done * 100 / total);
            }
        }
        Coarctatio.LOGGER.info("Item model prebake finished in {} ms", (System.nanoTime() - start) / 1_000_000);
    }
}

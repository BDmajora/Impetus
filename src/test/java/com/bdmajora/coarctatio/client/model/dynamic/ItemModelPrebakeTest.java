package com.bdmajora.coarctatio.client.model.dynamic;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.mixin.client.model.dynamic.ItemModelMesherForgeAccessor;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.init.Items;
import net.minecraftforge.client.ItemModelMesherForge;
import net.minecraftforge.client.model.IModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemModelPrebakeTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @AfterEach
    void stopBaking() {
        ItemModelPrebake.stopAndJoin();
        Mixins.set(DynamicModels.class, "baked", null);
        Mixins.set(CoarctatioConfig.class, "instance", null);
    }

    private static void prebake(boolean enabled) {
        CoarctatioConfig config = Mc.uninitialized(CoarctatioConfig.class);
        config.dynamicModelsPrebakeItems = enabled;
        Mixins.set(CoarctatioConfig.class, "instance", config);
    }

    private static ItemModelPrebake running() {
        return Mixins.get(ItemModelPrebake.class, "running");
    }

    @Test
    void everyRegisteredItemModelIsBakedAndPinnedInTheBackground() throws Exception {
        Minecraft client = Mc.client();
        UnbakedModelProvider unbaked = new UnbakedModelProvider(new LinkedHashSet<>());
        BakedModelProvider baked = new BakedModelProvider(unbaked);
        ModelResourceLocation diamond = new ModelResourceLocation("minecraft:diamond", "inventory");
        IModel model = mock(IModel.class);
        when(model.bake(any(), any(), any())).thenReturn(mock(IBakedModel.class));
        unbaked.putObject(diamond, model);

        // Nothing to bake before the first dynamic reload, or with the prebake switched off
        prebake(true);
        ItemModelPrebake.restart();
        assertNull(running());
        Mixins.set(DynamicModels.class, "baked", baked);
        prebake(false);
        ItemModelPrebake.restart();
        assertNull(running());
        // In a world a reload is a pack change, and baking everything would compete with the game
        prebake(true);
        Mixins.set(client, "world", Mc.uninitialized(WorldClient.class));
        ItemModelPrebake.restart();
        assertNull(running());
        Mixins.set(client, "world", null);

        RenderItem renderItem = mock(RenderItem.class);
        ItemModelMesherForge mesher = Mc.mock(ItemModelMesherForge.class, ItemModelMesherForgeAccessor.class);
        when(client.getRenderItem()).thenReturn(renderItem);
        when(renderItem.getItemModelMesher()).thenReturn(mesher);
        Int2ObjectMap<ModelResourceLocation> byMeta = new Int2ObjectOpenHashMap<>();
        byMeta.put(0, diamond);
        when(((ItemModelMesherForgeAccessor) mesher).coarctatio$locations()).thenReturn(Map.of(Items.DIAMOND.delegate, byMeta));

        ItemModelPrebake.restart();
        ItemModelPrebake thread = running();
        assertNotNull(thread);
        thread.join();
        // Pinned, so the next lookup is answered without baking again
        assertEquals(1, baked.permanentCount());
        assertNotNull(baked.getObject(diamond));

        // Stopping waits for the baker and forgets it
        ItemModelPrebake.stopAndJoin();
        assertNull(running());
    }
}

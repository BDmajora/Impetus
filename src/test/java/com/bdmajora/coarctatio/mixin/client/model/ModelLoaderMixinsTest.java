package com.bdmajora.coarctatio.mixin.client.model;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.ShadowStubs;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.animation.ModelBlockAnimation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelLoaderMixinsTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        // ModelLoaderRegistry registers a reload listener as it initialises
        net.minecraft.client.Minecraft client = Mc.client();
        Mockito.when(client.getResourceManager())
                .thenReturn(mock(net.minecraft.client.resources.IReloadableResourceManager.class));
    }

    @AfterEach
    void forgetShadowAnswers() {
        ShadowStubs.clear();
    }

    @Test
    void aModelThatFailedToLoadGetsThePlainMissingModel() {
        IModel plain = mock(IModel.class);
        ShadowStubs.on(null, "getMissingModel", args -> plain);

        CallbackInfoReturnable<IModel> cir = Mixins.cir();
        Mixins.call(ModelLoaderRegistryMissingMixin.class, "coarctatio$plainMissingModel",
                new ResourceLocation("minecraft:broken"), new RuntimeException("no such model"), cir);
        assertTrue(cir.isCancelled());
        assertSame(plain, cir.getReturnValue());

        // The shadow's own body is a placeholder for the real static on the target class
        assertThrows(AssertionError.class, ModelLoaderRegistryMissingMixin::getMissingModel);
        assertNotNull(Mixins.instance(ModelLoaderRegistryMissingMixin.class));
    }

}

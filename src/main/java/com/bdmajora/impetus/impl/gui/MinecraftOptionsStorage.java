package com.bdmajora.impetus.impl.gui;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionStorage;

// Storage of the vanilla game settings.
public class MinecraftOptionsStorage implements OptionStorage<GameSettings> {
    // Client that owns the settings.
    private final Minecraft client;

    // Creates a minecraft options storage with default state.
    public MinecraftOptionsStorage() {
        this.client = Minecraft.getMinecraft();
    }

    // Returns the data.
    @Override
    public GameSettings getData() {
        return this.client.gameSettings;
    }

    // Saves the game settings.
    @Override
    public void save(Set<OptionFlag> flags) {
        this.getData().saveOptions();
    }
}
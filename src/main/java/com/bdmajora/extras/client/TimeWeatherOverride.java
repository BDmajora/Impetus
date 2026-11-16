package com.bdmajora.extras.client;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import net.minecraft.network.play.server.SPacketChangeGameState;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;

// Forces the time and weather in creative worlds.
public final class TimeWeatherOverride {
    // Length of a day in ticks.
    private static final long DAY_LENGTH = 24000L;
    // First tick of the day.
    private static final long DAY_START = 1000L;
    // Last tick of the day.
    private static final long DAY_END = 11000L;
    // First tick of the night.
    private static final long NIGHT_START = 14000L;
    // Last tick of the night.
    private static final long NIGHT_END = 22000L;

    // Prevents instantiation
    private TimeWeatherOverride() {
    }

    // Applies the configured time and weather.
    public static void apply(WorldServer world) {
        ExtrasConfig.ExtraSettings settings = Extras.options().extra;

        if (settings.timeOverride != ExtrasConfig.TimeOverride.DEFAULT) {
            applyTime(world, settings.timeOverride);
        }

        if (settings.weatherOverride != ExtrasConfig.WeatherOverride.DEFAULT) {
            applyWeather(world, settings.weatherOverride);
        }
    }

    // Moves the time into the forced part of the day.
    private static void applyTime(WorldServer world, ExtrasConfig.TimeOverride override) {
        if (!isCreative(world)) {
            return;
        }

        long time = world.getWorldTime();
        long timeOfDay = Math.floorMod(time, DAY_LENGTH);

        switch (override) {
            case DAY -> {
                if (timeOfDay <= DAY_START) {
                    world.setWorldTime(time - timeOfDay + DAY_START + 1L);
                } else if (timeOfDay >= DAY_END) {
                    world.setWorldTime(time - timeOfDay + DAY_LENGTH + 1L);
                }
            }
            case NIGHT -> {
                if (timeOfDay <= NIGHT_START) {
                    world.setWorldTime(time - timeOfDay + NIGHT_START + 1L);
                } else if (timeOfDay >= NIGHT_END) {
                    world.setWorldTime(time - timeOfDay + DAY_LENGTH + NIGHT_START + 1L);
                }
            }
            default -> {
            }
        }
    }

    // Sets the forced weather and informs the players.
    private static void applyWeather(WorldServer world, ExtrasConfig.WeatherOverride override) {
        if (!isCreative(world)) {
            return;
        }

        WorldInfo info = world.getWorldInfo();
        boolean raining = override != ExtrasConfig.WeatherOverride.CLEAR;
        boolean thundering = override == ExtrasConfig.WeatherOverride.THUNDER;

        if (info.isRaining() == raining && info.isThundering() == thundering) {
            return;
        }

        info.setRaining(raining);
        info.setRainTime(0);
        info.setThundering(thundering);
        info.setThunderTime(0);
        world.setRainStrength(raining ? 1.0F : 0.0F);
        world.setThunderStrength(thundering ? 1.0F : 0.0F);

        if (world.getMinecraftServer() != null) {
            world.getMinecraftServer().getPlayerList()
                    .sendPacketToAllPlayers(new SPacketChangeGameState(raining ? 1 : 2, 0.0F));
            world.getMinecraftServer().getPlayerList()
                    .sendPacketToAllPlayers(new SPacketChangeGameState(7, raining ? 1.0F : 0.0F));
            world.getMinecraftServer().getPlayerList()
                    .sendPacketToAllPlayers(new SPacketChangeGameState(8, thundering ? 1.0F : 0.0F));
        }
    }

    // Returns whether the world uses creative mode.
    private static boolean isCreative(WorldServer world) {
        return world.getWorldInfo().getGameType() == GameType.CREATIVE;
    }
}

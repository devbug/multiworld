package xyz.nucleoid.fantasy;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.level.storage.LevelStorage;
import org.apache.commons.io.FileUtils;
import xyz.nucleoid.fantasy.mixin.MinecraftServerAccess;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class RuntimeWorldManager {
    private final MinecraftServer server;
    private final MinecraftServerAccess serverAccess;

    public Map<RegistryKey<World>, ServerWorld> worldss;
    
    RuntimeWorldManager(MinecraftServer server) {
        this.server = server;
        this.worldss = new HashMap<>();
        this.serverAccess = (MinecraftServerAccess) server;
    }

    RuntimeWorld add(RegistryKey<World> worldKey, RuntimeWorldConfig config, RuntimeWorld.Style style) {
        DimensionOptions options = config.createDimensionOptions(this.server);

        if (style == RuntimeWorld.Style.TEMPORARY) {
            ((FantasyDimensionOptions) (Object) options).fantasy$setSave(false);
        }

        SimpleRegistry<DimensionOptions> dimensionsRegistry = getDimensionsRegistry(this.server);
        boolean isFrozen = ((RemoveFromRegistry<?>) dimensionsRegistry).fantasy$isFrozen();
        ((RemoveFromRegistry<?>) dimensionsRegistry).fantasy$setFrozen(false);
        dimensionsRegistry.add(RegistryKey.of(RegistryKeys.DIMENSION, worldKey.getValue()), options, RegistryEntryInfo.DEFAULT);
        ((RemoveFromRegistry<?>) dimensionsRegistry).fantasy$setFrozen(isFrozen);

        RuntimeWorld world = new RuntimeWorld(this.server, worldKey, config, style);

        IMC imc = (IMC) this.server;
        imc.add_world(world.getRegistryKey(), world);

        if (FantasyInitializer.after_tick_start) {
        	worldss.put(world.getRegistryKey(), world);
        }

        // this.serverAccess.getWorlds().put(world.getRegistryKey(), world);
        // ServerWorldEvents.LOAD.invoker().onWorldLoad(this.server, world);
        // Avoid ticking here; the base server loop will tick the world shortly.

        return world;
    }

    void delete(ServerWorld world) {
        RegistryKey<World> dimensionKey = world.getRegistryKey();

        if (this.serverAccess.getWorlds().remove(dimensionKey, world)) {
            // ServerWorldEvents.UNLOAD.invoker().onWorldUnload(this.server, world);

            SimpleRegistry<DimensionOptions> dimensionsRegistry = getDimensionsRegistry(this.server);
            RemoveFromRegistry.remove(dimensionsRegistry, dimensionKey.getValue());

            LevelStorage.Session session = this.serverAccess.getSession();
            File worldDirectory = session.getWorldDirectory(dimensionKey).toFile();
            if (worldDirectory.exists()) {
                try {
                    FileUtils.deleteDirectory(worldDirectory);
                } catch (IOException e) {
                    Fantasy.LOGGER.warn("Failed to delete world directory", e);
                    try {
                        FileUtils.forceDeleteOnExit(worldDirectory);
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        // Keep internal map in sync
        this.worldss.remove(dimensionKey);
    }

    private static SimpleRegistry<DimensionOptions> getDimensionsRegistry(MinecraftServer server) {
        DynamicRegistryManager registryManager = server.getCombinedDynamicRegistries().getCombinedRegistryManager();
        return (SimpleRegistry<DimensionOptions>) registryManager.get(RegistryKeys.DIMENSION);
    }
}

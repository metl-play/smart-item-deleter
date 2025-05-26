package com.metl_group.smart_item_deleter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.level.ChunkPos;     // für chunk.getPos()
import net.minecraft.world.level.chunk.LevelChunk; // für getTickingChunk()
import net.minecraft.server.level.ServerPlayer;


// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(SmartItemDeleter.MOD_ID)
public class SmartItemDeleter {
    public static final String MOD_ID = "smartitemdeleter";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public SmartItemDeleter(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        //modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM SMART ITEM DELETER");
    }


    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    private static final int CHECK_INTERVAL = 20;
    //private static final int VANILLA_DESPAWN_TICKS = 6000;
    private int tickCounter = 0;
    private boolean isThresholdBreached = false;

    @SubscribeEvent
    public void onWorldTick(LevelTickEvent.Post event) {
    }

    private List<ItemEntity> getAllItemEntities(ServerLevel level) {
        List<ItemEntity> all = new ArrayList<>();

        int radius = level.getServer().getPlayerList().getViewDistance(); // oder fix: 10
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        for (ServerPlayer player : level.players()) {
            ChunkPos center = player.chunkPosition();

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                    LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
                    if (chunk != null) {
                        AABB area = new AABB(
                                pos.getMinBlockX(), minY, pos.getMinBlockZ(),
                                pos.getMaxBlockX(), maxY, pos.getMaxBlockZ()
                        );
                        all.addAll(level.getEntitiesOfClass(ItemEntity.class, area));
                    }
                }
            }
        }

        return all;
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;

        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        ServerLevel level = (ServerLevel) event.getLevel();
        List<ItemEntity> allItems = getAllItemEntities(level);

        int threshold = ModConfig.thresholdCached;

        if (allItems.size() <= threshold) {
            isThresholdBreached = false;
            return;
        }

        // Nur ausführen, wenn Threshold überschritten wurde und vorher nicht
        if (isThresholdBreached) return;
        isThresholdBreached = true;

        int targetCount = threshold / 2;

        // Sortiere: älteste Items zuerst
        allItems.sort((a, b) -> Integer.compare(b.getAge(), a.getAge()));

        int removed = 0;
        for (ItemEntity item : allItems) {
            if (allItems.size() - removed <= targetCount) break;
            if (!item.isRemoved()) {
                item.discard();
                removed++;
            }
        }

        LOGGER.info("SmartItemDeleter: removed {} ItemEntities (threshold exceeded: {} → {})",
                removed, allItems.size(), allItems.size() - removed);

        //Log information to Chat for Debugging
        /*
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(
                    Component.literal(
                            String.format(
                                    "SmartItemDeleter Debug – gefunden: %d Items, gelöscht: %d",
                                    allItems.size(),
                                    removed
                            )
                    )
            );
        }
        */
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
        }
    }
}

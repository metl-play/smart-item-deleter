package com.metl_group.smart_item_deleter;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


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
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM SMART ITEM DELETER");
    }

    private static final int CHECK_INTERVAL = 20;
    private int tickCounter = 0;
    private boolean isThresholdBreached = false;

    private static final Method CHUNKMAP_GET_CHUNKS;

    static {
        try {
            Class<?> cmClass = Class.forName("net.minecraft.server.level.ChunkMap");
            CHUNKMAP_GET_CHUNKS = cmClass.getDeclaredMethod("getChunks");
            CHUNKMAP_GET_CHUNKS.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize ChunkMap reflection", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Iterable<ChunkHolder> getLoadedChunkHolders(ServerLevel level) {
        try {
            Object chunkMap = level.getChunkSource()
                    .getClass()
                    .getField("chunkMap")
                    .get(level.getChunkSource());
            return (Iterable<ChunkHolder>) CHUNKMAP_GET_CHUNKS.invoke(chunkMap);
        } catch (Exception e) {
            LOGGER.error("cleanup: cannot access chunkMap", e);
            return Collections.emptyList();
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;
        if (++tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        ServerLevel level = (ServerLevel) event.getLevel();

        List<ItemEntity> allItems = new ArrayList<>();
        for (ServerLevel lvl : level.getServer().getAllLevels()) {
            for (ChunkHolder h : getLoadedChunkHolders(lvl)) {
                LevelChunk c = h.getTickingChunk();
                if (c == null) continue;
                ChunkPos pos = c.getPos();
                AABB box = new AABB(
                        pos.getMinBlockX(), lvl.getMinBuildHeight(), pos.getMinBlockZ(),
                        pos.getMaxBlockX(), lvl.getMaxBuildHeight(), pos.getMaxBlockZ()
                );
                allItems.addAll(lvl.getEntitiesOfClass(ItemEntity.class, box));
            }
        }
        //LOGGER.info("SID: scanned {} items across dimensions", allItems.size());

        int threshold = ModConfig.thresholdCached;
        if (allItems.size() <= threshold) {
            isThresholdBreached = false;
            return;
        }
        if (isThresholdBreached) return;
        isThresholdBreached = true;

        int keepPercent = ModConfig.keepPercentCached;
        int targetCount = (int) Math.ceil(threshold * (keepPercent / 100.0));

        allItems.sort((a, b) -> Integer.compare(b.getAge(), a.getAge()));
        int removed = 0;
        for (ItemEntity item : allItems) {
            if (allItems.size() - removed <= targetCount) break;
            item.discard();
            removed++;
        }

        LOGGER.info("SID: removed {} items, kept {}%", removed, keepPercent);

        if (ModConfig.debugCached) {
            Component msg = Component.literal(
                    String.format("SID Debug – found: %d, removed: %d", allItems.size(), removed)
            );
            level.players().forEach(p -> p.sendSystemMessage(msg));
        }
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
        }
    }
}
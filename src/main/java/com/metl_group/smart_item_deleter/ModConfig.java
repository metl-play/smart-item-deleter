package com.metl_group.smart_item_deleter;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = SmartItemDeleter.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModConfig {

    public static final ModConfigSpec SPEC;
    public static ModConfigSpec.IntValue THRESHOLD;

    public static int thresholdCached = 16000; // Default

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("smart_item_deleter");

        THRESHOLD = builder
                .comment("Maximale Anzahl von ItemEntities bevor gelöscht wird")
                .defineInRange("threshold", 16000, 100, 100000);

        builder.pop();
        SPEC = builder.build();
    }

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("SmartItemDeleter");

    @SubscribeEvent
    public static void onConfigLoaded(ModConfigEvent.Loading event) {
        if (!event.getConfig().getSpec().equals(ModConfig.SPEC)) return;

        thresholdCached = ModConfig.THRESHOLD.get(); // Jetzt ist es sicher!
        LOGGER.info("Threshold config loaded: {}", thresholdCached);
    }
}
package com.andria.lodestars;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ATTUNE_TO_PLAYER = BUILDER
            .comment("Whether the player or the lodestar itself attunes to lodestones.\nIf true, using any lodestar will teleport the player to the lodestone they last attuned to.\nIf false, using a lodestar will teleport the player to the last lodestone that specific lodestar was attuned to.")
            .define("attuneToPlayer", true);
    
    public static final ModConfigSpec.BooleanValue INFINITE_USES = BUILDER
            .comment("If true, lodestars have infinite durability.")
            .define("infiniteUses", true);
    
    public static final ModConfigSpec.IntValue LODESTAR_USE_COST = BUILDER
            .comment("How much durability is lost each time the lodestar is used.")
            .defineInRange("lodestarUseCost", 1, 0, 512);
    
    public static final ModConfigSpec.DoubleValue LODESTAR_USE_TIME = BUILDER
            .comment("How long you need to use the lodestar before teleporting, in seconds.")
            .defineInRange("lodestarUseTime", 1.5, 0.0, 999.0);
    
    public static final ModConfigSpec.DoubleValue LODESTAR_COOLDOWN = BUILDER
            .comment("Duration of the lodestar's cooldown after use, in seconds.")
            .defineInRange("lodestarCooldown", 30.0, 0.0, 999.0);
    
    static final ModConfigSpec SPEC = BUILDER.build();

    @SuppressWarnings("unused")
    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}

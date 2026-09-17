package com.andria.lodestars;

import java.util.function.Supplier;

import org.slf4j.Logger;

import com.andria.lodestars.common.registry.ModItems;
import com.mojang.logging.LogUtils;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(Lodestars.MODID)
public class Lodestars {
    public static final String MODID = "lodestars";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);
	public static final Supplier<AttachmentType<PlayerData>> PLAYER_DATA = ATTACHMENT_TYPES.register("player_data", () -> AttachmentType.serializable(() -> new PlayerData()).build());

    public Lodestars(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NeoForge.EVENT_BUS.register(this.getClass());
        modEventBus.addListener(this::register);

        ModItems.ITEMS.register(modEventBus); // Register items.
        modEventBus.addListener(this::addCreative); // Add Lodestar to creative menu.

        ATTACHMENT_TYPES.register(modEventBus); // Register attachment.
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Setting up Lodestars!");
    }

    // Adds the Lodestar to the tools and utilities tab.
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
        	event.accept(ModItems.LODESTAR.get());
        }
    }

    // Syncs player data on login.
    @SubscribeEvent
	public static void onPlayerLoggedInSyncPlayerData(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			if (player.hasData(PLAYER_DATA)) {
				player.getData(PLAYER_DATA).syncPlayerVariables(event.getEntity());
			}
    	}
	}
    
    // Syncs player data on respawn.
	@SubscribeEvent
	public static void onPlayerRespawnedSyncPlayerData(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			if (player.hasData(PLAYER_DATA)) {
				player.getData(PLAYER_DATA).syncPlayerVariables(event.getEntity());
			}
    	}
	}

    // Syncs player data on changing dimensions.
	@SubscribeEvent
	public static void onPlayerChangedDimensionSyncPlayerData(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			if (player.hasData(PLAYER_DATA)) {
				player.getData(PLAYER_DATA).syncPlayerVariables(event.getEntity());
			}
    	}
	}

    // Syncs player data on cloning.
    @SubscribeEvent
	public static void clonePlayer(PlayerEvent.Clone event) {
		PlayerData original = event.getOriginal().getData(PLAYER_DATA);
		PlayerData clone = new PlayerData();
		clone.lodestar_destination_x = original.lodestar_destination_x;
		clone.lodestar_destination_y = original.lodestar_destination_y;
		clone.lodestar_destination_z = original.lodestar_destination_z;
		clone.lodestar_destination_dimension = original.lodestar_destination_dimension;
		clone.lodestar_destination_exists = original.lodestar_destination_exists;
		if (!event.isWasDeath()) {
		}
		event.getEntity().setData(PLAYER_DATA, clone);
	}
    
    // Registers player data payload.
    public void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MODID);
        registrar.playBidirectional(
    		PlayerDataPayload.TYPE,
    		PlayerDataPayload.STREAM_CODEC,
    		new DirectionalPayloadHandler<>(
    			PlayerDataPayload::handleDataClient,
    			PlayerDataPayload::handleDataServer
    		)
        );
    }

    // Payload used for transferring player data between the server and the client.
    public record PlayerDataPayload(String uuid, int x, int y, int z, String dim, boolean exists) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PlayerDataPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "player_data"));
        public static final StreamCodec<ByteBuf, PlayerDataPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            PlayerDataPayload::uuid,
            ByteBufCodecs.VAR_INT,
            PlayerDataPayload::x,
            ByteBufCodecs.VAR_INT,
            PlayerDataPayload::y,
            ByteBufCodecs.VAR_INT,
            PlayerDataPayload::z,
            ByteBufCodecs.STRING_UTF8,
            PlayerDataPayload::dim,
            ByteBufCodecs.BOOL,
            PlayerDataPayload::exists,
            PlayerDataPayload::new
        );
        
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
        
        // Handles sending data over to the server player.
        public static void handleDataServer(final PlayerDataPayload data, final IPayloadContext context) {
        	if (context.flow() == PacketFlow.SERVERBOUND && data != null) {
        		context.enqueueWork(() -> {
        			PlayerData _data = context.player().getData(PLAYER_DATA);
        			_data.lodestar_destination_x = data.x;
        			_data.lodestar_destination_y = data.y;
        			_data.lodestar_destination_z = data.z;
        			_data.lodestar_destination_dimension = data.dim;
        			_data.lodestar_destination_exists = data.exists;
        		}).exceptionally(e -> {
        			context.connection().disconnect(Component.literal(e.getMessage()));
        			return null;
        		});
        	}
        }
        
        // Handles copying data from the server player to the client.
        public static void handleDataClient(final PlayerDataPayload data, final IPayloadContext context) {
        	if (context.flow() == PacketFlow.CLIENTBOUND && data != null) {
        		context.enqueueWork(() -> {
        			PlayerData _data = context.player().getData(PLAYER_DATA);
        			_data.lodestar_destination_x = data.x;
        			_data.lodestar_destination_y = data.y;
        			_data.lodestar_destination_z = data.z;
        			_data.lodestar_destination_dimension = data.dim;
        			_data.lodestar_destination_exists = data.exists;
        		}).exceptionally(e -> {
        			context.connection().disconnect(Component.literal(e.getMessage()));
        			return null;
        		});
        	}
        }
    }

    // Storage class for Lodestars player data.
    public static class PlayerData implements INBTSerializable<CompoundTag> {
    	public double lodestar_destination_x = 0;
    	public double lodestar_destination_y = 0;
    	public double lodestar_destination_z = 0;
    	public String lodestar_destination_dimension = "minecraft:overworld";
    	public boolean lodestar_destination_exists = false;
    	
    	@Override
    	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
    		CompoundTag nbt = new CompoundTag();
    		nbt.putDouble("lodestar_destination_x", lodestar_destination_x);
    		nbt.putDouble("lodestar_destination_y", lodestar_destination_y);
    		nbt.putDouble("lodestar_destination_z", lodestar_destination_z);
    		nbt.putString("lodestar_destination_dimension", lodestar_destination_dimension);
    		nbt.putBoolean("lodestar_destination_exists", lodestar_destination_exists);
    		return nbt;
    	}
    	
    	@Override
    	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
    		lodestar_destination_x = nbt.getDouble("lodestar_destination_x");
    		lodestar_destination_y = nbt.getDouble("lodestar_destination_y");
    		lodestar_destination_z = nbt.getDouble("lodestar_destination_z");
    		lodestar_destination_dimension = nbt.getString("lodestar_destination_dimension");
    		lodestar_destination_exists = nbt.getBoolean("lodestar_destination_exists");
    	}
    	
    	public void syncPlayerVariables(Entity entity) {
			if (entity instanceof ServerPlayer serverPlayer)
				PacketDistributor.sendToPlayer(serverPlayer, new PlayerDataPayload(entity.getUUID().toString(), (int) lodestar_destination_x, (int) lodestar_destination_y, (int) lodestar_destination_z, lodestar_destination_dimension, lodestar_destination_exists));
		}
    }
}

package com.andria.lodestars.common.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

import com.andria.lodestars.Config;
import com.andria.lodestars.Lodestars;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

public class LodestarItem extends Item{

	public LodestarItem() {
		super(new Item.Properties()
			.stacksTo(1)
			.durability(512)
			.fireResistant()
			.rarity(Rarity.RARE));
	}
	
    // Uses the bow drawing animation for the use animation.
	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}
	
    // Grabs the use time configuration from the configs.
	@Override
	public int getUseDuration(ItemStack itemstack, LivingEntity livingEntity) {
		return (int) (Config.LODESTAR_USE_TIME.get() * 20);
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public boolean isFoil(ItemStack stack) {
		Entity entity = Minecraft.getInstance().player;

		if (entity == null) {
			return false;
		}

		if (entity instanceof Player _plrCldCheck1 && _plrCldCheck1.getCooldowns().isOnCooldown(stack.getItem())) {
			return false;
		}

		return true;
	}
	
    // Tooltip text, shows the location of the last attuned lodestone or tells the player they haven't attuned to a lodestone if they haven't.
    // Todo: Instead of showing, "in another dimension", show "in [dimension name] at [location]"
	@Override
	@OnlyIn(Dist.CLIENT)
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> list, TooltipFlag flag) {
		super.appendHoverText(stack, context, list, flag);
		Entity entity = stack.getEntityRepresentation() != null ? stack.getEntityRepresentation() : Minecraft.getInstance().player;
		String hoverText = null;
		double destination_x = 0.0;
		double destination_y = 0.0;
		double destination_z = 0.0;
		boolean same_level = false;
		boolean has_destination = false;

        // Check the player's current dimension.
        String player_dimension = "";
        if (context.level() != null) {
            player_dimension = context.level().dimension().toString();
        }

		if (entity == null) {
			hoverText = "";
		}

		Lodestars.PlayerData _data = entity.getData(Lodestars.PLAYER_DATA);
		if (Config.ATTUNE_TO_PLAYER.get()) {
			has_destination = _data.lodestar_destination_exists;
		} else {
			has_destination = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("lodestar_destination_exists");
		}

		if (has_destination) {
			if (Config.ATTUNE_TO_PLAYER.get()) {
				if (_data.lodestar_destination_dimension.equals(player_dimension)) {
					same_level = true;
				}

				destination_x = _data.lodestar_destination_x;
				destination_y = _data.lodestar_destination_y;
				destination_z = _data.lodestar_destination_z;
			} else {
				if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("lodestar_destination_dimension").equals(player_dimension)) {
					same_level = true;
				}

				destination_x = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("lodestar_destination_x");
				destination_y = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("lodestar_destination_y");
				destination_z = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("lodestar_destination_z");
			}
			
			if (same_level) {
				hoverText = "Attuned to a lodestone at: (" + Math.round(destination_x) + ", " + Math.round(destination_y)
				+ ", " + Math.round(destination_z) + ")";
			} else {
				hoverText = "Attuned to a lodestone in another dimension.";
			}
            
		} else {
			if (Config.ATTUNE_TO_PLAYER.get()) {
				hoverText = "You are not attuned to a lodestone.";
			} else {
				hoverText = "This lodestar is not attuned to a lodestone.";
			}
		}

		if (hoverText != null) {
			for (String line : hoverText.split("\n")) {
				list.add(Component.literal(line));
			}
		}
	}
	
    // Teleports the player to their attuned lodestone if they have one, or to the lodestar's attuned lodestone if player attunement is set to false.=
	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack retval = super.finishUsingItem(stack, level, entity);
		if (entity == null) {
			return retval;
		}
        
		boolean has_destination = false;
		Lodestars.PlayerData _data = entity.getData(Lodestars.PLAYER_DATA);
		if (Config.ATTUNE_TO_PLAYER.get()) {
			has_destination = _data.lodestar_destination_exists;
		} else {
			has_destination = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("lodestar_destination_exists");
		}

		if (has_destination) {
			double destination_x = 0.0;
			double destination_y = 0.0;
			double destination_z = 0.0;

			if (entity instanceof ServerPlayer _serverPlayer && !_serverPlayer.level().isClientSide()) {
				ServerLevel destination_level = _serverPlayer.getServer().getLevel(Level.OVERWORLD);
				
				// Obtain teleport destination information.
				if (Config.ATTUNE_TO_PLAYER.get()) {
					for (ServerLevel a_level : _serverPlayer.getServer().getAllLevels()) {
						if (a_level.dimension().toString().equals(_data.lodestar_destination_dimension)) {
							destination_level = a_level;
						}
					}

					destination_x = _data.lodestar_destination_x;
					destination_y = _data.lodestar_destination_y;
					destination_z = _data.lodestar_destination_z;

				} else {
					for (ServerLevel a_level : _serverPlayer.getServer().getAllLevels()) {
						if (a_level.dimension().toString().equals(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("lodestar_destination_dimension"))) {
							destination_level = a_level;
						}
					}

					destination_x = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("lodestar_destination_x");
					destination_y = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("lodestar_destination_y");
					destination_z = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("lodestar_destination_z");
				}
				
				// Verify that lodestone still exists.
				if (!(destination_level.getBlockState(BlockPos.containing(destination_x, destination_y, destination_z)).getBlock() == Blocks.LODESTONE)) {
					if (Config.ATTUNE_TO_PLAYER.get()) {
						_data.lodestar_destination_exists = false;
						_data.syncPlayerVariables(_serverPlayer);
					} else {
						CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("lodestar_destination_exists", false));
					}
					
					if (level instanceof Level _level) {
						if (!_level.isClientSide()) {
							_level.playSound(null, BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.lodestone.place")), SoundSource.NEUTRAL, 1, 1);
						} else {
							_level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.lodestone.place")), SoundSource.NEUTRAL, 1, 1, false);
						}
					}
					
					// If lodestone does not exist, tell player that teleport failed.
					if (entity instanceof Player _player) {
						_player.getCooldowns().addCooldown(stack.getItem(), 20);
						_player.stopUsingItem();
						_player.displayClientMessage(Component.literal("Failed to return to missing lodestone."), true);
					}
					
					return retval;
				}

				// Teleport player.
                _serverPlayer.teleportTo(destination_level, destination_x + 0.5, destination_y + 1, destination_z + 0.5, entity.getYRot(), entity.getXRot());
                _serverPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(_serverPlayer.getAbilities()));

                for (MobEffectInstance _effectinstance : _serverPlayer.getActiveEffects())
                    _serverPlayer.connection.send(new ClientboundUpdateMobEffectPacket(_serverPlayer.getId(), _effectinstance, false));
				
				// Play teleportation sound effects
				if (level instanceof Level _level) {
                    _level.playSound(null, BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.bell.resonate")), SoundSource.NEUTRAL, 1, 1);
                    destination_level.playSound(null, BlockPos.containing(destination_x + 0.5, destination_y + 1, destination_z + 0.5), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.bell.resonate")), SoundSource.NEUTRAL, 1, 1);
                    destination_level.playSound(null, BlockPos.containing(destination_x + 0.5, destination_y + 1, destination_z + 0.5), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.ender_eye.death")), SoundSource.NEUTRAL, 1, 1);
				}
			}
			
			if (entity instanceof Player _player) {
				_player.getCooldowns().addCooldown(stack.getItem(), (int) (Config.LODESTAR_COOLDOWN.get() * 20));
			}

			if (!Config.INFINITE_USES.get() && level instanceof ServerLevel _level) {
				stack.hurtAndBreak(Config.LODESTAR_USE_COST.get(), _level, null, _stkprov -> {});
			}

		} else {
			if (level instanceof Level _level) {
				if (!_level.isClientSide()) {
					_level.playSound(null, BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.lodestone.place")), SoundSource.NEUTRAL, 1, 1);
				} else {
					_level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.lodestone.place")), SoundSource.NEUTRAL, 1, 1, false);
				}
			}

			if (entity instanceof Player _player) {
				_player.getCooldowns().addCooldown(stack.getItem(), 20);
				_player.stopUsingItem();
				if (Config.ATTUNE_TO_PLAYER.get()) {
					_player.displayClientMessage(Component.literal("You are not attuned to a lodestone."), true);
				} else {
					_player.displayClientMessage(Component.literal("This lodestar is not attuned to a lodestone."), true);
				}
			}
		}

		return retval;
	}
	
	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player entity, InteractionHand hand) {
		InteractionResultHolder<ItemStack> ar = super.use(world, entity, hand);
		entity.startUsingItem(hand);
		return ar;
	}
	
	@Override
	public InteractionResult useOn(UseOnContext context) {
		super.useOn(context);
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		Level level = context.getLevel();
		int x = context.getClickedPos().getX();
		int y = context.getClickedPos().getY();
		int z = context.getClickedPos().getZ();

		if (player == null)
			return InteractionResult.PASS;
        
		if (((level.getBlockState(BlockPos.containing(x, y, z))).getBlock() == Blocks.LODESTONE) && player.isShiftKeyDown()) {
			player.displayClientMessage(Component.literal("Attuned to this lodestone."), true);

			if (level instanceof Level _level) {
				// Store lodestone information.
				if (Config.ATTUNE_TO_PLAYER.get()) {
					Lodestars.PlayerData _data = player.getData(Lodestars.PLAYER_DATA);
					_data.lodestar_destination_dimension = player.level().dimension().toString();
					_data.lodestar_destination_x = x;
					_data.lodestar_destination_y = y;
					_data.lodestar_destination_z = z;
					_data.lodestar_destination_exists = true;
					_data.syncPlayerVariables(player);
				} else {
					CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("lodestar_destination_dimension", player.level().dimension().toString()));
					CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble("lodestar_destination_x", x));
					CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble("lodestar_destination_y", y));
					CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble("lodestar_destination_z", z));
					CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("lodestar_destination_exists", true));
				}

				if (!_level.isClientSide()) {
					_level.playSound(null, BlockPos.containing(x, y, z), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.ender_eye.death")), SoundSource.NEUTRAL, 1, 1);
				} else {
					_level.playLocalSound(x, y, z, BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.ender_eye.death")), SoundSource.NEUTRAL, 1, 1, false);
				}
			}
			player.getCooldowns().addCooldown(stack.getItem(), 20);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}

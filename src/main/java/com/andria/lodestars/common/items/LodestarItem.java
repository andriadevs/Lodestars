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
import java.util.Random;

import com.andria.lodestars.Config;
import com.andria.lodestars.Lodestars;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;


public class LodestarItem extends Item{

    private static final Random random = new Random();

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

		if (entity instanceof Player player && player.getCooldowns().isOnCooldown(stack.getItem())) {
			return false;
		}

		return true;
	}
	
    // Tooltip text, shows the location of the last attuned lodestone or tells the player they haven't attuned to a lodestone if they haven't.
	@Override
	@OnlyIn(Dist.CLIENT)
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> list, TooltipFlag flag) {
		super.appendHoverText(stack, context, list, flag);
		Entity entity = stack.getEntityRepresentation() != null ? stack.getEntityRepresentation() : Minecraft.getInstance().player;
		String hoverText = null;
        String destination_dimension = "";
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
                destination_dimension = _data.lodestar_destination_dimension;

				if (destination_dimension.equals(player_dimension)) {
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
				hoverText = "Attuned to a lodestone in " + getNiceDimensionName(destination_dimension) + " at: (" + Math.round(destination_x) + ", " + Math.round(destination_y)
				+ ", " + Math.round(destination_z) + ")";
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
		Lodestars.PlayerData data = entity.getData(Lodestars.PLAYER_DATA);
		if (Config.ATTUNE_TO_PLAYER.get()) {
			has_destination = data.lodestar_destination_exists;
		} else {
			has_destination = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("lodestar_destination_exists");
		}

		if (has_destination) {
			if (entity instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide()) {
                ServerLevel destination_level = entity.getServer().getLevel(Level.OVERWORLD);
                double destination_x = 0.0;
                double destination_y = 0.0;
                double destination_z = 0.0;

				// Obtain teleport destination information.
				if (Config.ATTUNE_TO_PLAYER.get()) {
					for (ServerLevel a_level : serverPlayer.getServer().getAllLevels()) {
						if (a_level.dimension().toString().equals(data.lodestar_destination_dimension)) {
							destination_level = a_level;
						}
					}

					destination_x = data.lodestar_destination_x;
					destination_y = data.lodestar_destination_y;
					destination_z = data.lodestar_destination_z;

				} else {
					for (ServerLevel a_level : serverPlayer.getServer().getAllLevels()) {
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
						data.lodestar_destination_exists = false;
						data.syncPlayerVariables(serverPlayer);
					} else {
						CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("lodestar_destination_exists", false));
					}
					
                    level.playSound(null, BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.lodestone.place")), SoundSource.NEUTRAL, 1, 1);

					// If lodestone does not exist, tell player that teleport failed.
                    serverPlayer.getCooldowns().addCooldown(stack.getItem(), 20);
                    serverPlayer.stopUsingItem();
                    serverPlayer.displayClientMessage(Component.literal("Failed to return to missing lodestone."), true);
					
					return retval;
				}

				// Teleport player.
                serverPlayer.teleportTo(destination_level, destination_x + 0.5, destination_y + 1, destination_z + 0.5, entity.getYRot(), entity.getXRot());
                serverPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(serverPlayer.getAbilities()));

                for (MobEffectInstance _effectinstance : serverPlayer.getActiveEffects())
                    serverPlayer.connection.send(new ClientboundUpdateMobEffectPacket(serverPlayer.getId(), _effectinstance, false));
				
				// Play teleportation sound effects
                level.playSound(null, BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.bell.resonate")), SoundSource.NEUTRAL, 1, 1);
                destination_level.playSound(null, BlockPos.containing(destination_x + 0.5, destination_y + 1, destination_z + 0.5), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.bell.resonate")), SoundSource.NEUTRAL, 1, 1);
                destination_level.playSound(null, BlockPos.containing(destination_x + 0.5, destination_y + 1, destination_z + 0.5), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.ender_eye.death")), SoundSource.NEUTRAL, 1, 1);
            }

            // Do teleportation particles
            for (int i = 0; i < 24; i++) {
                level.addParticle(
                    ParticleTypes.END_ROD,
                    entity.getX() + (random.nextDouble() - 0.5),
                    entity.getY() + random.nextDouble(),
                    entity.getZ() + (random.nextDouble() - 0.5),
                    0,
                    random.nextDouble() * 0.5f + 0.1f,
                    0
                );
            }
			
			if (entity instanceof Player player) {
				player.getCooldowns().addCooldown(stack.getItem(), (int) (Config.LODESTAR_COOLDOWN.get() * 20));
			}

			if (!Config.INFINITE_USES.get() && level instanceof ServerLevel serverLevel) {
				stack.hurtAndBreak(Config.LODESTAR_USE_COST.get(), serverLevel, null, _stkprov -> {});
			}

		} else {
            level.playSound(null, BlockPos.containing(entity.getX(), entity.getY(), entity.getZ()), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.lodestone.place")), SoundSource.NEUTRAL, 1, 1);

			if (entity instanceof Player player) {
				player.getCooldowns().addCooldown(stack.getItem(), 20);
				player.stopUsingItem();
				if (Config.ATTUNE_TO_PLAYER.get()) {
					player.displayClientMessage(Component.literal("You are not attuned to a lodestone."), true);
				} else {
					player.displayClientMessage(Component.literal("This lodestar is not attuned to a lodestone."), true);
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

            // Store lodestone information.
            if (Config.ATTUNE_TO_PLAYER.get()) {
                Lodestars.PlayerData data = player.getData(Lodestars.PLAYER_DATA);
                data.lodestar_destination_dimension = player.level().dimension().toString();
                data.lodestar_destination_x = x;
                data.lodestar_destination_y = y;
                data.lodestar_destination_z = z;
                data.lodestar_destination_exists = true;
                data.syncPlayerVariables(player);
            } else {
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("lodestar_destination_dimension", player.level().dimension().toString()));
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble("lodestar_destination_x", x));
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble("lodestar_destination_y", y));
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble("lodestar_destination_z", z));
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("lodestar_destination_exists", true));
            }

            level.playSound(null, BlockPos.containing(x, y, z), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.ender_eye.death")), SoundSource.NEUTRAL, 1, 1);
    
			player.getCooldowns().addCooldown(stack.getItem(), 20);

			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
    }

    // Takes the resourcekey string and turns it into a nicer-looking dimension name string for the tooltip.
    private static String getNiceDimensionName(String rkey) {
        try {
            String name  = rkey.split(":")[2].split("]")[0];
            StringBuilder sb = new StringBuilder();
            boolean append_the = true;

            for (String s : name.split("_")) {
                if (s.equals("the") || s.equals("a") || s.equals("an")) {
                    append_the = false;
                    sb.append(s + " ");
                } else {
                    sb.append(s.substring(0, 1).toUpperCase() + s.substring(1) + " ");
                }
            }

            if (append_the) {
                return "the " + sb.toString().trim();
            } else {
                return sb.toString().trim();
            }
        } catch (Exception e) {
            return "another dimension";
        }
    }
}

package com.andria.lodestars.common.registry;

import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

import com.andria.lodestars.common.items.LodestarItem;
import com.andria.lodestars.Lodestars;

public class ModItems {
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Lodestars.MODID);

	public static final DeferredItem<Item> LODESTAR = ITEMS.register("lodestar", () -> new LodestarItem());
}

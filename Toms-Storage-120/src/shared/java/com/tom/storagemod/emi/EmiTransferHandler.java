package com.tom.storagemod.emi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.systems.RenderSystem;

import com.tom.storagemod.gui.CraftingTerminalMenu;
import com.tom.storagemod.util.IAutoFillTerminal;
import com.tom.storagemod.util.StoredItemStack;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;

public class EmiTransferHandler implements StandardRecipeHandler<CraftingTerminalMenu> {

	@Override
	public List<Slot> getInputSources(CraftingTerminalMenu handler) {
		return Collections.emptyList();
	}

	@Override
	public List<Slot> getCraftingSlots(CraftingTerminalMenu handler) {
		return Collections.emptyList();
	}

	@Override
	public EmiPlayerInventory getInventory(AbstractContainerScreen<CraftingTerminalMenu> screen) {
		List<EmiStack> stacks = new ArrayList<>();
		screen.getMenu().slots.subList(1, screen.getMenu().slots.size()).stream().map(Slot::getItem).map(EmiStack::of).forEach(stacks::add);
		screen.getMenu().getStoredItems().forEach(s -> stacks.add(EmiStack.of(s.getStack(), s.getQuantity())));
		return new EmiPlayerInventory(stacks);
	}

	@Override
	public boolean supportsRecipe(EmiRecipe recipe) {
		return recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING && recipe.supportsRecipeTree();
	}

	@Override
	public boolean craft(EmiRecipe recipe, EmiCraftContext<CraftingTerminalMenu> context) {
		AbstractContainerScreen<CraftingTerminalMenu> screen = context.getScreen();
		handleRecipe(recipe, screen, false);
		Minecraft.getInstance().setScreen(screen);
		return true;
	}

	@Override
	public void render(EmiRecipe recipe, EmiCraftContext<CraftingTerminalMenu> context, List<Widget> widgets,
			GuiGraphics matrices) {
		RenderSystem.enableDepthTest();
		List<Integer> missing = handleRecipe(recipe, context.getScreen(), true);
		int i = 0;
		for (Widget w : widgets) {
			if (w instanceof SlotWidget sw) {
				int j = i++;
				EmiIngredient stack = sw.getStack();
				Bounds bounds = sw.getBounds();
				if (sw.getRecipe() == null && !stack.isEmpty()) {
					if (missing.contains(j)) {
						matrices.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0x44FF0000);
					}
				}
			}
		}
	}

	private static List<Integer> handleRecipe(EmiRecipe recipe, AbstractContainerScreen<CraftingTerminalMenu> screen, boolean simulate) {
		IAutoFillTerminal term = screen.getMenu();
		ItemStack[][] stacks = recipe.getInputs().stream().map(i ->
		i.getEmiStacks().stream().map(EmiStack::getItemStack).filter(s -> !s.isEmpty()).toArray(ItemStack[]::new)
				).toArray(ItemStack[][]::new);

		int width = recipe.getDisplayWidth();
		List<StoredItemStack> available = new ArrayList<>(term.getStoredItems());
		screen.getMenu().slots.subList(1, screen.getMenu().slots.size()).stream()
		.map(Slot::getItem)
		.filter(stack -> !stack.isEmpty())
		.map(StoredItemStack::new)
		.forEach(available::add);
		List<Integer> missing = findMissing(stacks, available, width);

		if(!simulate) {
			var recipeId = recipe.getId();
			if (recipeId != null && !Minecraft.getInstance().level.getRecipeManager().byKey(recipeId).isEmpty()) {
				CompoundTag compound = new CompoundTag();
				compound.putString("fill", recipeId.toString());
				term.sendMessage(compound);
			}
		}
		return missing;
	}

	static List<Integer> findMissing(ItemStack[][] ingredients, List<StoredItemStack> available, int width) {
		int[] assignments = new int[ingredients.length];
		int[] used = new int[available.size()];
		Arrays.fill(assignments, -1);

		List<Integer> missing = new ArrayList<>();
		for (int i = 0; i < ingredients.length; i++) {
			if (ingredients[i].length > 0 && !assignIngredient(i, ingredients, available, assignments, used,
					new boolean[ingredients.length], new boolean[available.size()])) {
				missing.add(width == 1 ? i * 3 : width == 2 ? ((i % 2) + i / 2 * 3) : i);
			}
		}
		return missing;
	}

	private static boolean assignIngredient(int ingredient, ItemStack[][] ingredients, List<StoredItemStack> available,
			int[] assignments, int[] used, boolean[] visitedIngredients, boolean[] visitedAvailable) {
		if (visitedIngredients[ingredient]) {
			return false;
		}
		visitedIngredients[ingredient] = true;

		for (int i = 0; i < available.size(); i++) {
			if (visitedAvailable[i] || !matches(ingredients[ingredient], available.get(i).getStack())) {
				continue;
			}
			visitedAvailable[i] = true;

			if (used[i] < available.get(i).getQuantity()) {
				used[i]++;
				assignments[ingredient] = i;
				return true;
			}

			for (int j = 0; j < assignments.length; j++) {
				if (assignments[j] == i && assignIngredient(j, ingredients, available, assignments, used,
						visitedIngredients, visitedAvailable)) {
					assignments[ingredient] = i;
					return true;
				}
			}
		}
		return false;
	}

	private static boolean matches(ItemStack[] ingredient, ItemStack available) {
		for (ItemStack option : ingredient) {
			if (ItemStack.isSameItemSameTags(option, available)) {
				return true;
			}
		}
		return false;
	}
}

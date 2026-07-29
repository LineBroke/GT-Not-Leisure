package com.science.gtnl.common.recipe.gtnl;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.science.gtnl.api.IRecipePool;
import com.science.gtnl.common.material.GTNLRecipeMaps;
import com.science.gtnl.utils.recipes.RecipeBuilder;

import gregtech.api.enums.Materials;
import gregtech.api.objects.OreDictItemStack;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTUtility;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.recipe.RecipePetals;

public class PetalApothecaryRecipes implements IRecipePool {

    public RecipeMap<?> PAR = GTNLRecipeMaps.PetalApothecaryRecipes;

    @Override
    public void loadRecipes() {
        for (RecipePetals recipe : BotaniaAPI.petalRecipes) {
            List<AbstractMap.SimpleEntry<ItemStack, Integer>> stackCounts = new ArrayList<>();
            Map<String, Integer> oreDictCounts = new LinkedHashMap<>();

            oreDictCounts.merge("listAllseed", 1, Integer::sum);

            for (Object input : recipe.getInputs()) {
                if (input instanceof ItemStack stack) {
                    mergeItemStack(stackCounts, stack);
                } else if (input instanceof String oreName) {
                    oreDictCounts.merge(oreName, 1, Integer::sum);
                }
            }

            List<Object> finalInputs = new ArrayList<>(stackCounts.size() + oreDictCounts.size());
            for (AbstractMap.SimpleEntry<ItemStack, Integer> entry : stackCounts) {
                ItemStack stack = entry.getKey()
                    .copy();
                stack.stackSize = entry.getValue();
                finalInputs.add(stack);
            }
            for (Map.Entry<String, Integer> entry : oreDictCounts.entrySet()) {
                finalInputs.add(new OreDictItemStack(entry.getKey(), entry.getValue()));
            }

            RecipeBuilder.builder()
                .itemInputs(finalInputs.toArray(new Object[0]))
                .itemOutputs(
                    recipe.getOutput()
                        .copy())
                .fluidInputs(Materials.Water.getFluid(1000))
                .duration(40)
                .eut(0)
                .addTo(PAR);
        }
    }

    private static void mergeItemStack(List<AbstractMap.SimpleEntry<ItemStack, Integer>> stackCounts, ItemStack input) {
        for (AbstractMap.SimpleEntry<ItemStack, Integer> entry : stackCounts) {
            if (GTUtility.areStacksEqual(input, entry.getKey())) {
                entry.setValue(entry.getValue() + input.stackSize);
                return;
            }
        }
        stackCounts.add(new AbstractMap.SimpleEntry<>(input.copy(), input.stackSize));
    }
}

package com.science.gtnl.common.machine.multiblock;

import static com.gtnewhorizon.cropsnh.init.CropsNHBlockTextures.OVERLAY_FRONT_CROP_BREEDER;
import static com.gtnewhorizon.cropsnh.init.CropsNHBlockTextures.OVERLAY_FRONT_CROP_BREEDER_ACTIVE;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.cropsnh.api.ICropCard;
import com.gtnewhorizon.cropsnh.api.ICropMutation;
import com.gtnewhorizon.cropsnh.api.IMutationPool;
import com.gtnewhorizon.cropsnh.api.ISeedData;
import com.gtnewhorizon.cropsnh.api.ISeedStats;
import com.gtnewhorizon.cropsnh.farming.SeedStats;
import com.gtnewhorizon.cropsnh.farming.registries.FertilizerRegistry;
import com.gtnewhorizon.cropsnh.farming.registries.MutationRegistry;
import com.gtnewhorizon.cropsnh.init.CropsNHBlocks;
import com.gtnewhorizon.cropsnh.init.CropsNHFluids;
import com.gtnewhorizon.cropsnh.recipes.CropsNHGTRecipeMaps;
import com.gtnewhorizon.cropsnh.tileentity.TileEntityCropSticks;
import com.gtnewhorizon.cropsnh.tileentity.singleblock.MTECropBreeder;
import com.gtnewhorizon.cropsnh.utility.CropsNHUtils;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.science.gtnl.common.gui.modularui.GTNLMultiBlockBaseGui;
import com.science.gtnl.common.machine.multiMachineBase.MultiMachineBase;
import com.science.gtnl.utils.structure.GTNLStructureErrors;

import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Textures;
import gregtech.api.enums.TierEU;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

@IMetaTileEntity.SkipGenerateDescription
public class BreedingArray extends MultiMachineBase<BreedingArray> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFFSET = 1;
    private static final int VERTICAL_OFFSET = 1;
    private static final int DEPTH_OFFSET = 0;
    private static final int MIN_PARENTS = 2;
    private static final int MIN_CASINGS = 20;
    private static final int MODE_RANDOM = 0;
    private static final int MODE_EXACT = 1;
    private static final int MAX_CROP_TIER = 8;
    private static final double EXACT_FERTILIZER_POTENCY_SCALE = 5.0;
    private static final int CHANCE_SCALE = 1_000_000;
    private static final long BREEDING_RECIPE_EUT = TierEU.RECIPE_LV;
    private static final int POOL_RECIPE_DURATION = 20 * GTRecipeBuilder.SECONDS;

    private static final String[][] SHAPE = new String[][] { { "AAA", "AAA", "AAA" }, { "A~A", "ABA", "AAA" },
        { "AAA", "AAA", "AAA" } };

    public BreedingArray(int id, String name, String regionalName) {
        super(id, name, regionalName);
    }

    public BreedingArray(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new BreedingArray(this.mName);
    }

    @Override
    public IStructureDefinition<BreedingArray> getStructureDefinition() {
        return StructureDefinition.<BreedingArray>builder()
            .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(SHAPE))
            .addElement(
                'A',
                buildHatchAdder(BreedingArray.class).casingIndex(getCasingTextureID())
                    .hint(1)
                    .atLeast(
                        HatchElement.InputBus,
                        HatchElement.InputHatch,
                        HatchElement.OutputBus,
                        HatchElement.Maintenance,
                        HatchElement.Energy.or(HatchElement.ExoticEnergy))
                    .buildAndChain(
                        StructureUtility.onElementPass(
                            machine -> ++machine.mCountCasing,
                            StructureUtility.ofBlock(CropsNHBlocks.blockCasings1, 0))))
            .addElement('B', StructureUtility.isAir())
            .build();
    }

    @Override
    public void checkMachine(IGregTechTileEntity baseTileEntity, ItemStack stack, List<StructureError> errors) {
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;
        setupParameters();
        checkHatch(errors);
        checkCasingMin(errors, mCountCasing, MIN_CASINGS);
        if (!mDualInputHatches.isEmpty()) errors.add(GTNLStructureErrors.invalidHatchConfiguration());
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stack, hintsOnly, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET);
    }

    @Override
    public int survivalConstruct(ItemStack stack, int elementBudget, ISurvivalBuildEnvironment environment) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stack,
            HORIZONTAL_OFFSET,
            VERTICAL_OFFSET,
            DEPTH_OFFSET,
            elementBudget,
            environment,
            false,
            true);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return CropsNHGTRecipeMaps.fakeCropBreederRecipeMap;
    }

    @Override
    @NotNull
    public CheckRecipeResult checkProcessing() {
        List<ItemStack> inputs = getAllStoredInputs();
        if (inputs == null || inputs.isEmpty()) return CheckRecipeResultRegistry.NO_RECIPE;

        List<ISeedData> parents = new ArrayList<>();
        ItemStack[] catalysts = new ItemStack[inputs.size()];

        for (int index = 0; index < inputs.size(); index++) {
            ItemStack input = inputs.get(index);
            if (GTUtility.isStackInvalid(input)) continue;

            ISeedData seed = CropsNHUtils.getAnalyzedSeedData(input);
            if (seed != null) {
                parents.add(seed);
                continue;
            }

            catalysts[index] = input;
        }

        List<FluidStack> liquidFertilizers = new ArrayList<>();
        for (FluidStack fluid : getStoredFluids()) {
            if (fluid == null || fluid.amount <= 0) continue;
            int potency = FertilizerRegistry.instance.getPotency(fluid);
            if (potency > 0) {
                liquidFertilizers.add(fluid);
            }
        }
        if (parents.size() < MIN_PARENTS || liquidFertilizers.isEmpty()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        ArrayList<ICropCard> parentCards = new ArrayList<>(parents.size());
        for (ISeedData parent : parents) parentCards.add(parent.getCrop());
        ICropMutation selectedMutation = null;
        IMutationPool selectedPool = null;
        List<ICropCard> candidates = null;
        ICropCard resultCrop = null;
        int[] catalystConsumption = new int[catalysts.length];
        long recipeEUt = BREEDING_RECIPE_EUT;
        int recipeDuration;

        if (machineMode == MODE_EXACT) {
            List<ICropMutation> exactMutations = findExactMutations(parentCards);
            if (exactMutations.isEmpty()) return CheckRecipeResultRegistry.NO_RECIPE;
            for (ICropMutation mutation : exactMutations) {
                if (mutation.getOutput()
                    .getTier() > MAX_CROP_TIER) continue;
                int[] consumption = mutation.canBreed(parentCards, getBaseMetaTileEntity(), catalysts);
                if (consumption == null || consumption.length > catalysts.length) continue;
                selectedMutation = mutation;
                catalystConsumption = consumption;
                break;
            }
            if (selectedMutation == null) return CheckRecipeResultRegistry.NO_RECIPE;
            resultCrop = selectedMutation.getOutput();
            recipeDuration = selectedMutation.getBreedingMachineRecipeDuration();
        } else {
            List<IMutationPool> possiblePools = MutationRegistry.instance.getPossiblePoolMutations(parentCards);
            if (possiblePools == null || possiblePools.isEmpty()) return CheckRecipeResultRegistry.NO_RECIPE;

            List<IMutationPool> supportedPools = new ArrayList<>();
            for (IMutationPool pool : possiblePools) {
                for (ICropCard member : pool.getMembers()) {
                    if (member.getTier() <= MAX_CROP_TIER) {
                        supportedPools.add(pool);
                        break;
                    }
                }
            }
            if (supportedPools.isEmpty()) return CheckRecipeResultRegistry.NO_RECIPE;

            selectedPool = supportedPools.get(getBaseMetaTileEntity().getRandomNumber(supportedPools.size()));
            candidates = new ArrayList<>();
            for (ICropCard member : selectedPool.getMembers()) {
                if (member.getTier() <= MAX_CROP_TIER) candidates.add(member);
            }
            if (candidates.isEmpty()) return CheckRecipeResultRegistry.NO_RECIPE;
            recipeDuration = POOL_RECIPE_DURATION;
        }

        Collection<ISeedData> contributingParents = selectedMutation != null ? parents
            : getPoolParents(parents, selectedPool);
        ISeedStats outputStats = selectedMutation != null ? averageStats(contributingParents)
            : variateStats(contributingParents, true);

        FluidStack liquidFertilizer = null;
        int liquidFertilizerAmount = 0;
        int liquidFertilizerPotency = 0;
        int maxBaseFluidAmount = 0;
        if (selectedMutation != null) {
            maxBaseFluidAmount = MTECropBreeder.getFluidAmount(
                resultCrop.getTier(),
                outputStats.getGrowth(),
                outputStats.getGain(),
                outputStats.getResistance(),
                1.0F);
        } else {
            for (ICropCard candidate : candidates) {
                maxBaseFluidAmount = Math.max(
                    maxBaseFluidAmount,
                    MTECropBreeder.getFluidAmount(
                        candidate.getTier(),
                        outputStats.getGrowth(),
                        outputStats.getGain(),
                        outputStats.getResistance(),
                        1.0F));
            }
        }
        int enrichedPotency = Math.max(1, FertilizerRegistry.instance.getPotency(CropsNHFluids.enrichedFertilizer));
        for (FluidStack candidate : liquidFertilizers) {
            int potency = FertilizerRegistry.instance.getPotency(candidate);
            int required = (int) Math
                .min(Integer.MAX_VALUE, ((long) maxBaseFluidAmount * enrichedPotency + potency - 1L) / potency);
            if (candidate.amount >= required && potency > liquidFertilizerPotency) {
                liquidFertilizer = candidate;
                liquidFertilizerPotency = potency;
            }
        }
        if (liquidFertilizer == null) return CheckRecipeResultRegistry.NO_RECIPE;

        int consumedFertilizerPotency = liquidFertilizerPotency;
        if (selectedMutation == null) resultCrop = weightedRandomCrop(candidates, consumedFertilizerPotency);

        ItemStack output = resultCrop.getSeedItem(outputStats);
        if (GTUtility.isStackInvalid(output)) return CheckRecipeResultRegistry.NO_RECIPE;
        output.stackSize = 1;
        if (!canOutputAll(new ItemStack[] { output })) return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;

        if (liquidFertilizer != null) {
            int baseFluidAmount = MTECropBreeder.getFluidAmount(
                resultCrop.getTier(),
                outputStats.getGrowth(),
                outputStats.getGain(),
                outputStats.getResistance(),
                1.0F);
            liquidFertilizerAmount = (int) Math.min(
                Integer.MAX_VALUE,
                ((long) baseFluidAmount * enrichedPotency + liquidFertilizerPotency - 1L) / liquidFertilizerPotency);
        }

        IdentityHashMap<ItemStack, Integer> consumptionPlan = new IdentityHashMap<>();
        for (ISeedData seed : parents) addConsumption(consumptionPlan, seed.getStack(), 1);
        for (int index = 0; index < catalystConsumption.length; index++) {
            if (catalystConsumption[index] > 0) {
                addConsumption(consumptionPlan, inputs.get(index), catalystConsumption[index]);
            }
        }
        for (Map.Entry<ItemStack, Integer> entry : consumptionPlan.entrySet()) {
            if (GTUtility.isStackInvalid(entry.getKey()) || entry.getKey().stackSize < entry.getValue()) {
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
        }
        for (Map.Entry<ItemStack, Integer> entry : consumptionPlan.entrySet()) {
            entry.getKey().stackSize -= entry.getValue();
        }
        if (liquidFertilizer != null) liquidFertilizer.amount -= liquidFertilizerAmount;

        boolean successful = selectedMutation == null || rollTierChance(resultCrop, consumedFertilizerPotency);
        this.mOutputItems = successful ? new ItemStack[] { output } : new ItemStack[0];
        this.lEUt = -recipeEUt;
        this.mMaxProgresstime = Math.max(1, recipeDuration);
        this.mEfficiency = 10000;
        this.mEfficiencyIncrease = 10000;
        updateSlots();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private static List<ICropMutation> findExactMutations(List<ICropCard> parents) {
        List<ICropMutation> matches = new ArrayList<>();
        List<ICropMutation> possible = MutationRegistry.instance.getPossibleDeterministicMutations(parents);
        if (possible == null) return matches;
        for (ICropMutation mutation : possible) {
            if (mutation.getParentCount() == parents.size() && parents.containsAll(mutation.getParents())) {
                matches.add(mutation);
            }
        }
        matches.sort(
            Comparator.comparing(
                mutation -> mutation.getOutput()
                    .getId()));
        return matches;
    }

    private static Collection<ISeedData> getPoolParents(List<ISeedData> parents, IMutationPool pool) {
        List<ISeedData> contributing = new ArrayList<>();
        for (ISeedData parent : parents) {
            if (pool.contains(parent.getCrop())) contributing.add(parent);
        }
        return contributing;
    }

    private static ISeedStats averageStats(Collection<ISeedData> parents) {
        int growth = 0;
        int gain = 0;
        int resistance = 0;
        for (ISeedData parent : parents) {
            growth += parent.getStats()
                .getGrowth();
            gain += parent.getStats()
                .getGain();
            resistance += parent.getStats()
                .getResistance();
        }
        int count = parents.size();
        return new SeedStats((byte) (growth / count), (byte) (gain / count), (byte) (resistance / count), false);
    }

    private static ISeedStats variateStats(Collection<ISeedData> parents, boolean onlyGoUp) {
        List<ISeedStats> stats = new ArrayList<>(parents.size());
        for (ISeedData parent : parents) stats.add(parent.getStats());
        byte growth = TileEntityCropSticks.variateStat(onlyGoUp, stats, ISeedStats::getGrowth);
        byte gain = TileEntityCropSticks.variateStat(onlyGoUp, stats, ISeedStats::getGain);
        byte resistance = TileEntityCropSticks.variateStat(onlyGoUp, stats, ISeedStats::getResistance);
        return new SeedStats(growth, gain, resistance, false);
    }

    private ICropCard weightedRandomCrop(List<ICropCard> candidates, int fertilizerPotency) {
        double totalWeight = 0.0;
        for (ICropCard candidate : candidates) totalWeight += getTierWeight(candidate, fertilizerPotency);
        double roll = getBaseMetaTileEntity().getRandomNumber(CHANCE_SCALE) * totalWeight / CHANCE_SCALE;
        for (ICropCard candidate : candidates) {
            roll -= getTierWeight(candidate, fertilizerPotency);
            if (roll <= 0.0) return candidate;
        }
        return candidates.get(candidates.size() - 1);
    }

    private boolean rollTierChance(ICropCard crop, int fertilizerPotency) {
        double luck = 1.0 + Math.max(0, fertilizerPotency) / EXACT_FERTILIZER_POTENCY_SCALE;
        double chanceMultiplier = Math.pow(2.0, -Math.max(0, crop.getTier() - 1) / luck);
        int chance = (int) Math.round(chanceMultiplier * CHANCE_SCALE);
        return getBaseMetaTileEntity().getRandomNumber(CHANCE_SCALE) < chance;
    }

    private static double getTierWeight(ICropCard crop, int fertilizerPotency) {
        double luck = 1.0 + Math.max(0, fertilizerPotency) / 100.0;
        return Math.pow(2.0, -Math.max(0, crop.getTier() - 1) / luck);
    }

    private static void addConsumption(IdentityHashMap<ItemStack, Integer> plan, ItemStack stack, int amount) {
        if (amount > 0) plan.put(stack, plan.getOrDefault(stack, 0) + amount);
    }

    @Override
    public int getCasingTextureID() {
        return GTUtility.getCasingTextureIndex(CropsNHBlocks.blockCasings1, 0);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        ITexture casing = Textures.BlockIcons.getCasingTextureForId(getCasingTextureID());
        if (side != facing) return new ITexture[] { casing };
        return new ITexture[] { casing, TextureFactory.builder()
            .addIcon(active ? OVERLAY_FRONT_CROP_BREEDER_ACTIVE : OVERLAY_FRONT_CROP_BREEDER)
            .extFacing()
            .build() };
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    @Override
    public boolean supportsInputSeparation() {
        return false;
    }

    @Override
    public boolean supportsBatchMode() {
        return false;
    }

    @Override
    public boolean supportsMachineModeSwitch() {
        return true;
    }

    @Override
    public int nextMachineMode() {
        return machineMode == MODE_RANDOM ? MODE_EXACT : MODE_RANDOM;
    }

    @Override
    public String getMachineModeName() {
        return StatCollector.translateToLocal("BreedingArray_Mode_" + machineMode);
    }

    @Override
    public void onModeChangeByScrewdriver(ForgeDirection side, EntityPlayer player, float x, float y, float z,
        ItemStack tool) {
        machineMode = nextMachineMode();
        GTUtility.sendChatTrans(player, "BreedingArray_Mode_" + machineMode);
    }

    @Override
    @Deprecated
    public void setMachineModeIcons() {
        machineModeIcons.add(GTUITextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT);
        machineModeIcons.add(GTUITextures.OVERLAY_BUTTON_MACHINEMODE_PACKAGER);
    }

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new GTNLMultiBlockBaseGui<>(this).withMachineModeIcons(
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_DEFAULT,
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_PACKAGER);
    }

    @Override
    public MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType(StatCollector.translateToLocal("BreedingArrayRecipeType"))
            .addInfo(StatCollector.translateToLocal("Tooltip_BreedingArray_00"))
            .addInfo(StatCollector.translateToLocal("Tooltip_BreedingArray_01"))
            .addInfo(StatCollector.translateToLocal("Tooltip_BreedingArray_02"))
            .addInfo(StatCollector.translateToLocal("Tooltip_BreedingArray_03"))
            .addInfo(StatCollector.translateToLocal("Tooltip_BreedingArray_04"))
            .addInfo(StatCollector.translateToLocal("Tooltip_BreedingArray_05"))
            .addInfo(StatCollector.translateToLocal("Tooltip_BreedingArray_06"))
            .addInfo(StatCollector.translateToLocal("Tooltip_BreedingArray_07"))
            .beginStructureBlock(3, 3, 3, true)
            .addInputBus(StatCollector.translateToLocal("Tooltip_BreedingArray_Casing"))
            .addInputHatch(StatCollector.translateToLocal("Tooltip_BreedingArray_Casing"))
            .addOutputBus(StatCollector.translateToLocal("Tooltip_BreedingArray_Casing"))
            .addEnergyHatch(StatCollector.translateToLocal("Tooltip_BreedingArray_Casing"))
            .addMaintenanceHatch(StatCollector.translateToLocal("Tooltip_BreedingArray_Casing"))
            .toolTipFinisher();
    }
}

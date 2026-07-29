package com.science.gtnl.common.machine.multiblock;

import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.science.gtnl.common.machine.multiMachineBase.MultiMachineBase;
import com.science.gtnl.common.material.GTNLRecipeMaps;

import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import vazkii.botania.common.block.ModBlocks;

@IMetaTileEntity.SkipGenerateDescription
public class LargePetalApothecary extends MultiMachineBase<LargePetalApothecary> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 1;
    private static final int VERTICAL_OFF_SET = 1;
    private static final int DEPTH_OFF_SET = 0;
    private static final int CASING_TEXTURE_ID = GTUtility.getTextureId((byte) 116, (byte) 53);
    private static final String[][] SHAPE = StructureUtility
        .transpose(new String[][] { { "AAA", "AAA", "AAA" }, { "A~A", "A A", "AAA" }, { "AAA", "AAA", "AAA" } });

    public LargePetalApothecary(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public LargePetalApothecary(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new LargePetalApothecary(mName);
    }

    @Override
    public IStructureDefinition<LargePetalApothecary> getStructureDefinition() {
        return StructureDefinition.<LargePetalApothecary>builder()
            .addShape(STRUCTURE_PIECE_MAIN, SHAPE)
            .addElement(
                'A',
                buildHatchAdder(LargePetalApothecary.class)
                    .atLeast(HatchElement.InputBus, HatchElement.OutputBus, HatchElement.InputHatch)
                    .casingIndex(getCasingTextureID())
                    .hint(1)
                    .buildAndChain(
                        StructureUtility
                            .onElementPass(x -> ++x.mCountCasing, StructureUtility.ofBlock(ModBlocks.livingrock, 0))))
            .build();
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;
        checkHatch(errors);
        checkHatchMin(errors, HatchElement.InputBus, 1);
        checkHatchMin(errors, HatchElement.OutputBus, 1);
        checkHatchMin(errors, HatchElement.InputHatch, 1);
        checkCasingMin(errors, mCountCasing, 20);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTNLRecipeMaps.PetalApothecaryRecipes;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 4;
    }

    @Override
    public int getMaxOverclocks() {
        return 0;
    }

    @Override
    public int getCasingTextureID() {
        return CASING_TEXTURE_ID;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                TextureFactory.builder()
                    .addIcon(aActive ? Textures.BlockIcons.OVERLAY_DTPF_ON : Textures.BlockIcons.OVERLAY_DTPF_OFF)
                    .extFacing()
                    .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    @Override
    public MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder()
            .addMachineType(StatCollector.translateToLocal("LargePetalApothecaryRecipeType"))
            .addInfo(StatCollector.translateToLocal("Tooltip_LargePetalApothecary_00"))
            .addInfo(StatCollector.translateToLocal("Tooltip_LargePetalApothecary_01"))
            .addInfo(StatCollector.translateToLocal("Tooltip_LargePetalApothecary_02"))
            .beginStructureBlock(3, 3, 3, true)
            .addInputBus(StatCollector.translateToLocal("Tooltip_LargePetalApothecary_Casing"), 1)
            .addOutputBus(StatCollector.translateToLocal("Tooltip_LargePetalApothecary_Casing"), 1)
            .addInputHatch(StatCollector.translateToLocal("Tooltip_LargePetalApothecary_Casing"), 1)
            .toolTipFinisher();
    }

    @Override
    public void checkMaintenance() {}

    @Override
    public boolean shouldCheckMaintenance() {
        return false;
    }
}

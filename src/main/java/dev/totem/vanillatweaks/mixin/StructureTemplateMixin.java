package dev.totem.vanillatweaks.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.TagValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {
    private static final int CHISELED_BOOKSHELF_SLOT_COUNT = 6;

    @Inject(method = "processBlockInfos", at = @At("RETURN"), cancellable = true)
    private static void deadrecall$replaceBookshelfBlockInfos(
            ServerLevelAccessor level,
            BlockPos origin,
            BlockPos pivot,
            StructurePlaceSettings settings,
            List<StructureTemplate.StructureBlockInfo> infos,
            CallbackInfoReturnable<List<StructureTemplate.StructureBlockInfo>> cir
    ) {
        List<StructureTemplate.StructureBlockInfo> original = cir.getReturnValue();
        List<StructureTemplate.StructureBlockInfo> replaced = new ArrayList<>(original.size());

        for (StructureTemplate.StructureBlockInfo info : original) {
            boolean convertedBookshelf = info.state().is(Blocks.BOOKSHELF);
            boolean emptyGeneratedChiseledBookshelf =
                    info.state().is(Blocks.CHISELED_BOOKSHELF) && info.nbt() == null;
            if (!convertedBookshelf && !emptyGeneratedChiseledBookshelf) {
                replaced.add(info);
                continue;
            }

            BlockState bookshelfState = deadrecall$filledBookshelfState();
            CompoundTag bookshelfNbt = deadrecall$filledBookshelfNbt(level, info.pos(), bookshelfState);
            replaced.add(new StructureTemplate.StructureBlockInfo(info.pos(), bookshelfState, bookshelfNbt));
        }

        cir.setReturnValue(replaced);
    }

    private static BlockState deadrecall$filledBookshelfState() {
        BlockState state = Blocks.CHISELED_BOOKSHELF.defaultBlockState();
        for (var occupiedProperty : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) {
            state = state.setValue(occupiedProperty, true);
        }
        return state;
    }

    private static CompoundTag deadrecall$filledBookshelfNbt(
            ServerLevelAccessor level,
            BlockPos pos,
            BlockState state
    ) {
        ChiseledBookShelfBlockEntity shelf = new ChiseledBookShelfBlockEntity(pos, state);
        int slotCount = Math.min(CHISELED_BOOKSHELF_SLOT_COUNT, shelf.getItems().size());
        for (int slot = 0; slot < slotCount; slot++) {
            shelf.getItems().set(slot, new ItemStack(Items.BOOK));
        }

        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING,
                level.registryAccess()
        );
        shelf.saveWithId(output);
        return output.buildResult();
    }
}

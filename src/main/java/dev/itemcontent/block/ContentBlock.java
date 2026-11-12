package dev.itemcontent.block;

import net.minecraft.block.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;
import java.util.List;

/** Decorative variants intentionally have no block entity, inventory or machine logic. */
public final class ContentBlock extends Block {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    private static final VoxelShape CHEST = Block.createCuboidShape(1, 0, 1, 15, 14, 15);
    private final boolean oriented, chest;
    public ContentBlock(Settings settings, boolean oriented, boolean chest) {
        super(settings); this.oriented = oriented; this.chest = chest;
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, oriented ? context.getHorizontalPlayerFacing().getOpposite() : Direction.NORTH);
    }
    @Override public BlockState rotate(BlockState state, BlockRotation rotation) { return state.with(FACING, rotation.rotate(state.get(FACING))); }
    @Override public BlockState mirror(BlockState state, BlockMirror mirror) { return state.rotate(mirror.getRotation(state.get(FACING))); }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return chest ? CHEST : VoxelShapes.fullCube(); }
    @Override public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) { return List.of(new ItemStack(asItem())); }
}

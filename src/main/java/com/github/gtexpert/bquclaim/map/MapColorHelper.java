package com.github.gtexpert.bquclaim.map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MapColorHelper {

    public static int getBlockColor(World world, int x, int z) {
        // その座標の最高地点を取得（空気以外の最上部）
        BlockPos pos = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z));
        IBlockState state = world.getBlockState(pos.down()); // 地表のブロック

        // Minecraft標準のMapColor（地図用色）を取得
        int color = state.getMapColor(world, pos.down()).colorValue;

        // 高低差による影をつけると一気にリアルになります
        // 北側のブロックより高ければ明るく、低ければ暗くする
        BlockPos northPos = pos.north();
        int northY = world.getTopSolidOrLiquidBlock(northPos).getY();

        float shadow = 1.0F;
        if (pos.getY() > northY) shadow = 1.1F; // 明るく
        else if (pos.getY() < northY) shadow = 0.9F; // 暗く

        int r = (int) (((color >> 16) & 0xFF) * shadow);
        int g = (int) (((color >> 8) & 0xFF) * shadow);
        int b = (int) ((color & 0xFF) * shadow);

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}

package com.github.gtexpert.blpc.client.map;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class MapColorHelper {

    private static final int COLOR_VOID = 0xFF000000;
    private static final int DEFAULT_FLUID_COLOR = 0xFF3F76E4;
    private static final int MAX_FLUID_DEPTH = 16;
    private static final float SHADE_STRENGTH = 0.04f;
    private static final float SHADE_LIMIT = 0.15f;
    private static final float MIN_DEPTH_FACTOR = 0.4f;

    private static final Map<IBlockState, CachedColor> colorCache = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;

        Minecraft mc = Minecraft.getMinecraft();
        BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
        IResourceManager resourceManager = mc.getResourceManager();

        for (Block block : Block.REGISTRY) {
            for (IBlockState state : block.getBlockState().getValidStates()) {
                try {
                    IBakedModel model = dispatcher.getModelForState(state);

                    List<BakedQuad> upQuads = model.getQuads(state, EnumFacing.UP, 0L);
                    TextureAtlasSprite sprite;
                    int tintIndex = -1;

                    if (!upQuads.isEmpty()) {
                        BakedQuad quad = upQuads.get(0);
                        sprite = quad.getSprite();
                        tintIndex = quad.hasTintIndex() ? quad.getTintIndex() : -1;
                    } else {
                        sprite = model.getParticleTexture();
                    }

                    if ("missingno".equals(sprite.getIconName())) continue;

                    int color = averageFromResource(resourceManager, sprite);
                    if (color != 0) {
                        colorCache.put(state, new CachedColor(color, tintIndex));
                    }
                } catch (Exception ignored) {}
            }
        }

        TextureMap textureMap = mc.getTextureMapBlocks();
        registerFluid(resourceManager, textureMap, "minecraft:blocks/water_still",
                Blocks.WATER.getDefaultState(), Blocks.FLOWING_WATER.getDefaultState());
        registerFluid(resourceManager, textureMap, "minecraft:blocks/lava_still",
                Blocks.LAVA.getDefaultState(), Blocks.FLOWING_LAVA.getDefaultState());

        initialized = true;
    }

    /**
     * Computes the color array for a single chunk.
     * Directly accesses the Chunk object to avoid per-block chunk lookups.
     */
    public static int[] computeChunkColors(World world, Chunk chunk, Chunk northChunk, int cx, int cz) {
        int[] colors = new int[256];
        var pos = new BlockPos.MutableBlockPos();
        int bx = cx << 4;
        int bz = cz << 4;

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int topY = chunk.getHeightValue(lx, lz);
                if (topY <= 0) {
                    colors[lx + lz * 16] = COLOR_VOID;
                    continue;
                }

                pos.setPos(bx + lx, topY - 1, bz + lz);
                IBlockState state = chunk.getBlockState(pos);

                if (state.getMaterial() == Material.AIR) {
                    colors[lx + lz * 16] = COLOR_VOID;
                    continue;
                }

                int color;
                if (state.getMaterial().isLiquid()) {
                    color = computeFluidColor(world, chunk, state, pos);
                } else {
                    color = computeSolidColor(state, world, pos);
                }

                // Height-difference shading against north neighbor (direct access within chunk, neighbor chunk at
                // boundary)
                int northY;
                if (lz > 0) {
                    northY = chunk.getHeightValue(lx, lz - 1);
                } else if (northChunk != null) {
                    northY = northChunk.getHeightValue(lx, 15);
                } else {
                    northY = topY;
                }

                float shadow = 1.0f + MathHelper.clamp((topY - northY) * SHADE_STRENGTH, -SHADE_LIMIT, SHADE_LIMIT);
                colors[lx + lz * 16] = applyShading(color, shadow);
            }
        }
        return colors;
    }

    private static int computeSolidColor(IBlockState state, World world, BlockPos pos) {
        CachedColor cached = colorCache.get(state);
        if (cached == null) {
            return state.getMapColor(world, pos).colorValue;
        }

        int color = cached.color;
        if (cached.tintIndex >= 0) {
            int tint = Minecraft.getMinecraft().getBlockColors()
                    .colorMultiplier(state, world, pos, cached.tintIndex);
            if (tint != -1) {
                color = multiplyColor(color, tint);
            }
        }
        return color;
    }

    private static int computeFluidColor(World world, Chunk chunk, IBlockState state, BlockPos.MutableBlockPos pos) {
        CachedColor cached = colorCache.get(state);
        int fluidColor;
        if (cached != null) {
            int tint = Minecraft.getMinecraft().getBlockColors()
                    .colorMultiplier(state, world, pos, 0);
            fluidColor = (tint != -1) ? multiplyColor(cached.color, tint) : cached.color;
        } else {
            int mapColor = state.getMapColor(world, pos).colorValue;
            fluidColor = (mapColor != 0) ? mapColor : DEFAULT_FLUID_COLOR;
        }

        // Measure fluid depth by scanning downward within the same chunk
        Material fluidMat = state.getMaterial();
        int depth = 0;
        int origY = pos.getY();
        for (int y = origY; y >= 0 && depth < MAX_FLUID_DEPTH; y--) {
            pos.setY(y);
            if (chunk.getBlockState(pos).getMaterial() != fluidMat) break;
            depth++;
        }
        pos.setY(origY);

        float depthFactor = MathHelper.clamp(1.0f - (depth * SHADE_STRENGTH), MIN_DEPTH_FACTOR, 1.0f);
        return applyShading(fluidColor, depthFactor);
    }

    private static void registerFluid(IResourceManager rm, TextureMap textureMap,
                                      String spriteName, IBlockState... states) {
        try {
            TextureAtlasSprite sprite = textureMap.getAtlasSprite(spriteName);
            if ("missingno".equals(sprite.getIconName())) return;

            int color = averageFromResource(rm, sprite);
            if (color != 0) {
                for (IBlockState state : states) {
                    colorCache.put(state, new CachedColor(color, 0));
                }
            }
        } catch (Exception ignored) {}
    }

    private static int averageFromResource(IResourceManager rm, TextureAtlasSprite sprite) throws Exception {
        String iconName = sprite.getIconName();
        ResourceLocation loc = new ResourceLocation(iconName);
        ResourceLocation texLoc = new ResourceLocation(loc.getNamespace(), "textures/" + loc.getPath() + ".png");

        try (IResource resource = rm.getResource(texLoc)) {
            BufferedImage img = TextureUtil.readBufferedImage(resource.getInputStream());

            long r = 0, g = 0, b = 0;
            int count = 0;
            int w = img.getWidth();
            int h = Math.min(img.getHeight(), w);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int pixel = img.getRGB(x, y);
                    int a = (pixel >> 24) & 0xFF;
                    if (a < 128) continue;
                    r += (pixel >> 16) & 0xFF;
                    g += (pixel >> 8) & 0xFF;
                    b += pixel & 0xFF;
                    count++;
                }
            }

            if (count == 0) return 0;
            return 0xFF000000 | ((int) (r / count) << 16) | ((int) (g / count) << 8) | (int) (b / count);
        }
    }

    private static int multiplyColor(int base, int tint) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int tr = (tint >> 16) & 0xFF, tg = (tint >> 8) & 0xFF, tb = tint & 0xFF;
        return 0xFF000000 | ((br * tr / 255) << 16) | ((bg * tg / 255) << 8) | (bb * tb / 255);
    }

    private static int applyShading(int color, float factor) {
        int r = MathHelper.clamp((int) (((color >> 16) & 0xFF) * factor), 0, 255);
        int g = MathHelper.clamp((int) (((color >> 8) & 0xFF) * factor), 0, 255);
        int b = MathHelper.clamp((int) ((color & 0xFF) * factor), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static synchronized void clearCache() {
        colorCache.clear();
        initialized = false;
    }

    private static class CachedColor {

        final int color;
        final int tintIndex;

        CachedColor(int color, int tintIndex) {
            this.color = color;
            this.tintIndex = tintIndex;
        }
    }
}

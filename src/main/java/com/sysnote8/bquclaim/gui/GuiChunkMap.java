package com.sysnote8.bquclaim.gui;

import net.minecraft.client.gui.GuiScreen;

import com.sysnote8.bquclaim.BQPartyHelper;
import com.sysnote8.bquclaim.chunk.ClaimedChunkData;
import com.sysnote8.bquclaim.chunk.ClientCache;
import com.sysnote8.bquclaim.network.MessageClaimChunk;
import com.sysnote8.bquclaim.network.ModNetwork;

public class GuiChunkMap extends GuiScreen {

    private final int size = 16; // 1チャンクの表示サイズ
    private int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int cx = width / 2, cy = height / 2;
        int pX = mc.player.chunkCoordX, pZ = mc.player.chunkCoordZ;

        // 半径10チャンク分を描画
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                int rx = pX + x, rz = pZ + z;
                int dx = cx + (x * size), dy = cy + (z * size);

                // グリッド線
                drawRect(dx, dy, dx + size, dy + size, 0x22FFFFFF);

                // 領地データ確認
                ClaimedChunkData d = ClientCache.get(rx, rz);
                if (d != null) {
                    int color = 0x88FF0000; // デフォルト：赤（他人）
                    if (d.ownerUUID.equals(mc.player.getUniqueID())) color = 0x8800FF00; // 自分：緑
                    else if (BQPartyHelper.areInSameParty(mc.player.getUniqueID(), d.ownerUUID)) color = 0x8800FFFF; // BQ仲間：水色

                    drawRect(dx + 1, dy + 1, dx + size - 1, dy + size - 1, color);
                }

                // マウスホバー
                if (mouseX >= dx && mouseX < dx + size && mouseY >= dy && mouseY < dy + size) {
                    drawRect(dx, dy, dx + size, dy + size, 0x44FFFFFF);
                    if (d != null) drawHoveringText(d.ownerName, mouseX, mouseY);
                }
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long time) {
        // ドラッグ中のチャンク座標計算
        int rx = (mx - (width / 2)) / size + mc.player.chunkCoordX;
        int rz = (my - (height / 2)) / size + mc.player.chunkCoordZ;

        if (rx != lastX || rz != lastZ) {
            // 左ドラッグ(0)でClaim, 右ドラッグ(1)でUnclaim
            ModNetwork.INSTANCE.sendToServer(new MessageClaimChunk(rx, rz, btn == 0 ? 0 : 1));
            lastX = rx;
            lastZ = rz;
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        lastX = Integer.MIN_VALUE;
        lastZ = Integer.MIN_VALUE;
    }
}

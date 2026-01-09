package com.sysnote8.bquclaim.gui;

import com.sysnote8.bquclaim.BQPartyHelper;
import com.sysnote8.bquclaim.ModConfig;
import com.sysnote8.bquclaim.chunk.ClaimedChunkData;
import com.sysnote8.bquclaim.chunk.ClientCache;
import com.sysnote8.bquclaim.network.MessageClaimChunk;
import com.sysnote8.bquclaim.network.ModNetwork;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;

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
                int rx = pX + x;
                int rz = pZ + z;
                int dx = (width / 2) + (x * size);
                int dy = (height / 2) + (z * size);

                int[] colors = AsyncMapRenderer.getColors(rx, rz);

                if (colors != null) {
                    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                    // データがあればテクスチャをバインドして描画
                    TextureCache.ChunkTexture tex = TextureCache.getOrCreate(rx, rz, colors);
                    tex.bind();
                    Gui.drawModalRectWithCustomSizedTexture(dx, dy, 0, 0, size, size, 16, 16);
                } else {
                    // データがなければ非同期計算をリクエスト
                    AsyncMapRenderer.requestChunk(mc.world, rx, rz);
                    // ロード中として暗い四角を描画
                    drawRect(dx, dy, dx + size, dy + size, 0xFF222222);
                }

                // その上に領地を重ねる
                renderClaimOverlay(rx, rz, dx, dy);
            }
        }
        drawPlayerIcon();
        renderLimitOverlay();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void renderClaimOverlay(int rx, int rz, int dx, int dy) {
        // クライアント側のキャッシュから領地データを取得
        ClaimedChunkData d = ClientCache.get(rx, rz);

        if (d != null) {
            int color;
            // 自分の領地
            if (d.ownerUUID.equals(mc.player.getUniqueID())) {
                color = 0x5500FF00; // 半透明の緑
            }
            // BQのパーティー仲間
            else if (BQPartyHelper.areInSameParty(mc.player.getUniqueID(), d.ownerUUID)) {
                color = 0x5500FFFF; // 半透明の水色
            }
            // 他人の領地
            else {
                color = 0x55FF0000; // 半透明の赤
            }

            // 地形が見えるように、drawRectで半透明の四角を重ねる
            // dx, dy は画面上の描画開始位置、size はチャンクの表示サイズ(16)
            drawRect(dx, dy, dx + size, dy + size, color);

            if (d.isForceLoaded) {
                // 領地の色の上に、さらに濃い色で小さな四角を描く
                drawRect(dx + 5, dy + 5, dx + 11, dy + 11, 0xFFFF0000); // 中央に赤いポッチ
            }
        }
    }

    private void renderLimitOverlay() {
        // 2. 統計情報の取得
        int claims = countMyClaims();
        int loads = countMyForceLoads();

        // 3. 表示文字列の作成
        String claimText = String.format("Claims: %d / %d", claims, ModConfig.maxClaimsPerPlayer);
        String loadText = String.format("Force Loaded: %d / %d", loads, ModConfig.maxForceLoadsPerPlayer);

        // 4. 描画（左上から少し余白をあける）
        int x = 10;
        int y = 10;

        // 制限ギリギリなら赤文字にするなどの演出も可能
        int claimColor = (claims >= ModConfig.maxClaimsPerPlayer) ? 0xFFFF5555 : 0xFFFFFFFF;
        int loadColor = (loads >= ModConfig.maxForceLoadsPerPlayer) ? 0xFFFF5555 : 0xFFFFFFFF;

        this.fontRenderer.drawStringWithShadow(claimText, x, y, claimColor);
        this.fontRenderer.drawStringWithShadow(loadText, x, y + 12, loadColor);
    }

    private void drawPlayerIcon() {
        // プレイヤーの向き（yaw）を取得
        float yaw = mc.player.rotationYaw;

        // map_icons.png をバインド
        mc.getTextureManager().bindTexture(new ResourceLocation("textures/map/map_icons.png"));

        GlStateManager.pushMatrix();

        // 1. 画面中央に移動
        GlStateManager.translate((float) this.width / 2, (float) this.height / 2, 0);
        // 2. プレイヤーの向きに合わせて回転
        GlStateManager.rotate(yaw, 0, 0, 1);
        // 3. アイコンのサイズ（例: 8x8）に合わせて少し戻す（中心を軸にするため）
        GlStateManager.translate(-4, -4, 0);

        // チャンク内の0〜15の相対位置を計算してピクセルに変換
        float relativeX = (float) (mc.player.posX % 16);
        float relativeZ = (float) (mc.player.posZ % 16);

        // 負の座標対策
        if (relativeX < 0) relativeX += 16;
        if (relativeZ < 0) relativeZ += 16;
        // テクスチャのUV座標を指定して描画（プレイヤーの矢印は一番左上 0,0）
        // drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, textureWidth, textureHeight)
        Gui.drawModalRectWithCustomSizedTexture((int) relativeX, (int) relativeZ, 0, 0, 8, 8, 32, 32);

        GlStateManager.popMatrix();
    }

    // GUIクラス内の変数
    private int lastDragX = Integer.MIN_VALUE;
    private int lastDragZ = Integer.MIN_VALUE;

    /**
     * 普通のシングルクリックを処理
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        // クリックした瞬間の座標で一度処理を実行
        handleAction(mouseX, mouseY, mouseButton);
    }

    /**
     * ドラッグ（クリックしたまま移動）を処理
     */
    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);

        // ドラッグ中も同じメソッドを呼ぶ
        handleAction(mouseX, mouseY, clickedMouseButton);
    }

    /**
     * 実際の Claim/Unclaim パケット送信処理を一本化
     */
    private void handleAction(int mouseX, int mouseY, int mouseButton) {
        // 1. 画面中央のピクセル座標
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 2. 中央からの相対ピクセル距離
        int diffX = mouseX - centerX;
        int diffY = mouseY - centerY;

        // 3. チャンク単位に変換（Math.floorを使わないと負の座標でズレる）
        // size = 16 の場合、-1~-16ピクセルは -1チャンク目になる必要がある
        int rx = (int) Math.floor((double) diffX / size) + mc.player.chunkCoordX;
        int rz = (int) Math.floor((double) diffY / size) + mc.player.chunkCoordZ;

        // デバッグ用（コンソールで確認）
        // System.out.println("Mouse: " + mouseY + " -> ChunkZ: " + rz);

        // 前回の処理と同じチャンクなら通信をスキップ（ドラッグの重複防止）
        if (rx == lastDragX && rz == lastDragZ) {
            return;
        }

        // 左クリック(0)でClaim、右クリック(1)でUnclaim
        if (mouseButton == 0) { // 左クリック
            int mode = GuiScreen.isShiftKeyDown() ? 2 : 0; // Shiftなら2、普通なら0
            ModNetwork.INSTANCE.sendToServer(new MessageClaimChunk(rx, rz, mode));
        } else if (mouseButton == 1) { // 右クリック
            ModNetwork.INSTANCE.sendToServer(new MessageClaimChunk(rx, rz, 1)); // 解除
        }
        if (mouseButton == 0 || mouseButton == 1) {
            // 最後に処理した座標を保存
            lastDragX = rx;
            lastDragZ = rz;

            // クリック音を鳴らすと操作感が良くなる
            mc.player.playSound(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 0.3F, 1.0F);
        }
    }

    /**
     * マウスを離したときにリセット（次に同じ場所をクリックしても反応するように）
     */
    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        lastDragX = Integer.MIN_VALUE;
        lastDragZ = Integer.MIN_VALUE;
    }

    private int countMyClaims() {
        int count = 0;
        // ClientCacheから全データを取得（ClientCacheにgetAllメソッド等を追加しておくと楽です）
        for (ClaimedChunkData d : ClientCache.getAll()) {
            if (d.ownerUUID.equals(mc.player.getUniqueID())) {
                count++;
            }
        }
        return count;
    }

    private int countMyForceLoads() {
        int count = 0;
        for (ClaimedChunkData d : ClientCache.getAll()) {
            if (d.ownerUUID.equals(mc.player.getUniqueID()) && d.isForceLoaded) {
                count++;
            }
        }
        return count;
    }
}

package com.sysnote8.bquclaim.gui;

import java.io.IOException;
import java.util.UUID;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.sysnote8.bquclaim.BQPartyHelper;
import com.sysnote8.bquclaim.ModConfig;
import com.sysnote8.bquclaim.chunk.ClaimedChunkData;
import com.sysnote8.bquclaim.chunk.ClientCache;
import com.sysnote8.bquclaim.network.MessageClaimChunk;
import com.sysnote8.bquclaim.network.ModNetwork;

public class GuiChunkMap extends GuiScreen {

    private final int size = 16; // 1チャンクの表示サイズ
    private int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
    // Selected chunk under cursor (updated each draw)
    private int selectedRX = Integer.MIN_VALUE;
    private int selectedRZ = Integer.MIN_VALUE;

    // GuiButtons
    private net.minecraft.client.gui.GuiButton btnClaim;
    private net.minecraft.client.gui.GuiButton btnUnclaim;
    private net.minecraft.client.gui.GuiButton btnToggleForce;
    // Confirmation dialog
    private boolean confirmOpen = false;
    private int confirmAction = 0; // 1 = unclaim, 2 = toggle force
    private String confirmMessage = "";
    private net.minecraft.client.gui.GuiButton confirmYes;
    private net.minecraft.client.gui.GuiButton confirmNo;

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        // Update selected chunk under mouse BEFORE drawing so it can be highlighted
        updateSelectedChunk(mouseX, mouseY);

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

        // Info panel above buttons (bottom center)
        drawInfoPanel();

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw tooltips for buttons (if hovered)
        // Tooltips for buttons (plain single-line tooltips)
        for (Object o : this.buttonList) {
            if (o instanceof net.minecraft.client.gui.GuiButton) {
                net.minecraft.client.gui.GuiButton b = (net.minecraft.client.gui.GuiButton) o;
                if (b.visible && mouseX >= b.xPosition && mouseY >= b.yPosition
                        && mouseX < b.xPosition + b.width && mouseY < b.yPosition + b.height) {
                    String tip;
                    if (b == btnClaim) {
                        tip = "Claim this chunk (Left-click). Shift+Click = Claim+Force";
                    } else if (b == btnUnclaim) {
                        tip = "Unclaim this chunk (owner or OP only)";
                    } else {
                        tip = "Toggle force-load for this chunk";
                    }
                    java.util.List<String> lines = new java.util.ArrayList<>();
                    lines.add(tip);
                    this.drawHoveringText(lines, mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        int btnWidth = 80;
        int gap = 6;
        int startX = (this.width / 2) - ((btnWidth * 3 + gap * 2) / 2);
        int y = this.height - 30;
        btnClaim = new net.minecraft.client.gui.GuiButton(1, startX, y, btnWidth, 20, "Claim");
        btnUnclaim = new net.minecraft.client.gui.GuiButton(2, startX + (btnWidth + gap), y, btnWidth, 20, "Unclaim");
        btnToggleForce = new net.minecraft.client.gui.GuiButton(3, startX + (btnWidth + gap) * 2, y, btnWidth, 20, "Toggle Force");
        this.buttonList.clear();
        this.buttonList.add(btnClaim);
        this.buttonList.add(btnUnclaim);
        this.buttonList.add(btnToggleForce);
        updateButtonStates();
    }

    @Override
    protected void actionPerformed(net.minecraft.client.gui.GuiButton button) throws IOException {
        // If a confirmation dialog is open, only handle confirm buttons
        if (confirmOpen) {
            if (button == confirmYes) {
                // Execute confirmed action
                if (confirmAction == 1) {
                    ModNetwork.INSTANCE.sendToServer(new MessageClaimChunk(selectedRX, selectedRZ, 1));
                } else if (confirmAction == 2) {
                    ModNetwork.INSTANCE.sendToServer(new MessageClaimChunk(selectedRX, selectedRZ, 2));
                }
                closeConfirm();
            } else if (button == confirmNo) {
                closeConfirm();
            }
        } else {
            if (button == btnClaim) {
                ModNetwork.INSTANCE.sendToServer(new MessageClaimChunk(selectedRX, selectedRZ, 0));
            } else if (button == btnUnclaim) {
                // Ask for confirmation before unclaiming
                openConfirm(1);
            } else if (button == btnToggleForce) {
                ClaimedChunkData d = ClientCache.get(selectedRX, selectedRZ);
                // If disabling force, ask for confirmation; otherwise toggle immediately
                if (d != null && d.isForceLoaded) {
                    openConfirm(2);
                } else {
                    ModNetwork.INSTANCE.sendToServer(new MessageClaimChunk(selectedRX, selectedRZ, 2));
                }
            }
        }
        mc.player.playSound(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 0.3F, 1.0F);
    }

    private void openConfirm(int action) {
        this.confirmAction = action;
        this.confirmOpen = true;
        String title;
        if (action == 1) {
            title = String.format("Unclaim chunk %d, %d?", selectedRX, selectedRZ);
            this.confirmMessage = "This will release the claim and unforce this chunk.";
        } else {
            title = String.format("Disable force on chunk %d, %d?", selectedRX, selectedRZ);
            this.confirmMessage = "This will remove the force-load ticket for this chunk.";
        }

        int btnY = this.height / 2 + 20;
        int btnW = 80;
        int gap = 10;
        int centerX = this.width / 2;
        int yesX = centerX - btnW - (gap / 2);
        int noX = centerX + (gap / 2);

        confirmYes = new net.minecraft.client.gui.GuiButton(100, yesX, btnY, btnW, 20, "Yes");
        confirmNo = new net.minecraft.client.gui.GuiButton(101, noX, btnW, 20, "No");
        this.buttonList.add(confirmYes);
        this.buttonList.add(confirmNo);
        // Disable main buttons while confirming
        if (btnClaim != null) btnClaim.enabled = false;
        if (btnUnclaim != null) btnUnclaim.enabled = false;
        if (btnToggleForce != null) btnToggleForce.enabled = false;
    }

    private void closeConfirm() {
        this.confirmOpen = false;
        this.confirmAction = 0;
        this.confirmMessage = "";
        if (confirmYes != null) this.buttonList.remove(confirmYes);
        if (confirmNo != null) this.buttonList.remove(confirmNo);
        confirmYes = null;
        confirmNo = null;
        updateButtonStates();
    }

    private void updateButtonStates() {
        ClaimedChunkData d = ClientCache.get(selectedRX, selectedRZ);
        boolean isOwner = (d != null && d.ownerUUID.equals(mc.player.getUniqueID()));
        btnClaim.enabled = (d == null) && (countMyClaims() < ModConfig.maxClaimsPerPlayer);
        btnUnclaim.enabled = isOwner;
        btnToggleForce.enabled = isOwner || (d == null && countMyClaims() < ModConfig.maxClaimsPerPlayer && countMyForceLoads() < ModConfig.maxForceLoadsPerPlayer);
        // Update button labels to reflect state
        if (d != null && d.isForceLoaded) {
            btnToggleForce.displayString = "Unforce";
        } else {
            btnToggleForce.displayString = "Force";
        }
    }

    private void updateSelectedChunk(int mouseX, int mouseY) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int diffX = mouseX - centerX;
        int diffY = mouseY - centerY;
        int rx = (int) Math.floor((double) diffX / size) + mc.player.chunkCoordX;
        int rz = (int) Math.floor((double) diffY / size) + mc.player.chunkCoordZ;
        if (rx != selectedRX || rz != selectedRZ) {
            selectedRX = rx;
            selectedRZ = rz;
            if (this.buttonList != null) updateButtonStates();
        }
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

            // 境界線の描画（これをつなげる！）
            renderChunkBorder(rx, rz, dx, dy, d.ownerUUID);

            // 2. 強制ロード中ならハッチングを重ねる
            if (d.isForceLoaded) {
                // 赤い斜線を描画
                drawHatching(dx, dy, size, size, 0xAAFF0000);
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

    private void drawInfoPanel() {
        // Draw a semi-transparent rounded panel above the buttons
        int panelWidth = 300;
        int panelHeight = 48;
        int x = (this.width / 2) - (panelWidth / 2);
        int y = this.height - 30 - panelHeight - 8;

        // Background
        drawRect(x, y, x + panelWidth, y + panelHeight, 0xAA000000);

        // Selected chunk info
        String coords = String.format("Chunk: %d, %d", selectedRX, selectedRZ);
        ClaimedChunkData d = ClientCache.get(selectedRX, selectedRZ);
        String owner = (d == null) ? "Unclaimed" : d.ownerName;
        String force = (d != null && d.isForceLoaded) ? "Force: ON" : "Force: OFF";

        int textX = x + 8;
        int textY = y + 8;
        this.fontRenderer.drawStringWithShadow(coords, textX, textY, 0xFFFFFF);
        this.fontRenderer.drawStringWithShadow("Owner: " + owner, textX, textY + 12, 0xFFFFFF);
        this.fontRenderer.drawStringWithShadow(force, textX, textY + 24, (d != null && d.isForceLoaded) ? 0xFFAA0000 : 0xFFAAAAAA);
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

    private void drawHatching(int x, int y, int width, int height, int color) {
        // 線の間隔
        int spacing = 4;

        // OpenGLのライン描画設定
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.glLineWidth(1.0F); // 線の太さ

        // 色の分解 (ARGB)
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GlStateManager.color(r, g, b, a);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);

        // 斜線を描画（左上から右下へ）
        for (int i = 0; i <= width + height; i += spacing) {
            // 描画範囲をチャンクの枠内にクリップ（制限）するための計算
            int xStart = Math.max(0, i - height);
            int yStart = Math.min(i, height);
            int xEnd = Math.min(i, width);
            int yEnd = Math.max(0, i - width);

            bufferbuilder.pos(x + xStart, y + yStart, 0.0D).endVertex();
            bufferbuilder.pos(x + xEnd, y + yEnd, 0.0D).endVertex();
        }

        tessellator.draw();
        GlStateManager.enableTexture2D();
    }

    private void renderChunkBorder(int rx, int rz, int dx, int dy, UUID owner) {
        int borderColor = 0xFFFFFFFF; // 白色の枠線
        int thickness = 1; // 線の太さ

        // 上 (Z-1)
        if (!isSameOwner(rx, rz - 1, owner)) {
            drawRect(dx, dy, dx + size, dy + thickness, borderColor);
        }
        // 下 (Z+1)
        if (!isSameOwner(rx, rz + 1, owner)) {
            drawRect(dx, dy + size - thickness, dx + size, dy + size, borderColor);
        }
        // 左 (X-1)
        if (!isSameOwner(rx - 1, rz, owner)) {
            drawRect(dx, dy, dx + thickness, dy + size, borderColor);
        }
        // 右 (X+1)
        if (!isSameOwner(rx + 1, rz, owner)) {
            drawRect(dx + size - thickness, dy, dx + size, dy + size, borderColor);
        }
    }

    // 隣が同じ所有者かどうかを判定するヘルパー
    private boolean isSameOwner(int rx, int rz, UUID currentOwner) {
        ClaimedChunkData neighbor = ClientCache.get(rx, rz);
        if (neighbor == null) return false;
        return neighbor.ownerUUID.equals(currentOwner);
    }
}

package com.github.gtexpert.blpc.common.network.message;

import java.util.UUID;

import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import com.github.gtexpert.blpc.api.party.RelationType;

import io.netty.buffer.ByteBuf;

/**
 * S→C packet that multiplexes every transient client notification (toast)
 * through a single {@code kind} discriminator. Inspired by Clayium's
 * {@code writeCustomData(discriminator, ...)} pattern: adding a new
 * notification only requires a new {@code KIND_*} constant plus matching
 * arms in {@link #toBytes}, {@link #fromBytes} and the client handler — no
 * new wire ID and no churn in {@code ModNetwork} or
 * {@link com.github.gtexpert.blpc.client.network.ClientPacketHandlers}.
 * <p>
 * <b>Wire stability:</b> existing {@code KIND_*} values must not be
 * renumbered. New kinds append at the end. Sub-discriminators (party event
 * types, claim failure reasons) stay as strings so they remain
 * forward-compatible across mismatched client/server versions.
 * <p>
 * Use the static factory methods ({@link #chunkTransit}, {@link #partyEvent},
 * {@link #claimFailed}) to construct outbound packets — they keep the
 * encoder and decoder in lockstep.
 */
public class ClientNotify implements IMessage {

    /** Top-level discriminator. Stable wire ordering — append only. */
    public static final int KIND_CHUNK_TRANSIT = 0;
    public static final int KIND_PARTY_EVENT = 1;
    public static final int KIND_CLAIM_FAILED = 2;

    /** Sub-discriminators for {@link #KIND_PARTY_EVENT}. */
    public static final String EVENT_MEMBER_JOINED = "MEMBER_JOINED";
    public static final String EVENT_MEMBER_LEFT = "MEMBER_LEFT";
    public static final String EVENT_KICKED = "KICKED";
    public static final String EVENT_DISBANDED = "DISBANDED";
    public static final String EVENT_INVITE_RECEIVED = "INVITE_RECEIVED";
    public static final String EVENT_OWNER_TRANSFERRED = "OWNER_TRANSFERRED";
    public static final String EVENT_ROLE_CHANGED = "ROLE_CHANGED";
    public static final String EVENT_BQU_LINKED = "BQU_LINKED";
    public static final String EVENT_BQU_UNLINKED = "BQU_UNLINKED";
    public static final String EVENT_PARTY_FULL = "PARTY_FULL";
    public static final String EVENT_JOIN_FAILED = "JOIN_FAILED";

    /** Sub-discriminators for {@link #KIND_CLAIM_FAILED}. */
    public static final String REASON_CLAIM_LIMIT = "CLAIM_LIMIT";
    public static final String REASON_FORCELOAD_LIMIT = "FORCELOAD_LIMIT";
    public static final String REASON_NO_PARTY = "NO_PARTY";
    public static final String REASON_DIMENSION_BLOCKED = "DIMENSION_BLOCKED";

    private int kind;

    // KIND_CHUNK_TRANSIT — also reuses playerName below
    private String relationName;
    private boolean entered;
    /** Owner/party display name — only populated for {@link com.github.gtexpert.blpc.api.party.RelationType#NONE}. */
    private String ownerName;
    /** UUID of the transiting player, for rendering their head on the toast; may be {@code null}. */
    private UUID playerUUID;
    /** True when the recipient of this toast is the transiting player themself (second-person wording). */
    private boolean self;

    // KIND_PARTY_EVENT
    private String eventType;
    private String playerName;
    private String extraInfo;

    // KIND_CLAIM_FAILED
    private String reason;
    private int current;
    private int max;

    public ClientNotify() {}

    /**
     * @param ownerName owner/party display name shown to the transiting player when
     *                  {@code relation} is {@link RelationType#NONE}; ignored otherwise.
     * @param self      true if this packet is being sent to the transiting player themself,
     *                  so the client renders second-person wording instead of third-person.
     */
    public static ClientNotify chunkTransit(String playerName, UUID playerId, RelationType relation, boolean entered,
                                            String ownerName, boolean self) {
        ClientNotify msg = new ClientNotify();
        msg.kind = KIND_CHUNK_TRANSIT;
        msg.playerName = playerName != null ? playerName : "";
        msg.playerUUID = playerId;
        msg.relationName = relation.name();
        msg.entered = entered;
        msg.ownerName = ownerName != null ? ownerName : "";
        msg.self = self;
        return msg;
    }

    public static ClientNotify partyEvent(String eventType, String playerName, String extraInfo) {
        ClientNotify msg = new ClientNotify();
        msg.kind = KIND_PARTY_EVENT;
        msg.eventType = eventType;
        msg.playerName = playerName != null ? playerName : "";
        msg.extraInfo = extraInfo != null ? extraInfo : "";
        return msg;
    }

    public static ClientNotify claimFailed(String reason, int current, int max) {
        ClientNotify msg = new ClientNotify();
        msg.kind = KIND_CLAIM_FAILED;
        msg.reason = reason;
        msg.current = current;
        msg.max = max;
        return msg;
    }

    public int getKind() {
        return kind;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getRelationName() {
        return relationName;
    }

    public boolean isEntered() {
        return entered;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public boolean isSelf() {
        return self;
    }

    public String getEventType() {
        return eventType;
    }

    public String getExtraInfo() {
        return extraInfo;
    }

    public String getReason() {
        return reason;
    }

    public int getCurrent() {
        return current;
    }

    public int getMax() {
        return max;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        kind = buf.readByte();
        switch (kind) {
            case KIND_CHUNK_TRANSIT -> {
                playerName = ByteBufUtils.readUTF8String(buf);
                relationName = ByteBufUtils.readUTF8String(buf);
                entered = buf.readBoolean();
                ownerName = ByteBufUtils.readUTF8String(buf);
                playerUUID = buf.readBoolean() ? new UUID(buf.readLong(), buf.readLong()) : null;
                self = buf.readBoolean();
            }
            case KIND_PARTY_EVENT -> {
                eventType = ByteBufUtils.readUTF8String(buf);
                playerName = ByteBufUtils.readUTF8String(buf);
                extraInfo = ByteBufUtils.readUTF8String(buf);
            }
            case KIND_CLAIM_FAILED -> {
                reason = ByteBufUtils.readUTF8String(buf);
                current = buf.readInt();
                max = buf.readInt();
            }
            default -> {
                // Unknown kind from a newer server — silently ignore.
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(kind);
        switch (kind) {
            case KIND_CHUNK_TRANSIT -> {
                ByteBufUtils.writeUTF8String(buf, playerName);
                ByteBufUtils.writeUTF8String(buf, relationName);
                buf.writeBoolean(entered);
                ByteBufUtils.writeUTF8String(buf, ownerName);
                buf.writeBoolean(playerUUID != null);
                if (playerUUID != null) {
                    buf.writeLong(playerUUID.getMostSignificantBits());
                    buf.writeLong(playerUUID.getLeastSignificantBits());
                }
                buf.writeBoolean(self);
            }
            case KIND_PARTY_EVENT -> {
                ByteBufUtils.writeUTF8String(buf, eventType);
                ByteBufUtils.writeUTF8String(buf, playerName);
                ByteBufUtils.writeUTF8String(buf, extraInfo);
            }
            case KIND_CLAIM_FAILED -> {
                ByteBufUtils.writeUTF8String(buf, reason);
                buf.writeInt(current);
                buf.writeInt(max);
            }
            default -> {
                // No payload for unknown kinds.
            }
        }
    }
}

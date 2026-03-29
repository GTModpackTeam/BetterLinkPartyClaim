package com.github.gtexpert.blpc.common.party;

/**
 * Party member roles with ascending privilege: MEMBER < ADMIN < OWNER.
 * Maps 1:1 to BetterQuesting's {@code EnumPartyStatus}.
 */
public enum PartyRole {

    MEMBER,
    ADMIN,
    OWNER;

    public boolean canInvite() {
        return ordinal() >= ADMIN.ordinal();
    }

    public boolean canKick(PartyRole target) {
        return ordinal() > target.ordinal();
    }

    public boolean canEditName() {
        return ordinal() >= ADMIN.ordinal();
    }

    public boolean canDisband() {
        return this == OWNER;
    }

    public boolean canChangeRole() {
        return this == OWNER;
    }

    public TrustLevel toTrustLevel() {
        return switch (this) {
            case OWNER -> TrustLevel.OWNER;
            case ADMIN -> TrustLevel.MODERATOR;
            case MEMBER -> TrustLevel.MEMBER;
        };
    }

    /** Maps a role name string (e.g. from BQu's EnumPartyStatus) to PartyRole. Defaults to MEMBER. */
    public static PartyRole fromName(String name) {
        if (name == null) return MEMBER;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return MEMBER;
        }
    }
}

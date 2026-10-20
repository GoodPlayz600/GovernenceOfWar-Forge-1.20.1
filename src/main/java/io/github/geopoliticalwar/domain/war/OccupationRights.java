package io.github.geopoliticalwar.domain.war;

public enum OccupationRights {
    EQUAL_CITIZEN,
    RESIDENT,
    SUBJECT,
    RESTRICTED,
    NONE;

    public static OccupationRights parse(String value) {
        return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }

    public boolean allowsBuild() {
        return this == EQUAL_CITIZEN;
    }

    public boolean allowsContainerUse() {
        return this == EQUAL_CITIZEN || this == RESIDENT;
    }

    public boolean allowsInteraction() {
        return this != NONE;
    }
}

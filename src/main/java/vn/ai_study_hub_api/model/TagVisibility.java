package vn.ai_study_hub_api.model;

public enum TagVisibility {
    PUBLIC,
    PRIVATE;

    public static TagVisibility fromString(String value) {
        if (value == null) return PUBLIC;
        try {
            return TagVisibility.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PUBLIC;
        }
    }
}

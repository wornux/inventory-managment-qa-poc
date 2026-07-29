package com.wornux.security.permission;

public enum AppAction {
    VIEW("view", "View"),
    CREATE("create", "Create"),
    UPDATE("update", "Update"),
    DELETE("delete", "Delete"),
    ASSIGN("assign", "Assign");

    private final String code;
    private final String label;

    AppAction(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public boolean grants(AppAction requested) {
        return this == requested || requested == VIEW;
    }
}

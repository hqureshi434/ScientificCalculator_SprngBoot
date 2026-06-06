package com.example.scicalculator.audit;

public final class AuditUserContext {
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    private AuditUserContext(){
    }

    public static void set(String username) {
        CURRENT_USER.set(username);
    }

    public static String get() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}

package dev.itemcontent.definition;

import java.util.Arrays;

public final class Identifiers {
    private Identifiers() {}
    public static String check(String id) {
        if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Некорректный ID: " + id);
        String path = id.substring(id.indexOf(':') + 1);
        if (Arrays.stream(path.split("/", -1)).anyMatch(p -> p.isEmpty() || p.equals(".") || p.equals("..")))
            throw new IllegalArgumentException("Недопустимый путь ID: " + id);
        return id;
    }
}

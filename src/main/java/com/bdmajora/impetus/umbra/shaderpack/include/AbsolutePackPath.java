package com.bdmajora.impetus.umbra.shaderpack.include;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Normalised absolute path inside a pack.
public final class AbsolutePackPath {
    // Path text.
    private final String path;

    // Creates an absolute pack path from its path.
    private AbsolutePackPath(String path) {
        this.path = path;
    }

    // Creates from the absolute path.
    public static AbsolutePackPath fromAbsolutePath(String path) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Not an absolute path: " + path);
        }
        return new AbsolutePackPath(normalize(path));
    }

    // Returns a path relative to the folder of this path.
    public AbsolutePackPath resolve(String target) {
        if (target.startsWith("/")) {
            return fromAbsolutePath(target);
        }
        int lastSlash = this.path.lastIndexOf('/');
        String parent = lastSlash <= 0 ? "/" : this.path.substring(0, lastSlash + 1);
        return fromAbsolutePath(parent + target);
    }

    // Returns the path string.
    public String getPathString() {
        return this.path;
    }

    // Returns a path with dot segments resolved.
    private static String normalize(String raw) {
        String[] segments = raw.split("/");
        List<String> out = new ArrayList<>();
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (!out.isEmpty()) {
                    out.remove(out.size() - 1);
                }
                continue;
            }
            out.add(segment);
        }
        return "/" + String.join("/", out);
    }

    // Returns whether it equals.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbsolutePackPath)) {
            return false;
        }
        return this.path.equals(((AbsolutePackPath) o).path);
    }

    // Computes the hash of the code.
    @Override
    public int hashCode() {
        return Objects.hashCode(this.path);
    }

    // Returns the path text.
    @Override
    public String toString() {
        return "AbsolutePackPath[" + this.path + "]";
    }
}

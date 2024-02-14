package com.google.debugging.sourcemap;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/**
 * Simple representation of information made available when parsing a sourcemap file.
 */
@AutoValue
public abstract class OriginalMapping {
    public enum Precision {
        EXACT,
        APPROXIMATE_LINE;
    }

    public abstract Builder toBuilder();
    public static Builder builder() {
        return new AutoValue_OriginalMapping.Builder();
    }

    /**
     * The original source file.
     */
    public abstract String getOriginalFile();

    /**
     * The line in the original file.
     */
    public abstract int getLineNumber();

    /**
     * The column number on the line.
     */
    public abstract int getColumnPosition();

    /**
     * The original name of the identifier, if any.
     */
    public abstract Optional<String> getIdentifier();

    /**
     * The type of retrieval performed to get this mapping.
     */
    public abstract Precision getPrecision();

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setOriginalFile(String originalFile);
        public abstract Builder setLineNumber(int lineNumber);
        public abstract Builder setColumnPosition(int columnPosition);
        public abstract Builder setIdentifier(String identifier);
        public abstract Builder setPrecision(Precision precision);
        public abstract OriginalMapping build();
    }
}

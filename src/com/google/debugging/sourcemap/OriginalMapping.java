package com.google.debugging.sourcemap;

import com.google.auto.value.AutoValue;
import java.util.Optional;

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

    public abstract String getOriginalFile();

    public abstract int getLineNumber();

    public abstract int getColumnPosition();

    public abstract Optional<String> getIdentifier();

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

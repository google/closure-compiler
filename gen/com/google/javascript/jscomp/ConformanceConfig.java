// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: conformance.proto

package com.google.javascript.jscomp;

/**
 * Protobuf type {@code jscomp.ConformanceConfig}
 *
 * <pre>
 * A container to describe code requirements
 * </pre>
 */
public  final class ConformanceConfig extends
    com.google.protobuf.GeneratedMessage
    implements ConformanceConfigOrBuilder {
  // Use ConformanceConfig.newBuilder() to construct.
  private ConformanceConfig(com.google.protobuf.GeneratedMessage.Builder<?> builder) {
    super(builder);
    this.unknownFields = builder.getUnknownFields();
  }
  private ConformanceConfig(boolean noInit) { this.unknownFields = com.google.protobuf.UnknownFieldSet.getDefaultInstance(); }

  private static final ConformanceConfig defaultInstance;
  public static ConformanceConfig getDefaultInstance() {
    return defaultInstance;
  }

  public ConformanceConfig getDefaultInstanceForType() {
    return defaultInstance;
  }

  private final com.google.protobuf.UnknownFieldSet unknownFields;
  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
      getUnknownFields() {
    return this.unknownFields;
  }
  private ConformanceConfig(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    initFields();
    int mutable_bitField0_ = 0;
    com.google.protobuf.UnknownFieldSet.Builder unknownFields =
        com.google.protobuf.UnknownFieldSet.newBuilder();
    try {
      boolean done = false;
      while (!done) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            done = true;
            break;
          default: {
            if (!parseUnknownField(input, unknownFields,
                                   extensionRegistry, tag)) {
              done = true;
            }
            break;
          }
          case 10: {
            if (!((mutable_bitField0_ & 0x00000001) == 0x00000001)) {
              requirement_ = new java.util.ArrayList<com.google.javascript.jscomp.Requirement>();
              mutable_bitField0_ |= 0x00000001;
            }
            requirement_.add(input.readMessage(com.google.javascript.jscomp.Requirement.PARSER, extensionRegistry));
            break;
          }
        }
      }
    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
      throw e.setUnfinishedMessage(this);
    } catch (java.io.IOException e) {
      throw new com.google.protobuf.InvalidProtocolBufferException(
          e.getMessage()).setUnfinishedMessage(this);
    } finally {
      if (((mutable_bitField0_ & 0x00000001) == 0x00000001)) {
        requirement_ = java.util.Collections.unmodifiableList(requirement_);
      }
      this.unknownFields = unknownFields.build();
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.google.javascript.jscomp.Conformance.internal_static_jscomp_ConformanceConfig_descriptor;
  }

  protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.javascript.jscomp.Conformance.internal_static_jscomp_ConformanceConfig_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.javascript.jscomp.ConformanceConfig.class, com.google.javascript.jscomp.ConformanceConfig.Builder.class);
  }

  public static com.google.protobuf.Parser<ConformanceConfig> PARSER =
      new com.google.protobuf.AbstractParser<ConformanceConfig>() {
    public ConformanceConfig parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new ConformanceConfig(input, extensionRegistry);
    }
  };

  @java.lang.Override
  public com.google.protobuf.Parser<ConformanceConfig> getParserForType() {
    return PARSER;
  }

  // repeated .jscomp.Requirement requirement = 1;
  public static final int REQUIREMENT_FIELD_NUMBER = 1;
  private java.util.List<com.google.javascript.jscomp.Requirement> requirement_;
  /**
   * <code>repeated .jscomp.Requirement requirement = 1;</code>
   */
  public java.util.List<com.google.javascript.jscomp.Requirement> getRequirementList() {
    return requirement_;
  }
  /**
   * <code>repeated .jscomp.Requirement requirement = 1;</code>
   */
  public java.util.List<? extends com.google.javascript.jscomp.RequirementOrBuilder> 
      getRequirementOrBuilderList() {
    return requirement_;
  }
  /**
   * <code>repeated .jscomp.Requirement requirement = 1;</code>
   */
  public int getRequirementCount() {
    return requirement_.size();
  }
  /**
   * <code>repeated .jscomp.Requirement requirement = 1;</code>
   */
  public com.google.javascript.jscomp.Requirement getRequirement(int index) {
    return requirement_.get(index);
  }
  /**
   * <code>repeated .jscomp.Requirement requirement = 1;</code>
   */
  public com.google.javascript.jscomp.RequirementOrBuilder getRequirementOrBuilder(
      int index) {
    return requirement_.get(index);
  }

  private void initFields() {
    requirement_ = java.util.Collections.emptyList();
  }
  private byte memoizedIsInitialized = -1;
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized != -1) return isInitialized == 1;

    for (int i = 0; i < getRequirementCount(); i++) {
      if (!getRequirement(i).isInitialized()) {
        memoizedIsInitialized = 0;
        return false;
      }
    }
    memoizedIsInitialized = 1;
    return true;
  }

  public void writeTo(com.google.protobuf.CodedOutputStream output)
                      throws java.io.IOException {
    getSerializedSize();
    for (int i = 0; i < requirement_.size(); i++) {
      output.writeMessage(1, requirement_.get(i));
    }
    getUnknownFields().writeTo(output);
  }

  private int memoizedSerializedSize = -1;
  public int getSerializedSize() {
    int size = memoizedSerializedSize;
    if (size != -1) return size;

    size = 0;
    for (int i = 0; i < requirement_.size(); i++) {
      size += com.google.protobuf.CodedOutputStream
        .computeMessageSize(1, requirement_.get(i));
    }
    size += getUnknownFields().getSerializedSize();
    memoizedSerializedSize = size;
    return size;
  }

  private static final long serialVersionUID = 0L;
  @java.lang.Override
  protected java.lang.Object writeReplace()
      throws java.io.ObjectStreamException {
    return super.writeReplace();
  }

  public static com.google.javascript.jscomp.ConformanceConfig parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.javascript.jscomp.ConformanceConfig parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.javascript.jscomp.ConformanceConfig parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.google.javascript.jscomp.ConformanceConfig parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.google.javascript.jscomp.ConformanceConfig parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return PARSER.parseFrom(input);
  }
  public static com.google.javascript.jscomp.ConformanceConfig parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseFrom(input, extensionRegistry);
  }
  public static com.google.javascript.jscomp.ConformanceConfig parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return PARSER.parseDelimitedFrom(input);
  }
  public static com.google.javascript.jscomp.ConformanceConfig parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseDelimitedFrom(input, extensionRegistry);
  }
  public static com.google.javascript.jscomp.ConformanceConfig parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return PARSER.parseFrom(input);
  }
  public static com.google.javascript.jscomp.ConformanceConfig parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return PARSER.parseFrom(input, extensionRegistry);
  }

  public static Builder newBuilder() { return Builder.create(); }
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder(com.google.javascript.jscomp.ConformanceConfig prototype) {
    return newBuilder().mergeFrom(prototype);
  }
  public Builder toBuilder() { return newBuilder(this); }

  @java.lang.Override
  protected Builder newBuilderForType(
      com.google.protobuf.GeneratedMessage.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * Protobuf type {@code jscomp.ConformanceConfig}
   *
   * <pre>
   * A container to describe code requirements
   * </pre>
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessage.Builder<Builder>
     implements com.google.javascript.jscomp.ConformanceConfigOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.google.javascript.jscomp.Conformance.internal_static_jscomp_ConformanceConfig_descriptor;
    }

    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.javascript.jscomp.Conformance.internal_static_jscomp_ConformanceConfig_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.javascript.jscomp.ConformanceConfig.class, com.google.javascript.jscomp.ConformanceConfig.Builder.class);
    }

    // Construct using com.google.javascript.jscomp.ConformanceConfig.newBuilder()
    private Builder() {
      maybeForceBuilderInitialization();
    }

    private Builder(
        com.google.protobuf.GeneratedMessage.BuilderParent parent) {
      super(parent);
      maybeForceBuilderInitialization();
    }
    private void maybeForceBuilderInitialization() {
      if (com.google.protobuf.GeneratedMessage.alwaysUseFieldBuilders) {
        getRequirementFieldBuilder();
      }
    }
    private static Builder create() {
      return new Builder();
    }

    public Builder clear() {
      super.clear();
      if (requirementBuilder_ == null) {
        requirement_ = java.util.Collections.emptyList();
        bitField0_ = (bitField0_ & ~0x00000001);
      } else {
        requirementBuilder_.clear();
      }
      return this;
    }

    public Builder clone() {
      return create().mergeFrom(buildPartial());
    }

    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.google.javascript.jscomp.Conformance.internal_static_jscomp_ConformanceConfig_descriptor;
    }

    public com.google.javascript.jscomp.ConformanceConfig getDefaultInstanceForType() {
      return com.google.javascript.jscomp.ConformanceConfig.getDefaultInstance();
    }

    public com.google.javascript.jscomp.ConformanceConfig build() {
      com.google.javascript.jscomp.ConformanceConfig result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    public com.google.javascript.jscomp.ConformanceConfig buildPartial() {
      com.google.javascript.jscomp.ConformanceConfig result = new com.google.javascript.jscomp.ConformanceConfig(this);
      @SuppressWarnings("unused")
	int from_bitField0_ = bitField0_;
      if (requirementBuilder_ == null) {
        if (((bitField0_ & 0x00000001) == 0x00000001)) {
          requirement_ = java.util.Collections.unmodifiableList(requirement_);
          bitField0_ = (bitField0_ & ~0x00000001);
        }
        result.requirement_ = requirement_;
      } else {
        result.requirement_ = requirementBuilder_.build();
      }
      onBuilt();
      return result;
    }

    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.google.javascript.jscomp.ConformanceConfig) {
        return mergeFrom((com.google.javascript.jscomp.ConformanceConfig)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.javascript.jscomp.ConformanceConfig other) {
      if (other == com.google.javascript.jscomp.ConformanceConfig.getDefaultInstance()) return this;
      if (requirementBuilder_ == null) {
        if (!other.requirement_.isEmpty()) {
          if (requirement_.isEmpty()) {
            requirement_ = other.requirement_;
            bitField0_ = (bitField0_ & ~0x00000001);
          } else {
            ensureRequirementIsMutable();
            requirement_.addAll(other.requirement_);
          }
          onChanged();
        }
      } else {
        if (!other.requirement_.isEmpty()) {
          if (requirementBuilder_.isEmpty()) {
            requirementBuilder_.dispose();
            requirementBuilder_ = null;
            requirement_ = other.requirement_;
            bitField0_ = (bitField0_ & ~0x00000001);
            requirementBuilder_ = 
              com.google.protobuf.GeneratedMessage.alwaysUseFieldBuilders ?
                 getRequirementFieldBuilder() : null;
          } else {
            requirementBuilder_.addAllMessages(other.requirement_);
          }
        }
      }
      this.mergeUnknownFields(other.getUnknownFields());
      return this;
    }

    public final boolean isInitialized() {
      for (int i = 0; i < getRequirementCount(); i++) {
        if (!getRequirement(i).isInitialized()) {
          
          return false;
        }
      }
      return true;
    }

    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      com.google.javascript.jscomp.ConformanceConfig parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.google.javascript.jscomp.ConformanceConfig) e.getUnfinishedMessage();
        throw e;
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }
    private int bitField0_;

    // repeated .jscomp.Requirement requirement = 1;
    private java.util.List<com.google.javascript.jscomp.Requirement> requirement_ =
      java.util.Collections.emptyList();
    private void ensureRequirementIsMutable() {
      if (!((bitField0_ & 0x00000001) == 0x00000001)) {
        requirement_ = new java.util.ArrayList<com.google.javascript.jscomp.Requirement>(requirement_);
        bitField0_ |= 0x00000001;
       }
    }

    private com.google.protobuf.RepeatedFieldBuilder<
        com.google.javascript.jscomp.Requirement, com.google.javascript.jscomp.Requirement.Builder, com.google.javascript.jscomp.RequirementOrBuilder> requirementBuilder_;

    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public java.util.List<com.google.javascript.jscomp.Requirement> getRequirementList() {
      if (requirementBuilder_ == null) {
        return java.util.Collections.unmodifiableList(requirement_);
      } else {
        return requirementBuilder_.getMessageList();
      }
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public int getRequirementCount() {
      if (requirementBuilder_ == null) {
        return requirement_.size();
      } else {
        return requirementBuilder_.getCount();
      }
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public com.google.javascript.jscomp.Requirement getRequirement(int index) {
      if (requirementBuilder_ == null) {
        return requirement_.get(index);
      } else {
        return requirementBuilder_.getMessage(index);
      }
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public Builder setRequirement(
        int index, com.google.javascript.jscomp.Requirement value) {
      if (requirementBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureRequirementIsMutable();
        requirement_.set(index, value);
        onChanged();
      } else {
        requirementBuilder_.setMessage(index, value);
      }
      return this;
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public Builder setRequirement(
        int index, com.google.javascript.jscomp.Requirement.Builder builderForValue) {
      if (requirementBuilder_ == null) {
        ensureRequirementIsMutable();
        requirement_.set(index, builderForValue.build());
        onChanged();
      } else {
        requirementBuilder_.setMessage(index, builderForValue.build());
      }
      return this;
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public Builder addRequirement(com.google.javascript.jscomp.Requirement value) {
      if (requirementBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureRequirementIsMutable();
        requirement_.add(value);
        onChanged();
      } else {
        requirementBuilder_.addMessage(value);
      }
      return this;
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public Builder addRequirement(
        int index, com.google.javascript.jscomp.Requirement value) {
      if (requirementBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        ensureRequirementIsMutable();
        requirement_.add(index, value);
        onChanged();
      } else {
        requirementBuilder_.addMessage(index, value);
      }
      return this;
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public Builder addRequirement(
        com.google.javascript.jscomp.Requirement.Builder builderForValue) {
      if (requirementBuilder_ == null) {
        ensureRequirementIsMutable();
        requirement_.add(builderForValue.build());
        onChanged();
      } else {
        requirementBuilder_.addMessage(builderForValue.build());
      }
      return this;
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public Builder addRequirement(
        int index, com.google.javascript.jscomp.Requirement.Builder builderForValue) {
      if (requirementBuilder_ == null) {
        ensureRequirementIsMutable();
        requirement_.add(index, builderForValue.build());
        onChanged();
      } else {
        requirementBuilder_.addMessage(index, builderForValue.build());
      }
      return this;
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public Builder addAllRequirement(
        java.lang.Iterable<? extends com.google.javascript.jscomp.Requirement> values) {
      if (requirementBuilder_ == null) {
        ensureRequirementIsMutable();
        super.addAll(values, requirement_);
        onChanged();
      } else {
        requirementBuilder_.addAllMessages(values);
      }
      return this;
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public Builder clearRequirement() {
      if (requirementBuilder_ == null) {
        requirement_ = java.util.Collections.emptyList();
        bitField0_ = (bitField0_ & ~0x00000001);
        onChanged();
      } else {
        requirementBuilder_.clear();
      }
      return this;
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public Builder removeRequirement(int index) {
      if (requirementBuilder_ == null) {
        ensureRequirementIsMutable();
        requirement_.remove(index);
        onChanged();
      } else {
        requirementBuilder_.remove(index);
      }
      return this;
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public com.google.javascript.jscomp.Requirement.Builder getRequirementBuilder(
        int index) {
      return getRequirementFieldBuilder().getBuilder(index);
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public com.google.javascript.jscomp.RequirementOrBuilder getRequirementOrBuilder(
        int index) {
      if (requirementBuilder_ == null) {
        return requirement_.get(index);  } else {
        return requirementBuilder_.getMessageOrBuilder(index);
      }
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public java.util.List<? extends com.google.javascript.jscomp.RequirementOrBuilder> 
         getRequirementOrBuilderList() {
      if (requirementBuilder_ != null) {
        return requirementBuilder_.getMessageOrBuilderList();
      } else {
        return java.util.Collections.unmodifiableList(requirement_);
      }
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public com.google.javascript.jscomp.Requirement.Builder addRequirementBuilder() {
      return getRequirementFieldBuilder().addBuilder(
          com.google.javascript.jscomp.Requirement.getDefaultInstance());
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public com.google.javascript.jscomp.Requirement.Builder addRequirementBuilder(
        int index) {
      return getRequirementFieldBuilder().addBuilder(
          index, com.google.javascript.jscomp.Requirement.getDefaultInstance());
    }
    /**
     * <code>repeated .jscomp.Requirement requirement = 1;</code>
     */
    public java.util.List<com.google.javascript.jscomp.Requirement.Builder> 
         getRequirementBuilderList() {
      return getRequirementFieldBuilder().getBuilderList();
    }
    private com.google.protobuf.RepeatedFieldBuilder<
        com.google.javascript.jscomp.Requirement, com.google.javascript.jscomp.Requirement.Builder, com.google.javascript.jscomp.RequirementOrBuilder> 
        getRequirementFieldBuilder() {
      if (requirementBuilder_ == null) {
        requirementBuilder_ = new com.google.protobuf.RepeatedFieldBuilder<
            com.google.javascript.jscomp.Requirement, com.google.javascript.jscomp.Requirement.Builder, com.google.javascript.jscomp.RequirementOrBuilder>(
                requirement_,
                ((bitField0_ & 0x00000001) == 0x00000001),
                getParentForChildren(),
                isClean());
        requirement_ = null;
      }
      return requirementBuilder_;
    }

    // @@protoc_insertion_point(builder_scope:jscomp.ConformanceConfig)
  }

  static {
    defaultInstance = new ConformanceConfig(true);
    defaultInstance.initFields();
  }

  // @@protoc_insertion_point(class_scope:jscomp.ConformanceConfig)
}


/*
 * Copyright 2026 The Closure Compiler Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @fileoverview Externs for WebGPU API.
 * @see https://gpuweb.github.io/gpuweb/
 *
 * @externs
 */

/**
 * @constructor
 * @see https://gpuweb.github.io/gpuweb/#gpuobjectbase
 */
function GPUObjectBase() {}
/** @type {string} */
GPUObjectBase.prototype.label;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpuobjectdescriptorbase
 */
function GPUObjectDescriptorBase() {}
/** @type {string|undefined} */
GPUObjectDescriptorBase.prototype.label;

/**
 * @constructor
 * @implements {ReadonlySet<string>}
 * @see https://developer.mozilla.org/docs/Web/API/GPUSupportedFeatures
 */
function GPUSupportedFeatures() {}
/**
 * @override
 * @param {function(string, string, !GPUSupportedFeatures): undefined}
 *     callbackfn
 * @param {*=} opt_thisArg
 * @return {undefined}
 */
GPUSupportedFeatures.prototype.forEach = function(callbackfn, opt_thisArg) {};
/**
 * @override
 * @param {string} value
 * @return {boolean}
 * @nosideeffects
 */
GPUSupportedFeatures.prototype.has = function(value) {};
/**
 * @override
 * @type {number}
 */
GPUSupportedFeatures.prototype.size;
/** @override */
GPUSupportedFeatures.prototype[Symbol.iterator] = function() {};
/**
 * @override
 * @return {!IteratorIterable<!Array<string>>}
 * @nosideeffects
 */
GPUSupportedFeatures.prototype.entries = function() {};
/**
 * @override
 * @return {!IteratorIterable<string>}
 * @nosideeffects
 */
GPUSupportedFeatures.prototype.keys = function() {};
/**
 * @override
 * @return {!IteratorIterable<string>}
 * @nosideeffects
 */
GPUSupportedFeatures.prototype.values = function() {};
/**
 * @override
 * @template OTHER_VALUE
 * @param {!ReadonlySetLike<OTHER_VALUE>} other
 * @return {!Set<OTHER_VALUE|string>}
 * @nosideeffects
 */
GPUSupportedFeatures.prototype.union = function(other) {};
/**
 * @override
 * @template OTHER_VALUE
 * @param {!ReadonlySetLike<OTHER_VALUE>} other
 * @return {!Set<string>}
 * @nosideeffects
 */
GPUSupportedFeatures.prototype.intersection = function(other) {};
/**
 * @override
 * @template OTHER_VALUE
 * @param {!ReadonlySetLike<OTHER_VALUE>} other
 * @return {!Set<string>}
 * @nosideeffects
 */
GPUSupportedFeatures.prototype.difference = function(other) {};
/**
 * @override
 * @template OTHER_VALUE
 * @param {!ReadonlySetLike<OTHER_VALUE>} other
 * @return {!Set<OTHER_VALUE|string>}
 * @nosideeffects
 */
GPUSupportedFeatures.prototype.symmetricDifference = function(other) {};
/**
 * @override
 * @param {!ReadonlySetLike<*>} other
 * @return {boolean}
 * @nosideeffects
 */
GPUSupportedFeatures.prototype.isSubsetOf = function(other) {};
/**
 * @override
 * @param {!ReadonlySetLike<*>} other
 * @return {boolean}
 * @nosideeffects
 */
GPUSupportedFeatures.prototype.isSupersetOf = function(other) {};
/**
 * @override
 * @param {!ReadonlySetLike<*>} other
 * @return {boolean}
 * @nosideeffects
 */
GPUSupportedFeatures.prototype.isDisjointFrom = function(other) {};

/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/GPUSupportedLimits
 */
function GPUSupportedLimits() {}
/** @type {number} */
GPUSupportedLimits.prototype.maxBindGroups;
/** @type {number} */
GPUSupportedLimits.prototype.maxBindGroupsPlusVertexBuffers;
/** @type {number} */
GPUSupportedLimits.prototype.maxBindingsPerBindGroup;
/** @type {number} */
GPUSupportedLimits.prototype.maxBufferSize;
/** @type {number} */
GPUSupportedLimits.prototype.maxColorAttachmentBytesPerSample;
/** @type {number} */
GPUSupportedLimits.prototype.maxColorAttachments;
/** @type {number} */
GPUSupportedLimits.prototype.maxComputeInvocationsPerWorkgroup;
/** @type {number} */
GPUSupportedLimits.prototype.maxComputeWorkgroupSizeX;
/** @type {number} */
GPUSupportedLimits.prototype.maxComputeWorkgroupSizeY;
/** @type {number} */
GPUSupportedLimits.prototype.maxComputeWorkgroupSizeZ;
/** @type {number} */
GPUSupportedLimits.prototype.maxComputeWorkgroupStorageSize;
/** @type {number} */
GPUSupportedLimits.prototype.maxComputeWorkgroupsPerDimension;
/** @type {number} */
GPUSupportedLimits.prototype.maxDynamicStorageBuffersPerPipelineLayout;
/** @type {number} */
GPUSupportedLimits.prototype.maxDynamicUniformBuffersPerPipelineLayout;
/** @type {number} */
GPUSupportedLimits.prototype.maxInterStageShaderVariables;
/** @type {number} */
GPUSupportedLimits.prototype.maxSampledTexturesPerShaderStage;
/** @type {number} */
GPUSupportedLimits.prototype.maxSamplersPerShaderStage;
/** @type {number} */
GPUSupportedLimits.prototype.maxStorageBufferBindingSize;
/** @type {number} */
GPUSupportedLimits.prototype.maxStorageBuffersPerShaderStage;
/** @type {number} */
GPUSupportedLimits.prototype.maxStorageTexturesPerShaderStage;
/** @type {number} */
GPUSupportedLimits.prototype.maxTextureArrayLayers;
/** @type {number} */
GPUSupportedLimits.prototype.maxTextureDimension1D;
/** @type {number} */
GPUSupportedLimits.prototype.maxTextureDimension2D;
/** @type {number} */
GPUSupportedLimits.prototype.maxTextureDimension3D;
/** @type {number} */
GPUSupportedLimits.prototype.maxUniformBufferBindingSize;
/** @type {number} */
GPUSupportedLimits.prototype.maxUniformBuffersPerShaderStage;
/** @type {number} */
GPUSupportedLimits.prototype.maxVertexAttributes;
/** @type {number} */
GPUSupportedLimits.prototype.maxVertexBufferArrayStride;
/** @type {number} */
GPUSupportedLimits.prototype.maxVertexBuffers;
/** @type {number} */
GPUSupportedLimits.prototype.minStorageBufferOffsetAlignment;
/** @type {number} */
GPUSupportedLimits.prototype.minUniformBufferOffsetAlignment;

/**
 * @constructor
 * @implements {ReadonlySet<string>}
 * @see https://developer.mozilla.org/docs/Web/API/WGSLLanguageFeatures
 */
function WGSLLanguageFeatures() {}
/**
 * @override
 * @param {function(string, string, !WGSLLanguageFeatures): undefined}
 *     callbackfn
 * @param {*=} opt_thisArg
 * @return {undefined}
 */
WGSLLanguageFeatures.prototype.forEach = function(callbackfn, opt_thisArg) {};
/**
 * @override
 * @param {string} value
 * @return {boolean}
 * @nosideeffects
 */
WGSLLanguageFeatures.prototype.has = function(value) {};
/**
 * @override
 * @type {number}
 */
WGSLLanguageFeatures.prototype.size;
/** @override */
WGSLLanguageFeatures.prototype[Symbol.iterator] = function() {};
/**
 * @override
 * @return {!IteratorIterable<!Array<string>>}
 * @nosideeffects
 */
WGSLLanguageFeatures.prototype.entries = function() {};
/**
 * @override
 * @return {!IteratorIterable<string>}
 * @nosideeffects
 */
WGSLLanguageFeatures.prototype.keys = function() {};
/**
 * @override
 * @return {!IteratorIterable<string>}
 * @nosideeffects
 */
WGSLLanguageFeatures.prototype.values = function() {};
/**
 * @override
 * @template OTHER_VALUE
 * @param {!ReadonlySetLike<OTHER_VALUE>} other
 * @return {!Set<OTHER_VALUE|string>}
 * @nosideeffects
 */
WGSLLanguageFeatures.prototype.union = function(other) {};
/**
 * @override
 * @template OTHER_VALUE
 * @param {!ReadonlySetLike<OTHER_VALUE>} other
 * @return {!Set<string>}
 * @nosideeffects
 */
WGSLLanguageFeatures.prototype.intersection = function(other) {};
/**
 * @override
 * @template OTHER_VALUE
 * @param {!ReadonlySetLike<OTHER_VALUE>} other
 * @return {!Set<string>}
 * @nosideeffects
 */
WGSLLanguageFeatures.prototype.difference = function(other) {};
/**
 * @override
 * @template OTHER_VALUE
 * @param {!ReadonlySetLike<OTHER_VALUE>} other
 * @return {!Set<OTHER_VALUE|string>}
 * @nosideeffects
 */
WGSLLanguageFeatures.prototype.symmetricDifference = function(other) {};
/**
 * @override
 * @param {!ReadonlySetLike<*>} other
 * @return {boolean}
 * @nosideeffects
 */
WGSLLanguageFeatures.prototype.isSubsetOf = function(other) {};
/**
 * @override
 * @param {!ReadonlySetLike<*>} other
 * @return {boolean}
 * @nosideeffects
 */
WGSLLanguageFeatures.prototype.isSupersetOf = function(other) {};
/**
 * @override
 * @param {!ReadonlySetLike<*>} other
 * @return {boolean}
 * @nosideeffects
 */
WGSLLanguageFeatures.prototype.isDisjointFrom = function(other) {};

/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/GPUAdapterInfo
 */
function GPUAdapterInfo() {}
/** @type {string} */
GPUAdapterInfo.prototype.architecture;
/** @type {string} */
GPUAdapterInfo.prototype.description;
/** @type {string} */
GPUAdapterInfo.prototype.device;
/** @type {boolean} */
GPUAdapterInfo.prototype.isFallbackAdapter;
/** @type {number} */
GPUAdapterInfo.prototype.subgroupMaxSize;
/** @type {number} */
GPUAdapterInfo.prototype.subgroupMinSize;
/** @type {string} */
GPUAdapterInfo.prototype.vendor;

/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/GPUError
 */
function GPUError() {}
/** @type {string} */
GPUError.prototype.message;

/**
 * @constructor
 * @extends {GPUError}
 * @see https://developer.mozilla.org/docs/Web/API/GPUInternalError
 * @param {string} message
 */
function GPUInternalError(message) {}

/**
 * @constructor
 * @extends {GPUError}
 * @see https://developer.mozilla.org/docs/Web/API/GPUOutOfMemoryError
 * @param {string} message
 */
function GPUOutOfMemoryError(message) {}

/**
 * @constructor
 * @extends {GPUError}
 * @see https://developer.mozilla.org/docs/Web/API/GPUValidationError
 * @param {string} message
 */
function GPUValidationError(message) {}

/** @typedef {number} */
var GPUBufferDynamicOffset;

/** @typedef {number} */
var GPUBufferUsageFlags;

/** @typedef {number} */
var GPUColorWriteFlags;

/** @typedef {!ImageBitmap|!ImageData|!HTMLImageElement|!HTMLVideoElement|!VideoFrame|!HTMLCanvasElement|!OffscreenCanvas} */
var GPUCopyExternalImageSource;

/** @typedef {number} */
var GPUDepthBias;

/** @typedef {number} */
var GPUFlagsConstant;

/** @typedef {number} */
var GPUIndex32;

/** @typedef {number} */
var GPUIntegerCoordinate;

/** @typedef {number} */
var GPUIntegerCoordinateOut;

/** @typedef {number} */
var GPUMapModeFlags;

/** @typedef {number} */
var GPUPipelineConstantValue;

/** @typedef {number} */
var GPUSampleMask;

/** @typedef {number} */
var GPUShaderStageFlags;

/** @typedef {number} */
var GPUSignedOffset32;

/** @typedef {number} */
var GPUSize32;

/** @typedef {number} */
var GPUSize32Out;

/** @typedef {number} */
var GPUSize64;

/** @typedef {number} */
var GPUSize64Out;

/** @typedef {number} */
var GPUStencilValue;

/** @typedef {number} */
var GPUTextureUsageFlags;

/**
 * @typedef {string}
 * Valid values: "clamp-to-edge", "mirror-repeat", "repeat"
 */
var GPUAddressMode;

/**
 * @typedef {string}
 * Valid values: "auto"
 */
var GPUAutoLayoutMode;

/**
 * @typedef {string}
 * Valid values: "constant", "dst", "dst-alpha", "one", "one-minus-constant",
 *     "one-minus-dst", "one-minus-dst-alpha", "one-minus-src",
 *     "one-minus-src-alpha", "src", "src-alpha", "src-alpha-saturated", "zero"
 */
var GPUBlendFactor;

/**
 * @typedef {string}
 * Valid values: "add", "max", "min", "reverse-subtract", "subtract"
 */
var GPUBlendOperation;

/**
 * @typedef {string}
 * Valid values: "read-only-storage", "storage", "uniform"
 */
var GPUBufferBindingType;

/**
 * @typedef {string}
 * Valid values: "mapped", "pending", "unmapped"
 */
var GPUBufferMapState;

/**
 * @typedef {string}
 * Valid values: "opaque", "premultiplied"
 */
var GPUCanvasAlphaMode;

/**
 * @typedef {string}
 * Valid values: "extended", "standard"
 */
var GPUCanvasToneMappingMode;

/**
 * @typedef {string}
 * Valid values: "always", "equal", "greater", "greater-equal", "less",
 *     "less-equal", "never", "not-equal"
 */
var GPUCompareFunction;

/**
 * @typedef {string}
 * Valid values: "error", "info", "warning"
 */
var GPUCompilationMessageType;

/**
 * @typedef {string}
 * Valid values: "back", "front", "none"
 */
var GPUCullMode;

/**
 * @typedef {string}
 * Valid values: "destroyed", "unknown"
 */
var GPUDeviceLostReason;

/**
 * @typedef {string}
 * Valid values: "internal", "out-of-memory", "validation"
 */
var GPUErrorFilter;

/**
 * @typedef {string}
 * Valid values: "bgra8unorm-storage", "clip-distances",
 *     "core-features-and-limits", "depth-clip-control",
 *     "depth32float-stencil8", "dual-source-blending", "float32-blendable",
 *     "float32-filterable", "indirect-first-instance", "primitive-index",
 *     "rg11b10ufloat-renderable", "shader-f16", "subgroups",
 *     "texture-compression-astc", "texture-compression-astc-sliced-3d",
 *     "texture-compression-bc", "texture-compression-bc-sliced-3d",
 *     "texture-compression-etc2", "texture-formats-tier1", "timestamp-query"
 */
var GPUFeatureName;

/**
 * @typedef {string}
 * Valid values: "linear", "nearest"
 */
var GPUFilterMode;

/**
 * @typedef {string}
 * Valid values: "ccw", "cw"
 */
var GPUFrontFace;

/**
 * @typedef {string}
 * Valid values: "uint16", "uint32"
 */
var GPUIndexFormat;

/**
 * @typedef {string}
 * Valid values: "clear", "load"
 */
var GPULoadOp;

/**
 * @typedef {string}
 * Valid values: "linear", "nearest"
 */
var GPUMipmapFilterMode;

/**
 * @typedef {string}
 * Valid values: "internal", "validation"
 */
var GPUPipelineErrorReason;

/**
 * @typedef {string}
 * Valid values: "high-performance", "low-power"
 */
var GPUPowerPreference;

/**
 * @typedef {string}
 * Valid values: "line-list", "line-strip", "point-list", "triangle-list",
 *     "triangle-strip"
 */
var GPUPrimitiveTopology;

/**
 * @typedef {string}
 * Valid values: "occlusion", "timestamp"
 */
var GPUQueryType;

/**
 * @typedef {string}
 * Valid values: "comparison", "filtering", "non-filtering"
 */
var GPUSamplerBindingType;

/**
 * @typedef {string}
 * Valid values: "decrement-clamp", "decrement-wrap", "increment-clamp",
 *     "increment-wrap", "invert", "keep", "replace", "zero"
 */
var GPUStencilOperation;

/**
 * @typedef {string}
 * Valid values: "read-only", "read-write", "write-only"
 */
var GPUStorageTextureAccess;

/**
 * @typedef {string}
 * Valid values: "discard", "store"
 */
var GPUStoreOp;

/**
 * @typedef {string}
 * Valid values: "all", "depth-only", "stencil-only"
 */
var GPUTextureAspect;

/**
 * @typedef {string}
 * Valid values: "1d", "2d", "3d"
 */
var GPUTextureDimension;

/**
 * @typedef {string}
 * Valid values: "astc-10x10-unorm", "astc-10x10-unorm-srgb",
 *     "astc-10x5-unorm", "astc-10x5-unorm-srgb", "astc-10x6-unorm",
 *     "astc-10x6-unorm-srgb", "astc-10x8-unorm", "astc-10x8-unorm-srgb",
 *     "astc-12x10-unorm", "astc-12x10-unorm-srgb", "astc-12x12-unorm",
 *     "astc-12x12-unorm-srgb", "astc-4x4-unorm", "astc-4x4-unorm-srgb",
 *     "astc-5x4-unorm", "astc-5x4-unorm-srgb", "astc-5x5-unorm",
 *     "astc-5x5-unorm-srgb", "astc-6x5-unorm", "astc-6x5-unorm-srgb",
 *     "astc-6x6-unorm", "astc-6x6-unorm-srgb", "astc-8x5-unorm",
 *     "astc-8x5-unorm-srgb", "astc-8x6-unorm", "astc-8x6-unorm-srgb",
 *     "astc-8x8-unorm", "astc-8x8-unorm-srgb", "bc1-rgba-unorm",
 *     "bc1-rgba-unorm-srgb", "bc2-rgba-unorm", "bc2-rgba-unorm-srgb",
 *     "bc3-rgba-unorm", "bc3-rgba-unorm-srgb", "bc4-r-snorm", "bc4-r-unorm",
 *     "bc5-rg-snorm", "bc5-rg-unorm", "bc6h-rgb-float", "bc6h-rgb-ufloat",
 *     "bc7-rgba-unorm", "bc7-rgba-unorm-srgb", "bgra8unorm",
 *     "bgra8unorm-srgb", "depth16unorm", "depth24plus",
 *     "depth24plus-stencil8", "depth32float", "depth32float-stencil8",
 *     "eac-r11snorm", "eac-r11unorm", "eac-rg11snorm", "eac-rg11unorm",
 *     "etc2-rgb8a1unorm", "etc2-rgb8a1unorm-srgb", "etc2-rgb8unorm",
 *     "etc2-rgb8unorm-srgb", "etc2-rgba8unorm", "etc2-rgba8unorm-srgb",
 *     "r16float", "r16sint", "r16snorm", "r16uint", "r16unorm", "r32float",
 *     "r32sint", "r32uint", "r8sint", "r8snorm", "r8uint", "r8unorm",
 *     "rg11b10ufloat", "rg16float", "rg16sint", "rg16snorm", "rg16uint",
 *     "rg16unorm", "rg32float", "rg32sint", "rg32uint", "rg8sint",
 *     "rg8snorm", "rg8uint", "rg8unorm", "rgb10a2uint", "rgb10a2unorm",
 *     "rgb9e5ufloat", "rgba16float", "rgba16sint", "rgba16snorm",
 *     "rgba16uint", "rgba16unorm", "rgba32float", "rgba32sint", "rgba32uint",
 *     "rgba8sint", "rgba8snorm", "rgba8uint", "rgba8unorm",
 *     "rgba8unorm-srgb", "stencil8"
 */
var GPUTextureFormat;

/**
 * @typedef {string}
 * Valid values: "depth", "float", "sint", "uint", "unfilterable-float"
 */
var GPUTextureSampleType;

/**
 * @typedef {string}
 * Valid values: "1d", "2d", "2d-array", "3d", "cube", "cube-array"
 */
var GPUTextureViewDimension;

/**
 * @typedef {string}
 * Valid values: "float16", "float16x2", "float16x4", "float32", "float32x2",
 *     "float32x3", "float32x4", "sint16", "sint16x2", "sint16x4", "sint32",
 *     "sint32x2", "sint32x3", "sint32x4", "sint8", "sint8x2", "sint8x4",
 *     "snorm16", "snorm16x2", "snorm16x4", "snorm8", "snorm8x2", "snorm8x4",
 *     "uint16", "uint16x2", "uint16x4", "uint32", "uint32x2", "uint32x3",
 *     "uint32x4", "uint8", "uint8x2", "uint8x4", "unorm10-10-10-2",
 *     "unorm16", "unorm16x2", "unorm16x4", "unorm8", "unorm8x2", "unorm8x4",
 *     "unorm8x4-bgra"
 */
var GPUVertexFormat;

/**
 * @typedef {string}
 * Valid values: "instance", "vertex"
 */
var GPUVertexStepMode;

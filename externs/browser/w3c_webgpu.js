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

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpuextent3ddict
 */
function GPUExtent3DDict() {}
/** @type {!GPUIntegerCoordinate|undefined} */
GPUExtent3DDict.prototype.depthOrArrayLayers;
/** @type {!GPUIntegerCoordinate|undefined} */
GPUExtent3DDict.prototype.height;
/** @type {!GPUIntegerCoordinate} */
GPUExtent3DDict.prototype.width;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpuorigin2ddict
 */
function GPUOrigin2DDict() {}
/** @type {!GPUIntegerCoordinate|undefined} */
GPUOrigin2DDict.prototype.x;
/** @type {!GPUIntegerCoordinate|undefined} */
GPUOrigin2DDict.prototype.y;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpuorigin3ddict
 */
function GPUOrigin3DDict() {}
/** @type {!GPUIntegerCoordinate|undefined} */
GPUOrigin3DDict.prototype.x;
/** @type {!GPUIntegerCoordinate|undefined} */
GPUOrigin3DDict.prototype.y;
/** @type {!GPUIntegerCoordinate|undefined} */
GPUOrigin3DDict.prototype.z;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpucolordict
 */
function GPUColorDict() {}
/** @type {number} */
GPUColorDict.prototype.a;
/** @type {number} */
GPUColorDict.prototype.b;
/** @type {number} */
GPUColorDict.prototype.g;
/** @type {number} */
GPUColorDict.prototype.r;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#gpucopyexternalimagesourceinfo
 */
function GPUCopyExternalImageSourceInfo() {}
/** @type {boolean|undefined} */
GPUCopyExternalImageSourceInfo.prototype.flipY;
/** @type {!GPUOrigin2D|undefined} */
GPUCopyExternalImageSourceInfo.prototype.origin;
/** @type {!GPUCopyExternalImageSource} */
GPUCopyExternalImageSourceInfo.prototype.source;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#gputexelcopybufferlayout
 */
function GPUTexelCopyBufferLayout() {}
/** @type {!GPUSize32|undefined} */
GPUTexelCopyBufferLayout.prototype.bytesPerRow;
/** @type {!GPUSize64|undefined} */
GPUTexelCopyBufferLayout.prototype.offset;
/** @type {!GPUSize32|undefined} */
GPUTexelCopyBufferLayout.prototype.rowsPerImage;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpubufferbindinglayout
 */
function GPUBufferBindingLayout() {}
/** @type {boolean|undefined} */
GPUBufferBindingLayout.prototype.hasDynamicOffset;
/** @type {!GPUSize64|undefined} */
GPUBufferBindingLayout.prototype.minBindingSize;
/** @type {!GPUBufferBindingType|undefined} */
GPUBufferBindingLayout.prototype.type;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpusamplerbindinglayout
 */
function GPUSamplerBindingLayout() {}
/** @type {!GPUSamplerBindingType|undefined} */
GPUSamplerBindingLayout.prototype.type;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gputexturebindinglayout
 */
function GPUTextureBindingLayout() {}
/** @type {boolean|undefined} */
GPUTextureBindingLayout.prototype.multisampled;
/** @type {!GPUTextureSampleType|undefined} */
GPUTextureBindingLayout.prototype.sampleType;
/** @type {!GPUTextureViewDimension|undefined} */
GPUTextureBindingLayout.prototype.viewDimension;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpustoragetexturebindinglayout
 */
function GPUStorageTextureBindingLayout() {}
/** @type {!GPUStorageTextureAccess|undefined} */
GPUStorageTextureBindingLayout.prototype.access;
/** @type {!GPUTextureFormat} */
GPUStorageTextureBindingLayout.prototype.format;
/** @type {!GPUTextureViewDimension|undefined} */
GPUStorageTextureBindingLayout.prototype.viewDimension;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpuexternaltexturebindinglayout
 */
function GPUExternalTextureBindingLayout() {}

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpubufferdescriptor
 */
function GPUBufferDescriptor() {}
/** @type {boolean|undefined} */
GPUBufferDescriptor.prototype.mappedAtCreation;
/** @type {!GPUSize64} */
GPUBufferDescriptor.prototype.size;
/** @type {!GPUBufferUsageFlags} */
GPUBufferDescriptor.prototype.usage;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPUBuffer
 */
function GPUBuffer() {}
/** @type {!GPUBufferMapState} */
GPUBuffer.prototype.mapState;
/** @type {!GPUSize64Out} */
GPUBuffer.prototype.size;
/** @type {!GPUFlagsConstant} */
GPUBuffer.prototype.usage;
/** @return {undefined} */
GPUBuffer.prototype.destroy = function() {};
/**
 * @param {!GPUSize64=} opt_offset
 * @param {!GPUSize64=} opt_size
 * @return {!ArrayBuffer}
 */
GPUBuffer.prototype.getMappedRange = function(opt_offset, opt_size) {};
/**
 * @param {!GPUMapModeFlags} mode
 * @param {!GPUSize64=} opt_offset
 * @param {!GPUSize64=} opt_size
 * @return {!Promise<void>}
 */
GPUBuffer.prototype.mapAsync = function(mode, opt_offset, opt_size) {};
/** @return {undefined} */
GPUBuffer.prototype.unmap = function() {};

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpubufferbinding
 */
function GPUBufferBinding() {}
/** @type {!GPUBuffer} */
GPUBufferBinding.prototype.buffer;
/** @type {!GPUSize64|undefined} */
GPUBufferBinding.prototype.offset;
/** @type {!GPUSize64|undefined} */
GPUBufferBinding.prototype.size;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpusamplerdescriptor
 */
function GPUSamplerDescriptor() {}
/** @type {!GPUAddressMode|undefined} */
GPUSamplerDescriptor.prototype.addressModeU;
/** @type {!GPUAddressMode|undefined} */
GPUSamplerDescriptor.prototype.addressModeV;
/** @type {!GPUAddressMode|undefined} */
GPUSamplerDescriptor.prototype.addressModeW;
/** @type {!GPUCompareFunction|undefined} */
GPUSamplerDescriptor.prototype.compare;
/** @type {number|undefined} */
GPUSamplerDescriptor.prototype.lodMaxClamp;
/** @type {number|undefined} */
GPUSamplerDescriptor.prototype.lodMinClamp;
/** @type {!GPUFilterMode|undefined} */
GPUSamplerDescriptor.prototype.magFilter;
/** @type {number|undefined} */
GPUSamplerDescriptor.prototype.maxAnisotropy;
/** @type {!GPUFilterMode|undefined} */
GPUSamplerDescriptor.prototype.minFilter;
/** @type {!GPUMipmapFilterMode|undefined} */
GPUSamplerDescriptor.prototype.mipmapFilter;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPUSampler
 */
function GPUSampler() {}

/**
 * @record
 * @extends {GPUTexelCopyBufferLayout}
 * @see https://gpuweb.github.io/gpuweb/#gputexelcopybufferinfo
 */
function GPUTexelCopyBufferInfo() {}
/** @type {!GPUBuffer} */
GPUTexelCopyBufferInfo.prototype.buffer;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gputextureviewdescriptor
 */
function GPUTextureViewDescriptor() {}
/** @type {!GPUIntegerCoordinate|undefined} */
GPUTextureViewDescriptor.prototype.arrayLayerCount;
/** @type {!GPUTextureAspect|undefined} */
GPUTextureViewDescriptor.prototype.aspect;
/** @type {!GPUIntegerCoordinate|undefined} */
GPUTextureViewDescriptor.prototype.baseArrayLayer;
/** @type {!GPUIntegerCoordinate|undefined} */
GPUTextureViewDescriptor.prototype.baseMipLevel;
/** @type {!GPUTextureViewDimension|undefined} */
GPUTextureViewDescriptor.prototype.dimension;
/** @type {!GPUTextureFormat|undefined} */
GPUTextureViewDescriptor.prototype.format;
/** @type {!GPUIntegerCoordinate|undefined} */
GPUTextureViewDescriptor.prototype.mipLevelCount;
/** @type {!GPUTextureUsageFlags|undefined} */
GPUTextureViewDescriptor.prototype.usage;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPUTextureView
 */
function GPUTextureView() {}

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gputexturedescriptor
 */
function GPUTextureDescriptor() {}
/** @type {!GPUTextureDimension|undefined} */
GPUTextureDescriptor.prototype.dimension;
/** @type {!GPUTextureFormat} */
GPUTextureDescriptor.prototype.format;
/** @type {!GPUIntegerCoordinate|undefined} */
GPUTextureDescriptor.prototype.mipLevelCount;
/** @type {!GPUSize32|undefined} */
GPUTextureDescriptor.prototype.sampleCount;
/** @type {!GPUExtent3D} */
GPUTextureDescriptor.prototype.size;
/** @type {!GPUTextureUsageFlags} */
GPUTextureDescriptor.prototype.usage;
/** @type {!Array<!GPUTextureFormat>|undefined} */
GPUTextureDescriptor.prototype.viewFormats;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPUTexture
 */
function GPUTexture() {}
/** @type {!GPUIntegerCoordinateOut} */
GPUTexture.prototype.depthOrArrayLayers;
/** @type {!GPUTextureDimension} */
GPUTexture.prototype.dimension;
/** @type {!GPUTextureFormat} */
GPUTexture.prototype.format;
/** @type {!GPUIntegerCoordinateOut} */
GPUTexture.prototype.height;
/** @type {!GPUIntegerCoordinateOut} */
GPUTexture.prototype.mipLevelCount;
/** @type {!GPUSize32Out} */
GPUTexture.prototype.sampleCount;
/** @type {!GPUFlagsConstant} */
GPUTexture.prototype.usage;
/** @type {!GPUIntegerCoordinateOut} */
GPUTexture.prototype.width;
/**
 * @param {!GPUTextureViewDescriptor=} opt_descriptor
 * @return {!GPUTextureView}
 */
GPUTexture.prototype.createView = function(opt_descriptor) {};
/** @return {undefined} */
GPUTexture.prototype.destroy = function() {};

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#gputexelcopytextureinfo
 */
function GPUTexelCopyTextureInfo() {}
/** @type {!GPUTextureAspect|undefined} */
GPUTexelCopyTextureInfo.prototype.aspect;
/** @type {!GPUIntegerCoordinate|undefined} */
GPUTexelCopyTextureInfo.prototype.mipLevel;
/** @type {!GPUOrigin3D|undefined} */
GPUTexelCopyTextureInfo.prototype.origin;
/** @type {!GPUTexture} */
GPUTexelCopyTextureInfo.prototype.texture;

/**
 * @record
 * @extends {GPUTexelCopyTextureInfo}
 * @see https://gpuweb.github.io/gpuweb/#gpucopyexternalimagedestinfo
 */
function GPUCopyExternalImageDestInfo() {}
/** @type {!PredefinedColorSpace|undefined} */
GPUCopyExternalImageDestInfo.prototype.colorSpace;
/** @type {boolean|undefined} */
GPUCopyExternalImageDestInfo.prototype.premultipliedAlpha;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpuexternaltexturedescriptor
 */
function GPUExternalTextureDescriptor() {}
/** @type {!PredefinedColorSpace|undefined} */
GPUExternalTextureDescriptor.prototype.colorSpace;
/** @type {!HTMLVideoElement|!VideoFrame} */
GPUExternalTextureDescriptor.prototype.source;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPUExternalTexture
 */
function GPUExternalTexture() {}

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpubindgrouplayoutentry
 */
function GPUBindGroupLayoutEntry() {}
/** @type {!GPUIndex32} */
GPUBindGroupLayoutEntry.prototype.binding;
/** @type {!GPUBufferBindingLayout|undefined} */
GPUBindGroupLayoutEntry.prototype.buffer;
/** @type {!GPUExternalTextureBindingLayout|undefined} */
GPUBindGroupLayoutEntry.prototype.externalTexture;
/** @type {!GPUSamplerBindingLayout|undefined} */
GPUBindGroupLayoutEntry.prototype.sampler;
/** @type {!GPUStorageTextureBindingLayout|undefined} */
GPUBindGroupLayoutEntry.prototype.storageTexture;
/** @type {!GPUTextureBindingLayout|undefined} */
GPUBindGroupLayoutEntry.prototype.texture;
/** @type {!GPUShaderStageFlags} */
GPUBindGroupLayoutEntry.prototype.visibility;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpubindgrouplayoutdescriptor
 */
function GPUBindGroupLayoutDescriptor() {}
/** @type {!Array<!GPUBindGroupLayoutEntry>} */
GPUBindGroupLayoutDescriptor.prototype.entries;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPUBindGroupLayout
 */
function GPUBindGroupLayout() {}

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpubindgroupentry
 */
function GPUBindGroupEntry() {}
/** @type {!GPUIndex32} */
GPUBindGroupEntry.prototype.binding;
/** @type {!GPUBindingResource} */
GPUBindGroupEntry.prototype.resource;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpubindgroupdescriptor
 */
function GPUBindGroupDescriptor() {}
/** @type {!Array<!GPUBindGroupEntry>} */
GPUBindGroupDescriptor.prototype.entries;
/** @type {!GPUBindGroupLayout} */
GPUBindGroupDescriptor.prototype.layout;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPUBindGroup
 */
function GPUBindGroup() {}

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpushadermoduledescriptor
 */
function GPUShaderModuleDescriptor() {}
/** @type {string} */
GPUShaderModuleDescriptor.prototype.code;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPUShaderModule
 */
function GPUShaderModule() {}
/**
 * @return {!Promise<!GPUCompilationInfo>}
 */
GPUShaderModule.prototype.getCompilationInfo = function() {};

/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/GPUCompilationMessage
 */
function GPUCompilationMessage() {}
/** @type {number} */
GPUCompilationMessage.prototype.length;
/** @type {number} */
GPUCompilationMessage.prototype.lineNum;
/** @type {number} */
GPUCompilationMessage.prototype.linePos;
/** @type {string} */
GPUCompilationMessage.prototype.message;
/** @type {number} */
GPUCompilationMessage.prototype.offset;
/** @type {!GPUCompilationMessageType} */
GPUCompilationMessage.prototype.type;

/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/GPUCompilationInfo
 */
function GPUCompilationInfo() {}
/** @type {!ReadonlyArray<!GPUCompilationMessage>} */
GPUCompilationInfo.prototype.messages;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpupipelinelayoutdescriptor
 */
function GPUPipelineLayoutDescriptor() {}
/** @type {!Array<!GPUBindGroupLayout|null>} */
GPUPipelineLayoutDescriptor.prototype.bindGroupLayouts;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPUPipelineLayout
 */
function GPUPipelineLayout() {}

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpublendcomponent
 */
function GPUBlendComponent() {}
/** @type {!GPUBlendFactor|undefined} */
GPUBlendComponent.prototype.dstFactor;
/** @type {!GPUBlendOperation|undefined} */
GPUBlendComponent.prototype.operation;
/** @type {!GPUBlendFactor|undefined} */
GPUBlendComponent.prototype.srcFactor;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpublendstate
 */
function GPUBlendState() {}
/** @type {!GPUBlendComponent} */
GPUBlendState.prototype.alpha;
/** @type {!GPUBlendComponent} */
GPUBlendState.prototype.color;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpucolortargetstate
 */
function GPUColorTargetState() {}
/** @type {!GPUBlendState|undefined} */
GPUColorTargetState.prototype.blend;
/** @type {!GPUTextureFormat} */
GPUColorTargetState.prototype.format;
/** @type {!GPUColorWriteFlags|undefined} */
GPUColorTargetState.prototype.writeMask;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpustencilfacestate
 */
function GPUStencilFaceState() {}
/** @type {!GPUCompareFunction|undefined} */
GPUStencilFaceState.prototype.compare;
/** @type {!GPUStencilOperation|undefined} */
GPUStencilFaceState.prototype.depthFailOp;
/** @type {!GPUStencilOperation|undefined} */
GPUStencilFaceState.prototype.failOp;
/** @type {!GPUStencilOperation|undefined} */
GPUStencilFaceState.prototype.passOp;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpudepthstencilstate
 */
function GPUDepthStencilState() {}
/** @type {!GPUDepthBias|undefined} */
GPUDepthStencilState.prototype.depthBias;
/** @type {number|undefined} */
GPUDepthStencilState.prototype.depthBiasClamp;
/** @type {number|undefined} */
GPUDepthStencilState.prototype.depthBiasSlopeScale;
/** @type {!GPUCompareFunction|undefined} */
GPUDepthStencilState.prototype.depthCompare;
/** @type {boolean|undefined} */
GPUDepthStencilState.prototype.depthWriteEnabled;
/** @type {!GPUTextureFormat} */
GPUDepthStencilState.prototype.format;
/** @type {!GPUStencilFaceState|undefined} */
GPUDepthStencilState.prototype.stencilBack;
/** @type {!GPUStencilFaceState|undefined} */
GPUDepthStencilState.prototype.stencilFront;
/** @type {!GPUStencilValue|undefined} */
GPUDepthStencilState.prototype.stencilReadMask;
/** @type {!GPUStencilValue|undefined} */
GPUDepthStencilState.prototype.stencilWriteMask;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpumultisamplestate
 */
function GPUMultisampleState() {}
/** @type {boolean|undefined} */
GPUMultisampleState.prototype.alphaToCoverageEnabled;
/** @type {!GPUSize32|undefined} */
GPUMultisampleState.prototype.count;
/** @type {!GPUSampleMask|undefined} */
GPUMultisampleState.prototype.mask;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpuprimitivestate
 */
function GPUPrimitiveState() {}
/** @type {!GPUCullMode|undefined} */
GPUPrimitiveState.prototype.cullMode;
/** @type {!GPUFrontFace|undefined} */
GPUPrimitiveState.prototype.frontFace;
/** @type {!GPUIndexFormat|undefined} */
GPUPrimitiveState.prototype.stripIndexFormat;
/** @type {!GPUPrimitiveTopology|undefined} */
GPUPrimitiveState.prototype.topology;
/** @type {boolean|undefined} */
GPUPrimitiveState.prototype.unclippedDepth;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#gpuprogrammablestage
 */
function GPUProgrammableStage() {}
/** @type {!Object<string, !GPUPipelineConstantValue>|undefined} */
GPUProgrammableStage.prototype.constants;
/** @type {string|undefined} */
GPUProgrammableStage.prototype.entryPoint;
/** @type {!GPUShaderModule} */
GPUProgrammableStage.prototype.module;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#gpupipelinebase
 */
function GPUPipelineBase() {}
/**
 * @param {number} index
 * @return {!GPUBindGroupLayout}
 */
GPUPipelineBase.prototype.getBindGroupLayout = function(index) {};

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpupipelinedescriptorbase
 */
function GPUPipelineDescriptorBase() {}
/** @type {!GPUPipelineLayout|!GPUAutoLayoutMode} */
GPUPipelineDescriptorBase.prototype.layout;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpupipelineerrorinit
 */
function GPUPipelineErrorInit() {}
/** @type {!GPUPipelineErrorReason} */
GPUPipelineErrorInit.prototype.reason;

/**
 * @constructor
 * @extends {DOMException}
 * @see https://developer.mozilla.org/docs/Web/API/GPUPipelineError
 * @param {string} message
 * @param {!GPUPipelineErrorInit} options
 */
function GPUPipelineError(message, options) {}
/** @type {!GPUPipelineErrorReason} */
GPUPipelineError.prototype.reason;

/**
 * @record
 * @extends {GPUPipelineDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpucomputepipelinedescriptor
 */
function GPUComputePipelineDescriptor() {}
/** @type {!GPUProgrammableStage} */
GPUComputePipelineDescriptor.prototype.compute;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @implements {GPUPipelineBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPUComputePipeline
 */
function GPUComputePipeline() {}
// From GPUPipelineBase
/**
 * @override
 * @param {number} index
 * @return {!GPUBindGroupLayout}
 */
GPUComputePipeline.prototype.getBindGroupLayout = function(index) {};

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpuvertexattribute
 */
function GPUVertexAttribute() {}
/** @type {!GPUVertexFormat} */
GPUVertexAttribute.prototype.format;
/** @type {!GPUSize64} */
GPUVertexAttribute.prototype.offset;
/** @type {!GPUIndex32} */
GPUVertexAttribute.prototype.shaderLocation;

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpuvertexbufferlayout
 */
function GPUVertexBufferLayout() {}
/** @type {!GPUSize64} */
GPUVertexBufferLayout.prototype.arrayStride;
/** @type {!Array<!GPUVertexAttribute>} */
GPUVertexBufferLayout.prototype.attributes;
/** @type {!GPUVertexStepMode|undefined} */
GPUVertexBufferLayout.prototype.stepMode;

/**
 * @record
 * @extends {GPUProgrammableStage}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpuvertexstate
 */
function GPUVertexState() {}
/** @type {!Array<!GPUVertexBufferLayout|null>|undefined} */
GPUVertexState.prototype.buffers;

/**
 * @record
 * @extends {GPUProgrammableStage}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpufragmentstate
 */
function GPUFragmentState() {}
/** @type {!Array<!GPUColorTargetState|null>} */
GPUFragmentState.prototype.targets;

/**
 * @record
 * @extends {GPUPipelineDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpurenderpipelinedescriptor
 */
function GPURenderPipelineDescriptor() {}
/** @type {!GPUDepthStencilState|undefined} */
GPURenderPipelineDescriptor.prototype.depthStencil;
/** @type {!GPUFragmentState|undefined} */
GPURenderPipelineDescriptor.prototype.fragment;
/** @type {!GPUMultisampleState|undefined} */
GPURenderPipelineDescriptor.prototype.multisample;
/** @type {!GPUPrimitiveState|undefined} */
GPURenderPipelineDescriptor.prototype.primitive;
/** @type {!GPUVertexState} */
GPURenderPipelineDescriptor.prototype.vertex;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @implements {GPUPipelineBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPURenderPipeline
 */
function GPURenderPipeline() {}
// From GPUPipelineBase
/**
 * @override
 * @param {number} index
 * @return {!GPUBindGroupLayout}
 */
GPURenderPipeline.prototype.getBindGroupLayout = function(index) {};

/**
 * @interface
 * @see https://gpuweb.github.io/gpuweb/#gpubindingcommandsmixin
 */
function GPUBindingCommandsMixin() {}
/**
 * @param {!GPUIndex32} index
 * @param {!GPUBindGroup|null} bindGroup
 * @param {(!Array<!GPUBufferDynamicOffset>|!Uint32Array|!Iterable<!GPUBufferDynamicOffset>)=}
 *     opt_dynamicOffsetsOrDynamicOffsetsData
 * @param {!GPUSize64=} opt_dynamicOffsetsDataStart
 * @param {!GPUSize32=} opt_dynamicOffsetsDataLength
 * @return {undefined}
 */
GPUBindingCommandsMixin.prototype.setBindGroup = function(
    index, bindGroup, opt_dynamicOffsetsOrDynamicOffsetsData,
    opt_dynamicOffsetsDataStart, opt_dynamicOffsetsDataLength) {};

/**
 * @interface
 * @see https://gpuweb.github.io/gpuweb/#gpudebugcommandsmixin
 */
function GPUDebugCommandsMixin() {}
/**
 * @param {string} markerLabel
 * @return {undefined}
 */
GPUDebugCommandsMixin.prototype.insertDebugMarker = function(markerLabel) {};
/**
 * @return {undefined}
 */
GPUDebugCommandsMixin.prototype.popDebugGroup = function() {};
/**
 * @param {string} groupLabel
 * @return {undefined}
 */
GPUDebugCommandsMixin.prototype.pushDebugGroup = function(groupLabel) {};

/**
 * @interface
 * @see https://gpuweb.github.io/gpuweb/#gpurendercommandsmixin
 */
function GPURenderCommandsMixin() {}
/**
 * @param {!GPUSize32} vertexCount
 * @param {!GPUSize32=} opt_instanceCount
 * @param {!GPUSize32=} opt_firstVertex
 * @param {!GPUSize32=} opt_firstInstance
 * @return {undefined}
 */
GPURenderCommandsMixin.prototype.draw = function(
    vertexCount, opt_instanceCount, opt_firstVertex, opt_firstInstance) {};
/**
 * @param {!GPUSize32} indexCount
 * @param {!GPUSize32=} opt_instanceCount
 * @param {!GPUSize32=} opt_firstIndex
 * @param {!GPUSignedOffset32=} opt_baseVertex
 * @param {!GPUSize32=} opt_firstInstance
 * @return {undefined}
 */
GPURenderCommandsMixin.prototype.drawIndexed = function(
    indexCount, opt_instanceCount, opt_firstIndex, opt_baseVertex,
    opt_firstInstance) {};
/**
 * @param {!GPUBuffer} indirectBuffer
 * @param {!GPUSize64} indirectOffset
 * @return {undefined}
 */
GPURenderCommandsMixin.prototype.drawIndexedIndirect = function(
    indirectBuffer, indirectOffset) {};
/**
 * @param {!GPUBuffer} indirectBuffer
 * @param {!GPUSize64} indirectOffset
 * @return {undefined}
 */
GPURenderCommandsMixin.prototype.drawIndirect = function(
    indirectBuffer, indirectOffset) {};
/**
 * @param {!GPUBuffer} buffer
 * @param {!GPUIndexFormat} indexFormat
 * @param {!GPUSize64=} opt_offset
 * @param {!GPUSize64=} opt_size
 * @return {undefined}
 */
GPURenderCommandsMixin.prototype.setIndexBuffer = function(
    buffer, indexFormat, opt_offset, opt_size) {};
/**
 * @param {!GPURenderPipeline} pipeline
 * @return {undefined}
 */
GPURenderCommandsMixin.prototype.setPipeline = function(pipeline) {};
/**
 * @param {!GPUIndex32} slot
 * @param {!GPUBuffer|null} buffer
 * @param {!GPUSize64=} opt_offset
 * @param {!GPUSize64=} opt_size
 * @return {undefined}
 */
GPURenderCommandsMixin.prototype.setVertexBuffer = function(
    slot, buffer, opt_offset, opt_size) {};

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpuquerysetdescriptor
 */
function GPUQuerySetDescriptor() {}
/** @type {!GPUSize32} */
GPUQuerySetDescriptor.prototype.count;
/** @type {!GPUQueryType} */
GPUQuerySetDescriptor.prototype.type;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPUQuerySet
 */
function GPUQuerySet() {}
/** @type {!GPUSize32Out} */
GPUQuerySet.prototype.count;
/** @type {!GPUQueryType} */
GPUQuerySet.prototype.type;
/** @return {undefined} */
GPUQuerySet.prototype.destroy = function() {};

/**
 * @record
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpucomputepasstimestampwrites
 */
function GPUComputePassTimestampWrites() {}
/** @type {!GPUSize32|undefined} */
GPUComputePassTimestampWrites.prototype.beginningOfPassWriteIndex;
/** @type {!GPUSize32|undefined} */
GPUComputePassTimestampWrites.prototype.endOfPassWriteIndex;
/** @type {!GPUQuerySet} */
GPUComputePassTimestampWrites.prototype.querySet;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpucomputepassdescriptor
 */
function GPUComputePassDescriptor() {}
/** @type {!GPUComputePassTimestampWrites|undefined} */
GPUComputePassDescriptor.prototype.timestampWrites;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @implements {GPUBindingCommandsMixin}
 * @implements {GPUDebugCommandsMixin}
 * @see https://developer.mozilla.org/docs/Web/API/GPUComputePassEncoder
 */
function GPUComputePassEncoder() {}
/**
 * @param {!GPUSize32} workgroupCountX
 * @param {!GPUSize32=} opt_workgroupCountY
 * @param {!GPUSize32=} opt_workgroupCountZ
 * @return {undefined}
 */
GPUComputePassEncoder.prototype.dispatchWorkgroups = function(
    workgroupCountX, opt_workgroupCountY, opt_workgroupCountZ) {};
/**
 * @param {!GPUBuffer} indirectBuffer
 * @param {!GPUSize64} indirectOffset
 * @return {undefined}
 */
GPUComputePassEncoder.prototype.dispatchWorkgroupsIndirect = function(
    indirectBuffer, indirectOffset) {};
/**
 * @return {undefined}
 */
GPUComputePassEncoder.prototype.end = function() {};
/**
 * @param {!GPUComputePipeline} pipeline
 * @return {undefined}
 */
GPUComputePassEncoder.prototype.setPipeline = function(pipeline) {};
// From GPUBindingCommandsMixin
/**
 * @override
 * @param {!GPUIndex32} index
 * @param {!GPUBindGroup|null} bindGroup
 * @param {(!Array<!GPUBufferDynamicOffset>|!Uint32Array|!Iterable<!GPUBufferDynamicOffset>)=}
 *     opt_dynamicOffsetsOrDynamicOffsetsData
 * @param {!GPUSize64=} opt_dynamicOffsetsDataStart
 * @param {!GPUSize32=} opt_dynamicOffsetsDataLength
 * @return {undefined}
 */
GPUComputePassEncoder.prototype.setBindGroup = function(
    index, bindGroup, opt_dynamicOffsetsOrDynamicOffsetsData,
    opt_dynamicOffsetsDataStart, opt_dynamicOffsetsDataLength) {};
// From GPUDebugCommandsMixin
/**
 * @override
 * @param {string} markerLabel
 * @return {undefined}
 */
GPUComputePassEncoder.prototype.insertDebugMarker = function(markerLabel) {};
/**
 * @override
 * @return {undefined}
 */
GPUComputePassEncoder.prototype.popDebugGroup = function() {};
/**
 * @override
 * @param {string} groupLabel
 * @return {undefined}
 */
GPUComputePassEncoder.prototype.pushDebugGroup = function(groupLabel) {};

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpucommandbufferdescriptor
 */
function GPUCommandBufferDescriptor() {}

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPUCommandBuffer
 */
function GPUCommandBuffer() {}

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpurenderpasslayout
 */
function GPURenderPassLayout() {}
/** @type {!Array<!GPUTextureFormat|null>} */
GPURenderPassLayout.prototype.colorFormats;
/** @type {!GPUTextureFormat|undefined} */
GPURenderPassLayout.prototype.depthStencilFormat;
/** @type {!GPUSize32|undefined} */
GPURenderPassLayout.prototype.sampleCount;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpurenderbundledescriptor
 */
function GPURenderBundleDescriptor() {}

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @see https://developer.mozilla.org/docs/Web/API/GPURenderBundle
 */
function GPURenderBundle() {}

/**
 * @record
 * @extends {GPURenderPassLayout}
 * @see https://gpuweb.github.io/gpuweb/#dictdef-gpurenderbundleencoderdescriptor
 */
function GPURenderBundleEncoderDescriptor() {}
/** @type {boolean|undefined} */
GPURenderBundleEncoderDescriptor.prototype.depthReadOnly;
/** @type {boolean|undefined} */
GPURenderBundleEncoderDescriptor.prototype.stencilReadOnly;

/**
 * @constructor
 * @extends {GPUObjectBase}
 * @implements {GPUBindingCommandsMixin}
 * @implements {GPUDebugCommandsMixin}
 * @implements {GPURenderCommandsMixin}
 * @see https://developer.mozilla.org/docs/Web/API/GPURenderBundleEncoder
 */
function GPURenderBundleEncoder() {}
/**
 * @param {!GPURenderBundleDescriptor=} opt_descriptor
 * @return {!GPURenderBundle}
 */
GPURenderBundleEncoder.prototype.finish = function(opt_descriptor) {};
// From GPUBindingCommandsMixin
/**
 * @override
 * @param {!GPUIndex32} index
 * @param {!GPUBindGroup|null} bindGroup
 * @param {(!Array<!GPUBufferDynamicOffset>|!Uint32Array|!Iterable<!GPUBufferDynamicOffset>)=}
 *     opt_dynamicOffsetsOrDynamicOffsetsData
 * @param {!GPUSize64=} opt_dynamicOffsetsDataStart
 * @param {!GPUSize32=} opt_dynamicOffsetsDataLength
 * @return {undefined}
 */
GPURenderBundleEncoder.prototype.setBindGroup = function(
    index, bindGroup, opt_dynamicOffsetsOrDynamicOffsetsData,
    opt_dynamicOffsetsDataStart, opt_dynamicOffsetsDataLength) {};
// From GPUDebugCommandsMixin
/**
 * @override
 * @param {string} markerLabel
 * @return {undefined}
 */
GPURenderBundleEncoder.prototype.insertDebugMarker = function(markerLabel) {};
/**
 * @override
 * @return {undefined}
 */
GPURenderBundleEncoder.prototype.popDebugGroup = function() {};
/**
 * @override
 * @param {string} groupLabel
 * @return {undefined}
 */
GPURenderBundleEncoder.prototype.pushDebugGroup = function(groupLabel) {};
// From GPURenderCommandsMixin
/**
 * @override
 * @param {!GPUSize32} vertexCount
 * @param {!GPUSize32=} opt_instanceCount
 * @param {!GPUSize32=} opt_firstVertex
 * @param {!GPUSize32=} opt_firstInstance
 * @return {undefined}
 */
GPURenderBundleEncoder.prototype.draw = function(
    vertexCount, opt_instanceCount, opt_firstVertex, opt_firstInstance) {};
/**
 * @override
 * @param {!GPUSize32} indexCount
 * @param {!GPUSize32=} opt_instanceCount
 * @param {!GPUSize32=} opt_firstIndex
 * @param {!GPUSignedOffset32=} opt_baseVertex
 * @param {!GPUSize32=} opt_firstInstance
 * @return {undefined}
 */
GPURenderBundleEncoder.prototype.drawIndexed = function(
    indexCount, opt_instanceCount, opt_firstIndex, opt_baseVertex,
    opt_firstInstance) {};
/**
 * @override
 * @param {!GPUBuffer} indirectBuffer
 * @param {!GPUSize64} indirectOffset
 * @return {undefined}
 */
GPURenderBundleEncoder.prototype.drawIndexedIndirect = function(
    indirectBuffer, indirectOffset) {};
/**
 * @override
 * @param {!GPUBuffer} indirectBuffer
 * @param {!GPUSize64} indirectOffset
 * @return {undefined}
 */
GPURenderBundleEncoder.prototype.drawIndirect = function(
    indirectBuffer, indirectOffset) {};
/**
 * @override
 * @param {!GPUBuffer} buffer
 * @param {!GPUIndexFormat} indexFormat
 * @param {!GPUSize64=} opt_offset
 * @param {!GPUSize64=} opt_size
 * @return {undefined}
 */
GPURenderBundleEncoder.prototype.setIndexBuffer = function(
    buffer, indexFormat, opt_offset, opt_size) {};
/**
 * @override
 * @param {!GPURenderPipeline} pipeline
 * @return {undefined}
 */
GPURenderBundleEncoder.prototype.setPipeline = function(pipeline) {};
/**
 * @override
 * @param {!GPUIndex32} slot
 * @param {!GPUBuffer|null} buffer
 * @param {!GPUSize64=} opt_offset
 * @param {!GPUSize64=} opt_size
 * @return {undefined}
 */
GPURenderBundleEncoder.prototype.setVertexBuffer = function(
    slot, buffer, opt_offset, opt_size) {};

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

/** @typedef {!Array<number>|!GPUColorDict} */
var GPUColor;

/** @typedef {!Array<!GPUIntegerCoordinate>|!GPUExtent3DDict} */
var GPUExtent3D;

/** @typedef {!Array<!GPUIntegerCoordinate>|!GPUOrigin2DDict} */
var GPUOrigin2D;

/** @typedef {!Array<!GPUIntegerCoordinate>|!GPUOrigin3DDict} */
var GPUOrigin3D;

/**
 * @typedef {!GPUSampler|!GPUTexture|!GPUTextureView|!GPUBuffer|!GPUBufferBinding|!GPUExternalTexture}
 * @see https://gpuweb.github.io/gpuweb/#typedefdef-gpubindingresource
 */
var GPUBindingResource;

/*
 * Copyright 2022 The Closure Compiler Authors.
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
 * @fileoverview Definitions for W3C's WebGPU specification.
 * @see https://www.w3.org/TR/webgpu
 * @externs
 */

/**
 * @const {!GPU}
 * @see https://www.w3.org/TR/webgpu/#navigatorgpu
 */
Navigator.prototype.gpu;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpucanvasconfiguration
 */
var GPUCanvasConfiguration = function() {};

/**
 * @type {!GPUDevice}
 * @see https://www.w3.org/TR/webgpu/#gpudevice
 */
GPUCanvasConfiguration.prototype.device;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#enumdef-gputextureformat
 */
GPUCanvasConfiguration.prototype.format;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#typedefdef-gputextureusageflags
 */
GPUCanvasConfiguration.prototype.usage;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#enumdef-gpupredefinedcolorspace
 */
GPUCanvasConfiguration.prototype.colorSpace;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#enumdef-gpupredefinedcolorspace
 */
GPUCanvasConfiguration.prototype.alphaMode;

/**
 * @interface
 * @see https://www.w3.org/TR/webgpu/#gpucanvascontext
 */
var GPUCanvasContext = function() {};

/**
 * @type {HTMLCanvasElement|OffscreenCanvas}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucanvascontext-canvas
 */
GPUCanvasContext.prototype.canvas;

/**
 * @param {!GPUCanvasConfiguration} configuration
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucanvascontext-configure
 */
GPUCanvasContext.prototype.configure = function(configuration) {}
/**
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucanvascontext-unconfigure
 */
GPUCanvasContext.prototype.unconfigure = function() {}

/**
 * @return {!GPUTexture}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucanvascontext-getcurrenttexture
 */
GPUCanvasContext.prototype.getCurrentTexture = function() {}

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpurequestadapteroptions
 */
var GPURequestAdapterOptions = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#enumdef-gpupowerpreference
 */
GPURequestAdapterOptions.prototype.powerPreference;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurequestadapteroptions-forcefallbackadapter
 */
GPURequestAdapterOptions.prototype.forceFallbackAdapter;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuobjectdescriptorbase
 */
var GPUObjectDescriptorBase = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuobjectdescriptorbase-label
 */
GPUObjectDescriptorBase.prototype.label

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpupipelinedescriptorbase
 */
var GPUPipelineDescriptorBase = function() {};

/**
 * @type {GPUPipelineLayout|string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpupipelinedescriptorbase-layout
 */
GPUPipelineDescriptorBase.prototype.layout

/**
 * @interface
 * @see https://www.w3.org/TR/webgpu/#gpu
 */
var GPU = function() {};

/**
 * @param {GPURequestAdapterOptions=} opt_GPURequestAdapterOptions
 * @return {Promise<GPUAdapter>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpu-requestadapter
 */
GPU.prototype.requestAdapter = function (opt_GPURequestAdapterOptions) {};

/**
 * @return {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpu-getpreferredcanvasformat
 */
GPU.prototype.getPreferredCanvasFormat = function() {};

/**
 * @interface
 * @extends {Iterable<!Array<string>>}
 * @see https://www.w3.org/TR/webgpu/#gpusupportedfeatures
 */
var GPUSupportedFeatures = function() {};

/**
 * @param {string} value
 * @return {boolean}
 */
GPUSupportedFeatures.prototype.has = function(value) {};

/**
 * @type {number} (readonly)
 */
GPUSupportedFeatures.prototype.size;

/**
 * @return {!IteratorIterable<string>}
 * @nosideeffects
 */
GPUSupportedFeatures.prototype.values = function() {};

/**
 * @return {!Iterator<string>}
 */
GPUSupportedFeatures.prototype[Symbol.iterator] = function() {};

/**
 * @interface
 * @see https://www.w3.org/TR/webgpu/#gpusupportedlimits
 */
var GPUSupportedLimits = function() {};

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxtexturedimension1d
 */
GPUSupportedLimits.prototype.maxTextureDimension1D;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxtexturedimension2d
 */
GPUSupportedLimits.prototype.maxTextureDimension2D;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxtexturedimension3d
 */
GPUSupportedLimits.prototype.maxTextureDimension3D;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxtexturearraylayers
 */
GPUSupportedLimits.prototype.maxTextureArrayLayers;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxdynamicuniformbuffersperpipelinelayout
 */
GPUSupportedLimits.prototype.maxDynamicUniformBuffersPerPipelineLayout;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxdynamicstoragebuffersperpipelinelayout
 */
GPUSupportedLimits.prototype.maxDynamicStorageBuffersPerPipelineLayout;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxsampledtexturespershaderstage
 */
GPUSupportedLimits.prototype.maxSampledTexturesPerShaderStage;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxsamplerspershaderstage
 */
GPUSupportedLimits.prototype.maxSamplersPerShaderStage;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxstoragebufferspershaderstage
 */
GPUSupportedLimits.prototype.maxStorageBuffersPerShaderStage;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxstoragetexturespershaderstage
 */
GPUSupportedLimits.prototype.maxStorageTexturesPerShaderStage;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxuniformbufferspershaderstage
 */
GPUSupportedLimits.prototype.maxUniformBuffersPerShaderStage;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxuniformbufferbindingsize
 */
GPUSupportedLimits.prototype.maxUniformBufferBindingSize;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxstoragebufferbindingsize
 */
GPUSupportedLimits.prototype.maxStorageBufferBindingSize;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxvertexbuffers
 */
GPUSupportedLimits.prototype.maxVertexBuffers;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxvertexattributes
 */
GPUSupportedLimits.prototype.maxVertexAttributes;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusupportedlimits-maxvertexbufferarraystride
 */
GPUSupportedLimits.prototype.maxVertexBufferArrayStride;

/**
 * @interface
 * @see https://www.w3.org/TR/webgpu/#gpuadapter
 */
var GPUAdapter = function() {};

/**
 * @const {GPUSupportedFeatures}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuadapter-features
 */
GPUAdapter.prototype.features;

/**
 * @const {GPUSupportedLimits}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuadapter-limits
 */
GPUAdapter.prototype.limits;

/**
 * @const {boolean}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuadapter-isfallbackadapter
 */
GPUAdapter.prototype.isFallbackAdapter;

/**
 * @param {GPUDeviceDescriptor=} opt_GPUDeviceDescriptor
 * @return {Promise<GPUDevice>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuadapter-requestdevice
 */
GPUAdapter.prototype.requestDevice = function (opt_GPUDeviceDescriptor) {};

/**
 * @param {Array<string>=} unmaskHints
 * @return {Promise<GPUAdapterInfo>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuadapter-requestadapterinfo
 */
GPUAdapter.prototype.requestAdapterInfo = function (unmaskHints) {};


/**
 * @interface
 * @see https://www.w3.org/TR/webgpu/#gpuadapterinfo
 */
var GPUAdapterInfo = function() {};

/**
 * @const {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuadapterinfo-vendor
 */
GPUAdapterInfo.prototype.vendor;

/**
 * @const {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuadapterinfo-architecture
 */
GPUAdapterInfo.prototype.architecture;

/**
 * @const {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuadapterinfo-device
 */
GPUAdapterInfo.prototype.device;

/**
 * @const {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuadapterinfo-description
 */
GPUAdapterInfo.prototype.description;


/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#gpudevicedescriptor
 */
var GPUDeviceDescriptor = function() {};

/**
 * @type {Array<string>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevicedescriptor-requiredfeatures
 */
GPUDeviceDescriptor.prototype.requiredFeatures;

/**
 * @type {Map<string, number>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevicedescriptor-requiredlimits
 */
GPUDeviceDescriptor.prototype.requiredLimits;

/**
 * @type {GPUQueueDescriptor}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevicedescriptor-defaultqueue
 */
GPUDeviceDescriptor.prototype.defaultQueue;


/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpupipelinedescriptorbase
 */
var GPUQueueDescriptor = function() {};

/**
 * @interface
 * @extends {EventTarget}
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gpudevice
 */
var GPUDevice = function() {};

/**
 * @const {GPUSupportedFeatures}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-features
 */
GPUDevice.prototype.features;

/**
 * @const {GPUSupportedLimits}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-limits
 */
GPUDevice.prototype.limits;

/**
 * @const {GPUQueue}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-queue
 */
GPUDevice.prototype.queue;

/**
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-destroy
 */
GPUDevice.prototype.destroy = function() {};

/**
 * @param {GPUBufferDescriptor} descriptor
 * @return {GPUBuffer}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createbuffer
 */
GPUDevice.prototype.createBuffer = function(descriptor) {};

/**
 * @param {GPUTextureDescriptor} descriptor
 * @return {GPUTexture}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createtexture
 */
GPUDevice.prototype.createTexture = function(descriptor) {};

/**
 * @param {GPUSamplerDescriptor=} descriptor
 * @return {GPUSampler}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createsampler
 */
GPUDevice.prototype.createSampler = function(descriptor) {};

/**
 * @param {GPUExternalTextureDescriptor} descriptor
 * @return {GPUExternalTexture}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-importexternaltexture
 */
GPUDevice.prototype.importExternalTexture = function(descriptor) {};

/**
 * @param {GPUBindGroupLayoutDescriptor} descriptor
 * @return {GPUBindGroupLayout}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createbindgrouplayout
 */
GPUDevice.prototype.createBindGroupLayout = function(descriptor) {};

/**
 * @param {GPUPipelineLayoutDescriptor} descriptor
 * @return {GPUPipelineLayout}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createpipelinelayout
 */
GPUDevice.prototype.createPipelineLayout = function(descriptor) {};

/**
 * @param {GPUBindGroupDescriptor} descriptor
 * @return {GPUBindGroup}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createbindgroup
 */
GPUDevice.prototype.createBindGroup = function(descriptor) {};

/**
 * @param {GPUShaderModuleDescriptor} descriptor
 * @return {GPUShaderModule}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createshadermodule
 */
GPUDevice.prototype.createShaderModule = function(descriptor) {};

/**
 * @param {GPUComputePipelineDescriptor} descriptor
 * @return {GPUComputePipeline}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createcomputepipeline
 */
GPUDevice.prototype.createComputePipeline = function(descriptor) {};

/**
 * @param {GPURenderPipelineDescriptor} descriptor
 * @return {GPURenderPipeline}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createrenderpipeline
 */
GPUDevice.prototype.createRenderPipeline = function(descriptor) {};

/**
 * @param {GPUComputePipelineDescriptor} descriptor
 * @return {Promise<GPUComputePipeline>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createcomputepipelineasync
 */
GPUDevice.prototype.createComputePipelineAsync = function(descriptor) {};

/**
 * @param {GPURenderPipelineDescriptor} descriptor
 * @return {Promise<GPURenderPipeline>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createrenderpipelineasync
 */
GPUDevice.prototype.createRenderPipelineAsync = function(descriptor) {};

/**
 * @param {GPUCommandEncoderDescriptor=} descriptor
 * @return {GPUCommandEncoder}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createcommandencoder
 */
GPUDevice.prototype.createCommandEncoder = function(descriptor) {};

/**
 * @param {GPURenderBundleEncoderDescriptor} descriptor
 * @return {GPURenderBundleEncoder}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createrenderbundleencoder
 */
GPUDevice.prototype.createRenderBundleEncoder = function(descriptor) {};

/**
 * @param {GPUQuerySetDescriptor} descriptor
 * @return {GPUQuerySet}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudevice-createqueryset
 */
GPUDevice.prototype.createQuerySet = function(descriptor) {};

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpucolordict
 */
var GPUColorDict = function() {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucolordict-r
 */
GPUColorDict.prototype.r

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucolordict-g
 */
GPUColorDict.prototype.g

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucolordict-b
 */
GPUColorDict.prototype.b

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucolordict-a
 */
GPUColorDict.prototype.a

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuorigin2ddict
 */
var GPUOrigin2DDict = function() {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuorigin2ddict-x
 */
GPUOrigin2DDict.prototype.x

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuorigin2ddict-y
 */
GPUOrigin2DDict.prototype.y

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuorigin3ddict
 */
var GPUOrigin3DDict = function() {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuorigin3ddict-x
 */
GPUOrigin3DDict.prototype.x

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuorigin3ddict-y
 */
GPUOrigin3DDict.prototype.y

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuorigin3ddict-z
 */
GPUOrigin3DDict.prototype.z

/**
 * @constructor
 * @private
 * @see https://www.w3.org/TR/webgpu/#gpubufferusage
 */
var GPUBufferUsage = function() {}

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferusage-map_read
 */
GPUBufferUsage.MAP_READ;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferusage-map_write
 */
GPUBufferUsage.MAP_WRITE;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferusage-copy_src
 */
GPUBufferUsage.COPY_SRC;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferusage-copy_dst
 */
GPUBufferUsage.COPY_DST;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferusage-index
 */
GPUBufferUsage.INDEX;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferusage-vertex
 */
GPUBufferUsage.VERTEX;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferusage-uniform
 */
GPUBufferUsage.UNIFORM;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferusage-storage
 */
GPUBufferUsage.STORAGE;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferusage-indirect
 */
GPUBufferUsage.INDIRECT;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferusage-query_resolve
 */
GPUBufferUsage.QUERY_RESOLVE;

/**
 * @interface
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gpuqueue
 */
var GPUQueue = function() {};

/**
 * @param {Array<GPUCommandBuffer>} commandBuffers
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuqueue-submit
 */
GPUQueue.prototype.submit = function(commandBuffers) {};

/**
 * @return {Promise<undefined>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuqueue-onsubmittedworkdone
 */
GPUQueue.prototype.onSubmittedWorkDone = function() {};

/**
 * @param {GPUBuffer} buffer
 * @param {number} bufferOffset
 * @param {BufferSource} data
 * @param {number=} dataOffset
 * @param {number=} size
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuqueue-writebuffer
 */
GPUQueue.prototype.writeBuffer = function(buffer, bufferOffset, data, dataOffset, size) {};

/**
 * @param {GPUImageCopyTexture} destination
 * @param {BufferSource} data
 * @param {GPUImageDataLayout} dataLayout
 * @param {Array<number>|GPUExtent3DDict} size
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuqueue-writetexture
 */
GPUQueue.prototype.writeTexture = function(destination, data, dataLayout, size) {};

/**
 * @param {GPUImageCopyExternalImage} source
 * @param {GPUImageCopyTexture} destination
 * @param {Array<number>|GPUExtent3DDict} copySize
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuqueue-copyexternalimagetotexture
 */
GPUQueue.prototype.copyExternalImageToTexture = function(source, destination, copySize) {};

/**
 * @interface
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gpubuffer
 */
var GPUBuffer = function() {};

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubuffer-size
 */
GPUBuffer.prototype.size;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubuffer-usage
 */
GPUBuffer.prototype.usage;

/**
 * @const {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubuffer-mapState
 */
GPUBuffer.prototype.mapState;

/**
 * @param {number} mode
 * @param {number=} offset
 * @param {number=} size
 * @return {Promise<undefined>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubuffer-mapasync
 */
GPUBuffer.prototype.mapAsync = function(mode, offset, size) {};

/**
 * @param {number=} offset
 * @param {number=} size
 * @return {ArrayBuffer}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubuffer-getmappedrange
 */
GPUBuffer.prototype.getMappedRange = function(offset, size) {};

/**
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubuffer-unmap
 */
GPUBuffer.prototype.unmap = function() {};

/**
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubuffer-destroy
 */
GPUBuffer.prototype.destroy = function() {};

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpubufferdescriptor
 */
var GPUBufferDescriptor = function() {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferdescriptor-size
 */
GPUBufferDescriptor.prototype.size;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferdescriptor-usage
 */
GPUBufferDescriptor.prototype.usage;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferdescriptor-mappedatcreation
 */
GPUBufferDescriptor.prototype.mappedAtCreation;

/**
 * @interface
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gputexture
 */
var GPUTexture = function() {};

/**
 * @param {GPUTextureViewDescriptor=} descriptor
 * @return {GPUTextureView}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexture-createview
 */
GPUTexture.prototype.createView = function(descriptor) {};

/**
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexture-destroy
 */
GPUTexture.prototype.destroy = function() {};

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexture-width
 */
GPUTexture.prototype.width;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexture-height
 */
GPUTexture.prototype.height;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexture-depthOrArrayLayers
 */
GPUTexture.prototype.depthOrArrayLayers;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexture-mipLevelCount
 */
GPUTexture.prototype.mipLevelCount;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexture-sampleCount
 */
GPUTexture.prototype.sampleCount;

/**
 * @const {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexture-dimension
 */
GPUTexture.prototype.dimension;

/**
 * @const {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexture-format
 */
GPUTexture.prototype.format;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexture-usage
 */
GPUTexture.prototype.usage;

/**
 *
 * @constructor
 * @private
 * @see https://www.w3.org/TR/webgpu/#namespacedef-gputextureusage
 */
var GPUTextureUsage = function () {}

/** @const {number} */
GPUTextureUsage.COPY_SRC;

/** @const {number} */
GPUTextureUsage.COPY_DST;

/** @const {number} */
GPUTextureUsage.TEXTURE_BINDING;

/** @const {number} */
GPUTextureUsage.STORAGE_BINDING;

/** @const {number} */
GPUTextureUsage.RENDER_ATTACHMENT;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpusamplerdescriptor
 */
var GPUSamplerDescriptor = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusamplerdescriptor-addressmodeu
 */
GPUSamplerDescriptor.prototype.addressModeU;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusamplerdescriptor-addressmodev
 */
GPUSamplerDescriptor.prototype.addressModeV;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusamplerdescriptor-addressmodew
 */
GPUSamplerDescriptor.prototype.addressModeW;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusamplerdescriptor-magfilter
 */
GPUSamplerDescriptor.prototype.magFilter;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusamplerdescriptor-minfilter
 */
GPUSamplerDescriptor.prototype.minFilter;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusamplerdescriptor-mipmapfilter
 */
GPUSamplerDescriptor.prototype.mipmapFilter;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusamplerdescriptor-lodminclamp
 */
GPUSamplerDescriptor.prototype.lodMinClamp;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusamplerdescriptor-lodmaxclamp
 */
GPUSamplerDescriptor.prototype.lodMaxClamp;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusamplerdescriptor-compare
 */
GPUSamplerDescriptor.prototype.compare;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusamplerdescriptor-maxanisotropy
 */
GPUSamplerDescriptor.prototype.maxAnisotropy;

/**
 * @interface
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gpusampler
 */
var GPUSampler = function() {};

/**
 * @interface
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gpubindgrouplayout
 */
var GPUBindGroupLayout = function() {};

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpubindgrouplayoutdescriptor
 */
var GPUBindGroupLayoutDescriptor = function() {};

/**
 * @type {Array<GPUBindGroupLayoutEntry>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubindgrouplayoutdescriptor-entries
 */
GPUBindGroupLayoutDescriptor.prototype.entries;

/**
 * @constructor
 * @private
 * @see https://www.w3.org/TR/webgpu/#gpushaderstage
 */
var GPUShaderStage = function() {};

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpushaderstage-vertex
 */
GPUShaderStage.VERTEX;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpushaderstage-fragment
 */
GPUShaderStage.FRAGMENT;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpushaderstage-compute
 */
GPUShaderStage.COMPUTE;

/**
 * @private
 * @constructor
 * @see https://www.w3.org/TR/webgpu/#namespacedef-gpucolorwrite
 */
var GPUColorWrite = function() {};

/** @const {number} */
GPUColorWrite.RED;

/** @const {number} */
GPUColorWrite.GREEN;

/** @const {number} */
GPUColorWrite.BLUE;

/** @const {number} */
GPUColorWrite.ALPHA;

/** @const {number} */
GPUColorWrite.ALL;

/**
 * @interface
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gpubindgroup
 */
var GPUBindGroup = function() {};

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpubindgroupdescriptor
 */
var GPUBindGroupDescriptor = function() {};

/**
 * @type {GPUBindGroupLayout}
 * @override
 * @see https://www.w3.org/TR/webgpu/#dom-gpubindgroupdescriptor-layout
 */
GPUBindGroupDescriptor.prototype.layout;

/**
 * @type {Array<GPUBindGroupEntry>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubindgroupdescriptor-entries
 */
GPUBindGroupDescriptor.prototype.entries;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpubindgroupentry
 */
var GPUBindGroupEntry = function() {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubindgroupentry-binding
 */
GPUBindGroupEntry.prototype.binding;

/**
 * @type {GPUSampler|GPUTextureView|GPUBufferBinding|GPUExternalTexture}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubindgroupentry-resource
 */
GPUBindGroupEntry.prototype.resource;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpubindgrouplayoutentry
 */
var GPUBindGroupLayoutEntry = function() {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubindgrouplayoutentry-binding
 */
GPUBindGroupLayoutEntry.prototype.binding;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubindgrouplayoutentry-visibility
 */
GPUBindGroupLayoutEntry.prototype.visibility;

/**
 * @type {GPUBufferBindingLayout}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubindgrouplayoutentry-buffer
 */
GPUBindGroupLayoutEntry.prototype.buffer;

/**
 * @type {GPUSamplerBindingLayout}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubindgrouplayoutentry-sampler
 */
GPUBindGroupLayoutEntry.prototype.sampler;

/**
 * @type {GPUTextureBindingLayout}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubindgrouplayoutentry-texture
 */
GPUBindGroupLayoutEntry.prototype.texture;

/**
 * @type {GPUStorageTextureBindingLayout}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubindgrouplayoutentry-storagetexture
 */
GPUBindGroupLayoutEntry.prototype.storageTexture;

/**
 * @type {GPUExternalTextureBindingLayout}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubindgrouplayoutentry-externaltexture
 */
GPUBindGroupLayoutEntry.prototype.externalTexture;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpubufferbinding
 */
var GPUBufferBinding = function() {};

/**
 * @type {GPUBuffer}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferbinding-buffer
 */
GPUBufferBinding.prototype.buffer;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferbinding-offset
 */
GPUBufferBinding.prototype.offset;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferbinding-size
 */
GPUBufferBinding.prototype.size;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpupipelinelayoutdescriptor
 */
var GPUPipelineLayoutDescriptor = function() {};

/**
 * @type {Array<GPUBindGroupLayout>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpupipelinelayoutdescriptor-bindgrouplayouts
 */
GPUPipelineLayoutDescriptor.prototype.bindGroupLayouts;

/**
 * @interface
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gpupipelinelayout
 */
var GPUPipelineLayout = function() {};

/**
 * @interface
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#gpushadermodule
 */
var GPUShaderModule = function() {};

/**
 * @return {Promise<GPUCompilationInfo>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpushadermodule-compilationinfo
 */
GPUShaderModule.prototype.compilationInfo = function() {};

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpushadermoduledescriptor
 */
var GPUShaderModuleDescriptor = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpushadermoduledescriptor-code
 */
GPUShaderModuleDescriptor.prototype.code;

/**
 * @type {Object}
 * @see https://www.w3.org/TR/webgpu/#dom-gpushadermoduledescriptor-sourcemap
 */
GPUShaderModuleDescriptor.prototype.sourceMap;

/**
 * @type {Map<string, (GPUPipelineLayout|string)>}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpushadermodulecompilationhint
 */
GPUShaderModuleDescriptor.prototype.hints;

/**
 * @interface
 * @see https://www.w3.org/TR/webgpu/#gpucompilationmessage
 */
var GPUCompilationMessage = function() {};

/**
 * @const {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucompilationmessage-message
 */
GPUCompilationMessage.prototype.message;

/**
 * @const {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucompilationmessage-type
 */
GPUCompilationMessage.prototype.type;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucompilationmessage-linenum
 */
GPUCompilationMessage.prototype.lineNum;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucompilationmessage-linepos
 */
GPUCompilationMessage.prototype.linePos;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucompilationmessage-offset
 */
GPUCompilationMessage.prototype.offset;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucompilationmessage-length
 */
GPUCompilationMessage.prototype.length;

/**
 * @interface
 * @see https://www.w3.org/TR/webgpu/#gpucompilationinfo
 */
var GPUCompilationInfo = function() {};

/**
 * @const {Array<GPUCompilationMessage>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucompilationinfo-messages
 */
GPUCompilationInfo.prototype.messages;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuexternaltexturedescriptor
 */
var GPUExternalTextureDescriptor = function() {};

/**
 * @type {HTMLVideoElement}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuexternaltexturedescriptor-source
 */
GPUExternalTextureDescriptor.prototype.source;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuexternaltexturedescriptor-colorspace
 */
GPUExternalTextureDescriptor.prototype.colorSpace;

/**
 * @interface
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gpuexternaltexture
 */
var GPUExternalTexture = function() {};

/**
 * @const {boolean}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuexternaltexture-expired
 */
GPUExternalTexture.prototype.expired;

/**
 * @record
 * @extends {GPUPipelineDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpucomputepipelinedescriptor
 */
var GPUComputePipelineDescriptor = function() {};

/**
 * @type {GPUProgrammableStage}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucomputepipelinedescriptor-compute
 */
GPUComputePipelineDescriptor.prototype.compute;

/**
 * @record
 * @extends {GPUPipelineDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuprogrammablestage
 */
var GPUProgrammableStage = function() {};

/**
 * @type {GPUShaderModule}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuprogrammablestage-module
 */
GPUProgrammableStage.prototype.module;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuprogrammablestage-entrypoint
 */
GPUProgrammableStage.prototype.entryPoint;

/**
 * @type {Map<string, number>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuprogrammablestage-constants
 */
GPUProgrammableStage.prototype.constants;

/**
 * @interface
 * @see https://www.w3.org/TR/webgpu/#gpupipelinebase
 */
var GPUPipelineBase = function() {};

/**
 * @param {number} index
 * @return {GPUBindGroupLayout}
 * @see https://www.w3.org/TR/webgpu/#dom-gpupipelinebase-getbindgrouplayout
 */
GPUPipelineBase.prototype.getBindGroupLayout = function(index) {};

/**
 * @interface
 * @extends {GPUObjectBase}
 * @extends {GPUPipelineBase}
 * @see https://www.w3.org/TR/webgpu/#gpucomputepipeline
 */
var GPUComputePipeline = function() {};

/**
 * @interface
 * @extends {GPUObjectBase}
 * @extends {GPUPipelineBase}
 * @see https://www.w3.org/TR/webgpu/#gpurenderpipeline
 */
var GPURenderPipeline = function() {};

/**
 * @record
 * @extends {GPUPipelineDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpurenderpipelinedescriptor
 */
var GPURenderPipelineDescriptor = function() {};

/**
 * @type {GPUVertexState}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpipelinedescriptor-vertex
 */
GPURenderPipelineDescriptor.prototype.vertex;

/**
 * @type {GPUPrimitiveState}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpipelinedescriptor-primitive
 */
GPURenderPipelineDescriptor.prototype.primitive;

/**
 * @type {GPUDepthStencilState}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpipelinedescriptor-depthstencil
 */
GPURenderPipelineDescriptor.prototype.depthStencil;

/**
 * @type {GPUMultisampleState}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpipelinedescriptor-multisample
 */
GPURenderPipelineDescriptor.prototype.multisample;

/**
 * @type {GPUFragmentState}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpipelinedescriptor-fragment
 */
GPURenderPipelineDescriptor.prototype.fragment;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpucommandencoderdescriptor
 */
var GPUCommandEncoderDescriptor = function() {};

/**
 * @interface
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gpucommandencoder
 */
var GPUCommandEncoder = function() {};

/**
 * @param {GPURenderPassDescriptor} descriptor
 * @return {GPURenderPassEncoder}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucommandencoder-beginrenderpass
 */
GPUCommandEncoder.prototype.beginRenderPass = function(descriptor) {};

/**
 * @param {GPUComputePassDescriptor=} descriptor
 * @return {GPUComputePassEncoder}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucommandencoder-begincomputepass
 */
GPUCommandEncoder.prototype.beginComputePass = function(descriptor) {};

/**
 * @param {GPUBuffer} source
 * @param {number} sourceOffset
 * @param {GPUBuffer} destination
 * @param {number} destinationOffset
 * @param {number} size
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucommandencoder-copybuffertobuffer
 */
GPUCommandEncoder.prototype.copyBufferToBuffer = function(source, sourceOffset, destination, destinationOffset, size) {};

/**
 * @param {GPUImageCopyBuffer} source
 * @param {GPUImageCopyTexture} destination
 * @param {Array<number>|GPUExtent3DDict} copySize
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucommandencoder-copybuffertotexture
 */
GPUCommandEncoder.prototype.copyBufferToTexture = function(source, destination, copySize) {};

/**
 * @param {GPUImageCopyTexture} source
 * @param {GPUImageCopyBuffer} destination
 * @param {Array<number>|GPUExtent3DDict} copySize
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucommandencoder-copytexturetobuffer
 */
GPUCommandEncoder.prototype.copyTextureToBuffer = function(source, destination, copySize) {};

/**
 * @param {GPUImageCopyTexture} source
 * @param {GPUImageCopyTexture} destination
 * @param {Array<number>|GPUExtent3DDict} copySize
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucommandencoder-copytexturetotexture
 */
GPUCommandEncoder.prototype.copyTextureToTexture = function(source, destination, copySize) {};

/**
 * @param {GPUBuffer} buffer
 * @param {number=} offset
 * @param {number=} size
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucommandencoder-clearbuffer
 */
GPUCommandEncoder.prototype.clearBuffer = function(buffer, offset, size){};

/**
 * @param {GPUQuerySet} querySet
 * @param {number} queryIndex
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucommandencoder-writetimestamp
 */
GPUCommandEncoder.prototype.writeTimestamp = function(querySet, queryIndex) {};


/**
 * @param {GPUQuerySet} querySet
 * @param {number} firstQuery
 * @param {number} queryCount
 * @param {GPUBuffer} destination
 * @param {number} destinationOffset
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucommandencoder-resolvequeryset
 */
GPUCommandEncoder.prototype.resolveQuerySet = function(querySet, firstQuery, queryCount, destination, destinationOffset) {};

/**
 * @param {GPUCommandBufferDescriptor=} descriptor
 * @return {GPUCommandBuffer}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucommandencoder-finish
 */
GPUCommandEncoder.prototype.finish = function(descriptor) {};

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpurenderpasslayout
 */
var GPURenderPassLayout = function() {};

/**
 * @type {Array<string>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderbundleencoderdescriptor-colorformats
 */
GPURenderPassLayout.prototype.colorFormats;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#enumdef-gputextureformat
 */
GPURenderPassLayout.prototype.depthStencilFormat;

/**
 * @type {number}
 */
GPURenderPassLayout.prototype.sampleCount;

/**
 * @record
 * @extends {GPURenderPassLayout}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpurenderbundleencoderdescriptor
 */
var GPURenderBundleEncoderDescriptor = function() {};

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpurenderbundleencoderdescriptor
 */
GPURenderBundleEncoderDescriptor.prototype.depthReadOnly;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpurenderbundleencoderdescriptor
 */
GPURenderBundleEncoderDescriptor.prototype.stencilReadOnly;

/**
 * @interface
 * @see https://www.w3.org/TR/webgpu/#gpucommandsmixin
 */
var GPUCommandsMixin = function() {};

/**
 * @interface
 * @see https://www.w3.org/TR/webgpu/#gpudebugcommandsmixin
 */
var GPUDebugCommandsMixin = function() {};

/**
 * @param {string} groupLabel
 * @see https://www.w3.org/TR/webgpu/#dom-gpudebugcommandsmixin-pushdebuggroup
 */
GPUDebugCommandsMixin.prototype.pushDebugGroup = function(groupLabel) {};

/**
 * @see https://www.w3.org/TR/webgpu/#dom-gpudebugcommandsmixin-popdebuggroup
 */
GPUDebugCommandsMixin.prototype.popDebugGroup = function() {};

/**
 * @param {string} markerLabel
 * @see https://www.w3.org/TR/webgpu/#dom-gpudebugcommandsmixin-insertdebugmarker
 */
GPUDebugCommandsMixin.prototype.insertDebugMarker = function(markerLabel) {};

/**
 * @interface
 * @extends {GPUObjectBase}
 * @extends {GPUCommandsMixin}
 * @extends {GPUDebugCommandsMixin}
 * @extends {GPUBindingCommandsMixin}
 * @extends {GPURenderCommandsMixin}
 * @see https://www.w3.org/TR/webgpu/#gpurenderbundleencoder
 */
var GPURenderBundleEncoder = function() {};

/**
 * @param {GPURenderBundleDescriptor=} descriptor
 * @return {GPURenderBundle}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderbundleencoder-finish
 */
GPURenderBundleEncoder.prototype.finish = function(descriptor) {};

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuquerysetdescriptor
 */
var GPUQuerySetDescriptor = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuquerysetdescriptor-type
 */
GPUQuerySetDescriptor.prototype.type;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuquerysetdescriptor-count
 */
GPUQuerySetDescriptor.prototype.count;

/**
 * @interface
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gpuqueryset
 */
var GPUQuerySet = function() {};

/**
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuqueryset-destroy
 */
GPUQuerySet.prototype.destroy = function() {};

/**
 * @const {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuqueryset-type
 */
GPUQuerySet.prototype.type;

/**
 * @const {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuqueryset-count
 */
GPUQuerySet.prototype.count;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuextent3ddict
 */
var GPUExtent3DDict = function() {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuextent3ddict-width
 */
GPUExtent3DDict.prototype.width;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuextent3ddict-height
 */
GPUExtent3DDict.prototype.height;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuextent3ddict-depthorarraylayers
 */
GPUExtent3DDict.prototype.depthOrArrayLayers;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gputexturedescriptor
 */
var GPUTextureDescriptor = function() {};

/**
 * @type {Array<number>|GPUExtent3DDict}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexturedescriptor-size
 */
GPUTextureDescriptor.prototype.size;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexturedescriptor-miplevelcount
 */
GPUTextureDescriptor.prototype.mipLevelCount;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexturedescriptor-samplecount
 */
GPUTextureDescriptor.prototype.sampleCount;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexturedescriptor-dimension
 */
GPUTextureDescriptor.prototype.dimension;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexturedescriptor-format
 */
GPUTextureDescriptor.prototype.format;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexturedescriptor-usage
 */
GPUTextureDescriptor.prototype.usage;

/**
 * @type {Array<string>}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexturedescriptor-viewformats
 */
GPUTextureDescriptor.prototype.viewFormats;

/**
 * @interface
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gpucommandbuffer
 */
var GPUCommandBuffer = function() {};

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuimagecopytexture
 */
var GPUImageCopyTexture = function() {};

/**
 * @type {GPUTexture}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuimagecopytexture-texture
 */
GPUImageCopyTexture.prototype.texture;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuimagecopytexture-miplevel
 */
GPUImageCopyTexture.prototype.mipLevel;

/**
 * @type {Array<number>|GPUOrigin3DDict}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuimagecopytexture-origin
 */
GPUImageCopyTexture.prototype.origin;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuimagecopytexture-aspect
 */
GPUImageCopyTexture.prototype.aspect;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuimagedatalayout
 */
var GPUImageDataLayout = function() {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuimagedatalayout-offset
 */
GPUImageDataLayout.prototype.offset;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuimagedatalayout-bytesperrow
 */
GPUImageDataLayout.prototype.bytesPerRow;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuimagedatalayout-rowsperimage
 */
GPUImageDataLayout.prototype.rowsPerImage;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuimagecopyexternalimage
 */
var GPUImageCopyExternalImage = function() {};

/**
 * @type {ImageBitmap|HTMLVideoElement|HTMLCanvasElement|OffscreenCanvas}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuimagecopyexternalimage-source
 */
GPUImageCopyExternalImage.prototype.source;

/**
 * @type {Array<number>|GPUOrigin2DDict}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuimagecopyexternalimage-origin
 */
GPUImageCopyExternalImage.prototype.origin;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuimagecopyexternalimage-flipy
 */
GPUImageCopyExternalImage.prototype.flipY;

/**
 * @constructor
 * @private
 * @see https://www.w3.org/TR/webgpu/#gpumapmode
 */
var GPUMapMode = function() {};

/** @const {number} */
GPUMapMode.READ;

/** @const {number} */
GPUMapMode.WRITE;

/**
 * @interface
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gputextureview
 */
var GPUTextureView = function() {};

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gputextureviewdescriptor
 */
var GPUTextureViewDescriptor = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gputextureviewdescriptor-format
 */
GPUTextureViewDescriptor.prototype.format;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gputextureviewdescriptor-dimension
 */
GPUTextureViewDescriptor.prototype.dimension;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gputextureviewdescriptor-aspect
 */
GPUTextureViewDescriptor.prototype.aspect;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gputextureviewdescriptor-basemiplevel
 */
GPUTextureViewDescriptor.prototype.baseMipLevel;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gputextureviewdescriptor-miplevelcount
 */
GPUTextureViewDescriptor.prototype.mipLevelCount;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gputextureviewdescriptor-basearraylayer
 */
GPUTextureViewDescriptor.prototype.baseArrayLayer;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gputextureviewdescriptor-arraylayercount
 */
GPUTextureViewDescriptor.prototype.arrayLayerCount;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpubufferbindinglayout
 */
var GPUBufferBindingLayout = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferbindinglayout-type
 */
GPUBufferBindingLayout.prototype.type;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferbindinglayout-hasdynamicoffset
 */
GPUBufferBindingLayout.prototype.hasDynamicOffset;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpubufferbindinglayout-minbindingsize
 */
GPUBufferBindingLayout.prototype.minBindingSize;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpusamplerbindinglayout
 */
var GPUSamplerBindingLayout = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpusamplerbindinglayout-type
 */
GPUSamplerBindingLayout.prototype.type;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gputexturebindinglayout
 */
var GPUTextureBindingLayout = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexturebindinglayout-sampletype
 */
GPUTextureBindingLayout.prototype.sampleType;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexturebindinglayout-viewdimension
 */
GPUTextureBindingLayout.prototype.viewDimension;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webgpu/#dom-gputexturebindinglayout-multisampled
 */
GPUTextureBindingLayout.prototype.multisampled;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpustoragetexturebindinglayout
 */
var GPUStorageTextureBindingLayout = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpustoragetexturebindinglayout-access
 */
GPUStorageTextureBindingLayout.prototype.access;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpustoragetexturebindinglayout-format
 */
GPUStorageTextureBindingLayout.prototype.format;

/**
 * @type {string} //GPUTextureViewDimension
 * @see https://www.w3.org/TR/webgpu/#dom-gpustoragetexturebindinglayout-viewdimension
 */
GPUStorageTextureBindingLayout.prototype.viewDimension;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuexternaltexturebindinglayout
 */
var GPUExternalTextureBindingLayout = function() {};

/**
 * @record
 * @extends {GPUProgrammableStage}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuvertexstate
 */
var GPUVertexState = function() {};

/**
 * @type {Array<GPUVertexBufferLayout>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuvertexstate-buffers
 */
GPUVertexState.prototype.buffers;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuvertexbufferlayout
 */
var GPUVertexBufferLayout = function() {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuvertexbufferlayout-arraystride
 */
GPUVertexBufferLayout.prototype.arrayStride;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuvertexbufferlayout-stepmode
 */
GPUVertexBufferLayout.prototype.stepMode;

/**
 * @type {Array<GPUVertexAttribute>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuvertexbufferlayout-attributes
 */
GPUVertexBufferLayout.prototype.attributes;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuvertexattribute
 */
var GPUVertexAttribute = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuvertexattribute-format
 */
GPUVertexAttribute.prototype.format;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuvertexattribute-offset
 */
GPUVertexAttribute.prototype.offset;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuvertexattribute-shaderlocation
 */
GPUVertexAttribute.prototype.shaderLocation;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuprimitivestate
 */
var GPUPrimitiveState = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuprimitivestate-topology
 */
GPUPrimitiveState.prototype.topology;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuprimitivestate-stripindexformat
 */
GPUPrimitiveState.prototype.stripIndexFormat;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuprimitivestate-frontface
 */
GPUPrimitiveState.prototype.frontFace;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuprimitivestate-cullmode
 */
GPUPrimitiveState.prototype.cullMode;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuprimitivestate-unclippeddepth
 */
GPUPrimitiveState.prototype.unclippedDepth;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpudepthstencilstate
 */
var GPUDepthStencilState = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudepthstencilstate-format
 */
GPUDepthStencilState.prototype.format;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudepthstencilstate-depthwriteenabled
 */
GPUDepthStencilState.prototype.depthWriteEnabled;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudepthstencilstate-depthcompare
 */
GPUDepthStencilState.prototype.depthCompare;

/**
 * @type {GPUStencilFaceState}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudepthstencilstate-stencilfront
 */
GPUDepthStencilState.prototype.stencilFront;

/**
 * @type {GPUStencilFaceState}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudepthstencilstate-stencilback
 */
GPUDepthStencilState.prototype.stencilBack;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudepthstencilstate-stencilreadmask
 */
GPUDepthStencilState.prototype.stencilReadMask;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudepthstencilstate-stencilwritemask
 */
GPUDepthStencilState.prototype.stencilWriteMask;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudepthstencilstate-depthbias
 */
GPUDepthStencilState.prototype.depthBias;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudepthstencilstate-depthbiasslopescale
 */
GPUDepthStencilState.prototype.depthBiasSlopeScale;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpudepthstencilstate-depthbiasclamp
 */
GPUDepthStencilState.prototype.depthBiasClamp;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpumultisamplestate
 */
var GPUMultisampleState = function() {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpumultisamplestate-count
 */
GPUMultisampleState.prototype.count;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpumultisamplestate-mask
 */
GPUMultisampleState.prototype.mask;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webgpu/#dom-gpumultisamplestate-alphatocoverageenabled
 */
GPUMultisampleState.prototype.alphaToCoverageEnabled;

/**
 * @record
 * @extends {GPUProgrammableStage}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpufragmentstate
 */
var GPUFragmentState = function() {};

/**
 * @type {Array<GPUColorTargetState>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpufragmentstate-targets
 */
GPUFragmentState.prototype.targets;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpucolortargetstate
 */
var GPUColorTargetState = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucolortargetstate-format
 */
GPUColorTargetState.prototype.format;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucolortargetstate-blend
 */
GPUColorTargetState.prototype.blend;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucolortargetstate-writemask
 */
GPUColorTargetState.prototype.writeMask;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpublendstate
 */
var GPUBlendState = function() {};

/**
 * @type {GPUBlendComponent}
 * @see https://www.w3.org/TR/webgpu/#dom-gpublendstate-color
 */
GPUBlendState.prototype.color;

/**
 * @type {GPUBlendComponent}
 * @see https://www.w3.org/TR/webgpu/#dom-gpublendstate-alpha
 */
GPUBlendState.prototype.alpha;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpublendcomponent
 */
var GPUBlendComponent = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpublendcomponent-srcfactor
 */
GPUBlendComponent.prototype.srcFactor;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpublendcomponent-dstfactor
 */
GPUBlendComponent.prototype.dstFactor;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpublendcomponent-operation
 */
GPUBlendComponent.prototype.operation;

/**
 * @interface
 * @see https://www.w3.org/TR/webgpu/#gpuobjectbase
 */
var GPUObjectBase = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuobjectbase-label
 */
GPUObjectBase.prototype.label;

/**
 * @interface
 * @extends {GPUObjectBase}
 * @extends {GPUCommandsMixin}
 * @extends {GPUDebugCommandsMixin}
 * @extends {GPUBindingCommandsMixin}
 * @see https://www.w3.org/TR/webgpu/#gpucomputepassencoder
 */
var GPUComputePassEncoder = function() {};

/**
 * @param {GPUComputePipeline} pipeline
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucomputepassencoder-setpipeline
 */
GPUComputePassEncoder.prototype.setPipeline = function(pipeline) {};

/**
 * @param {number} workgroupCountX
 * @param {number=} workgroupCountY
 * @param {number=} workgroupCountZ
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucomputepassencoder-dispatchworkgroups
 */
GPUComputePassEncoder.prototype.dispatchWorkgroups = function(workgroupCountX, workgroupCountY, workgroupCountZ) {};

/**
 * @param {GPUBuffer} indirectBuffer
 * @param {number} indirectOffset
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucomputepassencoder-dispatchworkgroupsindirect
 */
GPUComputePassEncoder.prototype.dispatchWorkgroupsIndirect = function(indirectBuffer, indirectOffset) {};

/**
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucomputepassencoder-end
 */
GPUComputePassEncoder.prototype.end = function() {};

/**
 * @interface
 * @see https://www.w3.org/TR/webgpu/#gpurendercommandsmixin
 */
var GPURenderCommandsMixin = function() {};

/**
 * @param {GPURenderPipeline} pipeline
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurendercommandsmixin-setpipeline
 */
GPURenderCommandsMixin.prototype.setPipeline = function(pipeline) {};

/**
 * @param {GPUBuffer} buffer
 * @param {string} indexFormat
 * @param {number=} offset
 * @param {number=} size
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurendercommandsmixin-setindexbuffer
 */
GPURenderCommandsMixin.prototype.setIndexBuffer = function(buffer, indexFormat, offset, size) {};

/**
 * @param {number} slot
 * @param {GPUBuffer} buffer
 * @param {number=} offset
 * @param {number=} size
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurendercommandsmixin-setvertexbuffer
 */
GPURenderCommandsMixin.prototype.setVertexBuffer = function(slot, buffer, offset, size) {};

/**
 * @param {number} vertexCount
 * @param {number=} instanceCount
 * @param {number=} firstVertex
 * @param {number=} firstInstance
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurendercommandsmixin-draw
 */
GPURenderCommandsMixin.prototype.draw = function(vertexCount, instanceCount, firstVertex, firstInstance) {};

/**
 * @param {number} indexCount
 * @param {number=} instanceCount
 * @param {number=} firstIndex
 * @param {number=} baseVertex
 * @param {number=} firstInstance
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurendercommandsmixin-drawindexed
 */
GPURenderCommandsMixin.prototype.drawIndexed = function(indexCount, instanceCount, firstIndex, baseVertex, firstInstance) {};

/**
 * @param {GPUBuffer} indirectBuffer
 * @param {number} indirectOffset
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurendercommandsmixin-drawindirect
 */
GPURenderCommandsMixin.prototype.drawIndirect = function(indirectBuffer, indirectOffset) {};

/**
 * @param {GPUBuffer} indirectBuffer
 * @param {number} indirectOffset
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurendercommandsmixin-drawindexedindirect
 */
GPURenderCommandsMixin.prototype.drawIndexedIndirect = function(indirectBuffer, indirectOffset) {};

/**
 * @interface
 * @see https://www.w3.org/TR/webgpu/#gpuprogrammablepassencoder
 */
var GPUBindingCommandsMixin = function() {};

/**
 * @param {number} index
 * @param {GPUBindGroup} bindGroup
 * @param {Array<number>|ArrayBuffer<number>=} dynamicOffsetsData
 * @param {number=} dynamicOffsetsDataStart
 * @param {number=} dynamicOffsetsDataLength
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuprogrammablepassencoder-setbindgroup
 */
GPUBindingCommandsMixin.prototype.setBindGroup = function(index, bindGroup, dynamicOffsetsData, dynamicOffsetsDataStart, dynamicOffsetsDataLength) {};

/**
 * @interface
 * @extends {GPUObjectBase}
 * @extends {GPUCommandsMixin}
 * @extends {GPUDebugCommandsMixin}
 * @extends {GPUBindingCommandsMixin}
 * @extends {GPURenderCommandsMixin}
 * @see https://www.w3.org/TR/webgpu/#gpurenderpassencoder
 */
var GPURenderPassEncoder = function() {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} width
 * @param {number} height
 * @param {number} minDepth
 * @param {number} maxDepth
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassencoder-setviewport
 */
GPURenderPassEncoder.prototype.setViewport = function(x, y, width, height, minDepth, maxDepth) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} width
 * @param {number} height
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassencoder-setscissorrect
 */
GPURenderPassEncoder.prototype.setScissorRect = function(x, y, width, height) {};

/**
 * @param {Array<number>|GPUColorDict} color
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassencoder-setblendconstant
 */
GPURenderPassEncoder.prototype.setBlendConstant = function(color) {};

/**
 * @param {number} reference
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassencoder-setstencilreference
 */
GPURenderPassEncoder.prototype.setStencilReference = function(reference) {};

/**
 * @param {number} queryIndex
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassencoder-beginocclusionquery
 */
GPURenderPassEncoder.prototype.beginOcclusionQuery = function(queryIndex) {};

/**
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassencoder-endocclusionquery
 */
GPURenderPassEncoder.prototype.endOcclusionQuery = function() {};

/**
 * @param {Array<GPURenderBundle>} querySet
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassencoder-executebundles
 */
GPURenderPassEncoder.prototype.executeBundles = function(querySet) {};

/**
 * @return {undefined}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassencoder-end
 */
GPURenderPassEncoder.prototype.end = function() {};

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpurenderpassdescriptor
 */
var GPURenderPassDescriptor = function() {};

/**
 * @type {Array<GPURenderPassColorAttachment>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassdescriptor-colorattachments
 */
GPURenderPassDescriptor.prototype.colorAttachments;

/**
 * @type {GPURenderPassDepthStencilAttachment}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassdescriptor-depthstencilattachment
 */
GPURenderPassDescriptor.prototype.depthStencilAttachment;

/**
 * @type {GPUQuerySet}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassdescriptor-occlusionqueryset
 */
GPURenderPassDescriptor.prototype.occlusionQuerySet;

/**
 * @type {Array<GPURenderPassTimestampWrite>}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassdescriptor-timestampwrites
 */
GPURenderPassDescriptor.prototype.timestampWrites;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpurenderpasscolorattachment
 */
var GPURenderPassColorAttachment = function() {};

/**
 * @type {GPUTextureView}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpasscolorattachment-view
 */
GPURenderPassColorAttachment.prototype.view;

/**
 * @type {GPUTextureView}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpasscolorattachment-resolvetarget
 */
GPURenderPassColorAttachment.prototype.resolveTarget;

/**
 * @type {GPUColorDict|number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpasscolorattachment-clearValue
 */
GPURenderPassColorAttachment.prototype.clearValue;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpasscolorattachment-loadOp
 */
GPURenderPassColorAttachment.prototype.loadOp;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpasscolorattachment-storeOp
 */
GPURenderPassColorAttachment.prototype.storeOp;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpurenderpassdepthstencilattachment
 */
var GPURenderPassDepthStencilAttachment = function() {};

/**
 * @type {GPUTextureView}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassdepthstencilattachment-view
 */
GPURenderPassDepthStencilAttachment.prototype.view;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassdepthstencilattachment-depthclearvalue
 */
GPURenderPassDepthStencilAttachment.prototype.depthClearValue;


/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassdepthstencilattachment-depthloadvalue
 */
GPURenderPassDepthStencilAttachment.prototype.depthLoadOp;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassdepthstencilattachment-depthstoreop
 */
GPURenderPassDepthStencilAttachment.prototype.depthStoreOp;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassdepthstencilattachment-depthreadonly
 */
GPURenderPassDepthStencilAttachment.prototype.depthReadOnly;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassdepthstencilattachment-stencilclearvalue
 */
GPURenderPassDepthStencilAttachment.prototype.stencilClearValue;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassdepthstencilattachment-stencilloadop
 */
GPURenderPassDepthStencilAttachment.prototype.stencilLoadOp;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassdepthstencilattachment-stencilstoreop
 */
GPURenderPassDepthStencilAttachment.prototype.stencilStoreOp;

/**
 * @type {boolean}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpassdepthstencilattachment-stencilstoreop
 */
GPURenderPassDepthStencilAttachment.prototype.stencilReadOnly;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpucomputepassdescriptor
 */
var GPUComputePassDescriptor = function() {};

/**
 * @type {Array<GPUComputePassTimestampWrite>}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpucomputepasstimestampwrite
 */
GPUComputePassDescriptor.prototype.timestampWrites

/**
 * @record
 * @extends {GPUImageDataLayout}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpuimagecopybuffer
 */
var GPUImageCopyBuffer = function() {};

/**
 * @type {GPUBuffer}
 * @see https://www.w3.org/TR/webgpu/#dom-gpuimagecopybuffer-buffer
 */
GPUImageCopyBuffer.prototype.buffer;

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpucommandbufferdescriptor
 */
var GPUCommandBufferDescriptor = function() {};

/**
 * @record
 * @extends {GPUObjectDescriptorBase}
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpurenderbundledescriptor
 */
var GPURenderBundleDescriptor = function() {};

/**
 * @interface
 * @extends {GPUObjectBase}
 * @see https://www.w3.org/TR/webgpu/#gpurenderbundle
 */
var GPURenderBundle = function() {};

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpustencilfacestate
 */
var GPUStencilFaceState = function() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpustencilfacestate-compare
 */
GPUStencilFaceState.prototype.compare;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpustencilfacestate-failop
 */
GPUStencilFaceState.prototype.failOp;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpustencilfacestate-depthfailop
 */
GPUStencilFaceState.prototype.depthFailOp;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpustencilfacestate-passop
 */
GPUStencilFaceState.prototype.passOp;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpurenderpasstimestampwrite
 */
var GPURenderPassTimestampWrite = function() {};

/**
 * @type {GPUQuerySet}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpasstimestampwrite-queryset
 */
GPURenderPassTimestampWrite.prototype.querySet;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpasstimestampwrite-queryindex
 */
GPURenderPassTimestampWrite.prototype.queryIndex;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpurenderpasstimestampwrite-queryindex
 */
GPURenderPassTimestampWrite.prototype.location;

/**
 * @record
 * @see https://www.w3.org/TR/webgpu/#dictdef-gpucomputepasstimestampwrite
 */
var GPUComputePassTimestampWrite = function() {};

/**
 * @type {GPUQuerySet}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucomputepasstimestampwrite-queryset
 */
GPUComputePassTimestampWrite.prototype.querySet;

/**
 * @type {number}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucomputepasstimestampwrite-queryindex
 */
GPUComputePassTimestampWrite.prototype.queryIndex;

/**
 * @type {string}
 * @see https://www.w3.org/TR/webgpu/#dom-gpucomputepasstimestampwrite-location
 */
GPUComputePassTimestampWrite.prototype.location;

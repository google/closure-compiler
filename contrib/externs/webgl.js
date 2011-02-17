/*
 * Copyright 2010 Google Inc.
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
 * @fileoverview Definitions for WebGL functions as described at
 *  https://cvs.khronos.org/svn/repos/registry/trunk/public/webgl/doc/spec/WebGL-spec.html
 *
 *  This relies on html5.js being included for Canvas and ArrayBuffer support.
 *
 * @externs
 */


/**
 * @constructor
 */
function WebGLRenderingContext() {
}


/** @type {number} */
WebGLRenderingContext.DEPTH_BUFFER_BIT;

/** @type {number} */
WebGLRenderingContext.STENCIL_BUFFER_BIT;

/** @type {number} */
WebGLRenderingContext.COLOR_BUFFER_BIT;

/** @type {number} */
WebGLRenderingContext.POINTS;

/** @type {number} */
WebGLRenderingContext.LINES;

/** @type {number} */
WebGLRenderingContext.LINE_LOOP;

/** @type {number} */
WebGLRenderingContext.LINE_STRIP;

/** @type {number} */
WebGLRenderingContext.TRIANGLES;

/** @type {number} */
WebGLRenderingContext.TRIANGLE_STRIP;

/** @type {number} */
WebGLRenderingContext.TRIANGLE_FAN;

/** @type {number} */
WebGLRenderingContext.ZERO;

/** @type {number} */
WebGLRenderingContext.ONE;

/** @type {number} */
WebGLRenderingContext.SRC_COLOR;

/** @type {number} */
WebGLRenderingContext.ONE_MINUS_SRC_COLOR;

/** @type {number} */
WebGLRenderingContext.SRC_ALPHA;

/** @type {number} */
WebGLRenderingContext.ONE_MINUS_SRC_ALPHA;

/** @type {number} */
WebGLRenderingContext.DST_ALPHA;

/** @type {number} */
WebGLRenderingContext.ONE_MINUS_DST_ALPHA;

/** @type {number} */
WebGLRenderingContext.DST_COLOR;

/** @type {number} */
WebGLRenderingContext.ONE_MINUS_DST_COLOR;

/** @type {number} */
WebGLRenderingContext.SRC_ALPHA_SATURATE;

/** @type {number} */
WebGLRenderingContext.FUNC_ADD;

/** @type {number} */
WebGLRenderingContext.BLEND_EQUATION;

/** @type {number} */
WebGLRenderingContext.BLEND_EQUATION_RGB;

/** @type {number} */
WebGLRenderingContext.BLEND_EQUATION_ALPHA;

/** @type {number} */
WebGLRenderingContext.FUNC_SUBTRACT;

/** @type {number} */
WebGLRenderingContext.FUNC_REVERSE_SUBTRACT;

/** @type {number} */
WebGLRenderingContext.BLEND_DST_RGB;

/** @type {number} */
WebGLRenderingContext.BLEND_SRC_RGB;

/** @type {number} */
WebGLRenderingContext.BLEND_DST_ALPHA;

/** @type {number} */
WebGLRenderingContext.BLEND_SRC_ALPHA;

/** @type {number} */
WebGLRenderingContext.CONSTANT_COLOR;

/** @type {number} */
WebGLRenderingContext.ONE_MINUS_CONSTANT_COLOR;

/** @type {number} */
WebGLRenderingContext.CONSTANT_ALPHA;

/** @type {number} */
WebGLRenderingContext.ONE_MINUS_CONSTANT_ALPHA;

/** @type {number} */
WebGLRenderingContext.BLEND_COLOR;

/** @type {number} */
WebGLRenderingContext.ARRAY_BUFFER;

/** @type {number} */
WebGLRenderingContext.ELEMENT_ARRAY_BUFFER;

/** @type {number} */
WebGLRenderingContext.ARRAY_BUFFER_BINDING;

/** @type {number} */
WebGLRenderingContext.ELEMENT_ARRAY_BUFFER_BINDING;

/** @type {number} */
WebGLRenderingContext.STREAM_DRAW;

/** @type {number} */
WebGLRenderingContext.STATIC_DRAW;

/** @type {number} */
WebGLRenderingContext.DYNAMIC_DRAW;

/** @type {number} */
WebGLRenderingContext.BUFFER_SIZE;

/** @type {number} */
WebGLRenderingContext.BUFFER_USAGE;

/** @type {number} */
WebGLRenderingContext.CURRENT_VERTEX_ATTRIB;

/** @type {number} */
WebGLRenderingContext.FRONT;

/** @type {number} */
WebGLRenderingContext.BACK;

/** @type {number} */
WebGLRenderingContext.FRONT_AND_BACK;

/** @type {number} */
WebGLRenderingContext.TEXTURE_2D;

/** @type {number} */
WebGLRenderingContext.CULL_FACE;

/** @type {number} */
WebGLRenderingContext.BLEND;

/** @type {number} */
WebGLRenderingContext.DITHER;

/** @type {number} */
WebGLRenderingContext.STENCIL_TEST;

/** @type {number} */
WebGLRenderingContext.DEPTH_TEST;

/** @type {number} */
WebGLRenderingContext.SCISSOR_TEST;

/** @type {number} */
WebGLRenderingContext.POLYGON_OFFSET_FILL;

/** @type {number} */
WebGLRenderingContext.SAMPLE_ALPHA_TO_COVERAGE;

/** @type {number} */
WebGLRenderingContext.SAMPLE_COVERAGE;

/** @type {number} */
WebGLRenderingContext.NO_ERROR;

/** @type {number} */
WebGLRenderingContext.INVALID_ENUM;

/** @type {number} */
WebGLRenderingContext.INVALID_VALUE;

/** @type {number} */
WebGLRenderingContext.INVALID_OPERATION;

/** @type {number} */
WebGLRenderingContext.OUT_OF_MEMORY;

/** @type {number} */
WebGLRenderingContext.CW;

/** @type {number} */
WebGLRenderingContext.CCW;

/** @type {number} */
WebGLRenderingContext.LINE_WIDTH;

/** @type {number} */
WebGLRenderingContext.ALIASED_POINT_SIZE_RANGE;

/** @type {number} */
WebGLRenderingContext.ALIASED_LINE_WIDTH_RANGE;

/** @type {number} */
WebGLRenderingContext.CULL_FACE_MODE;

/** @type {number} */
WebGLRenderingContext.FRONT_FACE;

/** @type {number} */
WebGLRenderingContext.DEPTH_RANGE;

/** @type {number} */
WebGLRenderingContext.DEPTH_WRITEMASK;

/** @type {number} */
WebGLRenderingContext.DEPTH_CLEAR_VALUE;

/** @type {number} */
WebGLRenderingContext.DEPTH_FUNC;

/** @type {number} */
WebGLRenderingContext.STENCIL_CLEAR_VALUE;

/** @type {number} */
WebGLRenderingContext.STENCIL_FUNC;

/** @type {number} */
WebGLRenderingContext.STENCIL_FAIL;

/** @type {number} */
WebGLRenderingContext.STENCIL_PASS_DEPTH_FAIL;

/** @type {number} */
WebGLRenderingContext.STENCIL_PASS_DEPTH_PASS;

/** @type {number} */
WebGLRenderingContext.STENCIL_REF;

/** @type {number} */
WebGLRenderingContext.STENCIL_VALUE_MASK;

/** @type {number} */
WebGLRenderingContext.STENCIL_WRITEMASK;

/** @type {number} */
WebGLRenderingContext.STENCIL_BACK_FUNC;

/** @type {number} */
WebGLRenderingContext.STENCIL_BACK_FAIL;

/** @type {number} */
WebGLRenderingContext.STENCIL_BACK_PASS_DEPTH_FAIL;

/** @type {number} */
WebGLRenderingContext.STENCIL_BACK_PASS_DEPTH_PASS;

/** @type {number} */
WebGLRenderingContext.STENCIL_BACK_REF;

/** @type {number} */
WebGLRenderingContext.STENCIL_BACK_VALUE_MASK;

/** @type {number} */
WebGLRenderingContext.STENCIL_BACK_WRITEMASK;

/** @type {number} */
WebGLRenderingContext.VIEWPORT;

/** @type {number} */
WebGLRenderingContext.SCISSOR_BOX;

/** @type {number} */
WebGLRenderingContext.COLOR_CLEAR_VALUE;

/** @type {number} */
WebGLRenderingContext.COLOR_WRITEMASK;

/** @type {number} */
WebGLRenderingContext.UNPACK_ALIGNMENT;

/** @type {number} */
WebGLRenderingContext.UNPACK_FLIP_Y_WEBGL;

/** @type {number} */
WebGLRenderingContext.PACK_ALIGNMENT;

/** @type {number} */
WebGLRenderingContext.MAX_TEXTURE_SIZE;

/** @type {number} */
WebGLRenderingContext.MAX_VIEWPORT_DIMS;

/** @type {number} */
WebGLRenderingContext.SUBPIXEL_BITS;

/** @type {number} */
WebGLRenderingContext.RED_BITS;

/** @type {number} */
WebGLRenderingContext.GREEN_BITS;

/** @type {number} */
WebGLRenderingContext.BLUE_BITS;

/** @type {number} */
WebGLRenderingContext.ALPHA_BITS;

/** @type {number} */
WebGLRenderingContext.DEPTH_BITS;

/** @type {number} */
WebGLRenderingContext.STENCIL_BITS;

/** @type {number} */
WebGLRenderingContext.POLYGON_OFFSET_UNITS;

/** @type {number} */
WebGLRenderingContext.POLYGON_OFFSET_FACTOR;

/** @type {number} */
WebGLRenderingContext.TEXTURE_BINDING_2D;

/** @type {number} */
WebGLRenderingContext.SAMPLE_BUFFERS;

/** @type {number} */
WebGLRenderingContext.SAMPLES;

/** @type {number} */
WebGLRenderingContext.SAMPLE_COVERAGE_VALUE;

/** @type {number} */
WebGLRenderingContext.SAMPLE_COVERAGE_INVERT;

/** @type {number} */
WebGLRenderingContext.NUM_COMPRESSED_TEXTURE_FORMATS;

/** @type {number} */
WebGLRenderingContext.COMPRESSED_TEXTURE_FORMATS;

/** @type {number} */
WebGLRenderingContext.DONT_CARE;

/** @type {number} */
WebGLRenderingContext.FASTEST;

/** @type {number} */
WebGLRenderingContext.NICEST;

/** @type {number} */
WebGLRenderingContext.GENERATE_MIPMAP_HINT;

/** @type {number} */
WebGLRenderingContext.BYTE;

/** @type {number} */
WebGLRenderingContext.UNSIGNED_BYTE;

/** @type {number} */
WebGLRenderingContext.SHORT;

/** @type {number} */
WebGLRenderingContext.UNSIGNED_SHORT;

/** @type {number} */
WebGLRenderingContext.INT;

/** @type {number} */
WebGLRenderingContext.UNSIGNED_INT;

/** @type {number} */
WebGLRenderingContext.FLOAT;

/** @type {number} */
WebGLRenderingContext.DEPTH_COMPONENT;

/** @type {number} */
WebGLRenderingContext.ALPHA;

/** @type {number} */
WebGLRenderingContext.RGB;

/** @type {number} */
WebGLRenderingContext.RGBA;

/** @type {number} */
WebGLRenderingContext.LUMINANCE;

/** @type {number} */
WebGLRenderingContext.LUMINANCE_ALPHA;

/** @type {number} */
WebGLRenderingContext.UNSIGNED_SHORT_4_4_4_4;

/** @type {number} */
WebGLRenderingContext.UNSIGNED_SHORT_5_5_5_1;

/** @type {number} */
WebGLRenderingContext.UNSIGNED_SHORT_5_6_5;

/** @type {number} */
WebGLRenderingContext.FRAGMENT_SHADER;

/** @type {number} */
WebGLRenderingContext.VERTEX_SHADER;

/** @type {number} */
WebGLRenderingContext.MAX_VERTEX_ATTRIBS;

/** @type {number} */
WebGLRenderingContext.MAX_VERTEX_UNIFORM_VECTORS;

/** @type {number} */
WebGLRenderingContext.MAX_VARYING_VECTORS;

/** @type {number} */
WebGLRenderingContext.MAX_COMBINED_TEXTURE_IMAGE_UNITS;

/** @type {number} */
WebGLRenderingContext.MAX_VERTEX_TEXTURE_IMAGE_UNITS;

/** @type {number} */
WebGLRenderingContext.MAX_TEXTURE_IMAGE_UNITS;

/** @type {number} */
WebGLRenderingContext.MAX_FRAGMENT_UNIFORM_VECTORS;

/** @type {number} */
WebGLRenderingContext.SHADER_TYPE;

/** @type {number} */
WebGLRenderingContext.DELETE_STATUS;

/** @type {number} */
WebGLRenderingContext.LINK_STATUS;

/** @type {number} */
WebGLRenderingContext.VALIDATE_STATUS;

/** @type {number} */
WebGLRenderingContext.ATTACHED_SHADERS;

/** @type {number} */
WebGLRenderingContext.ACTIVE_UNIFORMS;

/** @type {number} */
WebGLRenderingContext.ACTIVE_UNIFORM_MAX_LENGTH;

/** @type {number} */
WebGLRenderingContext.ACTIVE_ATTRIBUTES;

/** @type {number} */
WebGLRenderingContext.ACTIVE_ATTRIBUTE_MAX_LENGTH;

/** @type {number} */
WebGLRenderingContext.SHADING_LANGUAGE_VERSION;

/** @type {number} */
WebGLRenderingContext.CURRENT_PROGRAM;

/** @type {number} */
WebGLRenderingContext.NEVER;

/** @type {number} */
WebGLRenderingContext.LESS;

/** @type {number} */
WebGLRenderingContext.EQUAL;

/** @type {number} */
WebGLRenderingContext.LEQUAL;

/** @type {number} */
WebGLRenderingContext.GREATER;

/** @type {number} */
WebGLRenderingContext.NOTEQUAL;

/** @type {number} */
WebGLRenderingContext.GEQUAL;

/** @type {number} */
WebGLRenderingContext.ALWAYS;

/** @type {number} */
WebGLRenderingContext.KEEP;

/** @type {number} */
WebGLRenderingContext.REPLACE;

/** @type {number} */
WebGLRenderingContext.INCR;

/** @type {number} */
WebGLRenderingContext.DECR;

/** @type {number} */
WebGLRenderingContext.INVERT;

/** @type {number} */
WebGLRenderingContext.INCR_WRAP;

/** @type {number} */
WebGLRenderingContext.DECR_WRAP;

/** @type {number} */
WebGLRenderingContext.VENDOR;

/** @type {number} */
WebGLRenderingContext.RENDERER;

/** @type {number} */
WebGLRenderingContext.VERSION;

/** @type {number} */
WebGLRenderingContext.EXTENSIONS;

/** @type {number} */
WebGLRenderingContext.NEAREST;

/** @type {number} */
WebGLRenderingContext.LINEAR;

/** @type {number} */
WebGLRenderingContext.NEAREST_MIPMAP_NEAREST;

/** @type {number} */
WebGLRenderingContext.LINEAR_MIPMAP_NEAREST;

/** @type {number} */
WebGLRenderingContext.NEAREST_MIPMAP_LINEAR;

/** @type {number} */
WebGLRenderingContext.LINEAR_MIPMAP_LINEAR;

/** @type {number} */
WebGLRenderingContext.TEXTURE_MAG_FILTER;

/** @type {number} */
WebGLRenderingContext.TEXTURE_MIN_FILTER;

/** @type {number} */
WebGLRenderingContext.TEXTURE_WRAP_S;

/** @type {number} */
WebGLRenderingContext.TEXTURE_WRAP_T;

/** @type {number} */
WebGLRenderingContext.TEXTURE;

/** @type {number} */
WebGLRenderingContext.TEXTURE_CUBE_MAP;

/** @type {number} */
WebGLRenderingContext.TEXTURE_BINDING_CUBE_MAP;

/** @type {number} */
WebGLRenderingContext.TEXTURE_CUBE_MAP_POSITIVE_X;

/** @type {number} */
WebGLRenderingContext.TEXTURE_CUBE_MAP_NEGATIVE_X;

/** @type {number} */
WebGLRenderingContext.TEXTURE_CUBE_MAP_POSITIVE_Y;

/** @type {number} */
WebGLRenderingContext.TEXTURE_CUBE_MAP_NEGATIVE_Y;

/** @type {number} */
WebGLRenderingContext.TEXTURE_CUBE_MAP_POSITIVE_Z;

/** @type {number} */
WebGLRenderingContext.TEXTURE_CUBE_MAP_NEGATIVE_Z;

/** @type {number} */
WebGLRenderingContext.MAX_CUBE_MAP_TEXTURE_SIZE;

/** @type {number} */
WebGLRenderingContext.TEXTURE0;

/** @type {number} */
WebGLRenderingContext.TEXTURE1;

/** @type {number} */
WebGLRenderingContext.TEXTURE2;

/** @type {number} */
WebGLRenderingContext.TEXTURE3;

/** @type {number} */
WebGLRenderingContext.TEXTURE4;

/** @type {number} */
WebGLRenderingContext.TEXTURE5;

/** @type {number} */
WebGLRenderingContext.TEXTURE6;

/** @type {number} */
WebGLRenderingContext.TEXTURE7;

/** @type {number} */
WebGLRenderingContext.TEXTURE8;

/** @type {number} */
WebGLRenderingContext.TEXTURE9;

/** @type {number} */
WebGLRenderingContext.TEXTURE10;

/** @type {number} */
WebGLRenderingContext.TEXTURE11;

/** @type {number} */
WebGLRenderingContext.TEXTURE12;

/** @type {number} */
WebGLRenderingContext.TEXTURE13;

/** @type {number} */
WebGLRenderingContext.TEXTURE14;

/** @type {number} */
WebGLRenderingContext.TEXTURE15;

/** @type {number} */
WebGLRenderingContext.TEXTURE16;

/** @type {number} */
WebGLRenderingContext.TEXTURE17;

/** @type {number} */
WebGLRenderingContext.TEXTURE18;

/** @type {number} */
WebGLRenderingContext.TEXTURE19;

/** @type {number} */
WebGLRenderingContext.TEXTURE20;

/** @type {number} */
WebGLRenderingContext.TEXTURE21;

/** @type {number} */
WebGLRenderingContext.TEXTURE22;

/** @type {number} */
WebGLRenderingContext.TEXTURE23;

/** @type {number} */
WebGLRenderingContext.TEXTURE24;

/** @type {number} */
WebGLRenderingContext.TEXTURE25;

/** @type {number} */
WebGLRenderingContext.TEXTURE26;

/** @type {number} */
WebGLRenderingContext.TEXTURE27;

/** @type {number} */
WebGLRenderingContext.TEXTURE28;

/** @type {number} */
WebGLRenderingContext.TEXTURE29;

/** @type {number} */
WebGLRenderingContext.TEXTURE30;

/** @type {number} */
WebGLRenderingContext.TEXTURE31;

/** @type {number} */
WebGLRenderingContext.ACTIVE_TEXTURE;

/** @type {number} */
WebGLRenderingContext.REPEAT;

/** @type {number} */
WebGLRenderingContext.CLAMP_TO_EDGE;

/** @type {number} */
WebGLRenderingContext.MIRRORED_REPEAT;

/** @type {number} */
WebGLRenderingContext.FLOAT_VEC2;

/** @type {number} */
WebGLRenderingContext.FLOAT_VEC3;

/** @type {number} */
WebGLRenderingContext.FLOAT_VEC4;

/** @type {number} */
WebGLRenderingContext.INT_VEC2;

/** @type {number} */
WebGLRenderingContext.INT_VEC3;

/** @type {number} */
WebGLRenderingContext.INT_VEC4;

/** @type {number} */
WebGLRenderingContext.BOOL;

/** @type {number} */
WebGLRenderingContext.BOOL_VEC2;

/** @type {number} */
WebGLRenderingContext.BOOL_VEC3;

/** @type {number} */
WebGLRenderingContext.BOOL_VEC4;

/** @type {number} */
WebGLRenderingContext.FLOAT_MAT2;

/** @type {number} */
WebGLRenderingContext.FLOAT_MAT3;

/** @type {number} */
WebGLRenderingContext.FLOAT_MAT4;

/** @type {number} */
WebGLRenderingContext.SAMPLER_2D;

/** @type {number} */
WebGLRenderingContext.SAMPLER_CUBE;

/** @type {number} */
WebGLRenderingContext.VERTEX_ATTRIB_ARRAY_ENABLED;

/** @type {number} */
WebGLRenderingContext.VERTEX_ATTRIB_ARRAY_SIZE;

/** @type {number} */
WebGLRenderingContext.VERTEX_ATTRIB_ARRAY_STRIDE;

/** @type {number} */
WebGLRenderingContext.VERTEX_ATTRIB_ARRAY_TYPE;

/** @type {number} */
WebGLRenderingContext.VERTEX_ATTRIB_ARRAY_NORMALIZED;

/** @type {number} */
WebGLRenderingContext.VERTEX_ATTRIB_ARRAY_POINTER;

/** @type {number} */
WebGLRenderingContext.VERTEX_ATTRIB_ARRAY_BUFFER_BINDING;

/** @type {number} */
WebGLRenderingContext.IMPLEMENTATION_COLOR_READ_TYPE;

/** @type {number} */
WebGLRenderingContext.IMPLEMENTATION_COLOR_READ_FORMAT;

/** @type {number} */
WebGLRenderingContext.COMPILE_STATUS;

/** @type {number} */
WebGLRenderingContext.INFO_LOG_LENGTH;

/** @type {number} */
WebGLRenderingContext.SHADER_SOURCE_LENGTH;

/** @type {number} */
WebGLRenderingContext.SHADER_COMPILER;

/** @type {number} */
WebGLRenderingContext.LOW_FLOAT;

/** @type {number} */
WebGLRenderingContext.MEDIUM_FLOAT;

/** @type {number} */
WebGLRenderingContext.HIGH_FLOAT;

/** @type {number} */
WebGLRenderingContext.LOW_INT;

/** @type {number} */
WebGLRenderingContext.MEDIUM_INT;

/** @type {number} */
WebGLRenderingContext.HIGH_INT;

/** @type {number} */
WebGLRenderingContext.FRAMEBUFFER;

/** @type {number} */
WebGLRenderingContext.RENDERBUFFER;

/** @type {number} */
WebGLRenderingContext.RGBA4;

/** @type {number} */
WebGLRenderingContext.RGB5_A1;

/** @type {number} */
WebGLRenderingContext.RGB565;

/** @type {number} */
WebGLRenderingContext.DEPTH_COMPONENT16;

/** @type {number} */
WebGLRenderingContext.STENCIL_INDEX;

/** @type {number} */
WebGLRenderingContext.STENCIL_INDEX8;

/** @type {number} */
WebGLRenderingContext.DEPTH_STENCIL;

/** @type {number} */
WebGLRenderingContext.RENDERBUFFER_WIDTH;

/** @type {number} */
WebGLRenderingContext.RENDERBUFFER_HEIGHT;

/** @type {number} */
WebGLRenderingContext.RENDERBUFFER_INTERNAL_FORMAT;

/** @type {number} */
WebGLRenderingContext.RENDERBUFFER_RED_SIZE;

/** @type {number} */
WebGLRenderingContext.RENDERBUFFER_GREEN_SIZE;

/** @type {number} */
WebGLRenderingContext.RENDERBUFFER_BLUE_SIZE;

/** @type {number} */
WebGLRenderingContext.RENDERBUFFER_ALPHA_SIZE;

/** @type {number} */
WebGLRenderingContext.RENDERBUFFER_DEPTH_SIZE;

/** @type {number} */
WebGLRenderingContext.RENDERBUFFER_STENCIL_SIZE;

/** @type {number} */
WebGLRenderingContext.FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE;

/** @type {number} */
WebGLRenderingContext.FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;

/** @type {number} */
WebGLRenderingContext.FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL;

/** @type {number} */
WebGLRenderingContext.FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE;

/** @type {number} */
WebGLRenderingContext.COLOR_ATTACHMENT0;

/** @type {number} */
WebGLRenderingContext.DEPTH_ATTACHMENT;

/** @type {number} */
WebGLRenderingContext.STENCIL_ATTACHMENT;

/** @type {number} */
WebGLRenderingContext.DEPTH_STENCIL_ATTACHMENT;

/** @type {number} */
WebGLRenderingContext.NONE;

/** @type {number} */
WebGLRenderingContext.FRAMEBUFFER_COMPLETE;

/** @type {number} */
WebGLRenderingContext.FRAMEBUFFER_INCOMPLETE_ATTACHMENT;

/** @type {number} */
WebGLRenderingContext.FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT;

/** @type {number} */
WebGLRenderingContext.FRAMEBUFFER_INCOMPLETE_DIMENSIONS;

/** @type {number} */
WebGLRenderingContext.FRAMEBUFFER_UNSUPPORTED;

/** @type {number} */
WebGLRenderingContext.FRAMEBUFFER_BINDING;

/** @type {number} */
WebGLRenderingContext.RENDERBUFFER_BINDING;

/** @type {number} */
WebGLRenderingContext.MAX_RENDERBUFFER_SIZE;

/** @type {number} */
WebGLRenderingContext.INVALID_FRAMEBUFFER_OPERATION;


/** @type {number} */
WebGLRenderingContext.prototype.DEPTH_BUFFER_BIT;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_BUFFER_BIT;

/** @type {number} */
WebGLRenderingContext.prototype.COLOR_BUFFER_BIT;

/** @type {number} */
WebGLRenderingContext.prototype.POINTS;

/** @type {number} */
WebGLRenderingContext.prototype.LINES;

/** @type {number} */
WebGLRenderingContext.prototype.LINE_LOOP;

/** @type {number} */
WebGLRenderingContext.prototype.LINE_STRIP;

/** @type {number} */
WebGLRenderingContext.prototype.TRIANGLES;

/** @type {number} */
WebGLRenderingContext.prototype.TRIANGLE_STRIP;

/** @type {number} */
WebGLRenderingContext.prototype.TRIANGLE_FAN;

/** @type {number} */
WebGLRenderingContext.prototype.ZERO;

/** @type {number} */
WebGLRenderingContext.prototype.ONE;

/** @type {number} */
WebGLRenderingContext.prototype.SRC_COLOR;

/** @type {number} */
WebGLRenderingContext.prototype.ONE_MINUS_SRC_COLOR;

/** @type {number} */
WebGLRenderingContext.prototype.SRC_ALPHA;

/** @type {number} */
WebGLRenderingContext.prototype.ONE_MINUS_SRC_ALPHA;

/** @type {number} */
WebGLRenderingContext.prototype.DST_ALPHA;

/** @type {number} */
WebGLRenderingContext.prototype.ONE_MINUS_DST_ALPHA;

/** @type {number} */
WebGLRenderingContext.prototype.DST_COLOR;

/** @type {number} */
WebGLRenderingContext.prototype.ONE_MINUS_DST_COLOR;

/** @type {number} */
WebGLRenderingContext.prototype.SRC_ALPHA_SATURATE;

/** @type {number} */
WebGLRenderingContext.prototype.FUNC_ADD;

/** @type {number} */
WebGLRenderingContext.prototype.BLEND_EQUATION;

/** @type {number} */
WebGLRenderingContext.prototype.BLEND_EQUATION_RGB;

/** @type {number} */
WebGLRenderingContext.prototype.BLEND_EQUATION_ALPHA;

/** @type {number} */
WebGLRenderingContext.prototype.FUNC_SUBTRACT;

/** @type {number} */
WebGLRenderingContext.prototype.FUNC_REVERSE_SUBTRACT;

/** @type {number} */
WebGLRenderingContext.prototype.BLEND_DST_RGB;

/** @type {number} */
WebGLRenderingContext.prototype.BLEND_SRC_RGB;

/** @type {number} */
WebGLRenderingContext.prototype.BLEND_DST_ALPHA;

/** @type {number} */
WebGLRenderingContext.prototype.BLEND_SRC_ALPHA;

/** @type {number} */
WebGLRenderingContext.prototype.CONSTANT_COLOR;

/** @type {number} */
WebGLRenderingContext.prototype.ONE_MINUS_CONSTANT_COLOR;

/** @type {number} */
WebGLRenderingContext.prototype.CONSTANT_ALPHA;

/** @type {number} */
WebGLRenderingContext.prototype.ONE_MINUS_CONSTANT_ALPHA;

/** @type {number} */
WebGLRenderingContext.prototype.BLEND_COLOR;

/** @type {number} */
WebGLRenderingContext.prototype.ARRAY_BUFFER;

/** @type {number} */
WebGLRenderingContext.prototype.ELEMENT_ARRAY_BUFFER;

/** @type {number} */
WebGLRenderingContext.prototype.ARRAY_BUFFER_BINDING;

/** @type {number} */
WebGLRenderingContext.prototype.ELEMENT_ARRAY_BUFFER_BINDING;

/** @type {number} */
WebGLRenderingContext.prototype.STREAM_DRAW;

/** @type {number} */
WebGLRenderingContext.prototype.STATIC_DRAW;

/** @type {number} */
WebGLRenderingContext.prototype.DYNAMIC_DRAW;

/** @type {number} */
WebGLRenderingContext.prototype.BUFFER_SIZE;

/** @type {number} */
WebGLRenderingContext.prototype.BUFFER_USAGE;

/** @type {number} */
WebGLRenderingContext.prototype.CURRENT_VERTEX_ATTRIB;

/** @type {number} */
WebGLRenderingContext.prototype.FRONT;

/** @type {number} */
WebGLRenderingContext.prototype.BACK;

/** @type {number} */
WebGLRenderingContext.prototype.FRONT_AND_BACK;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_2D;

/** @type {number} */
WebGLRenderingContext.prototype.CULL_FACE;

/** @type {number} */
WebGLRenderingContext.prototype.BLEND;

/** @type {number} */
WebGLRenderingContext.prototype.DITHER;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_TEST;

/** @type {number} */
WebGLRenderingContext.prototype.DEPTH_TEST;

/** @type {number} */
WebGLRenderingContext.prototype.SCISSOR_TEST;

/** @type {number} */
WebGLRenderingContext.prototype.POLYGON_OFFSET_FILL;

/** @type {number} */
WebGLRenderingContext.prototype.SAMPLE_ALPHA_TO_COVERAGE;

/** @type {number} */
WebGLRenderingContext.prototype.SAMPLE_COVERAGE;

/** @type {number} */
WebGLRenderingContext.prototype.NO_ERROR;

/** @type {number} */
WebGLRenderingContext.prototype.INVALID_ENUM;

/** @type {number} */
WebGLRenderingContext.prototype.INVALID_VALUE;

/** @type {number} */
WebGLRenderingContext.prototype.INVALID_OPERATION;

/** @type {number} */
WebGLRenderingContext.prototype.OUT_OF_MEMORY;

/** @type {number} */
WebGLRenderingContext.prototype.CW;

/** @type {number} */
WebGLRenderingContext.prototype.CCW;

/** @type {number} */
WebGLRenderingContext.prototype.LINE_WIDTH;

/** @type {number} */
WebGLRenderingContext.prototype.ALIASED_POINT_SIZE_RANGE;

/** @type {number} */
WebGLRenderingContext.prototype.ALIASED_LINE_WIDTH_RANGE;

/** @type {number} */
WebGLRenderingContext.prototype.CULL_FACE_MODE;

/** @type {number} */
WebGLRenderingContext.prototype.FRONT_FACE;

/** @type {number} */
WebGLRenderingContext.prototype.DEPTH_RANGE;

/** @type {number} */
WebGLRenderingContext.prototype.DEPTH_WRITEMASK;

/** @type {number} */
WebGLRenderingContext.prototype.DEPTH_CLEAR_VALUE;

/** @type {number} */
WebGLRenderingContext.prototype.DEPTH_FUNC;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_CLEAR_VALUE;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_FUNC;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_FAIL;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_PASS_DEPTH_FAIL;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_PASS_DEPTH_PASS;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_REF;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_VALUE_MASK;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_WRITEMASK;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_BACK_FUNC;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_BACK_FAIL;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_BACK_PASS_DEPTH_FAIL;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_BACK_PASS_DEPTH_PASS;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_BACK_REF;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_BACK_VALUE_MASK;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_BACK_WRITEMASK;

/** @type {number} */
WebGLRenderingContext.prototype.VIEWPORT;

/** @type {number} */
WebGLRenderingContext.prototype.SCISSOR_BOX;

/** @type {number} */
WebGLRenderingContext.prototype.COLOR_CLEAR_VALUE;

/** @type {number} */
WebGLRenderingContext.prototype.COLOR_WRITEMASK;

/** @type {number} */
WebGLRenderingContext.prototype.UNPACK_ALIGNMENT;

/** @type {number} */
WebGLRenderingContext.prototype.UNPACK_FLIP_Y_WEBGL;

/** @type {number} */
WebGLRenderingContext.prototype.PACK_ALIGNMENT;

/** @type {number} */
WebGLRenderingContext.prototype.MAX_TEXTURE_SIZE;

/** @type {number} */
WebGLRenderingContext.prototype.MAX_VIEWPORT_DIMS;

/** @type {number} */
WebGLRenderingContext.prototype.SUBPIXEL_BITS;

/** @type {number} */
WebGLRenderingContext.prototype.RED_BITS;

/** @type {number} */
WebGLRenderingContext.prototype.GREEN_BITS;

/** @type {number} */
WebGLRenderingContext.prototype.BLUE_BITS;

/** @type {number} */
WebGLRenderingContext.prototype.ALPHA_BITS;

/** @type {number} */
WebGLRenderingContext.prototype.DEPTH_BITS;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_BITS;

/** @type {number} */
WebGLRenderingContext.prototype.POLYGON_OFFSET_UNITS;

/** @type {number} */
WebGLRenderingContext.prototype.POLYGON_OFFSET_FACTOR;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_BINDING_2D;

/** @type {number} */
WebGLRenderingContext.prototype.SAMPLE_BUFFERS;

/** @type {number} */
WebGLRenderingContext.prototype.SAMPLES;

/** @type {number} */
WebGLRenderingContext.prototype.SAMPLE_COVERAGE_VALUE;

/** @type {number} */
WebGLRenderingContext.prototype.SAMPLE_COVERAGE_INVERT;

/** @type {number} */
WebGLRenderingContext.prototype.NUM_COMPRESSED_TEXTURE_FORMATS;

/** @type {number} */
WebGLRenderingContext.prototype.COMPRESSED_TEXTURE_FORMATS;

/** @type {number} */
WebGLRenderingContext.prototype.DONT_CARE;

/** @type {number} */
WebGLRenderingContext.prototype.FASTEST;

/** @type {number} */
WebGLRenderingContext.prototype.NICEST;

/** @type {number} */
WebGLRenderingContext.prototype.GENERATE_MIPMAP_HINT;

/** @type {number} */
WebGLRenderingContext.prototype.BYTE;

/** @type {number} */
WebGLRenderingContext.prototype.UNSIGNED_BYTE;

/** @type {number} */
WebGLRenderingContext.prototype.SHORT;

/** @type {number} */
WebGLRenderingContext.prototype.UNSIGNED_SHORT;

/** @type {number} */
WebGLRenderingContext.prototype.INT;

/** @type {number} */
WebGLRenderingContext.prototype.UNSIGNED_INT;

/** @type {number} */
WebGLRenderingContext.prototype.FLOAT;

/** @type {number} */
WebGLRenderingContext.prototype.DEPTH_COMPONENT;

/** @type {number} */
WebGLRenderingContext.prototype.ALPHA;

/** @type {number} */
WebGLRenderingContext.prototype.RGB;

/** @type {number} */
WebGLRenderingContext.prototype.RGBA;

/** @type {number} */
WebGLRenderingContext.prototype.LUMINANCE;

/** @type {number} */
WebGLRenderingContext.prototype.LUMINANCE_ALPHA;

/** @type {number} */
WebGLRenderingContext.prototype.UNSIGNED_SHORT_4_4_4_4;

/** @type {number} */
WebGLRenderingContext.prototype.UNSIGNED_SHORT_5_5_5_1;

/** @type {number} */
WebGLRenderingContext.prototype.UNSIGNED_SHORT_5_6_5;

/** @type {number} */
WebGLRenderingContext.prototype.FRAGMENT_SHADER;

/** @type {number} */
WebGLRenderingContext.prototype.VERTEX_SHADER;

/** @type {number} */
WebGLRenderingContext.prototype.MAX_VERTEX_ATTRIBS;

/** @type {number} */
WebGLRenderingContext.prototype.MAX_VERTEX_UNIFORM_VECTORS;

/** @type {number} */
WebGLRenderingContext.prototype.MAX_VARYING_VECTORS;

/** @type {number} */
WebGLRenderingContext.prototype.MAX_COMBINED_TEXTURE_IMAGE_UNITS;

/** @type {number} */
WebGLRenderingContext.prototype.MAX_VERTEX_TEXTURE_IMAGE_UNITS;

/** @type {number} */
WebGLRenderingContext.prototype.MAX_TEXTURE_IMAGE_UNITS;

/** @type {number} */
WebGLRenderingContext.prototype.MAX_FRAGMENT_UNIFORM_VECTORS;

/** @type {number} */
WebGLRenderingContext.prototype.SHADER_TYPE;

/** @type {number} */
WebGLRenderingContext.prototype.DELETE_STATUS;

/** @type {number} */
WebGLRenderingContext.prototype.LINK_STATUS;

/** @type {number} */
WebGLRenderingContext.prototype.VALIDATE_STATUS;

/** @type {number} */
WebGLRenderingContext.prototype.ATTACHED_SHADERS;

/** @type {number} */
WebGLRenderingContext.prototype.ACTIVE_UNIFORMS;

/** @type {number} */
WebGLRenderingContext.prototype.ACTIVE_UNIFORM_MAX_LENGTH;

/** @type {number} */
WebGLRenderingContext.prototype.ACTIVE_ATTRIBUTES;

/** @type {number} */
WebGLRenderingContext.prototype.ACTIVE_ATTRIBUTE_MAX_LENGTH;

/** @type {number} */
WebGLRenderingContext.prototype.SHADING_LANGUAGE_VERSION;

/** @type {number} */
WebGLRenderingContext.prototype.CURRENT_PROGRAM;

/** @type {number} */
WebGLRenderingContext.prototype.NEVER;

/** @type {number} */
WebGLRenderingContext.prototype.LESS;

/** @type {number} */
WebGLRenderingContext.prototype.EQUAL;

/** @type {number} */
WebGLRenderingContext.prototype.LEQUAL;

/** @type {number} */
WebGLRenderingContext.prototype.GREATER;

/** @type {number} */
WebGLRenderingContext.prototype.NOTEQUAL;

/** @type {number} */
WebGLRenderingContext.prototype.GEQUAL;

/** @type {number} */
WebGLRenderingContext.prototype.ALWAYS;

/** @type {number} */
WebGLRenderingContext.prototype.KEEP;

/** @type {number} */
WebGLRenderingContext.prototype.REPLACE;

/** @type {number} */
WebGLRenderingContext.prototype.INCR;

/** @type {number} */
WebGLRenderingContext.prototype.DECR;

/** @type {number} */
WebGLRenderingContext.prototype.INVERT;

/** @type {number} */
WebGLRenderingContext.prototype.INCR_WRAP;

/** @type {number} */
WebGLRenderingContext.prototype.DECR_WRAP;

/** @type {number} */
WebGLRenderingContext.prototype.VENDOR;

/** @type {number} */
WebGLRenderingContext.prototype.RENDERER;

/** @type {number} */
WebGLRenderingContext.prototype.VERSION;

/** @type {number} */
WebGLRenderingContext.prototype.EXTENSIONS;

/** @type {number} */
WebGLRenderingContext.prototype.NEAREST;

/** @type {number} */
WebGLRenderingContext.prototype.LINEAR;

/** @type {number} */
WebGLRenderingContext.prototype.NEAREST_MIPMAP_NEAREST;

/** @type {number} */
WebGLRenderingContext.prototype.LINEAR_MIPMAP_NEAREST;

/** @type {number} */
WebGLRenderingContext.prototype.NEAREST_MIPMAP_LINEAR;

/** @type {number} */
WebGLRenderingContext.prototype.LINEAR_MIPMAP_LINEAR;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_MAG_FILTER;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_MIN_FILTER;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_WRAP_S;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_WRAP_T;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_CUBE_MAP;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_BINDING_CUBE_MAP;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_CUBE_MAP_POSITIVE_X;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_CUBE_MAP_NEGATIVE_X;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_CUBE_MAP_POSITIVE_Y;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_CUBE_MAP_NEGATIVE_Y;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_CUBE_MAP_POSITIVE_Z;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE_CUBE_MAP_NEGATIVE_Z;

/** @type {number} */
WebGLRenderingContext.prototype.MAX_CUBE_MAP_TEXTURE_SIZE;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE0;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE1;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE2;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE3;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE4;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE5;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE6;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE7;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE8;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE9;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE10;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE11;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE12;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE13;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE14;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE15;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE16;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE17;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE18;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE19;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE20;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE21;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE22;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE23;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE24;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE25;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE26;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE27;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE28;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE29;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE30;

/** @type {number} */
WebGLRenderingContext.prototype.TEXTURE31;

/** @type {number} */
WebGLRenderingContext.prototype.ACTIVE_TEXTURE;

/** @type {number} */
WebGLRenderingContext.prototype.REPEAT;

/** @type {number} */
WebGLRenderingContext.prototype.CLAMP_TO_EDGE;

/** @type {number} */
WebGLRenderingContext.prototype.MIRRORED_REPEAT;

/** @type {number} */
WebGLRenderingContext.prototype.FLOAT_VEC2;

/** @type {number} */
WebGLRenderingContext.prototype.FLOAT_VEC3;

/** @type {number} */
WebGLRenderingContext.prototype.FLOAT_VEC4;

/** @type {number} */
WebGLRenderingContext.prototype.INT_VEC2;

/** @type {number} */
WebGLRenderingContext.prototype.INT_VEC3;

/** @type {number} */
WebGLRenderingContext.prototype.INT_VEC4;

/** @type {number} */
WebGLRenderingContext.prototype.BOOL;

/** @type {number} */
WebGLRenderingContext.prototype.BOOL_VEC2;

/** @type {number} */
WebGLRenderingContext.prototype.BOOL_VEC3;

/** @type {number} */
WebGLRenderingContext.prototype.BOOL_VEC4;

/** @type {number} */
WebGLRenderingContext.prototype.FLOAT_MAT2;

/** @type {number} */
WebGLRenderingContext.prototype.FLOAT_MAT3;

/** @type {number} */
WebGLRenderingContext.prototype.FLOAT_MAT4;

/** @type {number} */
WebGLRenderingContext.prototype.SAMPLER_2D;

/** @type {number} */
WebGLRenderingContext.prototype.SAMPLER_CUBE;

/** @type {number} */
WebGLRenderingContext.prototype.VERTEX_ATTRIB_ARRAY_ENABLED;

/** @type {number} */
WebGLRenderingContext.prototype.VERTEX_ATTRIB_ARRAY_SIZE;

/** @type {number} */
WebGLRenderingContext.prototype.VERTEX_ATTRIB_ARRAY_STRIDE;

/** @type {number} */
WebGLRenderingContext.prototype.VERTEX_ATTRIB_ARRAY_TYPE;

/** @type {number} */
WebGLRenderingContext.prototype.VERTEX_ATTRIB_ARRAY_NORMALIZED;

/** @type {number} */
WebGLRenderingContext.prototype.VERTEX_ATTRIB_ARRAY_POINTER;

/** @type {number} */
WebGLRenderingContext.prototype.VERTEX_ATTRIB_ARRAY_BUFFER_BINDING;

/** @type {number} */
WebGLRenderingContext.prototype.IMPLEMENTATION_COLOR_READ_TYPE;

/** @type {number} */
WebGLRenderingContext.prototype.IMPLEMENTATION_COLOR_READ_FORMAT;

/** @type {number} */
WebGLRenderingContext.prototype.COMPILE_STATUS;

/** @type {number} */
WebGLRenderingContext.prototype.INFO_LOG_LENGTH;

/** @type {number} */
WebGLRenderingContext.prototype.SHADER_SOURCE_LENGTH;

/** @type {number} */
WebGLRenderingContext.prototype.SHADER_COMPILER;

/** @type {number} */
WebGLRenderingContext.prototype.LOW_FLOAT;

/** @type {number} */
WebGLRenderingContext.prototype.MEDIUM_FLOAT;

/** @type {number} */
WebGLRenderingContext.prototype.HIGH_FLOAT;

/** @type {number} */
WebGLRenderingContext.prototype.LOW_INT;

/** @type {number} */
WebGLRenderingContext.prototype.MEDIUM_INT;

/** @type {number} */
WebGLRenderingContext.prototype.HIGH_INT;

/** @type {number} */
WebGLRenderingContext.prototype.FRAMEBUFFER;

/** @type {number} */
WebGLRenderingContext.prototype.RENDERBUFFER;

/** @type {number} */
WebGLRenderingContext.prototype.RGBA4;

/** @type {number} */
WebGLRenderingContext.prototype.RGB5_A1;

/** @type {number} */
WebGLRenderingContext.prototype.RGB565;

/** @type {number} */
WebGLRenderingContext.prototype.DEPTH_COMPONENT16;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_INDEX;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_INDEX8;

/** @type {number} */
WebGLRenderingContext.prototype.DEPTH_STENCIL;

/** @type {number} */
WebGLRenderingContext.prototype.RENDERBUFFER_WIDTH;

/** @type {number} */
WebGLRenderingContext.prototype.RENDERBUFFER_HEIGHT;

/** @type {number} */
WebGLRenderingContext.prototype.RENDERBUFFER_INTERNAL_FORMAT;

/** @type {number} */
WebGLRenderingContext.prototype.RENDERBUFFER_RED_SIZE;

/** @type {number} */
WebGLRenderingContext.prototype.RENDERBUFFER_GREEN_SIZE;

/** @type {number} */
WebGLRenderingContext.prototype.RENDERBUFFER_BLUE_SIZE;

/** @type {number} */
WebGLRenderingContext.prototype.RENDERBUFFER_ALPHA_SIZE;

/** @type {number} */
WebGLRenderingContext.prototype.RENDERBUFFER_DEPTH_SIZE;

/** @type {number} */
WebGLRenderingContext.prototype.RENDERBUFFER_STENCIL_SIZE;

/** @type {number} */
WebGLRenderingContext.prototype.FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE;

/** @type {number} */
WebGLRenderingContext.prototype.FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;

/** @type {number} */
WebGLRenderingContext.prototype.FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL;

/** @type {number} */
WebGLRenderingContext.prototype.FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE;

/** @type {number} */
WebGLRenderingContext.prototype.COLOR_ATTACHMENT0;

/** @type {number} */
WebGLRenderingContext.prototype.DEPTH_ATTACHMENT;

/** @type {number} */
WebGLRenderingContext.prototype.STENCIL_ATTACHMENT;

/** @type {number} */
WebGLRenderingContext.prototype.DEPTH_STENCIL_ATTACHMENT;

/** @type {number} */
WebGLRenderingContext.prototype.NONE;

/** @type {number} */
WebGLRenderingContext.prototype.FRAMEBUFFER_COMPLETE;

/** @type {number} */
WebGLRenderingContext.prototype.FRAMEBUFFER_INCOMPLETE_ATTACHMENT;

/** @type {number} */
WebGLRenderingContext.prototype.FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT;

/** @type {number} */
WebGLRenderingContext.prototype.FRAMEBUFFER_INCOMPLETE_DIMENSIONS;

/** @type {number} */
WebGLRenderingContext.prototype.FRAMEBUFFER_UNSUPPORTED;

/** @type {number} */
WebGLRenderingContext.prototype.FRAMEBUFFER_BINDING;

/** @type {number} */
WebGLRenderingContext.prototype.RENDERBUFFER_BINDING;

/** @type {number} */
WebGLRenderingContext.prototype.MAX_RENDERBUFFER_SIZE;

/** @type {number} */
WebGLRenderingContext.prototype.INVALID_FRAMEBUFFER_OPERATION;


/**
 * @type {HTMLCanvasElement}
 */
WebGLRenderingContext.prototype.canvas;

/**
 * @param {number} type
 * @return {number}
 */
WebGLRenderingContext.prototype.sizeInBytes = function(type) {};

/**
 * @return {WebGLContextAttributes}
 */
WebGLRenderingContext.prototype.getContextAttributes = function() {};

/**
 * @param {number} texture
 */
WebGLRenderingContext.prototype.activeTexture = function(texture) {};

/**
 * @param {WebGLProgram} program
 * @param {WebGLShader} shader
 */
WebGLRenderingContext.prototype.attachShader = function(program, shader) {};

/**
 * @param {WebGLProgram} program
 * @param {number} index
 * @param {string} name
 */
WebGLRenderingContext.prototype.bindAttribLocation = function(
    program, index, name) {};

/**
 * @param {number} target
 * @param {WebGLBuffer} buffer
 */
WebGLRenderingContext.prototype.bindBuffer = function(target, buffer) {};

/**
 * @param {number} target
 * @param {WebGLFramebuffer} buffer
 */
WebGLRenderingContext.prototype.bindFramebuffer = function(target, buffer) {};

/**
 * @param {number} target
 * @param {WebGLRenderbuffer} buffer
 */
WebGLRenderingContext.prototype.bindRenderbuffer = function(target, buffer) {};

/**
 * @param {number} target
 * @param {WebGLTexture} texture
 */
WebGLRenderingContext.prototype.bindTexture = function(target, texture) {};

/**
 * @param {number} red
 * @param {number} green
 * @param {number} blue
 * @param {number} alpha
 */
WebGLRenderingContext.prototype.blendColor = function(
    red, blue, green, alpha) {};

/**
 * @param {number} mode
 */
WebGLRenderingContext.prototype.blendEquation = function(mode) {};

/**
 * @param {number} modeRGB
 * @param {number} modeAlpha
 */
WebGLRenderingContext.prototype.blendEquationSeparate = function(
    modeRGB, modeAlpha) {};

/**
 * @param {number} sfactor
 * @param {number} dfactor
 */
WebGLRenderingContext.prototype.blendFunc = function(sfactor, dfactor) {};

/**
 * @param {number} srcRGB
 * @param {number} dstRGB
 * @param {number} srcAlpha
 * @param {number} dstAlpha
 */
WebGLRenderingContext.prototype.blendFuncSeparate = function(
    srcRGB, dstRGB, srcAlpha, dstAlpha) {};

/**
 * @param {number} target
 * @param {WebGLArray|WebGLArrayBuffer|ArrayBufferView|ArrayBuffer|number} data
 * @param {number} usage
 */
WebGLRenderingContext.prototype.bufferData = function(target, data, usage) {};

/**
 * @param {number} target
 * @param {number} offset
 * @param {WebGLArray|WebGLArrayBuffer|ArrayBufferView|ArrayBuffer} data
 */
WebGLRenderingContext.prototype.bufferSubData = function(
    target, offset, data) {};

/**
 * @param {number} target
 * @return {number}
 */
WebGLRenderingContext.prototype.checkFramebufferStatus = function(target) {};

/**
 * @param {number} mask
 */
WebGLRenderingContext.prototype.clear = function(mask) {};

/**
 * @param {number} red
 * @param {number} green
 * @param {number} blue
 * @param {number} alpha
 */
WebGLRenderingContext.prototype.clearColor = function(
    red, green, blue, alpha) {};

/**
 * @param {number} depth
 */
WebGLRenderingContext.prototype.clearDepth = function(depth) {};

/**
 * @param {number} s
 */
WebGLRenderingContext.prototype.clearStencil = function(s) {};

/**
 * @param {boolean} red
 * @param {boolean} green
 * @param {boolean} blue
 * @param {boolean} alpha
 */
WebGLRenderingContext.prototype.colorMask = function(
    red, green, blue, alpha) {};

/**
 * @param {WebGLShader} shader
 */
WebGLRenderingContext.prototype.compileShader = function(shader) {};

/**
 * @param {number} target
 * @param {number} level
 * @param {number} format
 * @param {number} x
 * @param {number} y
 * @param {number} width
 * @param {number} height
 * @param {number} border
 */
WebGLRenderingContext.prototype.copyTexImage2D = function(
    target, level, format, x, y, width, height, border) {};

/**
 * @param {number} target
 * @param {number} level
 * @param {number} xoffset
 * @param {number} yoffset
 * @param {number} x
 * @param {number} y
 * @param {number} width
 * @param {number} height
 */
WebGLRenderingContext.prototype.copyTexSubImage2D = function(
    target, level, xoffset, yoffset, x, y, width, height) {};

/**
 * @return {WebGLBuffer}
 */
WebGLRenderingContext.prototype.createBuffer = function() {};

/**
 * @return {WebGLFramebuffer}
 */
WebGLRenderingContext.prototype.createFramebuffer = function() {};

/**
 * @return {WebGLProgram}
 */
WebGLRenderingContext.prototype.createProgram = function() {};

/**
 * @return {WebGLRenderbuffer}
 */
WebGLRenderingContext.prototype.createRenderbuffer = function() {};

/**
 * @param {number} type
 * @return {WebGLShader}
 */
WebGLRenderingContext.prototype.createShader = function(type) {};

/**
 * @return {WebGLTexture}
 */
WebGLRenderingContext.prototype.createTexture = function() {};

/**
 * @param {number} mode
 */
WebGLRenderingContext.prototype.cullFace = function(mode) {};

/**
 * @param {WebGLBuffer} buffer
 */
WebGLRenderingContext.prototype.deleteBuffer = function(buffer) {};

/**
 * @param {WebGLFramebuffer} buffer
 */
WebGLRenderingContext.prototype.deleteFramebuffer = function(buffer) {};

/**
 * @param {WebGLProgram} program
 */
WebGLRenderingContext.prototype.deleteProgram = function(program) {};

/**
 * @param {WebGLRenderbuffer} buffer
 */
WebGLRenderingContext.prototype.deleteRenderbuffer = function(buffer) {};

/**
 * @param {WebGLShader} shader
 */
WebGLRenderingContext.prototype.deleteShader = function(shader) {};

/**
 * @param {WebGLTexture} texture
 */
WebGLRenderingContext.prototype.deleteTexture = function(texture) {};

/**
 * @param {number} func
 */
WebGLRenderingContext.prototype.depthFunc = function(func) {};

/**
 * @param {boolean} flag
 */
WebGLRenderingContext.prototype.depthMask = function(flag) {};

/**
 * @param {number} nearVal
 * @param {number} farVal
 */
WebGLRenderingContext.prototype.depthRange = function(nearVal, farVal) {};

/**
 * @param {WebGLProgram} program
 * @param {WebGLShader} shader
 */
WebGLRenderingContext.prototype.detachShader = function(program, shader) {};

/**
 * @param {number} flags
 */
WebGLRenderingContext.prototype.disable = function(flags) {};

/**
 * @param {number} attribute
 */
WebGLRenderingContext.prototype.disableVertexAttribArray = function(
    attribute) {};

/**
 * @param {number} mode
 * @param {number} first
 * @param {number} count
 */
WebGLRenderingContext.prototype.drawArrays = function(mode, first, count) {};

/**
 * @param {number} mode
 * @param {number} count
 * @param {number} type
 * @param {number} offset
 */
WebGLRenderingContext.prototype.drawElements = function(
    mode, count, type, offset) {};

/**
 * @param {number} flags
 */
WebGLRenderingContext.prototype.enable = function(flags) {};

/**
 * @param {number} attribute
 */
WebGLRenderingContext.prototype.enableVertexAttribArray = function(
    attribute) {};

WebGLRenderingContext.prototype.finish = function() {};

WebGLRenderingContext.prototype.flush = function() {};

/**
 * @param {number} target
 * @param {number} attachment
 * @param {number} rbTarget
 * @param {WebGLRenderbuffer} buffer
 */
WebGLRenderingContext.prototype.framebufferRenderbuffer = function(
    target, attachment, rbTarget, buffer) {};

/**
 * @param {number} target
 * @param {number} attachment
 * @param {number} texTarget
 * @param {WebGLTexture} texture
 * @param {number} level
 */
WebGLRenderingContext.prototype.framebufferTexture2D = function(
    target, attachment, texTarget, texture, level) {};

/**
 * @param {number} mode
 */
WebGLRenderingContext.prototype.frontFace = function(mode) {};

/**
 * @param {number} target
 */
WebGLRenderingContext.prototype.generateMipmap = function(target) {};

/**
 * @param {number} program
 * @param {number} index
 * @return {WebGLActiveInfo}
 */
WebGLRenderingContext.prototype.getActiveAttrib = function(program, index) {};

/**
 * @param {number} program
 * @param {number} index
 * @return {WebGLActiveInfo}
 */
WebGLRenderingContext.prototype.getActiveUniform = function(program, index) {};

/**
 * @param {number} program
 * @return {WebGLObjectArray}
 */
WebGLRenderingContext.prototype.getAttachedShaders = function(program) {};

/**
 * @param {WebGLProgram} program
 * @param {string} name
 * @return {number}
 */
WebGLRenderingContext.prototype.getAttribLocation = function(program, name) {};

/**
 * @param {number} pname
 * @return {*}
 */
WebGLRenderingContext.prototype.getParameter = function(pname) {};

/**
 * @param {number} target
 * @param {number} flag
 * @return {*}
 */
WebGLRenderingContext.prototype.getBufferParameter = function(target, flag) {};

/**
 * @return {number}
 */
WebGLRenderingContext.prototype.getError = function() {};

/**
 * @param {number} target
 * @param {number} attachment
 * @param {number} flag
 * @return {*}
 */
WebGLRenderingContext.prototype.getFramebufferAttachmentParameter = function(
    target, attachment, flag) {};

/**
 * @param {WebGLProgram} program
 * @param {number} flag
 * @return {*}
 */
WebGLRenderingContext.prototype.getProgramParameter = function(
    program, flag) {};

/**
 * @param {WebGLProgram} program
 * @return {string}
 */
WebGLRenderingContext.prototype.getProgramInfoLog = function(program) {};

/**
 * @param {number} target
 * @param {number} flag
 * @return {*}
 */
WebGLRenderingContext.prototype.getRenderbufferParameter = function(
    target, flag) {};

/**
 * @param {WebGLShader} shader
 * @param {number} flag
 * @return {*}
 */
WebGLRenderingContext.prototype.getShaderParameter = function(shader, flag) {};

/**
 * @param {WebGLShader} shader
 * @return {string}
 */
WebGLRenderingContext.prototype.getShaderInfoLog = function(shader) {};

/**
 * @param {WebGLShader} shader
 * @return {string}
 */
WebGLRenderingContext.prototype.getShaderSource = function(shader) {};

/**
 * @param {number} name
 * @return {string}
 */
WebGLRenderingContext.prototype.getString = function(name) {};

/**
 * @param {number} target
 * @param {number} flag
 * @return {*}
 */
WebGLRenderingContext.prototype.getTexParameter = function(target, flag) {};

/**
 * @param {WebGLProgram} program
 * @param {WebGLUniformLocation} location
 * @return {*}
 */
WebGLRenderingContext.prototype.getUniform = function(program, location) {};

/**
 * @param {WebGLProgram} program
 * @param {string} name
 * @return {WebGLUniformLocation}
 */
WebGLRenderingContext.prototype.getUniformLocation = function(program, name) {};

/**
 * @param {number} index
 * @param {number} flag
 * @return {*}
 */
WebGLRenderingContext.prototype.getVertexAttrib = function(index, flag) {};

/**
 * @param {number} index
 * @param {number} flag
 * @return {number}
 */
WebGLRenderingContext.prototype.getVertexAttribOffset = function(
    index, flag) {};

/**
 * @param {number} target
 * @param {number} hint
 */
WebGLRenderingContext.prototype.hint = function(target, hint) {};

/**
 * @param {WebGLObject} buffer
 * @return {boolean}
 */
WebGLRenderingContext.prototype.isBuffer = function(buffer) {};

/**
 * @param {number} flag
 * @return {boolean}
 */
WebGLRenderingContext.prototype.isEnabled = function(flag) {};

/**
 * @param {WebGLObject} buffer
 * @return {boolean}
 */
WebGLRenderingContext.prototype.isFramebuffer = function(buffer) {};

/**
 * @param {WebGLObject} program
 * @return {boolean}
 */
WebGLRenderingContext.prototype.isProgram = function(program) {};

/**
 * @param {WebGLObject} buffer
 * @return {boolean}
 */
WebGLRenderingContext.prototype.isRenderbuffer = function(buffer) {};

/**
 * @param {WebGLObject} shader
 * @return {boolean}
 */
WebGLRenderingContext.prototype.isShader = function(shader) {};

/**
 * @param {WebGLObject} texture
 * @return {boolean}
 */
WebGLRenderingContext.prototype.isTexture = function(texture) {};

/**
 * @param {number} width
 */
WebGLRenderingContext.prototype.lineWidth = function(width) {};

/**
 * @param {WebGLProgram} program
 */
WebGLRenderingContext.prototype.linkProgram = function(program) {};

/**
 * @param {number} flag
 * @param {number} value
 */
WebGLRenderingContext.prototype.pixelStorei = function(flag, value) {};

/**
 * @param {number} factor
 * @param {number} units
 */
WebGLRenderingContext.prototype.polygonOffset = function(factor, units) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} width
 * @param {number} height
 * @param {number} format
 * @param {number} type
 * @param {ArrayBufferView} pixels
 */
WebGLRenderingContext.prototype.readPixels = function(
    x, y, width, height, format, type, pixels) {};

/**
 * @param {number} target
 * @param {number} format
 * @param {number} width
 * @param {number} height
 */
WebGLRenderingContext.prototype.renderbufferStorage = function(
    target, format, width, height) {};

/**
 * @param {number} coverage
 * @param {boolean} invert
 */
WebGLRenderingContext.prototype.sampleCoverage = function(coverage, invert) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} width
 * @param {number} height
 */
WebGLRenderingContext.prototype.scissor = function(x, y, width, height) {};

/**
 * @param {WebGLShader} shader
 * @param {string} source
 */
WebGLRenderingContext.prototype.shaderSource = function(shader, source) {};

/**
 * @param {number} func
 * @param {number} ref
 * @param {number} mask
 */
WebGLRenderingContext.prototype.stencilFunc = function(func, ref, mask) {};

/**
 * @param {number} face
 * @param {number} func
 * @param {number} ref
 * @param {number} mask
 */
WebGLRenderingContext.prototype.stencilFuncSeparate = function(
    face, func, ref, mask) {};

/**
 * @param {number} mask
 */
WebGLRenderingContext.prototype.stencilMask = function(mask) {};

/**
 * @param {number} face
 * @param {number} mask
 */
WebGLRenderingContext.prototype.stencilMaskSeparate = function(face, mask) {};

/**
 * @param {number} sFail
 * @param {number} dpFail
 * @param {number} dpPass
 */
WebGLRenderingContext.prototype.stencilOp = function(sFail, dpFail, dpPass) {};

/**
 * @param {number} face
 * @param {number} sFail
 * @param {number} dpFail
 * @param {number} dpPass
 */
WebGLRenderingContext.prototype.stencilOpSeparate = function(
    face, sFail, dpFail, dpPass) {};

/**
 * @param {number} target
 * @param {number} level
 * @param {number} internalformat
 * @param {number} format or width
 * @param {number} type or height
 * @param {ImageData|HTMLImageElement|HTMLCanvasElement|HTMLVideoElement|
 *     number} img or border
 * @param {number=} opt_format
 * @param {number=} opt_type
 * @param {WebGLArray|ArrayBufferView=} opt_pixels
 */
WebGLRenderingContext.prototype.texImage2D = function(
    target, level, internalformat, format, type, img, opt_format, opt_type,
    opt_pixels) {};

/**
 * @param {number} target
 * @param {number} flag
 * @param {number} value
 */
WebGLRenderingContext.prototype.texParameterf = function(
    target, flag, value) {};

/**
 * @param {number} target
 * @param {number} flag
 * @param {number} value
 */
WebGLRenderingContext.prototype.texParameteri = function(
    target, flag, value) {};

/**
 * @param {number} target
 * @param {number} level
 * @param {number} xoffset
 * @param {number} yoffset
 * @param {number} format or width
 * @param {number} type or height
 * @param {ImageData|HTMLImageElement|HTMLCanvasElement|HTMLVideoElement|
 *     number} data or format
 * @param {number=} opt_type
 * @param {WebGLArray|ArrayBufferView=} opt_pixels
 */
WebGLRenderingContext.prototype.texSubImage2D = function(
    target, level, xoffset, yoffset, format, type, data, opt_type,
    opt_pixels) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {number} value
 */
WebGLRenderingContext.prototype.uniform1f = function(location, value) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {WebGLFloatArray|Float32Array} value
 */
WebGLRenderingContext.prototype.uniform1fv = function(location, value) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {number} value
 */
WebGLRenderingContext.prototype.uniform1i = function(location, value) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {WebGLIntArray|Int32Array} value
 */
WebGLRenderingContext.prototype.uniform1iv = function(location, value) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {number} value1
 * @param {number} value2
 */
WebGLRenderingContext.prototype.uniform2f = function(
    location, value1, value2) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {WebGLFloatArray|Float32Array} value
 */
WebGLRenderingContext.prototype.uniform2fv = function(location, value) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {number} value1
 * @param {number} value2
 */
WebGLRenderingContext.prototype.uniform2i = function(
    location, value1, value2) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {WebGLIntArray|Int32Array} value
 */
WebGLRenderingContext.prototype.uniform2iv = function(location, value) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {number} value1
 * @param {number} value2
 * @param {number} value3
 */
WebGLRenderingContext.prototype.uniform3f = function(
    location, value1, value2, value3) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {WebGLFloatArray|Float32Array} value
 */
WebGLRenderingContext.prototype.uniform3fv = function(location, value) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {number} value1
 * @param {number} value2
 * @param {number} value3
 */
WebGLRenderingContext.prototype.uniform3i = function(
    location, value1, value2, value3) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {WebGLIntArray|Int32Array} value
 */
WebGLRenderingContext.prototype.uniform3iv = function(location, value) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {number} value1
 * @param {number} value2
 * @param {number} value3
 * @param {number} value4
 */
WebGLRenderingContext.prototype.uniform4f = function(
    location, value1, value2, value3, value4) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {WebGLFloatArray|Float32Array} value
 */
WebGLRenderingContext.prototype.uniform4fv = function(location, value) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {number} value1
 * @param {number} value2
 * @param {number} value3
 * @param {number} value4
 */
WebGLRenderingContext.prototype.uniform4i = function(
    location, value1, value2, value3, value4) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {WebGLIntArray|Int32Array} value
 */
WebGLRenderingContext.prototype.uniform4iv = function(location, value) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {boolean} transpose
 * @param {WebGLFloatArray|Float32Array} data
 */
WebGLRenderingContext.prototype.uniformMatrix2fv = function(
    location, transpose, data) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {boolean} transpose
 * @param {WebGLFloatArray|Float32Array} data
 */
WebGLRenderingContext.prototype.uniformMatrix3fv = function(
    location, transpose, data) {};

/**
 * @param {WebGLUniformLocation} location
 * @param {boolean} transpose
 * @param {WebGLFloatArray|Float32Array} data
 */
WebGLRenderingContext.prototype.uniformMatrix4fv = function(
    location, transpose, data) {};

/**
 * @param {WebGLProgram} program
 */
WebGLRenderingContext.prototype.useProgram = function(program) {};

/**
 * @param {WebGLProgram} program
 */
WebGLRenderingContext.prototype.validateProgram = function(program) {};

/**
 * @param {number} index
 * @param {number} value
 */
WebGLRenderingContext.prototype.vertexAttrib1f = function(index, value) {};

/**
 * @param {number} index
 * @param {WebGLFloatArray|Float32Array} values
 */
WebGLRenderingContext.prototype.vertexAttrib1fv = function(index, values) {};

/**
 * @param {number} index
 * @param {number} value1
 * @param {number} value2
 */
WebGLRenderingContext.prototype.vertexAttrib2f = function(
    index, value1, value2) {};

/**
 * @param {number} index
 * @param {WebGLFloatArray|Float32Array} values
 */
WebGLRenderingContext.prototype.vertexAttrib2fv = function(
    index, values) {};

/**
 * @param {number} index
 * @param {number} value1
 * @param {number} value2
 * @param {number} value3
 */
WebGLRenderingContext.prototype.vertexAttrib3f = function(
    index, value1, value2, value3) {};

/**
 * @param {number} index
 * @param {WebGLFloatArray|Float32Array} values
 */
WebGLRenderingContext.prototype.vertexAttrib3fv = function(index, values) {};

/**
 * @param {number} index
 * @param {number} value1
 * @param {number} value2
 * @param {number} value3
 * @param {number} value4
 */
WebGLRenderingContext.prototype.vertexAttrib4f = function(
    index, value1, value2, value3, value4) {};

/**
 * @param {number} index
 * @param {WebGLFloatArray|Float32Array} values
 */
WebGLRenderingContext.prototype.vertexAttrib4fv = function(index, values) {};

/**
 * @param {number} index
 * @param {number} size
 * @param {number} type
 * @param {boolean} norm
 * @param {number} stride
 * @param {number} offset
 */
WebGLRenderingContext.prototype.vertexAttribPointer = function(
    index, size, type, norm, stride, offset) {};

/**
 * @param {number} x
 * @param {number} y
 * @param {number} width
 * @param {number} height
 */
WebGLRenderingContext.prototype.viewport = function(x, y, width, height) {};


/**
 * @constructor
 */
function WebGLContextAttributes() {}

/**
 * @param {string} name
 * @return {string}
 */
WebGLContextAttributes.prototype.get = function(name) {};

/**
 * @param {string} name
 * @param {string} value
 */
WebGLContextAttributes.prototype.set = function(name, value) {};

/**
 * @param {string} name
 */
WebGLContextAttributes.prototype.remove = function(name) {};


/**
 * @constructor
 */
function WebGLObject() {}


/**
 * @constructor
 * @extends {WebGLObject}
 */
function WebGLBuffer() {}


/**
 * @constructor
 * @extends {WebGLObject}
 */
function WebGLFramebuffer() {}


/**
 * @constructor
 * @extends {WebGLObject}
 */
function WebGLProgram() {}


/**
 * @constructor
 * @extends {WebGLObject}
 */
function WebGLRenderbuffer() {}


/**
 * @constructor
 * @extends {WebGLObject}
 */
function WebGLShader() {}


/**
 * @constructor
 * @extends {WebGLObject}
 */
function WebGLTexture() {}


/**
 * @constructor
 */
function WebGLActiveInfo() {}

/** @type {number} */
WebGLActiveInfo.prototype.size;

/** @type {number} */
WebGLActiveInfo.prototype.type;

/** @type {string} */
WebGLActiveInfo.prototype.name;


/**
 * @param {number} length
 * @constructor
 */
function WebGLArrayBuffer(length) {}

/** @type {number} */
WebGLArrayBuffer.prototype.byteLength;


/**
 * @constructor
 */
function WebGLObjectArray() {}

/** @type {number} */
WebGLObjectArray.prototype.length;

/**
 * @param {number} index
 * @return {WebGLObject}
 */
WebGLObjectArray.prototype.get = function(index) {};


/**
 * @constructor
 */
function WebGLUniformLocation() {}

/**
 * @constructor
 * @extends {WebGLObject}
 */
function WebGLArray() {}

/** @type {WebGLArrayBuffer} */
WebGLArray.prototype.buffer;

/** @type {number} */
WebGLArray.prototype.byteOffset;

/** @type {number} */
WebGLArray.prototype.byteLength;

/** @type {number} */
WebGLArray.prototype.length;

/**
 * @param {number} offset
 * @param {number} length
 * @return {WebGLArray}
 */
WebGLArray.prototype.slice = function(offset, length) {};

/**
 * @param {Array.<number>|WebGLArray|number} dataOrLength
 * @extends {WebGLArray}
 * @constructor
 */
function WebGLFloatArray(dataOrLength) {}


/**
 * @param {Array.<number>|WebGLArray|number} dataOrLength
 * @extends {WebGLArray}
 * @constructor
 */
function WebGLUnsignedByteArray(dataOrLength) {}


/**
 * @param {Array.<number>|WebGLArray|number} dataOrLength
 * @extends {WebGLArray}
 * @constructor
 */
function WebGLByteArray(dataOrLength) {}


/**
 * @param {Array.<number>|WebGLArray|number} dataOrLength
 * @extends {WebGLArray}
 * @constructor
 */
function WebGLUnsignedShortArray(dataOrLength) {}


/**
 * @param {Array.<number>|WebGLArray|number} dataOrLength
 * @extends {WebGLArray}
 * @constructor
 */
function WebGLShortArray(dataOrLength) {}


/**
 * @param {Array.<number>|WebGLArray|number} dataOrLength
 * @extends {WebGLArray}
 * @constructor
 */
function WebGLUnsignedIntArray(dataOrLength) {}


/**
 * @param {Array.<number>|WebGLArray|number} dataOrLength
 * @extends {WebGLArray}
 * @constructor
 */
function WebGLIntArray(dataOrLength) {}


/**
 * @constructor
 */
function ArrayBufferView() {}

/** @type {ArrayBuffer} */
ArrayBufferView.prototype.buffer;

/** @type {number} */
ArrayBufferView.prototype.byteOffset;

/** @type {number} */
ArrayBufferView.prototype.byteLength;


/**
 * @constructor
 * @extends ArrayBufferView
 */
function TypedArray() {}

/** @type {number} */
TypedArray.BYTES_PER_ELEMENT;

/** @type {number} */
TypedArray.prototype.BYTES_PER_ELEMENT;

/** @type {number} */
TypedArray.prototype.length;

/**
 * @param {TypedArray|Array.<number>} array
 * @param {number=} opt_offset
 */
TypedArray.prototype.set = function(array, opt_offset) {};

/**
 * @param {number} begin
 * @param {number=} opt_end
 */
TypedArray.prototype.slice = function(begin, opt_end) {};

/**
 * @param {number} begin
 * @param {number=} opt_end
 * @return {TypedArray}
 */
TypedArray.prototype.subarray = function(begin, opt_end) {};


/**
 * @param {number|TypedArray|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {TypedArray}
 * @constructor
 */
function Int8Array(length, opt_byteOffset, opt_length) {}


/**
 * @param {number|TypedArray|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {TypedArray}
 * @constructor
 */
function Uint8Array(length, opt_byteOffset, opt_length) {}


/**
 * @param {number|TypedArray|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {TypedArray}
 * @constructor
 */
function Int16Array(length, opt_byteOffset, opt_length) {}


/**
 * @param {number|TypedArray|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {TypedArray}
 * @constructor
 */
function Uint16Array(length, opt_byteOffset, opt_length) {}


/**
 * @param {number|TypedArray|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {TypedArray}
 * @constructor
 */
function Int32Array(length, opt_byteOffset, opt_length) {}


/**
 * @param {number|TypedArray|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {TypedArray}
 * @constructor
 */
function Uint32Array(length, opt_byteOffset, opt_length) {}


/**
 * @param {number|TypedArray|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {TypedArray}
 * @constructor
 */
function Float32Array(length, opt_byteOffset, opt_length) {}


/**
 * @param {number|TypedArray|Array.<number>|ArrayBuffer} length or array
 *     or buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_length
 * @extends {TypedArray}
 * @constructor
 */
function Float64Array(length, opt_byteOffset, opt_length) {}


/**
 * @param {ArrayBuffer} buffer
 * @param {number=} opt_byteOffset
 * @param {number=} opt_byteLength
 * @extends {ArrayBufferView}
 * @constructor
 */
function DataView(buffer, opt_byteOffset, opt_byteLength) {}


/**
 * @param {number} byteOffset
 * @return {number}
 */
DataView.prototype.getInt8 = function(byteOffset) {};


/**
 * @param {number} byteOffset
 * @return {number}
 */
DataView.prototype.getUint8 = function(byteOffset) {};


/**
 * @param {number} byteOffset
 * @param {boolean=} opt_littleEndian
 * @return {number}
 */
DataView.prototype.getInt16 = function(byteOffset, opt_littleEndian) {};


/**
 * @param {number} byteOffset
 * @param {boolean=} opt_littleEndian
 * @return {number}
 */
DataView.prototype.getUint16 = function(byteOffset, opt_littleEndian) {};


/**
 * @param {number} byteOffset
 * @param {boolean=} opt_littleEndian
 * @return {number}
 */
DataView.prototype.getInt32 = function(byteOffset, opt_littleEndian) {};


/**
 * @param {number} byteOffset
 * @param {boolean=} opt_littleEndian
 * @return {number}
 */
DataView.prototype.getUint32 = function(byteOffset, opt_littleEndian) {};


/**
 * @param {number} byteOffset
 * @param {boolean=} opt_littleEndian
 * @return {number}
 */
DataView.prototype.getFloat32 = function(byteOffset, opt_littleEndian) {};


/**
 * @param {number} byteOffset
 * @param {boolean=} opt_littleEndian
 * @return {number}
 */
DataView.prototype.getFloat64 = function(byteOffset, opt_littleEndian) {};


/**
 * @param {number} byteOffset
 * @param {number} value
 */
DataView.prototype.setInt8 = function(byteOffset, value) {};


/**
 * @param {number} byteOffset
 * @param {number} value
 */
DataView.prototype.setUint8 = function(byteOffset, value) {};


/**
 * @param {number} byteOffset
 * @param {number} value
 * @param {boolean=} opt_littleEndian
 */
DataView.prototype.setInt16 = function(byteOffset, value, opt_littleEndian) {};


/**
 * @param {number} byteOffset
 * @param {number} value
 * @param {boolean=} opt_littleEndian
 */
DataView.prototype.setUint16 = function(byteOffset, value, opt_littleEndian) {};


/**
 * @param {number} byteOffset
 * @param {number} value
 * @param {boolean=} opt_littleEndian
 */
DataView.prototype.setInt32 = function(byteOffset, value, opt_littleEndian) {};


/**
 * @param {number} byteOffset
 * @param {number} value
 * @param {boolean=} opt_littleEndian
 */
DataView.prototype.setUint32 = function(byteOffset, value, opt_littleEndian) {};


/**
 * @param {number} byteOffset
 * @param {number} value
 * @param {boolean=} opt_littleEndian
 */
DataView.prototype.setFloat32 = function(
    byteOffset, value, opt_littleEndian) {};


/**
 * @param {number} byteOffset
 * @param {number} value
 * @param {boolean=} opt_littleEndian
 */
DataView.prototype.setFloat64 = function(
    byteOffset, value, opt_littleEndian) {};

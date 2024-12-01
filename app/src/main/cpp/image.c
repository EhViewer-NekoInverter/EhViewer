/*
 * Copyright 2022 Tarsin Norbin
 *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * EhViewer. If not, see <https://www.gnu.org/licenses/>.
 */

#include <stdlib.h>
#include <string.h>

#include <android/bitmap.h>
#include <android/data_space.h>
#include <android/log.h>

#include <GLES3/gl3.h>
#include <jni.h>

#define TAG "ImageDecoder_wrapper"

#include "ehviewer.h"

#define IMAGE_TILE_MAX_SIZE (512 * 512)

static char tile_buffer[IMAGE_TILE_MAX_SIZE * 8];

bool copy_pixels(const void *src, int src_w, int src_h, int src_x, int src_y,
                 void *dst, int dst_w, int dst_h, int dst_x, int dst_y,
                 int width, int height, int stride) {
    int left;
    int line;
    size_t line_stride;
    int src_stride;
    int src_pos;
    int dst_pos;
    size_t dst_blank_length;

    // Sanitize
    if (src_x < 0) {
        width -= src_x;
        dst_x -= src_x;
        src_x = 0;
    }
    if (dst_x < 0) {
        width -= dst_x;
        src_x -= dst_x;
        dst_x = 0;
    }
    if (width <= 0) {
        return false;
    }
    if (src_y < 0) {
        height -= src_y;
        dst_y -= src_y;
        src_y = 0;
    }
    if (dst_y < 0) {
        height -= dst_y;
        src_y -= dst_y;
        dst_y = 0;
    }
    if (height <= 0) {
        return false;
    }
    left = src_x + width - src_w;
    if (left > 0) {
        width -= left;
    }
    left = dst_x + width - dst_w;
    if (left > 0) {
        width -= left;
    }
    if (width <= 0) {
        return false;
    }
    left = src_y + height - src_h;
    if (left > 0) {
        height -= left;
    }
    left = dst_y + height - dst_h;
    if (left > 0) {
        height -= left;
    }
    if (height <= 0) {
        return false;
    }

    // Init
    line_stride = (size_t) (width * stride);
    src_stride = src_w * stride;
    src_pos = src_y * src_stride + src_x * stride;
    dst_pos = 0;

    dst_blank_length = (size_t) (dst_y * dst_w + dst_x) * stride;

    // First line
    dst_pos += (int) dst_blank_length;
    memcpy(dst + dst_pos, src + src_pos, line_stride);
    dst_pos += (int) line_stride;
    src_pos += src_stride;

    // Other lines
    dst_blank_length = (size_t) ((dst_w - width) * stride);
    for (line = 1; line < height; line++) {
        dst_pos += (int) dst_blank_length;
        memcpy(dst + dst_pos, src + src_pos, line_stride);
        dst_pos += (int) line_stride;
        src_pos += src_stride;
    }

    return true;
}

JNIEXPORT void JNICALL
Java_com_hippo_ehviewer_jni_ImageKt_nativeTexImage(JNIEnv *env, jclass clazz, jobject bitmap, jboolean init,
                                          jint offset_x, jint offset_y, jint width, jint height) {
    if (width * height > IMAGE_TILE_MAX_SIZE)
        return;
    AndroidBitmapInfo info;
    void *pixels = NULL;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    AndroidBitmap_getInfo(env, bitmap, &info);
    bool is_f16 = info.format == ANDROID_BITMAP_FORMAT_RGBA_F16;
    copy_pixels(pixels, info.width, info.height, offset_x, offset_y, tile_buffer, width, height, 0, 0, width, height, is_f16 ? 8 : 4);
    AndroidBitmap_unlockPixels(env, bitmap);
    if (init) {
        glTexImage2D(GL_TEXTURE_2D, 0, is_f16 ? GL_RGBA16F : GL_RGBA8, width, height, 0, GL_RGBA,
                     is_f16 ? GL_HALF_FLOAT : GL_UNSIGNED_BYTE, tile_buffer);
    } else {
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA,
                        is_f16 ? GL_HALF_FLOAT : GL_UNSIGNED_BYTE, tile_buffer);
    }
}

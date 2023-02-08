/*
 * Copyright 2023 Tarsin Norbin
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

#include <string.h>
#include <jni.h>

#define GIF_HEADER_87A "GIF87a"
#define GIF_HEADER_89A "GIF89a"

JNIEXPORT jboolean JNICALL
Java_com_hippo_Native_isGif(JNIEnv *env, jobject thiz, jobject buffer) {
    void *addr = (*env)->GetDirectBufferAddress(env, buffer);
    return !memcmp(addr, GIF_HEADER_87A, 6) || !memcmp(addr, GIF_HEADER_89A, 6);
}

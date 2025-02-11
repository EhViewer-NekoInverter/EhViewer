/*
 * Copyright 2022-2024 Tarsin Norbin
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

#include <unistd.h>

#include <jni.h>
#include <nettle/sha1.h>

#include "ehviewer.h"

#define BUFFER_SIZE 8192

typedef uint8_t byte;

const char hex_digits[] = "0123456789abcdef";

JNIEXPORT jstring JNICALL
Java_com_hippo_ehviewer_jni_HashKt_sha1(JNIEnv *env, jclass clazz, jint fd) {
    EH_UNUSED(clazz);
    struct sha1_ctx ctx;
    sha1_init(&ctx);

    size_t bytes_read;
    byte buffer[BUFFER_SIZE];
    while ((bytes_read = read(fd, buffer, BUFFER_SIZE)) > 0) {
        sha1_update(&ctx, bytes_read, buffer);
    }

    byte digest[SHA1_DIGEST_SIZE];
    sha1_digest(&ctx, SHA1_DIGEST_SIZE, digest);

    byte byte;
    char hex_digest[2 * SHA1_DIGEST_SIZE + 1];
    for (int i = 0; i < SHA1_DIGEST_SIZE; i++) {
        byte = digest[i];
        hex_digest[2 * i] = hex_digits[byte >> 4 & 0xF];
        hex_digest[2 * i + 1] = hex_digits[byte & 0xF];
    }
    hex_digest[2 * SHA1_DIGEST_SIZE] = '\0';

    return (*env)->NewStringUTF(env, hex_digest);
}

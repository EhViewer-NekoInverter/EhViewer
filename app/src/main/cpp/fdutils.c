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

#include <jni.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/sendfile.h>

#include <android/log.h>
#include <string.h>
#include <errno.h>

#define LOG_TAG "mmap_utils"

#include "ehviewer.h"

JNIEXPORT jint JNICALL
Java_com_hippo_Native_getFd(JNIEnv *env, jclass clazz, jobject fileDescriptor) {
    jint fd = -1;
    jclass fdClass = (*env)->FindClass(env, "java/io/FileDescriptor");

    if (fdClass != NULL) {
        jfieldID fdClassDescriptorFieldID = (*env)->GetFieldID(env, fdClass, "descriptor", "I");
        if (fdClassDescriptorFieldID != NULL && fileDescriptor != NULL) {
            fd = (*env)->GetIntField(env, fileDescriptor, fdClassDescriptorFieldID);
        }
    }

    return fd;
}

JNIEXPORT jobject JNICALL
Java_com_hippo_Native_mapFd(JNIEnv *env, jclass clazz, jint fd, jlong capability) {
    void *addr = mmap(0, capability, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, 0);
    if (addr != MAP_FAILED) {
        return (*env)->NewDirectByteBuffer(env, addr, capability);
    } else {
        LOGE("%s%s", "mmap failed with error ", strerror(errno));
        return NULL;
    }
}

JNIEXPORT void JNICALL
Java_com_hippo_Native_unmapDirectByteBuffer(JNIEnv *env, jclass clazz, jobject buffer) {
    void *addr = (*env)->GetDirectBufferAddress(env, buffer);
    size_t size = (*env)->GetDirectBufferCapacity(env, buffer);
    if (munmap(addr, size)) LOGE("munmap addr:%p, size%zu failed!", addr, size);
}

JNIEXPORT void JNICALL
Java_com_hippo_Native_sendfile(JNIEnv *env, jclass clazz, jint from, jint to) {
    struct stat st;
    fstat(from, &st);
    sendfile(to, from, 0, st.st_size);
}

/*
 * Copyright 2016 Hippo Seven
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
package com.hippo.unifile

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor

internal class SingleDocumentFile(parent: UniFile?, context: Context, override val uri: Uri) : UniFile(parent) {
    private val mContext = context.applicationContext

    override fun createFile(displayName: String): UniFile? = null

    override fun createDirectory(displayName: String): UniFile? = null

    override val name: String?
        get() = DocumentsContractApi19.getName(mContext, uri)
    override val type: String?
        get() = DocumentsContractApi19.getType(mContext, uri)
    override val isDirectory: Boolean
        get() = DocumentsContractApi19.isDirectory(mContext, uri)
    override val isFile: Boolean
        get() = DocumentsContractApi19.isFile(mContext, uri)

    override fun lastModified(): Long = DocumentsContractApi19.lastModified(mContext, uri)

    override fun length(): Long = DocumentsContractApi19.length(mContext, uri)

    override fun canRead(): Boolean = DocumentsContractApi19.canRead(mContext, uri)

    override fun canWrite(): Boolean = DocumentsContractApi19.canWrite(mContext, uri)

    override fun ensureDir(): Boolean = isDirectory

    override fun ensureFile(): Boolean = isFile

    override fun subFile(displayName: String): UniFile? = null

    override fun delete(): Boolean = DocumentsContractApi19.delete(mContext, uri)

    override fun exists(): Boolean = DocumentsContractApi19.exists(mContext, uri)

    override fun listFiles(): Array<UniFile>? = null

    override fun listFiles(filter: FilenameFilter?): Array<UniFile>? = null

    override fun findFile(displayName: String): UniFile? = null

    override fun renameTo(displayName: String): Boolean = false

    override fun openFileDescriptor(mode: String): ParcelFileDescriptor = Contracts.openFileDescriptor(mContext, uri, mode)
}

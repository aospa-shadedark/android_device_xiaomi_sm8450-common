/*
 * SPDX-FileCopyrightText: 2023-2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.xiaomiparts.display

import android.os.HwBinder
import android.os.HwParcel
import android.os.IHwBinder
import android.os.RemoteException

object DisplayFeatureHidl {
    private const val DESCRIPTOR =
        "vendor.xiaomi.hardware.displayfeature@1.0::IDisplayFeature"
    private const val INSTANCE = "default"
    private const val TRANSACTION_SET_FEATURE = 1

    @Volatile
    private var service: IHwBinder? = null

    private val deathRecipient = IHwBinder.DeathRecipient {
        service = null
    }

    @Synchronized
    private fun getService(): IHwBinder {
        service?.let { return it }

        val binder = HwBinder.getService(DESCRIPTOR, INSTANCE)
            ?: throw IllegalStateException(
                "Unable to find $DESCRIPTOR/$INSTANCE"
            )

        binder.linkToDeath(deathRecipient, 0)
        service = binder
        return binder
    }

    @Throws(RemoteException::class)
    fun setFeature(
        displayId: Int,
        caseId: Int,
        modeId: Int,
        cookie: Int,
    ): Int {
        val request = HwParcel()
        val reply = HwParcel()

        try {
            request.writeInterfaceToken(DESCRIPTOR)
            request.writeInt32(displayId)
            request.writeInt32(caseId)
            request.writeInt32(modeId)
            request.writeInt32(cookie)

            getService().transact(
                TRANSACTION_SET_FEATURE,
                request,
                reply,
                0,
            )

            reply.verifySuccess()
            return reply.readInt32()
        } finally {
            request.releaseTemporaryStorage()
            reply.release()
        }
    }
}


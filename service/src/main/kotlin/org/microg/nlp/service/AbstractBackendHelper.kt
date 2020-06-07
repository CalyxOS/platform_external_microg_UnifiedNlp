/*
 * SPDX-FileCopyrightText: 2014, microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.nlp.service

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.IBinder
import android.os.RemoteException
import android.util.Log

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

fun <T> Array<out T>?.isNotNullOrEmpty(): Boolean {
    return this != null && this.isNotEmpty()
}

abstract class AbstractBackendHelper(private val TAG: String, private val context: Context, val serviceIntent: Intent, val signatureDigest: String?) : ServiceConnection {
    private var bound: Boolean = false

    protected abstract fun close()

    protected abstract fun hasBackend(): Boolean

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        bound = true
        Log.d(TAG, "Bound to: $name")
    }

    override fun onServiceDisconnected(name: ComponentName) {
        bound = false
        Log.d(TAG, "Unbound from: $name")
    }

    fun unbind() {
        if (bound) {
            if (hasBackend()) {
                try {
                    close()
                } catch (e: Exception) {
                    Log.w(TAG, e)
                }

            }
            try {
                Log.d(TAG, "Unbinding from: $serviceIntent")
                context.unbindService(this)
            } catch (e: Exception) {
                Log.w(TAG, e)
            }

            bound = false
        }
    }

    fun bind() {
        if (!bound) {
            Log.d(TAG, "Binding to: $serviceIntent sig: $signatureDigest")
            if (signatureDigest == null) {
                Log.w(TAG, "No signature digest provided. Aborting.")
                return
            }
            if (serviceIntent.getPackage() == null) {
                Log.w(TAG, "Intent is not properly resolved, can't verify signature. Aborting.")
                return
            }
            if (signatureDigest != firstSignatureDigest(context, serviceIntent.getPackage())) {
                Log.w(TAG, "Target signature does not match selected package (" + signatureDigest + " = " + firstSignatureDigest(context, serviceIntent.getPackage()) + "). Aborting.")
                return
            }
            try {
                context.bindService(serviceIntent, this, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.w(TAG, e)
            }

        }
    }

    companion object {
        @Suppress("DEPRECATION")
        @SuppressLint("PackageManagerGetSignatures")
        fun firstSignatureDigest(context: Context, packageName: String?): String? {
            val packageManager = context.packageManager
            val info: PackageInfo?
            try {
                info = packageManager.getPackageInfo(packageName!!, PackageManager.GET_SIGNATURES)
            } catch (e: PackageManager.NameNotFoundException) {
                return null
            }

            if (info?.signatures.isNotNullOrEmpty()) {
                for (sig in info.signatures) {
                    sha256sum(sig.toByteArray())?.let { return it }
                }
            }
            return null
        }

        private fun sha256sum(bytes: ByteArray): String? {
            try {
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(bytes)
                val sb = StringBuilder(2 * digest.size)
                for (b in digest) {
                    sb.append(String.format("%02x", b))
                }
                return sb.toString()
            } catch (e: NoSuchAlgorithmException) {
                return null
            }
        }
    }

}
// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb.client

import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.session.Session
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import com.wisso.wizefiles.util.hasBits
import java.io.IOException

internal object SmbDiscoveryEngine {
    fun shareNames(session: Session): List<String> {
        val transport = try {
            SMBTransportFactories.SRVSVC.getTransport(session)
        } catch (failure: IOException) {
            throw SmbClientException(failure)
        } catch (failure: SMBRuntimeException) {
            throw SmbClientException(failure)
        }
        return try {
            ServerService(transport).shares1.mapNotNull { share ->
                share.netName.takeUnless {
                    share.type.hasBits(ShareTypes.STYPE_PRINTQ) ||
                        share.type.hasBits(ShareTypes.STYPE_DEVICE) ||
                        share.type.hasBits(ShareTypes.STYPE_IPC)
                }
            }
        } catch (failure: IOException) {
            throw SmbClientException(failure)
        } catch (failure: SMBRuntimeException) {
            throw SmbClientException(failure)
        }
    }
}

package org.starcoin.sirius.crypto

import org.starcoin.sirius.core.Hash
import org.starcoin.sirius.core.Signature
import org.starcoin.sirius.core.SiriusObject
import org.starcoin.sirius.crypto.fallback.FallbackCryptoService
import java.security.PublicKey
import java.util.*

interface CryptoService {

    fun getDummyCryptoKey(): CryptoKey

    fun generateCryptoKey(): CryptoKey

    fun loadCryptoKey(bytes: ByteArray): CryptoKey

    fun verify(data: ByteArray, sign: Signature, publicKey: PublicKey): Boolean

    fun hash(bytes: ByteArray): Hash

    fun <T : SiriusObject> hash(obj: T): Hash

    companion object {
        val INSTANCE: CryptoService by lazy {
            val loaders = ServiceLoader
                .load(CryptoServiceProvider::class.java).iterator()
            if (loaders.hasNext()) {
                loaders.next().createService()
            } else {
                //if can not find
                FallbackCryptoService
            }

        }
    }
}

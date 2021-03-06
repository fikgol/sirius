package org.starcoin.sirius.core

import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable
import org.starcoin.proto.Starcoin
import org.starcoin.sirius.serialization.ProtobufSchema

@ProtobufSchema(Starcoin.BalanceUpdateProof::class)
@Serializable
data class BalanceUpdateProof(
    @SerialId(1)
    val hasUpdate: Boolean = false,
    @SerialId(2)
    val update: Update = Update.DUMMY_UPDATE,
    @SerialId(3)
    val hasPath: Boolean = false,
    @SerialId(4)
    val path: AMTreePath = AMTreePath.DUMMY_PATH
) : SiriusObject() {

    //do not use proof's update, proof is e-1, proof's update is e-1-1, need update is e-1
    constructor(proof: AMTreeProof) : this(proof.path)

    constructor(update: Update) : this(hasUpdate = true, update = update)
    constructor(path: AMTreePath) : this(hasPath = true, path = path)
    constructor(update: Update, path: AMTreePath) : this(true, update, true, path)

    companion object :
        SiriusObjectCompanion<BalanceUpdateProof, Starcoin.BalanceUpdateProof>(BalanceUpdateProof::class) {

        var DUMMY_PROOF = BalanceUpdateProof(false, Update.DUMMY_UPDATE, false, AMTreePath.DUMMY_PATH)

        override fun mock(): BalanceUpdateProof {
            return BalanceUpdateProof(true, Update.mock(), true, AMTreePath.mock())
        }
    }
}

@Serializable
@ProtobufSchema(Starcoin.CloseBalanceUpdateChallenge::class)
data class CloseBalanceUpdateChallenge(
    @SerialId(1)
    val address: Address = Address.DUMMY_ADDRESS,
    @SerialId(2)
    val proof: AMTreeProof = AMTreeProof.DUMMY_PROOF
) : SiriusObject() {

    companion object :
        SiriusObjectCompanion<CloseBalanceUpdateChallenge, Starcoin.CloseBalanceUpdateChallenge>(
            CloseBalanceUpdateChallenge::class
        ) {

        var DUMMY_CHALLENGE =
            CloseBalanceUpdateChallenge(Address.DUMMY_ADDRESS, AMTreeProof.DUMMY_PROOF)

        override fun mock(): CloseBalanceUpdateChallenge {
            return CloseBalanceUpdateChallenge(Address.random(), AMTreeProof.mock())
        }
    }
}

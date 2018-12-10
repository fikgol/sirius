package org.starcoin.sirius.core

import com.google.protobuf.ByteString
import com.google.protobuf.GeneratedMessageV3
import io.grpc.MethodDescriptor.Marshaller
import org.apache.commons.lang3.RandomUtils
import org.starcoin.proto.Starcoin
import org.starcoin.proto.Starcoin.ProtoChainTransaction
import org.starcoin.sirius.util.KeyPairUtil

import java.io.ByteArrayInputStream
import java.security.KeyPair
import java.security.PublicKey
import java.util.Arrays
import java.util.Objects

class ChainTransaction : ProtobufCodec<Starcoin.ProtoChainTransaction>, Hashable, Mockable {
    var from: BlockAddress? = null
    var to: BlockAddress? = null
    var timestamp: Long = 0
    var amount: Long = 0

    // contract action and arguments
    var action: String? = null
    var arguments: ByteArray? = null
    var receipt: Receipt? = null

    private var publicKey: PublicKey? = null

    private var sign: Signature? = null

    @Transient
    private var hash: Hash? = null

    val isSuccess: Boolean
        get() = this.receipt == null || this.receipt!!.isSuccess

    constructor() {}

    constructor(from: BlockAddress, to: BlockAddress, timestamp: Long, amount: Long) {
        this.from = from
        this.to = to
        this.timestamp = timestamp
        this.amount = amount
    }

    constructor(from: BlockAddress, to: BlockAddress, amount: Long) {
        this.from = from
        this.to = to
        this.timestamp = System.currentTimeMillis()
        this.amount = amount
    }

    constructor(
        from: BlockAddress,
        to: BlockAddress,
        timestamp: Long,
        amount: Long,
        action: String,
        arguments: GeneratedMessageV3
    ) : this(from, to, timestamp, amount, action, arguments.toByteString().toByteArray()) {
    }

    constructor(
        from: BlockAddress, to: BlockAddress, action: String, arguments: GeneratedMessageV3
    ) {
        this.from = from
        this.to = to
        this.timestamp = System.currentTimeMillis()
        this.amount = 0
        this.action = action
        this.arguments = arguments.toByteArray()
    }

    constructor(
        from: BlockAddress,
        to: BlockAddress,
        timestamp: Long,
        amount: Long,
        action: String,
        arguments: ByteArray
    ) {
        this.from = from
        this.to = to
        this.timestamp = timestamp
        this.amount = amount
        this.action = action
        this.arguments = arguments
    }

    constructor(protoChainTransaction: ProtoChainTransaction) {
        this.unmarshalProto(protoChainTransaction)
    }

    fun <T : GeneratedMessageV3> getArguments(clazz: Class<T>): T? {
        if (arguments == null) {
            return null
        }
        try {
            return clazz.getMethod("parseFrom", ByteArray::class.java).invoke(null, this.arguments) as T
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    }

    fun <T> getArguments(marshaller: Marshaller<T>): T? {
        return if (arguments == null) {
            null
        } else marshaller.parse(ByteArrayInputStream(this.arguments!!))
    }

    override fun marshalProto(): Starcoin.ProtoChainTransaction {
        val builder = this.marshalSignData()
        if (this.sign != null) {
            builder.sign = this.sign!!.toProto()
        }
        if (this.receipt != null) {
            builder.receipt = this.receipt!!.toProto()
        }
        if (this.publicKey != null) {
            builder.publicKey = ByteString.copyFrom(KeyPairUtil.encodePublicKey(this.publicKey!!))
        }
        return builder.build()
    }

    fun marshalSignData(): Starcoin.ProtoChainTransaction.Builder {
        val builder = Starcoin.ProtoChainTransaction.newBuilder()
            .setFrom(this.from!!.toProto())
            .setTo(this.to!!.toProto())
            .setTimestamp(this.timestamp)
            .setAmount(this.amount)
        if (this.action != null) {
            builder.action = this.action
        }
        if (this.arguments != null) {
            builder.arguments = ByteString.copyFrom(this.arguments!!)
        }
        return builder
    }

    override fun unmarshalProto(proto: Starcoin.ProtoChainTransaction) {
        this.amount = proto.amount
        this.timestamp = proto.timestamp
        this.from = BlockAddress.valueOf(proto.from)
        this.to = BlockAddress.valueOf(proto.to)
        // protobuf string default value is empty string.
        this.action = if (proto.action.isEmpty()) null else proto.action
        // protobuf bytestring default value is empty bytes.
        this.arguments = if (proto.arguments.isEmpty) null else proto.arguments.toByteArray()
        this.receipt = if (proto.hasReceipt()) Receipt(proto.receipt) else null
        this.sign = if (proto.hasSign()) Signature.wrap(proto.sign) else null
        this.publicKey = if (proto.publicKey.isEmpty)
            null
        else
            KeyPairUtil.recoverPublicKey(proto.publicKey.toByteArray())
    }

    override fun hash(): Hash {
        //TODO !!
        if (this.hash != null) {
            return this.hash!!
        }
        this.hash = Hash.of(this.marshalSignData().build().toByteArray())
        return this.hash!!
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is ChainTransaction) {
            return false
        }
        val that = o as ChainTransaction?
        return (timestamp == that!!.timestamp
                && amount == that.amount
                && from == that.from
                && to == that.to
                && action == that.action
                && Arrays.equals(arguments, that.arguments)
                && receipt == that.receipt)
    }

    override fun hashCode(): Int {
        return Objects.hash(from, to, timestamp, amount, action, arguments, receipt)
    }

    override fun mock(context: MockContext) {
        val keyPair = context.getOrDefault("keyPair", KeyPairUtil.generateKeyPair())
        this.from = BlockAddress.genBlockAddressFromPublicKey(keyPair.public)
        this.to = BlockAddress.random()
        this.amount = RandomUtils.nextLong()
        this.timestamp = System.currentTimeMillis()
        this.publicKey = keyPair.public
    }

    fun sign(keyPair: KeyPair) {
        this.publicKey = keyPair.public
        this.sign = Signature.of(keyPair.private, this.marshalSignData().build().toByteArray())
    }

    fun verify(): Boolean {
        if (this.amount < 0) {
            return false
        }
        if (this.sign == null) {
            return false
        }
        if (this.publicKey == null) {
            return false
        }
        if (this.from == Constants.CONTRACT_ADDRESS) {
            return true
        }
        return if (this.from != BlockAddress.genBlockAddressFromPublicKey(this.publicKey!!)) {
            false
        } else this.sign!!.verify(this.publicKey!!, this.marshalSignData().build().toByteArray())
    }

    fun setPublicKey(publicKey: PublicKey) {
        this.publicKey = publicKey
    }

    fun setSign(sign: Signature) {
        this.sign = sign
    }

    fun setHash(hash: Hash) {
        this.hash = hash
    }

    companion object {

        fun generateChainTransaction(proto: Starcoin.ProtoChainTransaction): ChainTransaction {
            val transaction = ChainTransaction()
            transaction.unmarshalProto(proto)
            return transaction
        }
    }
}
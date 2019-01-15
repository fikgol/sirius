package org.starcoin.sirius.chain.eth

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.serializer
import org.ethereum.core.CallTransaction
import org.ethereum.solidity.SolidityType
import org.starcoin.sirius.chain.ChainStrategy
import org.starcoin.sirius.chain.ChainStrategyProvider
import org.starcoin.sirius.core.Address
import org.starcoin.sirius.core.ChainTransaction
import org.starcoin.sirius.core.SiriusObject
import org.starcoin.sirius.protocol.ChainAccount
import org.starcoin.sirius.protocol.ContractFunction
import org.starcoin.sirius.protocol.EthereumTransaction
import org.starcoin.sirius.protocol.FunctionSignature
import org.starcoin.sirius.protocol.ethereum.EthereumAccount
import org.starcoin.sirius.serialization.rlp.RLP
import java.math.BigInteger
import kotlin.reflect.KClass

class EthFunctionSignature(
    val function: ContractFunction<*>,
    val ethFunction: CallTransaction.Function = CallTransaction.Function.fromSignature(
        function.name,
        arrayOf("bytes"),
        arrayOf("bool")
    )
) : FunctionSignature(ethFunction.encodeSignature())

object EthChainStrategy : ChainStrategy {

    val bytesType = SolidityType.getType("bytes")

    override fun <S : SiriusObject> encode(obj: S): ByteArray {
        return obj.toRLP()
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    override fun <S : SiriusObject> decode(bytes: ByteArray, clazz: KClass<S>): S {
        return RLP.load(clazz.serializer(), bytes)
    }

    override fun signature(function: ContractFunction<*>): FunctionSignature {
        return EthFunctionSignature(function)
    }

    override fun <S : SiriusObject> encode(function: ContractFunction<S>, input: S): ByteArray {
        return (function.signature as EthFunctionSignature).ethFunction.encode(this.encode(input))
    }

    @UseExperimental(ImplicitReflectionSerializer::class)
    override fun <S : SiriusObject> decode(function: ContractFunction<S>, bytes: ByteArray): S {
        assert(bytes.size >= 4)
        assert(bytes.copyOfRange(0, 4).contentEquals((function.signature as EthFunctionSignature).value))
        val result = (function.signature as EthFunctionSignature).ethFunction.decode(bytes)[0] as ByteArray
        return this.decode(result, function.inputClass)
    }

    override fun <A:ChainAccount> newTransaction(account: A,value: BigInteger,to: Address) : ChainTransaction {
        var ethAccount = account as EthereumAccount
        var ethereumTransaction = EthereumTransaction(
            to, ethAccount.getAndIncNonce(), 21000.toBigInteger(),
            210000.toBigInteger(), value
        )
        return ethereumTransaction
    }

}

class EthChainStrategyProvider : ChainStrategyProvider {
    override fun createChainStrategy(): ChainStrategy {
        return EthChainStrategy
    }
}

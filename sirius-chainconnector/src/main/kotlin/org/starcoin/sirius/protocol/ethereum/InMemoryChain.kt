package org.starcoin.sirius.protocol.ethereum

import org.ethereum.util.blockchain.StandaloneBlockchain
import org.starcoin.sirius.core.Address
import org.starcoin.sirius.core.BlockInfo
import org.starcoin.sirius.core.Hash
import org.starcoin.sirius.protocol.*
import org.starcoin.sirius.util.KeyPairUtil
import java.math.BigInteger
import java.security.KeyPair

class InMemoryChain(autoGenblock :Boolean):Chain<EthereumTransaction,BlockInfo,HubContract> {

    private val autoGenblock = autoGenblock
    val sb = StandaloneBlockchain().withAutoblock(autoGenblock)
    private val inMemoryEthereumListener = InMemoryEthereumListener()

    override fun getBlock(height: BigInteger): BlockInfo? {
        return inMemoryEthereumListener.blocks.get(height.toInt())
    }

    override fun watchBlock(onNext: (block: BlockInfo) -> Unit) {
        sb.addEthereumListener(inMemoryEthereumListener)
        if(autoGenblock){
            sb.withAutoblock(autoGenblock)
        }
    }

    override fun watchTransactions(onNext: (tx: EthereumTransaction) -> Unit) {

    }

    override fun getBalance(address: Address): BigInteger {
        return sb.getBlockchain().getRepository().getBalance(address.toBytes())
    }

    override fun findTransaction(hash: Hash): EthereumTransaction? {
        return inMemoryEthereumListener.findTransaction(hash)
    }

    override fun newTransaction(keyPair:KeyPair,transaction: EthereumTransaction) {
        transaction.ethTransaction.sign(KeyPairUtil.getECKey(keyPair))
        sb.submitTransaction(transaction.ethTransaction)
    }

    override fun watchTransaction(txHash: Hash, listener: TransactionProgressListener) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getContract(parameter: QueryContractParameter): HubContract {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

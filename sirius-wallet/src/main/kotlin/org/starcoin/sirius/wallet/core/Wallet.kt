package org.starcoin.sirius.wallet.core

import com.google.protobuf.Empty
import kotlinx.coroutines.channels.Channel
import org.starcoin.proto.HubServiceGrpc
import org.starcoin.sirius.core.*
import org.starcoin.sirius.protocol.Chain
import org.starcoin.sirius.protocol.ChainAccount
import org.starcoin.sirius.wallet.core.blockchain.BlockChain
import java.math.BigInteger
import kotlin.properties.Delegates

class Wallet<T : ChainTransaction, A : ChainAccount> {

    internal var hub: Hub<T,A> by Delegates.notNull()
        private set

    internal var blockChain: BlockChain<T,A> by Delegates.notNull()

    private var account: ClientAccount<A> by Delegates.notNull()

    //TODO
    private var chain: Chain<T, out Block<T>, A> by Delegates.notNull()

    constructor(chain: Chain<T, out Block<T>, A>, account: ClientAccount<A>,serverEventHandler: ServerEventHandler?) {
        this.chain = chain
        this.account = account

        val hubServiceBlockingStub=HubServiceGrpc.newBlockingStub(ResourceManager.hubChannel)
        val hubInfo:HubInfo=hubServiceBlockingStub.getHubInfo(Empty.getDefaultInstance()).toSiriusObject()

        val contract=chain.loadContract(hubInfo.contractAddress)

        val hub = Hub(contract,account,serverEventHandler,chain)
        this.hub = hub

        blockChain = BlockChain(chain,hub,contract,account)


        //val eonNumber=(currentChainHeight-localHeight.toLong())/hub.hubInfo.blocksPerEon
        if(hub.hasRegister()){
            startWatch(true)
        }
    }

    @SuppressWarnings
    private fun startWatch(needSync:Boolean){
        val currentChainHeight=chain.getBlockNumber()
        //val localHeight = blockChain.getLocalHeight()

        /**
        var startBlockHeight = hub.hubInfo.startBlockNumber

        if(localHeight>hub.hubInfo.startBlockNumber)
            startBlockHeight=localHeight*/

        if(needSync){
            sync()
        }
        val startBlockHeight=BigInteger.valueOf(currentChainHeight)
        //hub.hubInfo.startBlockNumber+BigInteger.valueOf(hub.hubInfo.latestEon*hub.hubInfo.blocksPerEon.toLong()+1)
        blockChain.startWatch=true
        blockChain.watachBlock(startBlockHeight)

        hub.recieveTransacion()
        hub.recieveHubSign()
    }

    fun deposit(value:BigInteger) = hub.deposit(value)

    fun balance():BigInteger = hub.getAvailableCoin()

    fun withdrawal(value:BigInteger)= hub.withDrawal(value)

    fun register():Update?{
        val update=hub.register()
        if(update!=null){
            startWatch(false)
        }
        return update
    }

    fun openTransferChallenge(hash:Hash)= this.hub.openTransferChallenge(hash)

    internal fun initMessageChannel(){
        hub.eonChannel = Channel(200)
    }

    internal fun getMessageChannel():Channel<ClientEventType>?{
        return hub.eonChannel
    }

    fun hubTransfer(to:Address,value:BigInteger)=hub.newTransfer(to,value)

    internal fun hubAccount():HubAccount?=hub.accountInfo()

    internal fun cheat(flag:Int)= hub.cheat(flag)

    fun sync() = hub.sync()

    fun restore() = hub.restore()

    fun chainTransfer(to:Address,value:BigInteger)=hub.chainTransaction(to,value)

    fun recieveTransacion() = hub.recieveTransacion()

    fun recieveHubSign() = hub.recieveHubSign()

    fun hubInfo() = hub.hubInfo()

    fun isRecoveryMode():Boolean {
        return hub.isRecoveryMode()
    }

    suspend fun close() {
        blockChain.close()
        hub.eonChannel?.close()
    }

}

package org.starcoin.sirius.wallet

import com.google.protobuf.Empty
import io.grpc.Channel
import io.grpc.inprocess.InProcessChannelBuilder
import kotlinx.coroutines.runBlocking
import org.ethereum.util.blockchain.EtherUtil
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.starcoin.proto.HubServiceGrpc
import org.starcoin.proto.Starcoin
import org.starcoin.sirius.core.Address
import org.starcoin.sirius.core.ContractHubInfo
import org.starcoin.sirius.core.Eon
import org.starcoin.sirius.core.HubAccount
import org.starcoin.sirius.crypto.CryptoService
import org.starcoin.sirius.hub.Configuration
import org.starcoin.sirius.hub.HubServer
import org.starcoin.sirius.protocol.EthereumTransaction
import org.starcoin.sirius.protocol.HubContract
import org.starcoin.sirius.protocol.ethereum.EthereumAccount
import org.starcoin.sirius.protocol.ethereum.InMemoryChain
import org.starcoin.sirius.wallet.core.ChannelManager
import org.starcoin.sirius.wallet.core.Wallet
import java.math.BigInteger
import java.util.logging.Logger
import kotlin.properties.Delegates

class WalletTest {

    private val logger = Logger.getLogger("test")

    private var chain: InMemoryChain by Delegates.notNull()
    private var contract: HubContract<EthereumAccount> by Delegates.notNull()

    private var owner: EthereumAccount by Delegates.notNull()
    private var alice: EthereumAccount by Delegates.notNull()
    private var bob: EthereumAccount by Delegates.notNull()

    private var walletAlice : Wallet<EthereumTransaction,EthereumAccount> by Delegates.notNull()
    private var walletBob : Wallet<EthereumTransaction,EthereumAccount> by Delegates.notNull()

    private var hubServer: HubServer<EthereumTransaction,EthereumAccount> by Delegates.notNull()

    private val configuration = Configuration.configurationForUNIT()

    private var hubChannel : Channel by Delegates.notNull()

    private var stub : HubServiceGrpc.HubServiceBlockingStub by Delegates.notNull()

    private var hubInfo:ContractHubInfo by Delegates.notNull()


    @Before
    @Throws(InterruptedException::class)
    fun before() {
        chain = InMemoryChain(true)

        val owner = EthereumAccount(configuration.ownerKey)
        chain.miningCoin(owner, EtherUtil.convert(Int.MAX_VALUE.toLong(), EtherUtil.Unit.ETHER))
        alice = EthereumAccount(CryptoService.generateCryptoKey())
        bob = EthereumAccount(CryptoService.generateCryptoKey())

        val amount = EtherUtil.convert(100000, EtherUtil.Unit.ETHER)
        this.sendEther(alice.address, amount)
        this.sendEther(bob.address, amount)

        hubChannel = InProcessChannelBuilder.forName(configuration.rpcBind.toString()).build()
        stub = HubServiceGrpc.newBlockingStub(hubChannel)

        val channelManager = ChannelManager(hubChannel)

        hubServer = HubServer(configuration,chain,owner)
        hubServer.start()
        contract = hubServer.contract

        walletAlice= Wallet(this.contract.contractAddress,channelManager,chain,null,alice)
        walletAlice.initMessageChannel()

        walletBob= Wallet(this.contract.contractAddress,channelManager,chain,null,bob)
        walletBob.initMessageChannel()

        hubInfo= contract.queryHubInfo(alice)
    }

    fun sendEther(address: Address, amount: BigInteger) {
        chain.sb.sendEther(address.toBytes(), amount)
        chain.sb.createBlock()
        Assert.assertEquals(amount, chain.getBalance(address))
    }

    fun waitHubReady(stub: HubServiceGrpc.HubServiceBlockingStub) {
        var hubInfo = stub.getHubInfo(Empty.newBuilder().build())
        while (!hubInfo.ready) {
            logger.info("waiting hub ready:")
            Thread.sleep(100)
            hubInfo = stub.getHubInfo(Empty.newBuilder().build())
        }
    }

    @After
    fun after() {
        hubServer.stop()
    }

    @Test
    fun testDeposit(){
        testReg()

        val amount = 2000L
        deposit(amount)

        var account=stub.getHubAccount(alice.address.toProto())

        Assert.assertEquals(HubAccount.parseFromProtoMessage(account).deposit.toLong(),amount)

        account=stub.getHubAccount(bob.address.toProto())
        Assert.assertEquals(HubAccount.parseFromProtoMessage(account).deposit.toLong(),amount)

    }

    fun deposit(amount : Long){
        walletAlice.deposit(amount)
        walletBob.deposit(amount)

        chain.sb.createBlock()

        Assert.assertEquals(amount*2, chain.getBalance(contract.contractAddress).toLong())
        Assert.assertEquals(walletAlice.balance().toLong(),amount)
        Assert.assertEquals(walletBob.balance().toLong(),amount)

    }

    @Test
    fun testTransfer(){
        testDeposit()

        val amount=20L
        val transaction=walletAlice.hubTransfer(bob.address,amount)

        Assert.assertNotNull(transaction)

        runBlocking {
            walletBob.getMessageChannel()?.receive()
            walletAlice.getMessageChannel()?.receive()
            walletBob.getMessageChannel()?.receive()
        }

        var account=walletBob.hubAccount()

        Assert.assertEquals(account?.address,bob.address)
        Assert.assertEquals(account?.deposit?.toLong(),2000L)
        Assert.assertEquals(account?.update?.receiveAmount?.toLong(),amount)
        Assert.assertEquals(account?.update?.sendAmount?.toLong(),0L)

        account=walletAlice.hubAccount()
        Assert.assertEquals(account?.address,alice.address)
        Assert.assertEquals(account?.deposit?.toLong(),2000L)
        //Assert.assertEquals(account?.update?.receiveAmount?.toLong(),0L)
        //Assert.assertEquals(account?.update?.sendAmount?.toLong(),amount)

    }

    @Test
    fun testBalanceChallenge() {
        testReg()

        walletAlice.cheat(Starcoin.HubMaliciousFlag.STEAL_DEPOSIT_VALUE)

        val amount = 2000L
        deposit(amount)

        var account=walletAlice.hubAccount()
        Assert.assertEquals(account?.address,alice.address)
        Assert.assertTrue(account?.deposit?.toLong()?:0<amount)

        waitToNextEon()

        runBlocking {
            walletAlice.getMessageChannel()?.receive()
            walletAlice.getMessageChannel()?.receive()
        }


    }

    @Test
    fun testReg(){
        waitHubReady(stub)

        var update=walletAlice.register()
        Assert.assertNotNull(update)

        update=walletBob.register()
        Assert.assertNotNull(update)

    }

    @Test
    fun testWithdrawal() {
        testDeposit()

        waitToNextEon()

        runBlocking {
            walletAlice.getMessageChannel()?.receive()
        }

        val amount = 20L
        walletAlice.withdrawal(amount)

        runBlocking {
            walletAlice.getMessageChannel()?.receive()
        }
        Assert.assertTrue(!walletAlice.hub.hubStatus.couldWithDrawal())
    }

    private fun waitToNextEon() {
        var height = chain.getBlockNumber()
        var blockNumber = Eon.waitToEon(hubInfo.startBlockNumber.toLong(),height,hubInfo.blocksPerEon,hubInfo.latestEon+1)
        for (i in 0..blockNumber) {
            chain.sb.createBlock()
        }
    }

    private fun createBlocks(number:Int){
        for(i in 1..number)
            chain.createBlock()
    }

}

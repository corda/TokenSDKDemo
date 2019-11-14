package com.r3.demo.tokens.services

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.RetryOneTime
import java.util.concurrent.TimeUnit

@CordaService
class DirectoryService(private val serviceHub: AppServiceHub) : SingletonSerializeAsToken() {

    private val zookeeper = isValue("directory.service")

    init {
        if (zookeeper) {
            try {
                val url = getValue("directory.service.url", "localhost:2181")

                serviceHub.contextLogger().info("DirectoryService connecting on $url")

                val curatorFramework = CuratorFrameworkFactory.newClient(url, RetryOneTime(100))

                val accounts = listAccounts()

                serviceHub.contextLogger().info("Recording ${accounts.size} accounts in directory service")

                curatorFramework.start()
                curatorFramework.blockUntilConnected(3, TimeUnit.SECONDS)

                if (!curatorFramework.zookeeperClient.isConnected) {
                    throw IllegalArgumentException("Cannot connect on $url")
                }

                accounts.forEach { account ->
                    val path = "/accounts/${account.name}"
                    val bytes = account.serialize()

                    curatorFramework.create().orSetData().creatingParentContainersIfNeeded().forPath(path, bytes.bytes)
                }

                curatorFramework.close()
            } catch (e: Exception) {
                serviceHub.contextLogger().error("Exception processing accounts ${e.message}")
            }
        }
    }

    fun recordAccount(account: AccountInfo){
        if (zookeeper) {
            try {
                val url = getValue("directory.service.url", "localhost:2181")

                serviceHub.contextLogger().info("DirectoryService connecting on $url")

                val curatorFramework = CuratorFrameworkFactory.newClient(url, RetryOneTime(100))

                curatorFramework.start()
                curatorFramework.blockUntilConnected(3, TimeUnit.SECONDS)

                if (!curatorFramework.zookeeperClient.isConnected) {
                    throw IllegalArgumentException("Cannot connect on $url")
                }

                val path = "/accounts/${account.name}"
                val bytes = account.serialize()

                curatorFramework.create().orSetData().creatingParentContainersIfNeeded().forPath(path, bytes.bytes)

                curatorFramework.close()
            } catch (e: Exception) {
                serviceHub.contextLogger().error("Exception processing accounts ${e.message}")
            }
        }
    }

    private fun listAccounts(): List<AccountInfo> {
        val vault = serviceHub.vaultService.queryBy(AccountInfo::class.java)

        return vault.states.map { it.state.data }.filter { it.host == serviceHub.myInfo.legalIdentities.first() }
    }

    private fun getValue(key: String, value: String): String{
        val config = serviceHub.getAppContext().config

        return if (config.exists(key)) config.getString(key) else value
    }

    private fun isValue(key: String): Boolean{
        val config = serviceHub.getAppContext().config

        return if (config.exists(key)) config.getBoolean(key) else false
    }
}

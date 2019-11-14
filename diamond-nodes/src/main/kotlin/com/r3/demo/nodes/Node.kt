package com.r3.demo.nodes

import net.corda.core.identity.Party

/**
 * Records the node information for a user.
 */
class Node(val main: Main,
           val name: String,
           val legalName: String,
           val username: String,
           val password: String,
           val address: String){

    var party: Party? = null

    override fun toString(): String {
        return legalName
    }

    fun isIssuer(): Boolean {
        return legalName.contains("bank", true)
    }

    fun isCertifier(): Boolean {
        return legalName.contains("certifier", true)
    }

    fun isDealer(): Boolean {
        return legalName.contains("dealer", true)
    }
}

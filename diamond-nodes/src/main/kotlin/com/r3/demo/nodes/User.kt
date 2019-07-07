package com.r3.demo.nodes

import com.r3.demo.nodes.commands.*
import java.lang.IllegalArgumentException

/**
 * Records the node information for a user.
 * The [User] objects are embedded in the JEXL context so that they
 * can be referred to and invoke methods directly within JEXL scripts.
 */
class User(val main: Main,
    val name: String,
    val legalName: String,
    val username: String,
    val password: String,
    val address: String){

    override fun toString(): String {
        return legalName
    }
}

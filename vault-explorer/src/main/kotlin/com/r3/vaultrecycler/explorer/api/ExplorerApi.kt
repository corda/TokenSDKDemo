package com.r3.vaultrecycler.explorer.api

import com.r3.vaultrecycler.explorer.flows.runPagedExplorer
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * This API is accessible from /api/explorer. The endpoint paths specified below are relative to it.
 */
@Path("explorer")
class ExplorerApi(val rpcOps: CordaRPCOps) {
    private val me = rpcOps.nodeInfo().legalIdentities.first().name

    /** Helpers for filtering the network map cache. */
    private fun isNotary(nodeInfo: NodeInfo) = rpcOps.notaryIdentities().any { nodeInfo.isLegalIdentity(it) }
    private fun isMe(nodeInfo: NodeInfo) = nodeInfo.legalIdentities.first().name == me
    private fun isNetworkMap(nodeInfo : NodeInfo) = nodeInfo.legalIdentities.single().name.organisation == "Network Map Service"

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to me.toString())

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<String>> {
        return mapOf("peers" to rpcOps.networkMapSnapshot()
                .filter { isNotary(it).not() && isMe(it).not() && isNetworkMap(it).not() }
                .map { it.legalIdentities.first().name.toString() })
    }

    /**
     * Displays basic vault stats
     */
    @GET
    @Path("explore")
    fun getExplore(@QueryParam(value = "explorers") @DefaultValue("") explorers: String): Response {
        try {
            val data = rpcOps.runPagedExplorer("explore.txt", 10, explorers)
            val list = expandZipArray(data).joinToString( "<br>\n" )

            return Response
                .status(Response.Status.CREATED)
                .entity(list)
                .build()
        } catch (e: Exception) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(e.message)
                .build()
        }
    }

    private fun expandZipArray(array: ByteArray): List<String>{
        val list = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(array)).use {
            var entry = it.nextEntry

            while (entry != null){
                BufferedReader(InputStreamReader(it)).lineSequence().forEach{ list.add(it) }
                it.closeEntry()
                entry = it.nextEntry
            }
        }
        return list
    }
}
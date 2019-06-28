package com.r3.vaultrecycler.explorer.plugin

import com.r3.vaultrecycler.explorer.api.ExplorerApi
import net.corda.core.messaging.CordaRPCOps
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class ExplorerPlugin : WebServerPluginRegistry {
    /**
     * A list of classes that expose web APIs.
     */
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::ExplorerApi))

    /**
     * A list of directories in the resources directory that will be served by Jetty under /web.
     * The template's web frontend is accessible at /web/template.
     */
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the explorerWeb directory in resources to /web/template
            "explorer" to javaClass.classLoader.getResource("explorerWeb").toExternalForm()
    )
}
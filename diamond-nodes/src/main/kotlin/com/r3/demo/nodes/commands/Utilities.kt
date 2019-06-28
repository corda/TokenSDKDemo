package com.r3.demo.nodes.commands

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.contracts.Amount
import com.r3.demo.nodes.Main
import com.r3.demo.tokens.state.DiamondGradingReport
import net.corda.core.contracts.UniqueIdentifier
import net.corda.nodeapi.internal.persistence.currentDBSession
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import java.util.*
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

object Utilities {
    /**
     * Parse the amount value recorded in the text.
     */
    fun getAmount(text: String): Amount<Currency> {
        val pattern = Pattern.compile("([a-zA-Z$]+)(\\d+)")
        val matcher = pattern.matcher(text)

        if (!matcher.matches()){
            throw IllegalArgumentException("Cannot parse amount ${text}")
        }

        try {
            var currency = matcher.group(1)
            val amount = matcher.group(2)

            currency = currency.replace("A$", "AUD")
            currency = currency.replace("S$", "SGD")
            currency = currency.replace("$", "USD")

            return Amount(amount.toLong() * 100, Currency.getInstance(currency))
        } catch (e: Exception) {
            throw java.lang.IllegalArgumentException("Cannot parse amount ${text}")
        }
    }

    /**
     * Expanded the zip file embedded in byte array into
     * the files named in the zip file. The zip file is
     * the returned results from the detect/explore command.
     */
    fun expandZipArray(array: ByteArray){
        ZipInputStream(ByteArrayInputStream(array)).use {
            var entry = it.nextEntry

            while (entry != null){
                FileOutputStream(entry.name).use {output ->
                    it.copyTo(output)
                }
                it.closeEntry()
                entry = it.nextEntry
            }
        }
    }

    private fun parseClarity(parameters: String): DiamondGradingReport.ClarityScale {
        val pattern = Pattern.compile("[^\\w](VVS1|VVS2|VS1|VS2|VI1|VI2)[^\\w]")
        val matcher = pattern.matcher(parameters)

        if (matcher.find()){
            val clarity = matcher.group(1)
            return DiamondGradingReport.ClarityScale.valueOf(clarity)
        }

        throw IllegalArgumentException("No clarity")
    }

    private fun parseCut(parameters: String): String {
        val pattern = Pattern.compile("[^\\w]('.+')[^\\w]")
        val matcher = pattern.matcher(parameters)

        if (matcher.find()){
            return matcher.group(1)
        }

        throw IllegalArgumentException("No cut")
    }

    private fun parseColour(parameters: String): DiamondGradingReport.ColorScale {
        val pattern = Pattern.compile("[^\\w]([D-N])[^\\w]")
        val matcher = pattern.matcher(parameters)

        if (matcher.find()){
            val clarity = matcher.group(1)
            return DiamondGradingReport.ColorScale.valueOf(clarity)
        }

        throw IllegalArgumentException("No colour")
    }

    /**
     * Parse the results from the output state matcher to create a new Node.
     * Output state definitions look like (name, owner, amount).
     */
    fun parseReport(main: Main, service: CordaRPCOps, parameters: String, linearId: UniqueIdentifier = UniqueIdentifier()): DiamondGradingReport{
        val pattern = Pattern.compile("\\(\\s*([-\\w]+),\\s*([-\\w]+),\\s*([\\d.]+),\\s*(.+)\\)")
        val matcher = pattern.matcher(parameters)

        if (!matcher.find()){
            throw IllegalArgumentException("Invalid diamond report: ${parameters}")
        }
        val issuer = matcher.group(1)
        val requester = matcher.group(2)
        val caret = matcher.group(3)
        val stats = "," + matcher.group(4) + ","

        val issuerParty = main.getWellKnownUser(main.getUser(issuer), service)
        val requesterParty = main.getWellKnownUser(main.getUser(requester), service)
        val clarity = parseClarity(stats)
        val colour = parseColour(stats)
        val cut = parseCut(stats)

        return DiamondGradingReport(caret, colour, clarity, cut, issuerParty, requesterParty, linearId)
    }
}

fun DiamondGradingReport.printReport(): String {
    val pattern = Pattern.compile(".*O=([\\w\\d]+),.*")
    val m1 = pattern.matcher(this.assessor.toString())
    val m2 = pattern.matcher(this.requester.toString())
    val assessor = if (m1.matches()) m1.group(1) else this.assessor.toString()
    val requester = if (m2.matches()) m2.group(1) else this.requester.toString()

    val builder = StringBuilder(this.linearId.toString()).append(" = (")
    builder.append(assessor).append(", ")
    builder.append(requester).append(", ")
    builder.append(this.caratWeight).append(", ")
    builder.append(this.clarity).append(", ")
    builder.append(this.color).append(", ")
    builder.append(this.cut).append(')')

    return builder.toString()
}

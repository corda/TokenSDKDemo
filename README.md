# Token SDK Demo - Delivery vs Payment

## Overview

This sample code project demonstrates how the Token SDK can be used in a delivery vs payment scenario.
In this scenario a diamond dealer registers diamonds with a diamond grading authority and sells the diamonds
to customers. The customers can sell the diamond token to other customers. The diamond grading authority can
later change the diamond grading. Any evolution in the diamond grade report is seen by the current holder of
the token. Later the diamond token can be redeemed for cash.

### Actors (Corda Nodes)

* GIC - Diamond grading authority
* Dealer - Diamond dealer
* Alice - Diamond buyer/seller
* Bob - Diamond buyer/seller
* Charlie - Diamond buyer/seller

## Build and Execution

The following steps can be used to build the application, run the nodes and start the RCP client

    ./gradlew jar deployNodes
    ./deployCordapps
    ./build/nodes/runnodes
    java -jar diamond-nodes/build/libs/shell-nodes-0.1.jar

The current build.gradle assumes the Token SDK is version 1.1-SNAPSHOT.

    cordapp "com.r3.corda.lib.tokens:tokens-contracts:1.1-SNAPSHOT"
    cordapp "com.r3.corda.lib.tokens:tokens--workflows:1.1-SNAPSHOT"
    cordapp "com.r3.corda.lib.tokens:tokens--money:1.1-SNAPSHOT"

## RPC Client Commands

The RPC client application can be used to start flows on the Corda nodes. The general syntax of a command is

    <command> <node> arguments ...

Where node is one of GIC, Dealer, Alice, Bob or Charlie.

### help [command]

Display all commands, or the syntax of the given command

### issue-cash issuer receiver amount

Issue cash to the receiver. The amount should be in whole units with a three letter currency code.
The symbols $, A$ and S$ can be used for USD, AUD and SGD respectively.

    issue-cash GIC Alice $1000

### pay-cash payer payee amount

Transfer money from payer to payee

    pay-cash Alice Bob $500

### list user

List the unused fungible and non fungible tokens on a user's vault.

    list Alice

This command is needed to retrieve the token-ids and report-ids used in other commands.

### create authority (accessor, dealer, carets, clarity, colour, cut)

Create a diamond grade report. This will be recorded as an evolvable token on dealer's vault.
Carets should be a decimal number. Clarity one of VVS1, VVS2, VS1, VS2, VI1, VI2. Colour from D to N.

    create GIC (GIC, Dealer, 1.0, VVS1, D, 'oval')

This command will return the report-id which should be used in the purchase and update commands.

### update authority report-id (accessor, dealer, carets, clarity, colour, cut)

Update a diamond grade report.

    update GIC abcd (GIC, Dealer, 0.99, VVS1, E, 'oval')

The report-id can be any identifiable prefix of a valid report-id.

### purchase dealer buyer report-id amount

Purchase a diamond from a dealer. This will create a non-fungible token in the buyer's vault.
The report-id given on the command line can be a prefix of the linear-id used to record the diamond grade report.

    purchase Dealer Alice abcd $350

To find the token-id of the new NFT use the list command on the buyer's vault.

### transfer seller buyer token-id amount

Sell a diamond token between users. The token-id can be obtained by using the list command.
The token-id given on the command line can be a prefix of the token-id used to record the diamond token.

    transfer Alice Bob 1234 $550

### redeem owner dealer token-id amount

Redeem the diamond token for payment.

    redeem Bob Dealer 1234 $450

### explore user

Run the Vault Explore on the user's vault to display the transactions. See the Vault Recycler project for more details.

### bye

Exit the client

### Miscellaneous Commands

The commands issue, move, settle are older versions of purchase, transfer and redeem that do not involve payment.

The commands peers and whoami display information on the Corda network.

## Walk-through

The following is a walk through of the buying/selling process. (The linear-id have been edited to help it fit on a screen).

    $ java -jar diamond-nodes/build/libs/shell-nodes-0.1.jar
    > issue-cash GIC Alice $1000
    SignedTransaction(id=97647B868BF07FB5AA7121F55AF1F6A2CE406E6CF0ECEC54547013ACAD745E23)
    > issue-cash GIC Bob $1000
    SignedTransaction(id=3B7F54F3537AC3ADA0BC727B30203EA4AD2BBFDD18617AE2C9FC64AD76A450E0)
    > issue-cash GIC Charlie $1000
    SignedTransaction(id=BCB74A1E4EBF592C0B4139ADD8551C4986C7C50F44CD014297801B1417A649A2)
    > list Alice
    1000.00 USD issued by GIC owned by Alice
    > list Bob
    1000.00 USD issued by GIC owned by Bob
    > list Charlie
    1000.00 USD issued by GIC owned by Charlie
    > create GIC (GIC, Dealer, 1.0, VVS1, D, 'oval')
    3368dd92 = (GIC, Dealer, 1.0, VVS1, D, 'oval')
    > purchase Dealer Alice 3368 $550
    3368dd92 = (GIC, Dealer, 1.0, VVS1, D, 'oval')
    550.00 USD issued by GIC owned by Dealer
    > list Alice
    3368dd92 = (GIC, Dealer, 1.0, VVS1, D, 'oval')
    407033aa = (TokenPointer(class DiamondGradingReport, 3368dd92) issued by Dealer owned by Alice)
    450.00 USD issued by GIC owned by Alice
    > transfer Alice Bob 4070 $600
    3368dd92 = (GIC, Dealer, 1.0, VVS1, D, 'oval')
    450.00 USD issued by GIC owned by Alice
    600.00 USD issued by GIC owned by Alice
    > list Alice
    3368dd92 = (GIC, Dealer, 1.0, VVS1, D, 'oval')
    450.00 USD issued by GIC owned by Alice
    600.00 USD issued by GIC owned by Alice
    > list Bob
    3368dd92 = (GIC, Dealer, 1.0, VVS1, D, 'oval')
    407033aa = (TokenPointer(class DiamondGradingReport, 3368dd92) issued by Dealer owned by Bob)
    400.00 USD issued by GIC owned by Bob
    > transfer Bob Charlie 4070 $575
    3368dd92 = (GIC, Dealer, 1.0, VVS1, D, 'oval')
    400.00 USD issued by GIC owned by Bob
    575.00 USD issued by GIC owned by Bob
    > list Charlie
    3368dd92 = (GIC, Dealer, 1.0, VVS1, D, 'oval')
    407033aa = (TokenPointer(class DiamondGradingReport, 3368dd92) issued by Dealer owned by Charlie)
    425.00 USD issued by GIC owned by Charlie
    > update GIC 3368 (GIC, Dealer, 0.9, VVS2, D, 'oval')
    3368dd92 = (GIC, Dealer, 0.9, VVS2, D, 'oval')
    > list Charlie
    3368dd92 = (GIC, Dealer, 0.9, VVS2, D, 'oval')
    407033aa = (TokenPointer(class DiamondGradingReport, 3368dd92) issued by Dealer owned by Charlie)
    425.00 USD issued by GIC owned by Charlie
    > list Alice
    3368dd92 = (GIC, Dealer, 1.0, VVS1, D, 'oval')
    450.00 USD issued by GIC owned by Alice
    600.00 USD issued by GIC owned by Alice
    > redeem Charlie Dealer 4070 $550
    3368dd92 = (GIC, Dealer, 0.9, VVS2, D, 'oval')
    425.00 USD issued by GIC owned by Charlie
    550.00 USD issued by GIC owned by Charlie
    > list Charlie
    3368dd92 = (GIC, Dealer, 0.9, VVS2, D, 'oval')
    425.00 USD issued by GIC owned by Charlie
    550.00 USD issued by GIC owned by Charlie
    > list Dealer
    3368dd92 = (GIC, Dealer, 0.9, VVS2, D, 'oval')
    > explore Charlie
    3B7F54F3 consumed
      + > FungibleToken consumed
    97647B86 consumed
      + > FungibleToken consumed
    BCB74A1E consumed
      + > FungibleToken consumed
    A55E711A consumed
      + > DiamondGradingReport consumed
    4F69E76A active
      + < 2AEA5781[0]
      + < 3B7F54F3[0]
      + - A55E711A[0]
      + > NonFungibleToken consumed
      + > FungibleToken non participant
      + > FungibleToken non participant
    2AEA5781 active
      + < 97647B86[0]
      + - A55E711A[0]
      + > NonFungibleToken consumed
      + > FungibleToken consumed
      + > FungibleToken non participant
    8AE61EBE active
      + < 4F69E76A[0]
      + < BCB74A1E[0]
      + - A55E711A[0]
      + > NonFungibleToken consumed
      + > FungibleToken non participant
      + > FungibleToken unconsumed
    3D199808 active
      + < A55E711A[0]
      + > DiamondGradingReport non participant
    8050D4C4 active
      + < 8AE61EBE[0]
      + < 2AEA5781[1]
      + - 3D199808[0]
      + > FungibleToken unconsumed

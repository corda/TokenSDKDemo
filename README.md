# Token Accounts SDK Demo - Delivery vs Payment

## Overview

This sample code project demonstrates how the Tokens and Accounts SDKs can be used in a delivery vs payment scenario.
In this scenario diamond dealers register diamonds with a diamond grading authority and then sell the diamonds
to customers. The customers can sell the diamond tokens on to other customers. The diamond grading authority can
later change the diamond grading. Any evolution in the diamond grade report is seen by the current holder of
the token. Later the diamond token can be redeemed for cash.

### Actors (Corda Nodes)

* GIC - Diamond grading authority node
* Bank - Cash issuing authority node
* AAA - A diamond dealer/repository node
* BBB - A diamond dealer/repository node
* Notary - Network notary

### Actors (Accounts)

Diamond dealers and traders are represented as accounts on the dealer nodes. These accounts are created
using the com.r3.corda.lib.accounts.workflows.flows.CreateAccount workflow, which can be invoked using
the demo client.

In this scenario each dealer node (AAA, BBB) will have an account of the same name created on
the node. This is so that money for the dealer can be kept separate from money for clients (Alice, Bob etc.)

In this demo the account name is assumed to be unique across all nodes.

### Zookeeper Integration

Accounts created from the client through the CreateAccountFlow can be recorded in a Zookeeper instance by
activating the built-in zookeeper client. By default the client is not active. The following configuration
properties are used to configure the zookeeper client.

    directory.service=true|false
    directory.service.url=localhost:2181
    
The configuration properties must be recorded in the configuration file diamond-flows-0.1.conf 
in the cordaps/config directory.

For each account a znode is created in the /accounts directory on the zookeeper server, with the znode data
containing the serialised version of the AccountInfo object.

## Build and Execution

The following steps can be used to build the application, run the nodes and start the RCP client. (The script
startnodes will execute the Corda nodes in background without a shell window. If you want to use the shell
then start the nodes using runnodes.)

    ./gradlew jar deployNodes
    ./deployCordapps
    ./startnodes
    ./startclient

The current build.gradle uses the following releases

    corda_release_group = 'net.corda'
    corda_release_version = '4.3-RC01'
    tokens_release_group = 'com.r3.corda.lib.tokens'
    tokens_release_version = '1.1-RC05-PRESIGN'
    accounts_release_group = 'com.r3.corda.lib.accounts'
    accounts_release_version = '1.0-RC03'
    confidential_id_release_group = "com.r3.corda.lib.ci"
    confidential_id_release_version = "1.0-RC03"

## RPC Client Commands

The RPC demo client can be used to start flows on the Corda nodes. The general syntax of a command is

    <command> <node> arguments ...
    <command> <account> arguments ...

Where node is one of GIC, Bank, AAA, BBB and account is an account created using the demo client.

### help [command]

Display all commands, or the syntax of the given command

### create-account node name

Creates an account on a dealer node. Typically you should create an account for the dealer itself,
followed by accounts for each of their clients. Only dealer nodes can create accounts.

    create-account AAA AAA
    create-account AAA Alice
    
### load-accounts node

Loads the existing account names from a node and records them on the demo client. This command is needed
after restarting the demo client so that the client knows where the accounts are hosted.

    load-accounts AAA
    
### issue-cash issuer account amount

Issue cash to an account. The amount should be in whole units with a three letter currency code.
The symbols $, A$ and S$ can be used for USD, AUD and SGD respectively. Only banks can issue cash.

    issue-cash Bank Alice $1000

### pay-cash payer payee amount

Transfer money from payer to payee.

    pay-cash Alice Bob $500

### reissue-cash issuer payee amount

Return your money to the bank and receive new tokens.

    reissue-cash Bank Bob $500

### list account

List the unused fungible and non fungible tokens on an account's vault.

    list Alice

This command is needed to retrieve the token-ids used by other commands.

### create-report authority (accessor, dealer, carets, clarity, colour, cut)

Create a diamond grade report. This will be recorded as an evolvable token on dealer's node.
Carets should be a decimal number. Clarity one of VVS1, VVS2, VS1, VS2, VI1, VI2. Colour from D to N.

    create GIC (GIC, AAA, 1.0, VVS1, D, 'oval')

This command will return the report-id which should be used in the purchase and update commands.

### update-report authority report-id (accessor, dealer, carets, clarity, colour, cut)

Update a diamond grade report.

    update GIC abcd (GIC, AAA, 0.99, VVS1, E, 'oval')

The report-id can be any identifiable prefix of a valid report-id.

### reports node

List the reports created on a node.

    reports GIC
    reports AAA

This command is needed to retrieve the report-ids used by other commands.

### purchase dealer buyer report-id amount

Purchase a diamond from a dealer. This will create a non-fungible token in the buyer's vault.
The report-id given on the command line can be a prefix of the linear-id of the diamond grade report.

    purchase AAA Alice abcd $350

### transfer seller buyer token-id amount

Sell a diamond token between accounts. The token-id can be obtained by using the list command.
The token-id given on the command line can be a prefix of the linear-id of the diamond token.

    transfer Alice Bob 1234 $550

### redeem owner dealer token-id amount

Redeem the diamond token for payment.

    redeem Bob AAA 1234 $450

### bye

Exit the client

## Walk-through

The following is a walk through of the buying/selling process.

    $ java -jar diamond-nodes/build/libs/shell-nodes-0.1.jar
    Node AAA on [OU=Dealer,O=AAA,L=Sydney,C=AU]
    Node Bank on [O=Bank,L=London,C=GB]
    Node BBB on [OU=Dealer,O=BBB,L=Sydney,C=AU]
    Node GIC on [OU=Certifier,O=GIC,L=Canberra,C=AU]
    > create-account AAA AAA
    AAA on [OU=Dealer, O=AAA, L=Sydney, C=AU] with id 48832117-58a1-4ab1-be2d-312b1640d95d
    > create-account AAA Alice
    Alice on [OU=Dealer, O=AAA, L=Sydney, C=AU] with id b162581b-a104-41fc-9dfd-6c4c733b3d42
    > create-account AAA Bob
    Bob on [OU=Dealer, O=AAA, L=Sydney, C=AU] with id 774cd01f-f17a-429f-b536-71755dc09098
    > create-account BBB Charlie
    Charlie on [OU=Dealer, O=BBB, L=Sydney, C=AU] with id 14eea2d6-f092-4aa7-ae80-f03b978257fd
    > issue-cash Bank Alice $1000
    SignedTransaction(id=97647B868BF07FB5AA7121F55AF1F6A2CE406E6CF0ECEC54547013ACAD745E23)
    > issue-cash Bank Bob $1000
    SignedTransaction(id=3B7F54F3537AC3ADA0BC727B30203EA4AD2BBFDD18617AE2C9FC64AD76A450E0)
    > issue-cash Bank Charlie $1000
    SignedTransaction(id=BCB74A1E4EBF592C0B4139ADD8551C4986C7C50F44CD014297801B1417A649A2)
    > list Alice
    1000.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DLDoPWXDGAnXwyU3
    > list Bob
    1000.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DL6AFcBZkoCeJvaw
    > list Charlie
    1000.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DL33V2878sGPNcji
    > create-report GIC (GIC, AAA, 1.0, VVS1, D, 'oval')
    b599f218-80b5-4e9d-98e5-95ba25d9da9a
    > reports AAA
    b599f218 = (GIC, AAA, 1.0, VVS1, D, 'oval')
    > purchase AAA Alice b599f218 $550
    550.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DLF2kqr2VPakJbmv
    > list Alice
    348b66a6 = (TokenPointer(DiamondGradingReport, b599f218) issued by AAA held by DL6BFnAYerbhRULH)
    450.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DL6BFnAYerbhRULH
    > transfer Alice Bob 348b66a6 $600
    450.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DL6BFnAYerbhRULH
    600.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DLBrre4W6NsokuDu
    > list Bob
    348b66a6 = (TokenPointer(DiamondGradingReport, b599f218) issued by AAA held by DL6MtFq3LgntJGEF)
    400.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DL6MtFq3LgntJGEF
    > transfer Bob Charlie 4070 $575
    400.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DL6MtFq3LgntJGEF
    575.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DL9ssSv5gG8A3Ecy
    > list Charlie
    348b66a6 = (TokenPointer(DiamondGradingReport, b599f218) issued by AAA held by DL8aR91MY94vU8Vq)
    425.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DL8aR91MY94vU8Vq
    > update-report GIC b599f218 (GIC, AAA, 1.0, VVS2, D, 'oval')
    b599f218-80b5-4e9d-98e5-95ba25d9da9a
    > reports AAA
    b599f218 = (GIC, AAA, 1.0, VVS2, D, 'oval')
    > reports BBB
    b599f218 = (GIC, AAA, 1.0, VVS2, D, 'oval')
    > redeem Charlie AAA 348b66a6 $450
    425.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DL8aR91MY94vU8Vq
    450.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DLGkEwmpv3SS29Vs
    > list AAA
    100.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DL9JhdqwLGLgdcbM
    > list Alice
    450.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DL6BFnAYerbhRULH
    600.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DLBrre4W6NsokuDu
    > reissue-cash Bank Alice $1050
    SignedTransaction(id=2149578188C8DFC847925A5483BC39EAE93FF4BDB0259A65E78AFBD56E97BDF1)
    > list Alice
    1050.00 TokenType(tokenIdentifier='USD', fractionDigits=2) issued by Bank held by DL42uaq31F2UcQdX
    > bye

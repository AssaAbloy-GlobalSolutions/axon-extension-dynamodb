package com.assaabloyglobalsolutions.axon.extension.dynamodb.example.client

import com.assaabloyglobalsolutions.axon.extension.dynamodb.example.BalanceRequest
import com.assaabloyglobalsolutions.axon.extension.dynamodb.example.BalanceResponse
import com.assaabloyglobalsolutions.axon.extension.dynamodb.example.CreateAccountResponse
import com.assaabloyglobalsolutions.axon.extension.dynamodb.example.DepositRequest
import com.assaabloyglobalsolutions.axon.extension.dynamodb.example.api.AccountBalanceQuery
import com.assaabloyglobalsolutions.axon.extension.dynamodb.example.api.CreateBankAccountCommand
import com.assaabloyglobalsolutions.axon.extension.dynamodb.example.api.DepositMoneyCommand
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryGateway
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/bank")
class BankController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway
) {

    @PostMapping("/account", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun createAccount(): ResponseEntity<CreateAccountResponse> {
        val accountId = java.util.UUID.randomUUID().toString()

        commandGateway.send<String>(
            CreateBankAccountCommand(
                bankAccountId = accountId,
                overdraftLimit = 1000
            )
        )

        return ResponseEntity.ok(CreateAccountResponse(accountId))
    }

    @PostMapping("/account/deposit", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun deposit(@RequestBody request: DepositRequest): ResponseEntity<Unit> {
        commandGateway.send<Any?>(
            DepositMoneyCommand(
                bankAccountId = request.accountId,
                amountOfMoney = request.amount
            )
        )
        return ResponseEntity.ok().build()
    }

    @GetMapping(
        "/account",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun balance(@RequestBody request: BalanceRequest): ResponseEntity<BalanceResponse> =
        queryGateway.query(
            AccountBalanceQuery(
                bankAccountId = request.accountId
            ),
            ResponseTypes.instanceOf(Long::class.java)
        ).let { amount -> ResponseEntity.ok(BalanceResponse(request.accountId, amount?.get() ?: 0)) }
}
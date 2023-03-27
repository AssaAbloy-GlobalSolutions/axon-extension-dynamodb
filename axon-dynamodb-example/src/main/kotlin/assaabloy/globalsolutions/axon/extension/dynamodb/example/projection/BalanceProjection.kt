/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package assaabloy.globalsolutions.axon.extension.dynamodb.example.projection

import assaabloy.globalsolutions.axon.extension.dynamodb.DynamoAttribute
import assaabloy.globalsolutions.axon.extension.dynamodb.example.api.AccountBalanceQuery
import assaabloy.globalsolutions.axon.extension.dynamodb.example.api.BankAccountCreatedEvent
import assaabloy.globalsolutions.axon.extension.dynamodb.example.api.MoneyDepositedEvent
import assaabloy.globalsolutions.axon.extension.dynamodb.example.api.MoneyWithdrawnEvent
import assaabloy.globalsolutions.axon.extension.dynamodb.nullableLong
import mu.KLogging
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler
import org.axonframework.queryhandling.QueryHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

@ProcessingGroup("BalanceProjection")
@Component
class BalanceProjection(val balanceRepository: BalanceRepository) {

    companion object : KLogging()

    @EventHandler
    fun on(event: BankAccountCreatedEvent) {
        logger.info { "account created: ${event.id}" }
        balanceRepository.createBankAccount(event.id)
    }

    @EventHandler
    fun on(event: MoneyDepositedEvent) {
        logger.info { "money added to: ${event.bankAccountId}" }
        // sum old amount plus new one
        balanceRepository.depositMoney(event.bankAccountId, event.amount)
    }

    @EventHandler
    fun on(event: MoneyWithdrawnEvent) {
        logger.info { "money subtracted to: ${event.bankAccountId}" }
        // sum old amount minus new one
        balanceRepository.withdrawMoney(event.bankAccountId, event.amount)
    }

    @QueryHandler
    fun on(query: AccountBalanceQuery): Long? {
        return balanceRepository.checkBalance(query.bankAccountId)
    }

}

@Primary
@Component
class DynamoBalanceRepository(
    @Value("\${dynamodb.balance-table.name}")
    private val tableName: String,
    private val client: DynamoDbClient,
) : BalanceRepository {

    val bankAccountId = DynamoAttribute.String("aid")
    val balance = DynamoAttribute.String("b")

    override fun createBankAccount(bankAccountId: String) {
        client.putItem{
            it.tableName(tableName)
                .item(
                    mapOf(
                        this.bankAccountId.valuePair(bankAccountId)
                    )
                )
        }
    }

    override fun depositMoney(bankAccountId: String, amount: Long) {
        client.updateItem {
            it.tableName(tableName)
                .key(
                    mapOf(
                        this.bankAccountId.valuePair(bankAccountId)
                    )
                )
                .updateExpression("ADD $balance :amount")
                .expressionAttributeValues(
                    mapOf(
                        ":amount" to AttributeValue.fromN(amount.toString())
                    )
                )
        }
    }

    override fun withdrawMoney(bankAccountId: String, amount: Long) {
        client.updateItem {
            it.tableName(tableName)
                .key(
                    mapOf(
                        this.bankAccountId.valuePair(bankAccountId)
                    )
                )
                .updateExpression("ADD $balance :amount")
                .expressionAttributeValues(
                    mapOf(
                        ":amount" to AttributeValue.fromN(amount.unaryMinus().toString())
                    )
                )
        }
    }

    override fun checkBalance(bankAccountId: String): Long? = client.getItem {
        it.tableName(tableName)
            .key(
                mapOf(
                    this.bankAccountId.valuePair(bankAccountId)
                )
            )
    }.item()?.get("b").nullableLong()

}

interface BalanceRepository {

    fun createBankAccount(bankAccountId: String)
    fun depositMoney(bankAccountId: String, amount: Long)
    fun withdrawMoney(bankAccountId: String, amount: Long)
    fun checkBalance(bankAccountId: String): Long?
}
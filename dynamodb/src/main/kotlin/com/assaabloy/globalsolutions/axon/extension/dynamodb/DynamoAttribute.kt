package com.assaabloy.globalsolutions.axon.extension.dynamodb

import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

sealed class DynamoAttribute<T>(val name: kotlin.String) {

    fun from(item: Map<kotlin.String, AttributeValue>) = toDomain(item[name])!!
    fun nullableFrom(item: Map<kotlin.String, AttributeValue>) = toDomain(item[name])
    fun valuePair(field: T) = name to toAttributeValue(field)

    internal abstract fun toDomain(value: AttributeValue?): T?
    internal abstract fun toAttributeValue(field: T): AttributeValue

    override fun toString() = name

    class Int(name: kotlin.String) : DynamoAttribute<kotlin.Int>(name) {
        override fun toDomain(value: AttributeValue?) = value.nullableInt()
        override fun toAttributeValue(field: kotlin.Int): AttributeValue = AttributeValue.fromN("$field")
    }

    class Long(name: kotlin.String) : DynamoAttribute<kotlin.Long>(name) {
        override fun toDomain(value: AttributeValue?) = value.nullableLong()
        override fun toAttributeValue(field: kotlin.Long): AttributeValue = AttributeValue.fromN("$field")
    }

    class String(name: kotlin.String) : DynamoAttribute<kotlin.String>(name) {
        override fun toDomain(value: AttributeValue?): kotlin.String? = value.nullableString()
        override fun toAttributeValue(field: kotlin.String): AttributeValue = AttributeValue.fromS(field)
    }

    class Class(name: kotlin.String) : DynamoAttribute<java.lang.Class<*>>(name) {
        override fun toDomain(value: AttributeValue?): java.lang.Class<*>? =
            value.nullableString()?.let { java.lang.Class.forName(it) }

        override fun toAttributeValue(field: java.lang.Class<*>): AttributeValue =
            AttributeValue.fromS(field.name)
    }

    class ByteArray(name: kotlin.String) : DynamoAttribute<kotlin.ByteArray>(name) {
        override fun toDomain(value: AttributeValue?): kotlin.ByteArray? = value.nullableBytes()
        override fun toAttributeValue(field: kotlin.ByteArray): AttributeValue =
            AttributeValue.fromB(SdkBytes.fromByteArray(field))
    }

    class StringSet(name: kotlin.String) : DynamoAttribute<Set<kotlin.String>>(name) {
        override fun toDomain(value: AttributeValue?): Set<kotlin.String>? = value?.l()?.map { it.s() }?.toSet()
        override fun toAttributeValue(field: Set<kotlin.String>): AttributeValue =
            field.map(AttributeValue::fromS).let(AttributeValue::fromL)
    }
}

operator fun Map<String, AttributeValue>.contains(attribute: DynamoAttribute<*>): Boolean {
    return containsKey(attribute.name)
}
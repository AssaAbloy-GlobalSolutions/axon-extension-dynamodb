package com.assaabloy.globalsolutions.axon.extension.dynamodb

import software.amazon.awssdk.services.dynamodb.model.AttributeValue

fun AttributeValue?.bytes(): ByteArray = this!!.b().asByteArray()
fun AttributeValue?.nullableBytes(): ByteArray? = this?.b()?.asByteArray()
fun AttributeValue?.boolean(): Boolean = this!!.bool()
fun AttributeValue?.nullableBoolean(): Boolean? = this?.bool()
fun AttributeValue?.int(): Int = this!!.n().toInt()
fun AttributeValue?.nullableInt(): Int? = this?.n()?.toInt()
fun AttributeValue?.long(): Long = this!!.n().toLong()
fun AttributeValue?.nullableLong(): Long? = this?.n()?.toLong()
fun AttributeValue?.nullableString(): String? = this?.s()
fun AttributeValue?.string(default: String? = null): String =
    this?.s() ?: default ?: throw NullPointerException("Attribute Value is null")

fun stringAttributeValue(value: String): AttributeValue = AttributeValue.fromS(value)
fun numberAttributeValue(value: Number): AttributeValue = AttributeValue.fromN(value.toString())
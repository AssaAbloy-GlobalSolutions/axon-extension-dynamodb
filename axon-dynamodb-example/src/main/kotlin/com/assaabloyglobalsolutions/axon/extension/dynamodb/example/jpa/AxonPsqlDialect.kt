package com.assaabloyglobalsolutions.axon.extension.dynamodb.example.jpa

import org.hibernate.boot.model.TypeContributions
import org.hibernate.dialect.DatabaseVersion
import org.hibernate.dialect.PostgreSQLDialect
import org.hibernate.service.ServiceRegistry
import org.hibernate.type.SqlTypes
import java.sql.Types

class AxonPsqlDialect : PostgreSQLDialect(DatabaseVersion.make(9, 5, 0)) {

    override fun contributeTypes(typeContributions: TypeContributions, serviceRegistry: ServiceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry)
        typeContributions
            .typeConfiguration
            .jdbcTypeRegistry
            .addDescriptor(Types.BLOB, JsonBlobTypeDescriptor.INSTANCE)
    }

    override fun castType(sqlTypeCode: Int) = when (sqlTypeCode) {
        SqlTypes.BLOB -> "json"
        else          -> super.castType(sqlTypeCode)
    }

    // supposedly replacement for registerColumnType()
    override fun columnType(sqlTypeCode: Int) = when (sqlTypeCode) {
        SqlTypes.BLOB -> "json"
        else          -> super.columnType(sqlTypeCode)
    }
}

package com.assaabloyglobalsolutions.axon.extension.dynamodb.example.jpa
import org.hibernate.type.descriptor.ValueBinder
import org.hibernate.type.descriptor.ValueExtractor
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.type.descriptor.java.JavaType
import org.hibernate.type.descriptor.java.PrimitiveByteArrayJavaType
import org.hibernate.type.descriptor.jdbc.BasicBinder
import org.hibernate.type.descriptor.jdbc.BasicExtractor
import java.sql.CallableStatement
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.hibernate.type.descriptor.jdbc.JdbcType

open class JsonBlobTypeDescriptor : JdbcType {

    override fun getJdbcTypeCode(): Int {
        return Types.OTHER
    }

//    override fun canBeRemapped(): Boolean {
//        return true
//    }

    override fun <X> getBinder(javaType: JavaType<X>): ValueBinder<X> {
        require(javaType is PrimitiveByteArrayJavaType)

        return object : BasicBinder<X>(javaType, this) {
            override fun doBind(st: PreparedStatement, value: X, index: Int, options: WrapperOptions) {
                val payload = String(javaType.unwrap(value, javaType.javaType, options) as ByteArray)
                st.setObject(
                    index,
                    payload,
                    Types.OTHER
                )
            }

            override fun doBind(st: CallableStatement, value: X, name: String, options: WrapperOptions) {
                val payload = String(javaType.unwrap(value, javaType.javaType, options) as ByteArray)
                st.setObject(
                    name,
                    payload,
                    Types.OTHER
                )
            }
        }
    }

    override fun <X> getExtractor(javaType: JavaType<X>): ValueExtractor<X> {
        require(javaType is PrimitiveByteArrayJavaType)

        return object : BasicExtractor<X>(javaType, this) {
            override fun doExtract(rs: ResultSet, paramIndex: Int, options: WrapperOptions?): X {
                return javaType.wrap(rs.getString(paramIndex)?.toByteArray(), options) as X
            }

            override fun doExtract(statement: CallableStatement, index: Int, options: WrapperOptions): X {
                return javaType.wrap(statement.getString(index)?.toByteArray(), options) as X
            }

            override fun doExtract(statement: CallableStatement, name: String, options: WrapperOptions): X {
                return javaType.wrap(statement.getString(name)?.toByteArray(), options) as X
            }
        }
    }

    companion object {
        val INSTANCE = JsonBlobTypeDescriptor()
    }
}
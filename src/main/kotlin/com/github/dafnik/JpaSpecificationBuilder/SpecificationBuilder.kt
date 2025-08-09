@file:Suppress("PackageName")

package com.github.dafnik.JpaSpecificationBuilder

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import kotlin.reflect.KProperty1

enum class CompareOp {
    EQ, NOT_EQ, IS_NULL, NOT_NULL, LIKE, IN
}

enum class OrderDirection {
    ASC, DESC
}

class ColumnCondition<T>(
    val path: String,
    val op: CompareOp,
    val value: Any? = null
)

sealed interface PredicateNode<T>

class AndNode<T>(val children: List<PredicateNode<*>>) : PredicateNode<T>
class OrNode<T>(val children: List<PredicateNode<*>>) : PredicateNode<T>
class ConditionNode<T>(val condition: ColumnCondition<T>) : PredicateNode<T>
class JoinNode<P, C>(val path: String, val builder: ConditionBuilder<C>) : PredicateNode<P>

// DSL markers for better IntelliJ suggestions
@DslMarker
annotation class WhereDsl

@DslMarker
annotation class LogicalDsl

@DslMarker
annotation class ColumnDsl

@WhereDsl
class WhereBuilder<T> {
    internal val nodes = mutableListOf<PredicateNode<T>>()

    fun and(block: LogicalBuilder<T>.() -> Unit) {
        val childBuilder = LogicalBuilder<T>().apply(block)
        nodes += AndNode(childBuilder.nodes)
    }

    fun or(block: LogicalBuilder<T>.() -> Unit) {
        val childBuilder = LogicalBuilder<T>().apply(block)
        nodes += OrNode(childBuilder.nodes)
    }

    fun <R> join(prop: KProperty1<T, R>, block: WhereBuilder<R>.() -> Unit) {
        val joinName = prop.name
        val childBuilder = WhereBuilder<R>().apply(block)
        nodes += JoinNode(joinName, childBuilder.toConditionBuilder())
    }

    fun <R> join(path: String, block: WhereBuilder<R>.() -> Unit) {
        val childBuilder = WhereBuilder<R>().apply(block)
        nodes += JoinNode(path, childBuilder.toConditionBuilder())
    }

    internal fun toConditionBuilder(): ConditionBuilder<T> {
        val cb = ConditionBuilder<T>()
        cb.nodes.addAll(nodes)
        return cb
    }
}

@LogicalDsl
class LogicalBuilder<T> {
    internal val nodes = mutableListOf<PredicateNode<T>>()

    fun col(prop: KProperty1<out Any?, *>) = ColumnBuilder(prop.name)
    fun col(path: String) = ColumnBuilder(path)

    @ColumnDsl
    inner class ColumnBuilder(private val path: String) {
        infix fun eq(value: Any?) {
            if (value != null) {
                nodes += ConditionNode(ColumnCondition(path, CompareOp.EQ, value))
            }
        }

        infix fun notEq(value: Any?) {
            if (value != null) {
                nodes += ConditionNode(ColumnCondition(path, CompareOp.NOT_EQ, value))
            }
        }

        infix fun like(value: String?) {
            if (!value.isNullOrBlank()) {
                nodes += ConditionNode(ColumnCondition(path, CompareOp.LIKE, value))
            }
        }

        fun isNull() {
            nodes += ConditionNode(ColumnCondition(path, CompareOp.IS_NULL))
        }

        fun notNull() {
            nodes += ConditionNode(ColumnCondition(path, CompareOp.NOT_NULL))
        }

        infix fun inList(values: List<*>?) {
            if (!values.isNullOrEmpty()) {
                nodes += ConditionNode(ColumnCondition(path, CompareOp.IN, values))
            }
        }
    }

    fun and(block: LogicalBuilder<T>.() -> Unit) {
        val childBuilder = LogicalBuilder<T>().apply(block)
        nodes += AndNode(childBuilder.nodes)
    }

    fun or(block: LogicalBuilder<T>.() -> Unit) {
        val childBuilder = LogicalBuilder<T>().apply(block)
        nodes += OrNode(childBuilder.nodes)
    }

    fun <R> join(prop: KProperty1<T, R>, block: LogicalBuilder<R>.() -> Unit) {
        val joinName = prop.name
        val childBuilder = LogicalBuilder<R>().apply(block)
        nodes += JoinNode(joinName, childBuilder.toConditionBuilder())
    }

    fun <R> join(path: String, block: LogicalBuilder<R>.() -> Unit) {
        val childBuilder = LogicalBuilder<R>().apply(block)
        nodes += JoinNode(path, childBuilder.toConditionBuilder())
    }

    internal fun toConditionBuilder(): ConditionBuilder<T> {
        val cb = ConditionBuilder<T>()
        cb.nodes.addAll(nodes)
        return cb
    }
}

class ConditionBuilder<T> {
    internal val nodes = mutableListOf<PredicateNode<T>>()

    @Suppress("SpreadOperator")
    internal fun build(from: From<*, *>, cb: CriteriaBuilder): Predicate? {
        if (nodes.isEmpty()) return null
        return cb.and(*nodes.mapNotNull { it.toPredicate(from, cb) }.toTypedArray())
    }

    @Suppress("SpreadOperator")
    private fun PredicateNode<*>.toPredicate(
        from: From<*, *>,
        cb: CriteriaBuilder
    ): Predicate? = when (this) {
        is ConditionNode<*> -> condition.toPredicate(from, cb)
        is AndNode<*> -> cb.and(*children.mapNotNull { it.toPredicate(from, cb) }.toTypedArray())
        is OrNode<*> -> cb.or(*children.mapNotNull { it.toPredicate(from, cb) }.toTypedArray())
        is JoinNode<*, *> -> {
            val join = getOrCreateJoin(from, this.path)
            this.builder.build(join, cb)
        }
    }

    private fun ColumnCondition<*>.toPredicate(
        from: From<*, *>,
        cb: CriteriaBuilder
    ): Predicate {
        val pathObj = resolvePath(from, path)
        return when (op) {
            CompareOp.EQ -> cb.equal(pathObj, value)
            CompareOp.NOT_EQ -> cb.notEqual(pathObj, value)
            CompareOp.IS_NULL -> cb.isNull(pathObj)
            CompareOp.NOT_NULL -> cb.isNotNull(pathObj)
            CompareOp.LIKE -> cb.like(
                cb.lower(pathObj.`as`(String::class.java)),
                "%${(value as String).lowercase()}%"
            )
            CompareOp.IN -> cb.`in`(pathObj).apply {
                (value as List<*>).forEach { v -> value(v) }
            }
        }
    }

    private fun getOrCreateJoin(from: From<*, *>, path: String): From<*, *> {
        val parts = path.split('.')
        var current: From<*, *> = from
        for (part in parts) {
            current = current.join<Any, Any>(part, JoinType.LEFT)
        }
        return current
    }
}

@WhereDsl
class SpecificationBuilder<T> {
    var distinct: Boolean = false
    private var whereBuilder: WhereBuilder<T>? = null
    private var groupByProps: List<String> = emptyList()
    private val orderByList: MutableList<Pair<String, OrderDirection>> = mutableListOf()

    fun where(block: WhereBuilder<T>.() -> Unit) {
        whereBuilder = WhereBuilder<T>().apply(block)
    }

    fun groupBy(vararg props: KProperty1<T, *>) {
        groupByProps = props.map { it.name }
    }

    fun groupBy(vararg props: String) {
        groupByProps = props.toList()
    }

    fun orderBy(prop: KProperty1<T, *>, direction: OrderDirection) {
        orderByList += prop.name to direction
    }

    fun orderBy(path: String, direction: OrderDirection) {
        orderByList += path to direction
    }

    fun toSpecification(): Specification<T> = Specification { root, query, cb ->
        query.distinct(distinct)

        if (groupByProps.isNotEmpty()) {
            query.groupBy(groupByProps.map { resolvePath(root, it) })
        }

        if (orderByList.isNotEmpty()) {
            query.orderBy(
                orderByList.map { (path, dir) ->
                    val p = resolvePath(root, path)
                    if (dir == OrderDirection.ASC) cb.asc(p) else cb.desc(p)
                }
            )
        }

        whereBuilder?.toConditionBuilder()?.build(root, cb)
    }
}

private fun resolvePath(from: From<*, *>, path: String): Path<*> {
    val parts = path.split('.')
    var current: From<*, *> = from
    for ((index, part) in parts.withIndex()) {
        if (index < parts.size - 1) {
            current = current.join<Any, Any>(part, JoinType.LEFT)
        } else {
            return current.get<Any>(part)
        }
    }
    return current
}

fun <T> buildSpecification(block: SpecificationBuilder<T>.() -> Unit): Specification<T> =
    SpecificationBuilder<T>().apply(block).toSpecification()

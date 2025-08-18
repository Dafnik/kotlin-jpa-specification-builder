@file:Suppress("PackageName", "SpreadOperator")

package me.dafnik.JpaSpecificationBuilder

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.FetchParent
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import kotlin.reflect.KProperty1

enum class CompareOp {
    EQ, NOT_EQ, IS_NULL, NOT_NULL, LIKE, LOWERCASE_LIKE, IN, BETWEEN, GT, GTE, IS_EMPTY, IS_NOT_EMPTY, IS_TRUE,
    IS_FALSE, LT, LTE, NOT_LIKE, NOT_LOWERCASE_LIKE
}

enum class OrderDirection {
    ASC, DESC
}

sealed interface ValueOrColumn {
    data class Literal(val value: Any?) : ValueOrColumn
    data class ColumnRef(val path: String) : ValueOrColumn
}

class ColumnCondition<T>(
    val path: String,
    val op: CompareOp,
    val value: ValueOrColumn? = null
)

sealed interface PredicateNode<T>

class AndNode<T>(val children: List<PredicateNode<*>>) : PredicateNode<T>
class OrNode<T>(val children: List<PredicateNode<*>>) : PredicateNode<T>
class ConditionNode<T>(val condition: ColumnCondition<T>) : PredicateNode<T>
class JoinNode<P, C>(val path: String, val builder: ConditionBuilder<C>) : PredicateNode<P>
class FetchNode<P>(val path: String, val joinType: JoinType = JoinType.LEFT) : PredicateNode<P>

// DSL markers
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

    fun <R> fetch(prop: KProperty1<T, R>, joinType: JoinType = JoinType.LEFT) {
        nodes += FetchNode<T>(prop.name, joinType)
    }

    fun <R> fetch(path: String, joinType: JoinType = JoinType.LEFT) {
        nodes += FetchNode<T>(path, joinType)
    }

    internal fun toConditionBuilder(): ConditionBuilder<T> {
        val cb = ConditionBuilder<T>()
        cb.nodes.addAll(nodes)
        return cb
    }
}

@Suppress("TooManyFunctions")
@LogicalDsl
class LogicalBuilder<T> {
    internal val nodes = mutableListOf<PredicateNode<T>>()

    fun col(prop: KProperty1<out Any?, *>) = ColumnBuilder(prop.name)
    fun col(path: String) = ColumnBuilder(path)

    fun KProperty1<out Any?, *>.toCol() = ColumnBuilder(this.name)
    fun String.toCol() = ColumnBuilder(this)

    @Suppress("TooManyFunctions")
    @ColumnDsl
    inner class ColumnBuilder(internal val path: String) {

        private fun addCondition(op: CompareOp, value: Any?) {
            nodes += ConditionNode(ColumnCondition(path, op, ValueOrColumn.Literal(value)))
        }

        private fun addCondition(op: CompareOp, other: ColumnBuilder) {
            nodes += ConditionNode(ColumnCondition(path, op, ValueOrColumn.ColumnRef(other.path)))
        }

        infix fun eq(value: Any) = addCondition(CompareOp.EQ, value)
        infix fun eq(other: ColumnBuilder) = addCondition(CompareOp.EQ, other)

        infix fun notEq(value: Any) = addCondition(CompareOp.NOT_EQ, value)
        infix fun notEq(other: ColumnBuilder) = addCondition(CompareOp.NOT_EQ, other)

        fun isNull() = addCondition(CompareOp.IS_NULL, null)
        fun notNull() = addCondition(CompareOp.NOT_NULL, null)

        infix fun like(value: String) = addCondition(CompareOp.LIKE, value)

        infix fun notLike(value: String) = addCondition(CompareOp.NOT_LIKE, value)

        infix fun lowercaseLike(value: String) = addCondition(CompareOp.LOWERCASE_LIKE, value)

        infix fun notLowercaseLike(value: String) = addCondition(CompareOp.NOT_LOWERCASE_LIKE, value)

        infix fun inList(values: List<*>) = addCondition(CompareOp.IN, values)

        infix fun between(range: ClosedRange<out Comparable<*>>) = addCondition(CompareOp.BETWEEN, range)

        infix fun gt(value: Comparable<*>) = addCondition(CompareOp.GT, value)
        infix fun gt(other: ColumnBuilder) = addCondition(CompareOp.GT, other)

        infix fun gte(value: Comparable<*>) = addCondition(CompareOp.GTE, value)
        infix fun gte(other: ColumnBuilder) = addCondition(CompareOp.GTE, other)

        infix fun lt(value: Comparable<*>) = addCondition(CompareOp.LT, value)
        infix fun lt(other: ColumnBuilder) = addCondition(CompareOp.LT, other)

        infix fun lte(value: Comparable<*>) = addCondition(CompareOp.LTE, value)
        infix fun lte(other: ColumnBuilder) = addCondition(CompareOp.LTE, other)

        fun isEmpty() = addCondition(CompareOp.IS_EMPTY, null)
        fun isNotEmpty() = addCondition(CompareOp.IS_NOT_EMPTY, null)
        fun isTrue() = addCondition(CompareOp.IS_TRUE, null)
        fun isFalse() = addCondition(CompareOp.IS_FALSE, null)
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

    fun <R> fetch(prop: KProperty1<T, R>, joinType: JoinType = JoinType.LEFT) {
        nodes += FetchNode<T>(prop.name, joinType)
    }

    fun <R> fetch(path: String, joinType: JoinType = JoinType.LEFT) {
        nodes += FetchNode<T>(path, joinType)
    }

    internal fun toConditionBuilder(): ConditionBuilder<T> {
        val cb = ConditionBuilder<T>()
        cb.nodes.addAll(nodes)
        return cb
    }
}

class ConditionBuilder<T> {
    internal val nodes = mutableListOf<PredicateNode<T>>()
    private val joinCache = mutableMapOf<String, From<*, *>>()
    private val fetchCache = mutableSetOf<String>()

    internal fun build(from: From<*, *>, query: CriteriaQuery<*>, cb: CriteriaBuilder): Predicate? {
        if (nodes.isEmpty()) return null
        return cb.and(*nodes.mapNotNull { it.toPredicate(from, query, cb) }.toTypedArray())
    }

    private fun getOrCreateJoin(from: From<*, *>, path: String): From<*, *> {
        return joinCache.getOrPut(path) {
            val parts = path.split('.')
            var current: From<*, *> = from
            for (part in parts) {
                val existing = current.joins.find { it.attribute.name == part } as? From<*, *>
                current = existing ?: current.join<Any, Any>(part, JoinType.LEFT)
            }
            current
        }
    }

    private fun getOrCreateFetch(root: Root<*>, path: String, joinType: JoinType) {
        if (fetchCache.contains(path)) return
        fetchCache += path

        var current: FetchParent<*, *> = root
        val parts = path.split('.')
        for (part in parts) {
            val existing = current.fetches.find { it.attribute.name == part }
            current = existing ?: current.fetch<Any, Any>(part, joinType)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun PredicateNode<*>.toPredicate(
        from: From<*, *>,
        query: CriteriaQuery<*>,
        cb: CriteriaBuilder
    ): Predicate? = when (this) {
        is ConditionNode<*> -> condition.toPredicate(from, cb)
        is AndNode<*> -> cb.and(*children.mapNotNull { it.toPredicate(from, query, cb) }.toTypedArray())
        is OrNode<*> -> cb.or(*children.mapNotNull { it.toPredicate(from, query, cb) }.toTypedArray())
        is JoinNode<*, *> -> {
            val join = getOrCreateJoin(from, path)
            this.builder.build(join, query, cb)
        }
        is FetchNode<*> -> {
            if (
                from is Root<*>
                && query.resultType != java.lang.Long::class.java
                && query.resultType != Long::class.java
            ) {
                getOrCreateFetch(from, path, joinType)
            }
            null
        }
    }

    @Suppress("UNCHECKED_CAST", "CyclomaticComplexMethod", "LongMethod")
    private fun ColumnCondition<*>.toPredicate(
        from: From<*, *>,
        cb: CriteriaBuilder
    ): Predicate {
        val pathObj = resolvePath(from, path)

        fun resolveValueOrColumn(v: ValueOrColumn?): Any? = when (v) {
            is ValueOrColumn.Literal -> v.value
            is ValueOrColumn.ColumnRef -> resolvePath(from, v.path)
            null -> null
        }

        val rhs = resolveValueOrColumn(value)

        return when (op) {
            CompareOp.EQ -> cb.equal(pathObj, rhs)
            CompareOp.NOT_EQ -> cb.notEqual(pathObj, rhs)
            CompareOp.IS_NULL -> cb.isNull(pathObj)
            CompareOp.NOT_NULL -> cb.isNotNull(pathObj)
            CompareOp.LIKE -> cb.like(pathObj.`as`(String::class.java), rhs as String)
            CompareOp.NOT_LIKE -> cb.notLike(pathObj.`as`(String::class.java), rhs as String)
            CompareOp.LOWERCASE_LIKE -> cb.like(cb.lower(pathObj.`as`(String::class.java)), (rhs as String).lowercase())
            CompareOp.NOT_LOWERCASE_LIKE ->
                cb.notLike(cb.lower(pathObj.`as`(String::class.java)), (rhs as String).lowercase())

            CompareOp.IN -> cb.`in`(pathObj).apply { (rhs as List<*>).forEach { v -> value(v) } }
            CompareOp.BETWEEN -> cb.between(
                pathObj as Path<Comparable<Any>>,
                (rhs as ClosedRange<*>).start as Comparable<Any>,
                rhs.endInclusive as Comparable<Any>
            )

            CompareOp.GT -> when (rhs) {
                is Path<*> -> cb.greaterThan(
                    pathObj as Path<Comparable<Any>>,
                    rhs as Path<Comparable<Any>>
                )

                else -> cb.greaterThan(
                    pathObj as Path<Comparable<Any>>,
                    rhs as Comparable<Any>
                )
            }

            CompareOp.GTE -> when (rhs) {
                is Path<*> -> cb.greaterThanOrEqualTo(
                    pathObj as Path<Comparable<Any>>,
                    rhs as Path<Comparable<Any>>
                )

                else -> cb.greaterThanOrEqualTo(
                    pathObj as Path<Comparable<Any>>,
                    rhs as Comparable<Any>
                )
            }

            CompareOp.LT -> when (rhs) {
                is Path<*> -> cb.lessThan(
                    pathObj as Path<Comparable<Any>>,
                    rhs as Path<Comparable<Any>>
                )

                else -> cb.lessThan(
                    pathObj as Path<Comparable<Any>>,
                    rhs as Comparable<Any>
                )
            }

            CompareOp.LTE -> when (rhs) {
                is Path<*> -> cb.lessThanOrEqualTo(
                    pathObj as Path<Comparable<Any>>,
                    rhs as Path<Comparable<Any>>
                )

                else -> cb.lessThanOrEqualTo(
                    pathObj as Path<Comparable<Any>>,
                    rhs as Comparable<Any>
                )
            }

            CompareOp.IS_EMPTY -> cb.isEmpty(pathObj as Path<Collection<*>>)
            CompareOp.IS_NOT_EMPTY -> cb.isNotEmpty(pathObj as Path<Collection<*>>)
            CompareOp.IS_TRUE -> cb.isTrue(pathObj as Path<Boolean>)
            CompareOp.IS_FALSE -> cb.isFalse(pathObj as Path<Boolean>)
        }
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

        whereBuilder?.toConditionBuilder()?.build(root, query, cb)
    }
}

private fun resolvePath(from: From<*, *>, path: String): Path<*> {
    val parts = path.split('.')
    var current: From<*, *> = from
    for ((index, part) in parts.withIndex()) {
        if (index < parts.size - 1) {
            val existing = current.joins.find { it.attribute.name == part } as? From<*, *>
            current = existing ?: current.join<Any, Any>(part, JoinType.LEFT)
        } else {
            return current.get<Any>(part)
        }
    }
    return current
}

fun <T> buildSpecification(block: SpecificationBuilder<T>.() -> Unit): Specification<T> =
    SpecificationBuilder<T>().apply(block).toSpecification()

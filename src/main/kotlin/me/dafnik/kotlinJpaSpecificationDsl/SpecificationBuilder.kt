package me.dafnik.kotlinJpaSpecificationDsl

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

@DslMarker
annotation class SpecDsl

@SpecDsl
class ConditionBuilder<T> {
    private var nodes = mutableListOf<PredicateNode<T>>()

    /** Column by property reference */
    fun col(prop: KProperty1<out Any?, *>) = ColumnBuilder(prop.name)

    /** Column by string path */
    fun col(path: String) = ColumnBuilder(path)

    /** Join by property reference */
    fun <R> join(prop: KProperty1<T, R>, block: ConditionBuilder<R>.() -> Unit) {
        val joinName = prop.name
        val childBuilder = ConditionBuilder<R>().apply(block)
        nodes += JoinNode(joinName, childBuilder)
    }

    /** Join by string path */
    fun <R> join(path: String, block: ConditionBuilder<R>.() -> Unit) {
        val childBuilder = ConditionBuilder<R>().apply(block)
        nodes += JoinNode(path, childBuilder)
    }

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

    fun and(block: ConditionBuilder<T>.() -> Unit) {
        val childBuilder = ConditionBuilder<T>().apply(block)
        nodes += AndNode(childBuilder.nodes)
    }

    fun or(block: ConditionBuilder<T>.() -> Unit) {
        val childBuilder = ConditionBuilder<T>().apply(block)
        nodes += OrNode(childBuilder.nodes)
    }

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

@SpecDsl
class SpecBuilder<T> {
    var distinct: Boolean = false
    private var whereBuilder: ConditionBuilder<T>? = null
    private var groupByProps: List<String> = emptyList()
    private val orderByList: MutableList<Pair<String, OrderDirection>> = mutableListOf()

    fun where(block: ConditionBuilder<T>.() -> Unit) {
        whereBuilder = ConditionBuilder<T>().apply(block)
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

        whereBuilder?.build(root, cb)
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

fun <T> specBuilder(block: SpecBuilder<T>.() -> Unit): Specification<T> =
    SpecBuilder<T>().apply(block).toSpecification()

# Kotlin JPA Specification Builder

A **type-safe, Kotlin DSL** for building complex [Spring Data JPA
`Specification`](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#specifications) queries without
writing verbose Criteria API code.

This library provides a **fluent, composable, and readable** way to define dynamic queries with joins, grouping,
ordering, and nested conditions.

---

## ✨ Features

- **Type-safe** column references using `KProperty1`
- **String path** column references for dynamic joins
- **Nested conditions** with `and { ... }` and `or { ... }`
- **Joins** by property reference or string path (supports nested joins)
- **Column-to-column comparisons** (`eq`, `gt`, `lt`, etc.)
- **Group By** and **Order By** support
- **Null checks**, `like`, `lowercaseLike`, `in`, `between`, equality, and inequality operators
- **Collection checks** (`isEmpty`, `isNotEmpty`)
- **Boolean checks** (`isTrue`, `isFalse`)
- **Composable DSL** for building reusable query fragments
- **Distinct** queries
- **No boilerplate Criteria API code**

---

## 📦 Installation

Available via [JitPack](https://jitpack.io/#me.dafnik/kotlin-jpa-specification-builder).

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("me.dafnik:kotlin-jpa-specification-builder:{LATEST_VERSION}")
}
```

Check for the latest version [on GitHub](https://github.com/Dafnik/kotlin-jpa-specification-builder/releases).

---

## 🚀 Quick Start

### Example Entities

```kotlin
@Entity
data class User(
    @Id @GeneratedValue val id: Long? = null,
    val name: String?,
    val age: Int?,
    val active: Boolean = false,
    @ManyToOne val department: Department
)

@Entity
data class Department(
    @Id @GeneratedValue val id: Long? = null,
    val name: String,
    @OneToMany(mappedBy = "department") val users: List<User> = emptyList()
)
```

### Example Usage

```kotlin
import me.dafnik.JpaSpecificationBuilder.*

val spec = buildSpecification<User> {
    distinct = true

    where {
        and {
            col(User::name) eq "John Doe"
            col(User::age) between (25..40)
            col(User::active).isTrue()

            or {
                col("name") lowercaseLike "%smith%"
                col(User::id) inList listOf(1, 2, 3)
            }
        }

        join(User::department) {
            and {
                col(Department::name) eq "Engineering"
            }
        }
    }

    groupBy(User::department)
    orderBy(User::name, OrderDirection.ASC)
}
```

```kotlin
val users = userRepository.findAll(spec)
```

---

## 🛠 DSL Reference

### **Column Selection**

| Method                   | Description                             |
|--------------------------|-----------------------------------------|
| `col(User::name)`        | Column by property reference            |
| `col("department.name")` | Column by string path                   |
| `User::name.toCol()`     | Extension to convert property to column |
| `"name".toCol()`         | Extension to convert string to column   |

---

### **Comparison Operators**

All operators work with **literals** and **other columns**:

| Operator  | Literal Example                                  | Column-to-Column Example               |
|-----------|--------------------------------------------------|----------------------------------------|
| `eq`      | `col(User::age) eq 30`                           | `col("id") eq col("department.id")`    |
| `notEq`   | `col(User::name) notEq "John"`                   | `col("id") notEq col("department.id")` |
| `gt`      | `col(User::age) gt 18`                           | `col("id") gt col("department.id")`    |
| `gte`     | `col(User::age) gte 18`                          | `col("id") gte col("department.id")`   |
| `lt`      | `col(User::age) lt 65`                           | `col("department.id") lt col("id")`    |
| `lte`     | `col(User::age) lte 65`                          | `col("department.id") lte col("id")`   |
| `between` | `col(User::age) between (20..30)`                | *(literals only)*                      |
| `inList`  | `col(User::name) inList listOf("John", "Alice")` | —                                      |

---

### **String Matching**

| Operator           | Literal Example                             |
|--------------------|---------------------------------------------|
| `like`             | `col(User::name) like "%John%"`             |
| `notLike`          | `col(User::name) notLike "%John%"`          |
| `lowercaseLike`    | `col(User::name) lowercaseLike "%john%"`    |
| `notLowercaseLike` | `col(User::name) notLowercaseLike "%john%"` |

---

### **Null & Collection Checks**

| Method         | Example                               |
|----------------|---------------------------------------|
| `isNull()`     | `col(User::name).isNull()`            |
| `notNull()`    | `col(User::name).notNull()`           |
| `isEmpty()`    | `col(Department::users).isEmpty()`    |
| `isNotEmpty()` | `col(Department::users).isNotEmpty()` |

---

### **Boolean Checks**

| Method      | Example                       |
|-------------|-------------------------------|
| `isTrue()`  | `col(User::active).isTrue()`  |
| `isFalse()` | `col(User::active).isFalse()` |

---

### **Logical Operators**

```kotlin
and {
    col(User::age) gte 18
    col(User::active).isTrue()
}

or {
    col(User::name) eq "Alice"
    col(User::name) eq "Bob"
}

and {
    col(User::age).notNull()
    or {
        col(User::name) lowercaseLike "john%"
        col(User::name) lowercaseLike "alice%"
    }
}
```

---

### **Joins**

```kotlin
join(User::department) {
    and {
        col(Department::name) eq "Engineering"
    }
}

join<Department>("department") {
    and {
        col("name") like "%Eng%"
    }
}

join("department.manager") {
    and {
        col("name") eq "Alice"
    }
}
```

---

### **Grouping & Ordering**

```kotlin
groupBy(User::department, User::id, User::name)
orderBy(User::age, OrderDirection.ASC)
orderBy("department.name", OrderDirection.DESC)
```

---

### **Distinct**

```kotlin
distinct = true
```

---

## 📚 How It Works

The DSL builds a tree of `PredicateNode`s (`AndNode`, `OrNode`, `ConditionNode`, `JoinNode`) and converts them into JPA
`Predicate`s using the Criteria API.  
The `SpecificationBuilder` wraps this into a Spring Data `Specification<T>`.

---

## 📄 Cheat Sheet

```kotlin
// Column selection
col(User::name)
col("department.name")
User::name.toCol()
"name".toCol()

// Comparisons (literal or column)
eq, notEq, gt, gte, lt, lte, between, inList

// String matching
like, notLike, lowercaseLike, notLowercaseLike

// Null checks
isNull(), notNull()

// Collection checks
isEmpty(), isNotEmpty()

// Boolean checks
isTrue(), isFalse()

// Logical
and { ... }
or { ... }

// Joins
join(User::department) { ... }
join<Department>("department") { ... }
join("department.manager") { ... }

// Grouping & ordering
groupBy(User::department, User::id)
orderBy(User::name, OrderDirection.ASC)
orderBy("department.name", OrderDirection.DESC)

// Distinct
distinct = true
```

---

By [Dafnik](https://dafnik.me)

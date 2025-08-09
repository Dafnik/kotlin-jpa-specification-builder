# Kotlin JPA Specification Builder

A **type-safe, Kotlin DSL** for building complex [Spring Data JPA `Specification`](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#specifications) queries without writing verbose Criteria API code.

This library provides a **fluent, composable, and readable** way to define dynamic queries with joins, grouping, ordering, and nested conditions.

---

## ✨ Features

- **Type-safe** column references using `KProperty1`
- **Nested conditions** with `and { ... }` and `or { ... }`
- **Joins** by property reference or string path
- **Group By** and **Order By** support
- **Null checks**, `like`, `in`, equality, and inequality operators
- **Composable DSL** for building reusable query fragments
- **No boilerplate Criteria API code**

---

## 📦 Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.dafnik:kotlin-jpa-specification-builder:1.0.0")
}
```

---

## 🚀 Quick Start

### Example Entity

```kotlin
@Entity
data class User(
    @Id val id: Long,
    val username: String,
    val email: String?,
    @ManyToOne val department: Department
)

@Entity
data class Department(
    @Id val id: Long,
    val name: String
)
```

### Example Usage

```kotlin
import me.dafnik.JpaSpecificationBuilder.*

val spec = buildSpecification<User> {
    distinct = true

    where {
        col(User::username) eq "john_doe"
        col(User::email).notNull()

        or {
            col("email") like "example.com"
            col(User::id) inList listOf(1, 2, 3)
        }

        join(User::department) {
            col(Department::name) eq "Engineering"
        }
    }

    groupBy(User::department)
    orderBy(User::username, OrderDirection.ASC)
}
```

You can now use this `Specification` with any Spring Data JPA repository:

```kotlin
val users = userRepository.findAll(spec)
```

---

## 🛠 DSL Reference

### **Where Conditions**

| Method                              | Description                              |
|-------------------------------------|------------------------------------------|
| `col(prop)`                         | Select column by property reference      |
| `col("path")`                       | Select column by string path             |
| `eq(value)`                         | Equals                                   |
| `notEq(value)`                      | Not equals                               |
| `like(value)`                       | Case-insensitive LIKE                    |
| `isNull()`                          | IS NULL                                  |
| `notNull()`                         | IS NOT NULL                              |
| `inList(list)`                      | IN clause                                |

---

### **Logical Operators**

```kotlin
and {
    col(User::username) eq "john"
    col(User::email).notNull()
}

or {
    col(User::username) eq "alice"
    col(User::username) eq "bob"
}
```

---

### **Joins**

```kotlin
join(User::department) {
    col(Department::name) eq "Engineering"
}

join("department.manager") {
    col("name") eq "Alice"
}
```

---

### **Grouping & Ordering**

```kotlin
groupBy(User::department)
orderBy(User::username, OrderDirection.ASC)
orderBy("department.name", OrderDirection.DESC)
```

---

## 📚 How It Works

Internally, the DSL builds a tree of `PredicateNode`s (`AndNode`, `OrNode`, `ConditionNode`, `JoinNode`) and converts them into JPA `Predicate`s using the Criteria API.  
The `SpecificationBuilder` then wraps this into a Spring Data `Specification<T>`.

---

## ✅ Advantages Over Raw Criteria API

- **Readable**: No more deeply nested `cb.and(cb.equal(...))` calls
- **Composable**: Build reusable query fragments
- **Type-safe**: Catch column name typos at compile time
- **Flexible**: Supports both property references and string paths

---

By [Dafnik](https://dafnik.me)

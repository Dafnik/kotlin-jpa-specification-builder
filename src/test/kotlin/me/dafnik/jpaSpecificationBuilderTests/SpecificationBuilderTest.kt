package me.dafnik.jpaSpecificationBuilderTests

import jakarta.transaction.Transactional
import me.dafnik.JpaSpecificationBuilder.OrderDirection
import me.dafnik.JpaSpecificationBuilder.buildSpecification
import me.dafnik.jpaSpecificationBuilderTests.models.Department
import me.dafnik.jpaSpecificationBuilderTests.models.User
import me.dafnik.jpaSpecificationBuilderTests.repositories.DepartmentRepository
import me.dafnik.jpaSpecificationBuilderTests.repositories.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [TestConfig::class])
@Transactional
class SpecificationBuilderTest(
    @Autowired val userRepository: UserRepository,
    @Autowired val departmentRepository: DepartmentRepository
) {

    private lateinit var depEng: Department
    private lateinit var depHr: Department
    private lateinit var depSales: Department

    @BeforeEach
    fun setup() {
        depEng = departmentRepository.save(Department(name = "Engineering"))
        depHr = departmentRepository.save(Department(name = "HR"))
        depSales = departmentRepository.save(Department(name = "Sales"))

        val u1 = User(name = "John Doe", age = 30, department = depEng)
        val u2 = User(name = "Jane Smith", age = 25, department = depEng)
        val u3 = User(name = "Alice", age = 40, department = depHr)
        val u4 = User(name = "Bob", age = 50, department = depSales, previousActive = false)
        val u5 = User(name = null, age = null, department = depSales)

        userRepository.saveAll(listOf(u1, u2, u3, u4, u5))
    }

    @Nested
    inner class BasicComparisons {
        @Test
        fun `eq should match exact value`() {
            assertThat(
                userRepository.findAll(
                    buildSpecification<User> {
                        where {
                            and {
                                col(User::name) eq "John Doe"
                            }
                        }
                    }
                )
            ).extracting("name").containsExactly("John Doe")
        }

        @Test
        fun `neq should exclude exact value`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::name) notEq "John Doe"
                    }
                }
            })).noneMatch { it.name == "John Doe" }
        }

        @Test
        fun `like should not match`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::name) like "john"
                    }
                }
            })).hasSize(0)
        }

        @Test
        fun `like should match`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::name) like "%John%"
                    }
                }
            })).extracting("name").containsExactly("John Doe")
        }

        @Test
        fun `ilike should match case-insensitive`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::name) lowercaseLike "%john%"
                    }
                }
            })).extracting("name").containsExactly("John Doe")
        }

        @Test
        fun `like with null or blank should match all`() {
            val test1: String? = null
            val test2 = ""
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        test1?.let { col(User::name) like it }

                    }
                }
            })).hasSize(5)
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        test2.ifBlank { null }?.let { col(User::name) like it }
                    }
                }
            })).hasSize(5)
        }

        @Test
        fun `isNull should match null values`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::name).isNull()
                    }
                }
            })).allMatch { it.name == null }
        }

        @Test
        fun `notNull should match non-null values`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::name).notNull()
                    }
                }
            })).allMatch { it.name != null }
        }

        @Test
        fun `inList should match multiple values`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::name) inList listOf("John Doe", "Alice")
                    }
                }
            })).extracting("name").containsExactlyInAnyOrder("John Doe", "Alice")
        }

        @Test
        fun `notLike should exclude matching values`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::name) notLike "%John%"
                    }
                }
            })).noneMatch { it.name?.contains("John") == true }
        }

        @Test
        fun `notIlike should exclude case-insensitive matches`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::name) notLowercaseLike "%john%"
                    }
                }
            })).noneMatch { it.name?.lowercase()?.contains("john") == true }
        }

        @Test
        fun `between should match values in range`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::age) between (25..40)
                    }
                }
            })).extracting("age").containsExactlyInAnyOrder(25, 30, 40)
        }

        @Test
        fun `gt should match values greater than`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::age) gt 40
                    }
                }
            })).extracting("age").containsExactly(50)
        }

        @Test
        fun `gte should match values greater than or equal`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::age) gte 40
                    }
                }
            })).extracting("age").containsExactlyInAnyOrder(40, 50)
        }

        @Test
        fun `lt should match values less than`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::age) lt 30
                    }
                }
            })).extracting("age").containsExactly(25)
        }

        @Test
        fun `lte should match values less than or equal`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::age) lte 30
                    }
                }
            })).extracting("age").containsExactlyInAnyOrder(25, 30)
        }

        @Test
        fun `isEmpty should match empty collections`() {
            // Create a department with no users
            departmentRepository.save(Department(name = "EmptyDep"))

            assertThat(departmentRepository.findAll(buildSpecification<Department> {
                where {
                    and {
                        col(Department::users).isEmpty()
                    }
                }
            })).extracting("name").contains("EmptyDep")
        }

        @Test
        fun `isNotEmpty should match non-empty collections`() {
            assertThat(departmentRepository.findAll(buildSpecification<Department> {
                where {
                    and {
                        col(Department::users).isNotEmpty()
                    }
                }
            })).allMatch { it.users.isNotEmpty() }
        }

        @Test
        fun `isTrue should match boolean true`() {
            // Assuming User has a boolean field `active`
            userRepository.save(User(name = "Active", age = 20, department = depEng, active = true))
            userRepository.save(User(name = "Inactive", age = 22, department = depEng, active = false))

            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::active).isTrue()
                    }
                }
            })).allMatch { it.active }
        }

        @Test
        fun `isFalse should match boolean false`() {
            // Assuming User has a boolean field `active`
            userRepository.save(User(name = "Active", age = 20, department = depEng, active = true))
            userRepository.save(User(name = "Inactive", age = 22, department = depEng, active = false))

            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::active).isFalse()
                    }
                }
            })).allMatch { !it.active }
        }
    }

    @Nested
    inner class ColExtensionFunctions {
        @Test
        fun `eq should match exact value with extension function`() {
            assertThat(
                userRepository.findAll(
                    buildSpecification<User> {
                        where {
                            and {
                                User::name.toCol() eq "John Doe"
                            }
                        }
                    }
                )
            ).extracting("name").containsExactly("John Doe")
        }

        @Test
        fun `string path eq should work with extension function`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        "name".toCol() eq "John Doe"
                    }
                }
            })).extracting("name").containsExactly("John Doe")
        }
    }

    @Nested
    inner class StringPath {
        @Test
        fun `string path eq should work`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col("name") eq "John Doe"
                    }
                }
            })).extracting("name").containsExactly("John Doe")
        }

        @Test
        fun `string path join should work`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    join<Department>("department") {
                        and {
                            col("name") eq "Engineering"
                        }
                    }
                }
            })).allMatch { it.department?.name == "Engineering" }
        }

        @Test
        fun `string path nested join should work`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    join<Department>("department") {
                        and {
                            col("name") like "engineer"
                        }
                    }
                }
            })).allMatch { it.department?.name == "Engineering" }
        }
    }


    @Nested
    inner class Joins {
        @Test
        fun `join eq should match department by name`() {
            assertThat(userRepository.findAll(buildSpecification {
                where {
                    join(User::department) {
                        and {
                            col(Department::name) eq "Engineering"
                        }
                    }
                }
            })).allMatch { it.department?.name == "Engineering" }
        }

        @Test
        fun `join like should match department by name`() {
            assertThat(userRepository.findAll(buildSpecification {
                where {
                    join(User::department) {
                        and {
                            col(Department::name) like "engineer"
                        }
                    }
                }
            })).allMatch { it.department?.name == "Engineering" }
        }
    }

    @Nested
    inner class LogicalCombinations {
        @Test
        fun `and combination should match both conditions`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::age) eq 30
                        col(User::name) like "John%"
                    }
                }
            })).extracting("name").containsExactly("John Doe")
        }

        @Test
        fun `multiple and blocks in where`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::age).notNull()
                    }
                    and {
                        col(User::name) like "John%"
                    }
                }
            })).extracting("name").containsExactly("John Doe")
        }

        @Test
        fun `col in where without and`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::age) eq 30
                    }
                }
            })).extracting("age").containsExactly(30)
        }

        @Test
        fun `or combination should match either condition`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    or {
                        col(User::age) eq 50
                        col(User::name) lowercaseLike "%alice"
                    }
                }
            })).extracting("name").containsExactlyInAnyOrder("Alice", "Bob")
        }

        @Test
        fun `nested and-or combination`() {
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::age).notNull()
                        or {
                            col(User::name) lowercaseLike "john%"
                            col(User::name) lowercaseLike "alice%"
                        }
                    }
                }
            })).extracting("name").containsExactlyInAnyOrder("John Doe", "Alice")
        }
    }

    @Nested
    inner class GroupByOrderBy {
        @Test
        fun `groupBy should not break query`() {
            assertThat(userRepository.findAll(buildSpecification {
                groupBy(User::department, User::id, User::name, User::age)
            })).isNotEmpty()
        }

        @Test
        fun `orderBy asc should sort results`() {
            assertThat(userRepository.findAll(buildSpecification {
                orderBy(User::age, OrderDirection.ASC)
            }).map { it.age }).isSortedAccordingTo(nullsFirst(naturalOrder()))
        }

        @Test
        fun `orderBy desc should sort results`() {
            assertThat(userRepository.findAll(buildSpecification {
                orderBy(User::age, OrderDirection.DESC)
            }).map { it.age }).isSortedAccordingTo(nullsLast(reverseOrder()))
        }
    }

    // --- DISTINCT ---

    @Test
    fun `distinct should remove duplicates`() {
        assertThat(userRepository.findAll(buildSpecification {
            distinct = true
            groupBy(User::department, User::id, User::name, User::age)
        })).hasSize(5)
    }

    // --- EMPTY SPEC ---

    @Test
    fun `empty spec should match all`() {
        assertThat(userRepository.findAll(buildSpecification<User> {})).hasSize(5)
    }

    @Nested
    inner class ColumnToColumnComparison {
        @Test
        fun `eq should match when two columns are equal`() {
            // Users where department.id == department.id (always true)
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col("department.id") eq col("department.id")
                    }
                }
            })).hasSize(5)
        }

        @Test
        fun `notEq should match when two columns are not equal`() {
            // Users where department.id != id (should be all, since user.id != department.id)
            assertThat(userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::active) notEq col(User::previousActive)
                    }
                }
            })).hasSize(1)
        }

        @Test
        fun `gt should match when one column is greater than another`() {
            // Compare user.id > department.id (depends on generated IDs)
            val results = userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::id) gt col("department.id")
                    }
                }
            })
            assertThat(results).allMatch { (it.id ?: 0) > (it.department?.id ?: 0) }
        }

        @Test
        fun `gte should match when one column is greater than or equal to another`() {
            val results = userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col(User::id) gte col("department.id")
                    }
                }
            })
            assertThat(results).allMatch { (it.id ?: 0) >= (it.department?.id ?: 0) }
        }

        @Test
        fun `lt should match when one column is less than another`() {
            val results = userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col("department.id") lt col("id")
                    }
                }
            })
            assertThat(results).allMatch { (it.department?.id ?: 0) < (it.id ?: 0) }
        }

        @Test
        fun `lte should match when one column is less than or equal to another`() {
            val results = userRepository.findAll(buildSpecification<User> {
                where {
                    and {
                        col("department.id") lte col("id")
                    }
                }
            })
            assertThat(results).allMatch { (it.department?.id ?: 0) <= (it.id ?: 0) }
        }
    }

}

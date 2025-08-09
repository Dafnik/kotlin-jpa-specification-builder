package com.github.dafnik.jpaSpecificationBuilderTests

import jakarta.transaction.Transactional
import com.github.dafnik.JpaSpecificationBuilder.OrderDirection
import com.github.dafnik.JpaSpecificationBuilder.buildSpecification
import com.github.dafnik.jpaSpecificationBuilderTests.models.Department
import com.github.dafnik.jpaSpecificationBuilderTests.models.User
import com.github.dafnik.jpaSpecificationBuilderTests.repositories.DepartmentRepository
import com.github.dafnik.jpaSpecificationBuilderTests.repositories.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
        val u4 = User(name = "Bob", age = 50, department = depSales)
        val u5 = User(name = null, age = null, department = depSales)

        userRepository.saveAll(listOf(u1, u2, u3, u4, u5))
    }

    // --- BASIC COMPARISONS ---

    @Test
    fun `eq should match exact value`() {
        assertThat(
            userRepository.findAll(
                buildSpecification<User> {
                    where { col(User::name) eq "John Doe" }
                }
            )
        ).extracting("name").containsExactly("John Doe")
    }

    @Test
    fun `neq should exclude exact value`() {
        assertThat(userRepository.findAll(buildSpecification<User> {
            where { col(User::name) notEq "John Doe" }
        })).noneMatch { it.name == "John Doe" }
    }

    @Test
    fun `like should match case-insensitive`() {
        assertThat(userRepository.findAll(buildSpecification<User> {
            where { col(User::name) like "john" }
        })).extracting("name").containsExactly("John Doe")
    }

    @Test
    fun `like with null or blank should match all`() {
        assertThat(userRepository.findAll(buildSpecification<User> { where { col(User::name) like null } })).hasSize(5)
        assertThat(userRepository.findAll(buildSpecification<User> { where { col(User::name) like "" } })).hasSize(5)
    }

    @Test
    fun `isNull should match null values`() {
        assertThat(userRepository.findAll(buildSpecification<User> {
            where { col(User::name).isNull() }
        })).allMatch { it.name == null }
    }

    @Test
    fun `notNull should match non-null values`() {
        assertThat(userRepository.findAll(buildSpecification<User> {
            where { col(User::name).notNull() }
        })).allMatch { it.name != null }
    }

    @Test
    fun `inList should match multiple values`() {
        assertThat(userRepository.findAll(buildSpecification<User> {
            where { col(User::name) inList listOf("John Doe", "Alice") }
        })).extracting("name").containsExactlyInAnyOrder("John Doe", "Alice")
    }

    // --- STRING PATH TESTS ---

    @Test
    fun `string path eq should work`() {
        assertThat(userRepository.findAll(buildSpecification<User> {
            where { col("name") eq "John Doe" }
        })).extracting("name").containsExactly("John Doe")
    }

    @Test
    fun `string path join should work`() {
        assertThat(userRepository.findAll(buildSpecification<User> {
            where {
                join<Department>("department") {
                    col("name") eq "Engineering"
                }
            }
        })).allMatch { it.department?.name == "Engineering" }
    }

    @Test
    fun `string path nested join should work`() {
        assertThat(userRepository.findAll(buildSpecification<User> {
            where {
                join<Department>("department") {
                    col("name") like "engineer"
                }
            }
        })).allMatch { it.department?.name == "Engineering" }
    }

    // --- JOINS ---

    @Test
    fun `join eq should match department by name`() {
        assertThat(userRepository.findAll(buildSpecification {
            where {
                join(User::department) {
                    col(Department::name) eq "Engineering"
                }
            }
        })).allMatch { it.department?.name == "Engineering" }
    }

    @Test
    fun `join like should match department by name`() {
        assertThat(userRepository.findAll(buildSpecification {
            where {
                join(User::department) {
                    col(Department::name) like "engineer"
                }
            }
        })).allMatch { it.department?.name == "Engineering" }
    }

    // --- LOGICAL COMBINATIONS ---

    @Test
    fun `and combination should match both conditions`() {
        assertThat(userRepository.findAll(buildSpecification<User> {
            where {
                and {
                    col(User::age) eq 30
                    col(User::name) like "john"
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
                    col(User::name) like "john"
                }
            }
        })).extracting("name").containsExactly("John Doe")
    }

    @Test
    fun `col in where without and`() {
        assertThat(userRepository.findAll(buildSpecification<User> {
            where {
                col(User::age) eq 30
            }
        })).extracting("age").containsExactly(30)
    }

    @Test
    fun `or combination should match either condition`() {
        assertThat(userRepository.findAll(buildSpecification<User> {
            where {
                or {
                    col(User::age) eq 50
                    col(User::name) like "alice"
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
                        col(User::name) like "john"
                        col(User::name) like "alice"
                    }
                }
            }
        })).extracting("name").containsExactlyInAnyOrder("John Doe", "Alice")
    }

    // --- GROUP BY & ORDER BY ---

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
}

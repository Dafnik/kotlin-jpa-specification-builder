package me.dafnik.test

import jakarta.transaction.Transactional
import me.dafnik.kotlinJpaSpecificationDsl.OrderDirection
import me.dafnik.kotlinJpaSpecificationDsl.specBuilder
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
                specBuilder<User> {
                    where { col(User::name) eq "John Doe" }
                }
            )
        ).extracting("name").containsExactly("John Doe")
    }

    @Test
    fun `neq should exclude exact value`() {
        val spec = specBuilder<User> {
            where { col(User::name) notEq "John Doe" }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).noneMatch { it.name == "John Doe" }
    }

    @Test
    fun `like should match case-insensitive`() {
        val spec = specBuilder<User> {
            where { col(User::name) like "john" }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).extracting("name").containsExactly("John Doe")
    }

    @Test
    fun `like with null or blank should match all`() {
        val spec1 = specBuilder<User> { where { col(User::name) like null } }
        val spec2 = specBuilder<User> { where { col(User::name) like "" } }
        assertThat(userRepository.findAll(spec1)).hasSize(5)
        assertThat(userRepository.findAll(spec2)).hasSize(5)
    }

    @Test
    fun `isNull should match null values`() {
        val spec = specBuilder<User> {
            where { col(User::name).isNull() }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).allMatch { it.name == null }
    }

    @Test
    fun `notNull should match non-null values`() {
        val spec = specBuilder<User> {
            where { col(User::name).notNull() }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).allMatch { it.name != null }
    }

    @Test
    fun `inList should match multiple values`() {
        val spec = specBuilder<User> {
            where { col(User::name) inList listOf("John Doe", "Alice") }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).extracting("name").containsExactlyInAnyOrder("John Doe", "Alice")
    }

    // --- STRING PATH TESTS ---

    @Test
    fun `string path eq should work`() {
        val spec = specBuilder<User> {
            where { col("name") eq "John Doe" }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).extracting("name").containsExactly("John Doe")
    }

    @Test
    fun `string path join should work`() {
        val spec = specBuilder<User> {
            where {
                join<Department>("department") {
                    col("name") eq "Engineering"
                }
            }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).allMatch { it.department?.name == "Engineering" }
    }

    @Test
    fun `string path nested join should work`() {
        val spec = specBuilder<User> {
            where {
                join<Department>("department") {
                    col("name") like "engineer"
                }
            }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).allMatch { it.department?.name == "Engineering" }
    }

    // --- JOINS ---

    @Test
    fun `join eq should match department by name`() {
        val spec = specBuilder {
            where {
                join(User::department) {
                    col(Department::name) eq "Engineering"
                }
            }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).allMatch { it.department?.name == "Engineering" }
    }

    @Test
    fun `join like should match department by name`() {
        val spec = specBuilder {
            where {
                join(User::department) {
                    col(Department::name) like "engineer"
                }
            }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).allMatch { it.department?.name == "Engineering" }
    }

    // --- LOGICAL COMBINATIONS ---

    @Test
    fun `and combination should match both conditions`() {
        val spec = specBuilder<User> {
            where {
                and {
                    col(User::age) eq 30
                    col(User::name) like "john"
                }
            }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).extracting("name").containsExactly("John Doe")
    }

    @Test
    fun `multiple and blocks in where`() {
        val spec = specBuilder<User> {
            where {
                and {
                    col(User::age).notNull()
                }
                and {
                    col(User::name) like "john"
                }
            }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).extracting("name").containsExactly("John Doe")
    }

    @Test
    fun `col in where without and`() {
        val spec = specBuilder<User> {
            where {
                col(User::age) eq 30
            }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).extracting("age").containsExactly(30)
    }

    @Test
    fun `or combination should match either condition`() {
        val spec = specBuilder<User> {
            where {
                or {
                    col(User::age) eq 50
                    col(User::name) like "alice"
                }
            }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).extracting("name").containsExactlyInAnyOrder("Alice", "Bob")
    }

    @Test
    fun `nested and-or combination`() {
        val spec = specBuilder<User> {
            where {
                and {
                    col(User::age).notNull()
                    or {
                        col(User::name) like "john"
                        col(User::name) like "alice"
                    }
                }
            }
        }
        val results = userRepository.findAll(spec)
        assertThat(results).extracting("name").containsExactlyInAnyOrder("John Doe", "Alice")
    }

    // --- GROUP BY & ORDER BY ---

    @Test
    fun `groupBy should not break query`() {
        val spec = specBuilder {
            groupBy(User::department, User::id, User::name, User::age)
        }
        val results = userRepository.findAll(spec)
        assertThat(results).isNotEmpty()
    }

    @Test
    fun `orderBy asc should sort results`() {
        val spec = specBuilder {
            orderBy(User::age, OrderDirection.ASC)
        }
        val results = userRepository.findAll(spec)
        assertThat(results.map { it.age }).isSortedAccordingTo(nullsFirst(naturalOrder()))
    }

    @Test
    fun `orderBy desc should sort results`() {
        val spec = specBuilder {
            orderBy(User::age, OrderDirection.DESC)
        }
        val results = userRepository.findAll(spec)
        assertThat(results.map { it.age }).isSortedAccordingTo(nullsLast(reverseOrder()))
    }

    // --- DISTINCT ---

    @Test
    fun `distinct should remove duplicates`() {
        val spec = specBuilder {
            distinct = true
            groupBy(User::department, User::id, User::name, User::age)
        }
        val results = userRepository.findAll(spec)
        assertThat(results).hasSize(5)
    }

    // --- EMPTY SPEC ---

    @Test
    fun `empty spec should match all`() {
        val spec = specBuilder<User> {}
        assertThat(userRepository.findAll(spec)).hasSize(5)
    }
}

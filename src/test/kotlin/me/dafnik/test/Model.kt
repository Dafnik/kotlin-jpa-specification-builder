package me.dafnik.test

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

@Entity
data class Department(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val name: String? = null
)

@Entity
@Table(name = "app_users")
data class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val name: String? = null,
    val age: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    val department: Department? = null
)

@Repository
interface UserRepository : JpaRepository<User, Long>, JpaSpecificationExecutor<User>

@Repository
interface DepartmentRepository : JpaRepository<Department, Long>, JpaSpecificationExecutor<Department>

@SpringBootApplication
class TestConfig

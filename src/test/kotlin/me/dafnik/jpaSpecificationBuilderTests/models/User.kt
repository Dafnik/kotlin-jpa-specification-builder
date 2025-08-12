package me.dafnik.jpaSpecificationBuilderTests.models

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "app_users")
data class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val name: String? = null,
    val age: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    val department: Department? = null,

    val active: Boolean = true,
    val previousActive: Boolean = true,
)

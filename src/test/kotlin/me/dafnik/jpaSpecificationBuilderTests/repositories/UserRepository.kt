package me.dafnik.jpaSpecificationBuilderTests.repositories

import me.dafnik.jpaSpecificationBuilderTests.models.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long>, JpaSpecificationExecutor<User>

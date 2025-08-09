package me.dafnik.jpaSpecificationBuilderTests.repositories

import me.dafnik.jpaSpecificationBuilderTests.models.Department
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

@Repository
interface DepartmentRepository : JpaRepository<Department, Long>, JpaSpecificationExecutor<Department>

package com.github.dafnik.jpaSpecificationBuilderTests

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class DslCompileTimeTest {
    @Test
    fun `invalid DSL usage should fail compilation`() {
        val source = SourceFile.kotlin(
            "InvalidDsl.kt", """
            import com.github.dafnik.JpaSpecificationBuilder.*
            import com.github.dafnik.jpaSpecificationBuilderTests.models.User

            fun test() {
                buildSpecification<User> {
                    where {
                        col(User::name) eq "John" // ❌ should not compile
                    }
                }
            }
        """
        )

        val result = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        assertThat(result.exitCode)
            .isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    }

    @Test
    fun `valid DSL usage should compile`() {
        val source = SourceFile.kotlin(
            "ValidDsl.kt", """
            import com.github.dafnik.JpaSpecificationBuilder.*
            import com.github.dafnik.jpaSpecificationBuilderTests.models.User

            fun test() {
                buildSpecification<User> {
                    where {
                        and { col(User::name) eq "John" }
                    }
                }
            }
        """
        )

        val result = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        assertThat(result.exitCode)
            .isEqualTo(KotlinCompilation.ExitCode.OK)
    }
}

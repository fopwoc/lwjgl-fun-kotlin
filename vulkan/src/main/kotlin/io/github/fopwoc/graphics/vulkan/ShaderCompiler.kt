package io.github.fopwoc.graphics.vulkan

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import org.lwjgl.util.shaderc.Shaderc
import org.pmw.tinylog.Logger


object ShaderCompiler {

    fun compileShaderIfChanged(glsShaderFile: String, shaderType: Int) {
        val compiledShader: ByteArray
        try {
            val glslFile = File(glsShaderFile)
            val spvFile = File("$glsShaderFile.spv")
            if (!spvFile.exists() || glslFile.lastModified() > spvFile.lastModified()) {
                Logger.debug("Compiling [{}] to [{}]", glslFile.path, spvFile.path)
                val shaderCode = String(Files.readAllBytes(glslFile.toPath()))
                compiledShader = compileShader(shaderCode, shaderType)
                Files.write(spvFile.toPath(), compiledShader)
            } else {
                Logger.debug(
                    "Shader [{}] already compiled. Loading compiled version: [{}]",
                    glslFile.path,
                    spvFile.path
                )
            }
        } catch (excp: IOException) {
            throw RuntimeException(excp)
        }
    }

    fun compileShader(shaderCode: String, shaderType: Int): ByteArray {
        var compiler: Long = 0
        var options: Long = 0
        val compiledShader: ByteArray
        try {
            compiler = Shaderc.shaderc_compiler_initialize()
            options = Shaderc.shaderc_compile_options_initialize()
            val result: Long = Shaderc.shaderc_compile_into_spv(
                compiler,
                shaderCode,
                shaderType,
                "shader.glsl",
                "main",
                options
            )
            if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                throw java.lang.RuntimeException(
                    "Shader compilation failed: " + Shaderc.shaderc_result_get_error_message(
                        result
                    )
                )
            }
            val buffer: ByteBuffer = Shaderc.shaderc_result_get_bytes(result)!!
            compiledShader = ByteArray(buffer.remaining())
            buffer.get(compiledShader)
        } finally {
            Shaderc.shaderc_compile_options_release(options)
            Shaderc.shaderc_compiler_release(compiler)
        }
        return compiledShader
    }
}

package com.akatsuki.android

import com.akatsuki.android.domain.model.PathTraversalSecurityException
import com.akatsuki.android.domain.usecase.ValidatePathSecurityUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PathTraversalGuardTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var simulatedStorageRoot: File
    private lateinit var validatePathSecurity: ValidatePathSecurityUseCase

    @Before
    fun setUp() {
        simulatedStorageRoot = tempFolder.newFolder("storage", "emulated", "0")
        validatePathSecurity = ValidatePathSecurityUseCase {
            listOf(simulatedStorageRoot.canonicalPath)
        }
    }

    @Test
    fun `acepta ruta canonica dentro del volumen autorizado`() {
        val downloadDir = File(simulatedStorageRoot, "Download").apply { mkdirs() }
        val resolved = validatePathSecurity(downloadDir.path)
        assertEquals(downloadDir.canonicalPath, resolved.path)
    }

    @Test(expected = PathTraversalSecurityException::class)
    fun `rechaza intento de Path Traversal con doble punto hacia directorio padre`() {
        val attackPath = "${simulatedStorageRoot.path}/Download/../../etc/passwd"
        validatePathSecurity(attackPath)
    }

    @Test(expected = PathTraversalSecurityException::class)
    fun `rechaza acceso a directorios privados del sistema fuera del volumen autorizado`() {
        validatePathSecurity("/data/data/com.akatsuki.android/files/keystore")
    }

    @Test
    fun `resuelve rutas relativas dentro de la raiz autorizada`() {
        val resolved = validatePathSecurity("DCIM/Camera")
        assertTrue(resolved.path.startsWith(simulatedStorageRoot.canonicalPath))
    }
}

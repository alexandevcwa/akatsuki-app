package com.akatsuki.android.domain.usecase

import com.akatsuki.android.domain.model.InvalidRequestParameterException
import com.akatsuki.android.domain.model.PathTraversalSecurityException
import java.io.File

/**
 * Caso de Uso de Seguridad (Capa 4): Prevención de Path Traversal en Android.
 *
 * Normaliza toda ruta recibida usando File(requestedPath).canonicalFile y verifica
 * que comience estrictamente con uno de los volúmenes autorizados (por defecto /storage/emulated/0),
 * rechazando cualquier intento de usar "../" o acceder a rutas del sistema (/data, /proc, /system)
 * con 403 Forbidden (PathTraversalSecurityException).
 */
class ValidatePathSecurityUseCase(
    private val allowedRootsProvider: () -> List<String>
) {
    operator fun invoke(requestedPath: String): File {
        val trimmed = requestedPath.trim()
        if (trimmed.isEmpty()) {
            throw InvalidRequestParameterException("El parámetro 'path' es requerido y no puede estar vacío.")
        }

        // Rechazo inmediato de secuencias de escape explícitas o bytes nulos
        if (trimmed.contains('\u0000')) {
            throw PathTraversalSecurityException("Ruta bloqueada por contener caracteres nulos ilegales.")
        }

        val rawSegments = trimmed.replace('\\', '/').split('/')
        if (rawSegments.any { it == ".." }) {
            throw PathTraversalSecurityException(
                "Acceso denegado (403 Forbidden): Se detectó secuencia de Path Traversal ('..') en '$requestedPath'."
            )
        }

        val candidateFile = if (trimmed.startsWith("/")) {
            File(trimmed)
        } else {
            val defaultRoot = allowedRootsProvider().firstOrNull() ?: "/storage/emulated/0"
            File(defaultRoot, trimmed)
        }

        val canonicalTarget = runCatching { candidateFile.canonicalFile }.getOrElse {
            throw PathTraversalSecurityException("No se pudo normalizar la ruta solicitada: '$requestedPath'")
        }

        val canonicalAllowedRoots = allowedRootsProvider().mapNotNull { root ->
            runCatching { File(root).canonicalPath }.getOrNull()
        }

        val targetPath = canonicalTarget.path
        val isInsideAllowedVolume = canonicalAllowedRoots.any { allowedRoot ->
            targetPath == allowedRoot || targetPath.startsWith("$allowedRoot/")
        }

        if (!isInsideAllowedVolume) {
            throw PathTraversalSecurityException(
                "Acceso denegado (403 Forbidden): La ruta '$targetPath' está fuera de los volúmenes autorizados $canonicalAllowedRoots."
            )
        }

        return canonicalTarget
    }
}

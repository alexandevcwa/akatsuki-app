package com.akatsuki.android.infrastructure.crypto

import com.akatsuki.android.domain.model.TlsCertificateBundle
import com.akatsuki.android.domain.repository.TlsCertificateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Capa 1 y Capa 2 de Seguridad:
 * - Genera un par de claves criptográficas (ECDSA P-256 con fallback seguro a RSA-2048 para
 *   máxima compatibilidad con el SslContext de Netty en cualquier ROM Android) y un Certificado
 *   X.509 v3 Autofirmado con validez de 10 años.
 * - Almacena el certificado en un KeyStore PKCS12 privado de la aplicación.
 * - Calcula la huella SHA-256 sobre el certificado DER en formato hexadecimal separado por
 *   dos puntos (ej. "9F:86:D0:81:..."), requerido por el verificador TOFU de Rust en akatsuki-desktop.
 */
class TlsCertificateManager(
    private val keyStoreDir: File
) : TlsCertificateRepository {

    companion object {
        private const val KEYSTORE_FILE_NAME = "akatsuki_node_tls.p12"
        private const val KEY_ALIAS = "akatsuki_https_node"
        private const val KEYSTORE_PASSWORD_SECRET = "akatsuki_shinobi_tls_secret"
        private const val VALIDITY_DAYS = 3650L // 10 años

        private fun passwordChars(): CharArray = KEYSTORE_PASSWORD_SECRET.toCharArray()

        init {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            } else {
                // Asegurar que el proveedor completo de BouncyCastle tenga prioridad si el de Android está recortado
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                Security.addProvider(BouncyCastleProvider())
            }
        }

        /**
         * Calcula el hash SHA-256 de los bytes DER del certificado X.509 y lo formatea en
         * hexadecimal mayúsculas separado por ':' (ej. "9F:86:D0:81:...").
         */
        fun computeSha256Fingerprint(certificate: X509Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            return digest.joinToString(":") { byte -> "%02X".format(byte) }
        }
    }

    private val mutex = Mutex()
    @Volatile
    private var cachedBundle: TlsCertificateBundle? = null

    override suspend fun getOrCreateCertificateBundle(): TlsCertificateBundle = mutex.withLock {
        cachedBundle?.let { return it.copy(keyStorePassword = passwordChars()) }

        withContext(Dispatchers.IO) {
            if (!keyStoreDir.exists()) {
                keyStoreDir.mkdirs()
            }
            val ksFile = File(keyStoreDir, KEYSTORE_FILE_NAME)
            if (ksFile.exists()) {
                runCatching { loadExistingKeyStore(ksFile) }.getOrNull()?.let { loaded ->
                    cachedBundle = loaded
                    return@withContext loaded.copy(keyStorePassword = passwordChars())
                }
            }
            val generated = generateAndPersistBundle(ksFile)
            cachedBundle = generated
            generated.copy(keyStorePassword = passwordChars())
        }
    }

    override suspend fun regenerateCertificateBundle(): TlsCertificateBundle = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!keyStoreDir.exists()) {
                keyStoreDir.mkdirs()
            }
            val ksFile = File(keyStoreDir, KEYSTORE_FILE_NAME)
            if (ksFile.exists()) {
                ksFile.delete()
            }
            val generated = generateAndPersistBundle(ksFile)
            cachedBundle = generated
            generated.copy(keyStorePassword = passwordChars())
        }
    }

    private fun loadExistingKeyStore(ksFile: File): TlsCertificateBundle {
        val keyStore = KeyStore.getInstance("PKCS12")
        FileInputStream(ksFile).use { fis ->
            keyStore.load(fis, passwordChars())
        }
        val cert = keyStore.getCertificate(KEY_ALIAS) as? X509Certificate
            ?: throw IllegalStateException("Certificado X.509 no encontrado bajo alias $KEY_ALIAS")
        val fingerprint = computeSha256Fingerprint(cert)
        val cleanKeyAlg = when (cert.publicKey.algorithm.uppercase()) {
            "EC", "ECDSA" -> "ECDSA P-256"
            "RSA" -> "RSA-2048"
            else -> cert.publicKey.algorithm.uppercase()
        }
        return TlsCertificateBundle(
            keyStore = keyStore,
            keyAlias = KEY_ALIAS,
            keyStorePassword = passwordChars(),
            fingerprintSha256 = fingerprint,
            algorithmDescription = "$cleanKeyAlg • TLS 1.3"
        )
    }

    private fun generateAndPersistBundle(ksFile: File): TlsCertificateBundle {
        val secureRandom = SecureRandom()

        // Intentar ECDSA P-256 (secp256r1) según especificación 2.1; fallback a RSA-2048 si el KeyStore del dispositivo lo requiere
        val (keyPair, signatureAlgorithm, algLabel) = runCatching {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
            Triple(kpg.generateKeyPair(), "SHA256withECDSA", "ECDSA P-256 • TLS 1.3")
        }.getOrElse {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048, secureRandom)
            Triple(kpg.generateKeyPair(), "SHA256withRSA", "RSA-2048 • TLS 1.3")
        }

        val certificate = buildSelfSignedX509Certificate(
            keyPair = keyPair,
            signatureAlgorithm = signatureAlgorithm,
            secureRandom = secureRandom
        )

        val keyStore = runCatching {
            createKeyStoreWithCert("PKCS12", keyPair, certificate)
        }.getOrElse {
            // Si PKCS12 de Android rechaza EC en alguna versión de BouncyCastle, usar BKS o RSA
            val rsaKpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, secureRandom) }
            val rsaPair = rsaKpg.generateKeyPair()
            val rsaCert = buildSelfSignedX509Certificate(rsaPair, "SHA256withRSA", secureRandom)
            return persistAndReturnBundle(
                keyStore = createKeyStoreWithCert("PKCS12", rsaPair, rsaCert),
                certificate = rsaCert,
                ksFile = ksFile,
                algLabel = "RSA-2048 (TLS 1.3)"
            )
        }

        return persistAndReturnBundle(
            keyStore = keyStore,
            certificate = certificate,
            ksFile = ksFile,
            algLabel = algLabel
        )
    }

    private fun createKeyStoreWithCert(
        type: String,
        keyPair: KeyPair,
        certificate: X509Certificate
    ): KeyStore {
        val keyStore = KeyStore.getInstance(type)
        keyStore.load(null, passwordChars())
        keyStore.setKeyEntry(
            KEY_ALIAS,
            keyPair.private,
            passwordChars(),
            arrayOf(certificate)
        )
        return keyStore
    }

    private fun persistAndReturnBundle(
        keyStore: KeyStore,
        certificate: X509Certificate,
        ksFile: File,
        algLabel: String
    ): TlsCertificateBundle {
        FileOutputStream(ksFile).use { fos ->
            keyStore.store(fos, passwordChars())
        }
        val fingerprint = computeSha256Fingerprint(certificate)
        return TlsCertificateBundle(
            keyStore = keyStore,
            keyAlias = KEY_ALIAS,
            keyStorePassword = passwordChars(),
            fingerprintSha256 = fingerprint,
            algorithmDescription = algLabel
        )
    }

    private fun buildSelfSignedX509Certificate(
        keyPair: KeyPair,
        signatureAlgorithm: String,
        secureRandom: SecureRandom
    ): X509Certificate {
        val nowMillis = System.currentTimeMillis()
        val notBefore = Date(nowMillis - TimeUnit.HOURS.toMillis(1))
        val notAfter = Date(nowMillis + TimeUnit.DAYS.toMillis(VALIDITY_DAYS))
        val serialNumber = BigInteger(128, secureRandom).abs()

        val issuerAndSubject = X500Name("CN=AkatsukiNode.local, OU=Shinobi Secure Transfer, O=Akatsuki Network")
        val certBuilder = JcaX509v3CertificateBuilder(
            issuerAndSubject,
            serialNumber,
            notBefore,
            notAfter,
            issuerAndSubject,
            keyPair.public
        )

        certBuilder.addExtension(
            Extension.basicConstraints,
            true,
            BasicConstraints(false)
        )
        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment or KeyUsage.keyAgreement)
        )
        val subjectAltNames = GeneralNames(
            arrayOf(
                GeneralName(GeneralName.dNSName, "akatsuki.local"),
                GeneralName(GeneralName.dNSName, "localhost"),
                GeneralName(GeneralName.iPAddress, "127.0.0.1")
            )
        )
        certBuilder.addExtension(
            Extension.subjectAlternativeName,
            false,
            subjectAltNames
        )

        val signer = JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        return JcaX509CertificateConverter().getCertificate(certHolder)
    }
}

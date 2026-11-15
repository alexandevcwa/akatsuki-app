# Guía de Corrección Arquitectónica mDNS / DNS-SD (Android NSD + Tauri v2 Rust)

Este documento es una especificación técnica e instructivo de ejecución diseñado para asistentes de IA de programación. Contiene el diagnóstico de causa raíz, las reglas de detección de repositorio y los cambios exactos línea por línea para resolver el fallo en el que el cliente de escritorio (**Tauri v2 / Rust**) y el nodo móvil (**Android / Kotlin sobre JDK Temurin**) nunca logran descubrirse mutuamente en la red local (LAN).

## 1. Instrucciones de Enrutamiento para el Agente de IA

Antes de aplicar cualquier cambio, **identifica en cuál de los dos repositorios te encuentras** inspeccionando el árbol de archivos raíz del espacio de trabajo:

| Criterio de Detección en el Proyecto | Rol del Proyecto | Archivo Principal a Modificar | Archivos Secundarios a Verificar |
| :--- | :--- | :--- | :--- |
| Existe `build.gradle.kts` / `settings.gradle.kts`, `AndroidManifest.xml` y paquete `com.akatsuki.android` (compilado con Android Gradle Plugin / JDK Temurin) | **Servidor / Publicador mDNS (App Android)** | `AndroidNsdPublisher.kt` (`com/akatsuki/android/infrastructure/network/AndroidNsdPublisher.kt`) | `AndroidManifest.xml`, `AndroidNsdPublisherTest.kt` y el servicio/caso de uso que invoca `publishMdnsService` |
| Existe `src-tauri/Cargo.toml`, `tauri.conf.json` y módulos Rust bajo `src-tauri/src/` | **Cliente / Buscador mDNS y Sondeo LAN (Tauri v2)** | `mdns_discovery.rs` (`src-tauri/src/.../mdns_discovery.rs`) | `android_client.rs` (función `check_android_health`) y `Cargo.toml` |

**Regla de actuación para la IA:**
- Si estás en el **repositorio Android (Kotlin / JDK Temurin)**, aplica **únicamente la Sección 3** de este documento.
- Si estás en el **repositorio Desktop (Tauri v2 / Rust)**, aplica **únicamente la Sección 4** de este documento.
- Si estás en un **monorepo** que contiene ambos proyectos, aplica las **Secciones 3 y 4** de forma secuencial y ejecuta las pruebas de verificación de la **Sección 5**.

## 2. Resumen del Diagnóstico de Causa Raíz

El fallo de descubrimiento ("0 nodos encontrados") se produce por cinco bloqueos encadenados entre el publicador de Android y el consumidor de Rust:

1. **Punto final inválido en el `serviceType` de Android (`_akatsuki._tcp.` vs `_akatsuki._tcp`):** En `AndroidNsdPublisher.kt`, la constante `SERVICE_TYPE` está definida como `"_akatsuki._tcp."` y la función `normalizeAndroidServiceType()` fuerza siempre un punto al final. Aunque en el protocolo mDNS de bajo nivel el FQDN termina en punto (`_akatsuki._tcp.local.`), la especificación de [Android Developers para NsdServiceInfo.setServiceType](https://www.google.com/search?q=https://developer.android.com/reference/android/net/nsd/NsdServiceInfo%23setServiceType%28java.lang.String%29) y la [guía oficial de Android Network Service Discovery](https://developer.android.com/develop/connectivity/wifi/use-nsd) exigen el formato `<protocol>._<transport>` **sin punto final** (`"_akatsuki._tcp"`). Forzar el punto final provoca que en distintas versiones del demonio nativo de Android (`mdnsd` / `MdnsService`) falle la validación del tipo o se concatene un doble punto inválido (`_akatsuki._tcp..local.`), impidiendo la coincidencia con el cliente ([Stack Overflow: Android NSD service type mismatch](https://stackoverflow.com/questions/53510192/android-nsd-why-service-type-dont-match)).
2. **Destrucción prematura del `Receiver` en `mdns-sd` (Rust) al finalizar cada ventana de 1.8 s:** En `mdns_discovery.rs`, la función `discover_via_mdns` llama a `mdns.browse("_akatsuki._tcp.local.")`, lee eventos durante `1800 ms` y **destruye (`drop`) el `receiver` al salir de la función**. De acuerdo con el comportamiento de [ServiceDaemon::browse en mdns-sd](https://www.google.com/search?q=https://docs.rs/mdns-sd/latest/mdns_sd/struct.ServiceDaemon.html%23method.browse) y las notas de versión en [Releases · keepsimple1/mdns-sd (#522)](https://github.com/keepsimple1/mdns-sd/releases), cuando el `Receiver` se desconecta (`drop`), el daemon cierra esa suscripción y cancela sus consultas pendientes (`PTR -> SRV/TXT -> A`). En la primera llamada, como [ServiceDaemon::new](https://www.google.com/search?q=https://docs.rs/mdns-sd/latest/mdns_sd/struct.ServiceDaemon.html%23method.new) abre los sockets multicast de forma perezosa en otro hilo y añade un *jitter* inicial, los `1800 ms` suelen expirar tras recibir `ServiceFound` (`PTR`) pero antes de completar `ServiceResolved` (`SRV` + `A`). En llamadas posteriores, la caché del daemon aplica *Known-Answer Suppression* y Android limita las respuestas multicast a 1 por segundo ([Releases · keepsimple1/mdns-sd (#476)](https://github.com/keepsimple1/mdns-sd/releases)), por lo que un nuevo `receiver` efímero no vuelve a recibir el evento `ServiceResolved`.
3. **Ausencia de filtrado de interfaces virtuales e IPv6 en `ServiceDaemon`:** En `mdns_discovery.rs`, la función `detect_active_subnets()` filtra adaptadores virtuales (`docker0`, `veth`, `virbr`, `vmnet`, `vboxnet`, `tailscale`, `tun`), pero ese filtro nunca se pasa al `ServiceDaemon`. El daemon escucha en todas las interfaces e incluye IPv6. Cuando Android responde primero por IPv6 (`AAAA` `fe80::...`), `ServiceResolved` llega con `info.get_addresses_v4()` vacío y el código de `mdns_discovery.rs` (líneas 355–364) descarta el servicio con `continue`.
4. **Pérdida de metadatos TXT (`id`, `fp`, `name`) y descarte total si falla `check_android_health`:** `mdns_discovery.rs` lee `txt_id` y `txt_fp` solo para imprimir un log (líneas 366–367) y luego delega el 100% de la construcción del nodo a `verify_discovered_endpoints` (`GET /api/v1/health`). Si el cliente HTTP en `check_android_health` falla al validar el certificado TLS autofirmado (TOFU) de Android o expira el timeout, el nodo descubierto por mDNS se descarta por completo sin usar los atributos TXT del anuncio mDNS como respaldo.
5. **Barrido TCP desactivado (`ENABLE_TCP_SUBNET_SCAN = false`) y excepción silenciada en `MulticastLock`:** En `mdns_discovery.rs` (línea 34), `ENABLE_TCP_SUBNET_SCAN` está en `false`, dejando sin respaldo al cliente cuando el router o firewall local filtra UDP 5353 ([Guía de Tauri v2 para mDNS en red local](https://tauri.ninja/recipe.php?id=net-015)). En `AndroidNsdPublisher.kt` (líneas 611–617), `AndroidMulticastLockController.acquire` envuelve la adquisición del lock en `runCatching { ... }.getOrNull()`, ocultando cualquier `SecurityException` si falta el permiso `CHANGE_WIFI_MULTICAST_STATE`.

## 3. Solución Detallada para el Proyecto App Android (Kotlin / JDK Temurin)

### Paso 3.1: Verificar permisos requeridos en `AndroidManifest.xml`
Abre el archivo `AndroidManifest.xml` del módulo de la aplicación Android y asegúrate de que estén declarados explícitamente los siguientes cuatro permisos antes del bloque `<application>`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
```

### Paso 3.2: Modificaciones exactas en `AndroidNsdPublisher.kt`
Abre `com/akatsuki/android/infrastructure/network/AndroidNsdPublisher.kt` y realiza los siguientes cambios:

1. **Eliminar el punto final en `SERVICE_TYPE` y en `normalizeAndroidServiceType` (Líneas 99–116):**
   - Cambia `const val SERVICE_TYPE = "_akatsuki._tcp."` por `const val SERVICE_TYPE = "_akatsuki._tcp"`.
   - En `normalizeAndroidServiceType(rawType: String)`, elimina cualquier sufijo `.local.` o `.local` y aplica `.trimEnd('.')` para que Android `NsdServiceInfo` reciba siempre `"_akatsuki._tcp"` sin punto final.
2. **Registrar telemetría si falla la adquisición de `MulticastLock` (Líneas 340–348):**
   - Si `multicastController.acquire(MULTICAST_LOCK_TAG)` devuelve `null`, registra un mensaje de advertencia en `telemetryRepository` indicando que no se pudo adquirir el `MulticastLock` (para detectar permisos faltantes o Wi-Fi apagado).
3. **Verificar el bind del servidor HTTPS local en Android:**
   - Asegúrate de que el servidor HTTPS local en Android (puerto `8443`) haga bind en `0.0.0.0` (todas las interfaces) y no en `127.0.0.1`, y que su endpoint `GET /api/v1/health` devuelva HTTP 200 con `{"status": "online", ...}`.

#### Código de reemplazo para `AndroidNsdPublisher.kt`

Sustituye el bloque `companion object` (líneas 96–116) por esta implementación:

```kotlin
    companion object {
        const val SERVICE_NAME_DEFAULT = "AkatsukiNode"
        const val MDNS_STANDARD_PORT = 5353
        // IMPORTANTE: En Android NsdServiceInfo.setServiceType NO debe llevar punto final ni .local
        const val SERVICE_TYPE = "_akatsuki._tcp"
        const val SERVICE_TYPE_FQDN = "_akatsuki._tcp.local."
        private const val MULTICAST_LOCK_TAG = "akatsuki_mdns_lock"
        private const val UNREGISTER_WAIT_TIMEOUT_MS = 600L
        private const val MAX_TXT_FP_BYTES = 240
        private const val MAX_TXT_ID_BYTES = 64
        private const val MAX_TXT_NAME_BYTES = 64

        internal fun normalizeAndroidServiceType(rawType: String): String {
            val trimmed = rawType.trim().lowercase().trimEnd('.')
            if (trimmed.isEmpty()) return SERVICE_TYPE
            val withoutLocal = when {
                trimmed.endsWith(".local") -> trimmed.removeSuffix(".local")
                else -> trimmed
            }.trimEnd('.')
            return withoutLocal.ifEmpty { SERVICE_TYPE }
        }
```

Y en `publishMdnsService` (alrededor de la línea 340), añade la verificación explícita del `MulticastLock`:

```kotlin
        val acquiredLock = runCatching { multicastController.acquire(MULTICAST_LOCK_TAG) }.getOrNull()
        if (acquiredLock == null) {
            telemetryRepository.recordLog(
                tag = "mDNS-WARN",
                message = "No se pudo adquirir WifiManager.MulticastLock (verifica permiso CHANGE_WIFI_MULTICAST_STATE y estado Wi-Fi).",
                severity = AuditSeverity.WARNING
            )
        }
```

## 4. Solución Detallada para el Proyecto Cliente Desktop (Tauri v2 / Rust)

### Paso 4.1: Qué debe modificar el Agente de IA en `mdns_discovery.rs`
Abre `mdns_discovery.rs` y aplica los siguientes cambios estructurales:

1. **Habilitar el sondeo TCP multi-subred como respaldo paralelo (Línea 34):**
   - Cambia `const ENABLE_TCP_SUBNET_SCAN: bool = false;` a `const ENABLE_TCP_SUBNET_SCAN: bool = true;`.
2. **Mantener vivo el `Receiver<ServiceEvent>` de `mdns.browse()` y almacenar una caché en memoria de servicios resueltos:**
   - Nunca hagas `drop` del `Receiver` devuelto por `daemon.browse(AKATSUKI_MDNS_SERVICE_TYPE)` al terminar cada ciclo de descubrimiento.
   - Guarda el `ServiceDaemon`, el `Receiver<ServiceEvent>` y un `HashMap<String, Vec<(Ipv4Addr, u16, MdnsTxtMetadata)>>` persistente dentro de un `OnceLock<AsyncMutex<Option<PersistentMdnsBrowser>>>`.
   - En cada llamada a `discover_via_mdns`, drena los eventos del `Receiver` durante `MDNS_BROWSE_WINDOW`, actualizando `resolved_by_fullname` en `ServiceEvent::ServiceResolved` y eliminando entradas en `ServiceEvent::ServiceRemoved`, sin destruir el `Receiver`.
3. **Configurar el `ServiceDaemon` para usar solo IPv4 e ignorar adaptadores virtuales:**
   - Al crear el `ServiceDaemon`, llama a [daemon.disable\_interface(IfKind::IPv6)](https://www.google.com/search?q=https://docs.rs/mdns-sd/latest/mdns_sd/struct.ServiceDaemon.html%23method.disable_interface) para obligar a que la resolución DNS-SD se realice sobre IPv4 (`224.0.0.251:5353`, registros `A`) y evitar eventos `ServiceResolved` que solo traen direcciones `AAAA` (`fe80::...`).
   - Deshabilita en el daemon todas las interfaces virtuales (`docker`, `veth`, `br-`, `virbr`, `vmnet`, `vboxnet`, `tailscale`, `tun`, `tap`, `wg`) usando `daemon.disable_interface(iface_name)`.
4. **Conservar los metadatos TXT (`id`, `fp`, `name`) de mDNS como respaldo si falla `check_android_health`:**
   - Extrae los atributos TXT `"id"`, `"fp"` y `"name"` que `AndroidNsdPublisher.kt` publica.
   - En `verify_discovered_endpoints`, si `check_android_health(&ip_str, port)` responde `Ok`, usa los datos completos del endpoint `/api/v1/health`. Si `check_android_health` falla (por ejemplo, por validación de certificado TLS autofirmado antes del emparejamiento TOFU), pero el endpoint provino de `"mdns"` y trae `id` y `fp` en sus registros TXT, construye igualmente el `AndroidDeviceNode` con los datos del TXT record para que el dispositivo aparezca siempre en la interfaz de Tauri.

### Paso 4.2: Código completo de reemplazo para `mdns_discovery.rs`
Reemplaza el contenido de `mdns_discovery.rs` con la siguiente implementación completa:

```rust
use crate::domain::device::AndroidDeviceNode;
use crate::infrastructure::android_client::check_android_health;
use if_addrs::{get_if_addrs, IfAddr};
use mdns_sd::{IfKind, Receiver as MdnsReceiver, ServiceDaemon, ServiceEvent};
use serde::Serialize;
use std::collections::{BTreeMap, HashMap, HashSet};
use std::net::Ipv4Addr;
use std::sync::{Arc, OnceLock};
use std::time::{Duration, Instant};
use tokio::net::TcpStream;
use tokio::sync::{Mutex as AsyncMutex, Semaphore};
use tokio::task::JoinSet;
use tokio::time::timeout;

/// Tipo de servicio mDNS / DNS-SD anunciado por los nodos Android en LAN.
const AKATSUKI_MDNS_SERVICE_TYPE: &str = "_akatsuki._tcp.local.";

/// Puerto de respaldo cuando no se indican puertos personalizados ni hay anuncio mDNS.
const DEFAULT_ANDROID_PORT: u16 = 8443;

/// Límite de conexiones TCP simultáneas para no agotar file descriptors del sistema operativo.
const MAX_CONCURRENT_TCP_PROBES: usize = 96;

/// Límite máximo de hosts por subred para sondeo TCP (subredes `/16` o `/8` se acotan al `/24` local).
const MAX_TCP_HOSTS_PER_SUBNET: u32 = 1024;

/// Tiempo de espera por sonda TCP individual en red local.
const TCP_PROBE_TIMEOUT: Duration = Duration::from_millis(350);

/// Ventana de escucha mDNS por ciclo de descubrimiento (tolerante a latencia PTR -> SRV -> A en Wi-Fi/Android).
const MDNS_BROWSE_WINDOW: Duration = Duration::from_millis(2200);

/// Habilita el barrido TCP multi-subred en paralelo a mDNS para redes con filtrado multicast.
const ENABLE_TCP_SUBNET_SCAN: bool = true;

/// Prefijos de interfaces virtuales o de contenedores que deben excluirse si hay LAN física.
const IGNORED_VIRTUAL_IFACE_PREFIXES: &[&str] = &[
    "docker", "veth", "br-", "virbr", "vmnet", "vboxnet", "lxc", "cni", "flannel", "podman",
    "tailscale", "tun", "tap", "wg",
];

/// Información resumida de una subred local activa para exponerla al frontend.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalSubnetInfo {
    pub interface_name: String,
    pub local_ip: String,
    pub netmask: String,
    pub prefix_len: u8,
    pub cidr: String,
}

#[derive(Debug, Clone)]
struct LocalSubnet {
    interface_name: String,
    local_ip: Ipv4Addr,
    netmask: Ipv4Addr,
    prefix_len: u8,
    network_u32: u32,
    hosts: Vec<Ipv4Addr>,
}

/// Metadatos extraídos de los registros TXT del anuncio mDNS de Android (`fp`, `id`, `name`).
#[derive(Debug, Clone, Default)]
struct MdnsTxtMetadata {
    device_id: Option<String>,
    fingerprint_sha256: Option<String>,
    device_name: Option<String>,
}

#[derive(Debug, Clone)]
struct CandidateEndpoint {
    via: &'static str,
    mdns_meta: Option<MdnsTxtMetadata>,
}

/// Determina si una interfaz de red corresponde a un adaptador virtual/túnel secundario.
fn is_virtual_interface(name: &str) -> bool {
    let lower = name.to_ascii_lowercase();
    IGNORED_VIRTUAL_IFACE_PREFIXES
        .iter()
        .any(|prefix| lower.starts_with(prefix))
}

/// Verifica que una IPv4 sea válida para escaneo en red local (excluye loopback, link-local, etc.).
fn is_scannable_ipv4(ip: Ipv4Addr) -> bool {
    !ip.is_loopback()
        && !ip.is_link_local()
        && !ip.is_unspecified()
        && !ip.is_multicast()
        && !ip.is_broadcast()
}

/// Calcula el prefijo CIDR y la lista de IPs de host válidas dentro de una subred IPv4.
/// Si la subred supera `MAX_TCP_HOSTS_PER_SUBNET`, acota el rango al `/24` inmediato de `local_ip`.
fn compute_subnet_hosts(
    local_ip: Ipv4Addr,
    netmask: Ipv4Addr,
    own_ips: &HashSet<Ipv4Addr>,
) -> (u8, u32, Vec<Ipv4Addr>) {
    let ip_u32 = u32::from(local_ip);
    let mask_u32 = u32::from(netmask);
    let prefix_len = mask_u32.count_ones() as u8;
    let network_u32 = ip_u32 & mask_u32;
    let broadcast_u32 = network_u32 | !mask_u32;

    if broadcast_u32 <= network_u32.saturating_add(1) {
        return (prefix_len, network_u32, Vec::new());
    }

    let first_usable = network_u32 + 1;
    let last_usable = broadcast_u32 - 1;
    let total_hosts = last_usable - first_usable + 1;

    let (scan_start, scan_end) = if total_hosts > MAX_TCP_HOSTS_PER_SUBNET {
        let local_slash24_net = ip_u32 & 0xFFFF_FF00;
        let bounded_start = (local_slash24_net + 1).max(first_usable);
        let bounded_end = (local_slash24_net + 254).min(last_usable);
        eprintln!(
            "[WARN][NetworkDiscovery] Subred {}/{} tiene {} hosts; acotando sondeo TCP al bloque local {}/24 ({}..{})",
            Ipv4Addr::from(network_u32),
            prefix_len,
            total_hosts,
            Ipv4Addr::from(local_slash24_net),
            Ipv4Addr::from(bounded_start),
            Ipv4Addr::from(bounded_end)
        );
        (bounded_start, bounded_end)
    } else {
        (first_usable, last_usable)
    };

    let hosts = (scan_start..=scan_end)
        .map(Ipv4Addr::from)
        .filter(|candidate| !own_ips.contains(candidate))
        .collect();

    (prefix_len, network_u32, hosts)
}

/// Enumera todas las subredes IPv4 activas de la PC usando las interfaces reales del SO.
fn detect_active_subnets() -> Vec<LocalSubnet> {
    let interfaces = match get_if_addrs() {
        Ok(list) => list,
        Err(err) => {
            eprintln!(
                "[ERROR][NetworkDiscovery] No se pudieron enumerar las interfaces de red del sistema: {:?}",
                err
            );
            return Vec::new();
        }
    };

    let mut ipv4_candidates: Vec<(String, Ipv4Addr, Ipv4Addr, bool)> = Vec::new();
    let mut own_ips = HashSet::new();

    for iface in interfaces {
        if iface.is_loopback() {
            continue;
        }
        let IfAddr::V4(v4) = iface.addr else {
            continue;
        };
        if !is_scannable_ipv4(v4.ip) {
            continue;
        }
        own_ips.insert(v4.ip);
        let virtual_iface = is_virtual_interface(&iface.name);
        ipv4_candidates.push((iface.name, v4.ip, v4.netmask, virtual_iface));
    }

    // Preferimos interfaces físicas/LAN cuando existan; solo usamos virtuales si no hay otra opción.
    let has_physical = ipv4_candidates.iter().any(|(_, _, _, is_virt)| !is_virt);
    if has_physical {
        ipv4_candidates.retain(|(name, ip, _, is_virt)| {
            if *is_virt {
                eprintln!(
                    "[INFO][NetworkDiscovery] Omitiendo interfaz virtual secundaria {} ({})",
                    name, ip
                );
                false
            } else {
                true
            }
        });
    }

    // Priorizar redes privadas LAN (RFC 1918) sobre direcciones públicas directas.
    ipv4_candidates.sort_by_key(|(_, ip, _, _)| (!ip.is_private(), *ip));

    let mut seen_networks: HashSet<(u32, u32)> = HashSet::new();
    let mut subnets = Vec::new();

    for (interface_name, local_ip, netmask, _) in ipv4_candidates {
        let mask_u32 = u32::from(netmask);
        let network_u32 = u32::from(local_ip) & mask_u32;

        if !seen_networks.insert((network_u32, mask_u32)) {
            continue;
        }

        let (prefix_len, _, hosts) = compute_subnet_hosts(local_ip, netmask, &own_ips);
        if hosts.is_empty() {
            continue;
        }

        eprintln!(
            "[INFO][NetworkDiscovery] Subred detectada en {}: IP local = {}, máscara = {} (/{}), {} hosts a sondear",
            interface_name,
            local_ip,
            netmask,
            prefix_len,
            hosts.len()
        );

        subnets.push(LocalSubnet {
            interface_name,
            local_ip,
            netmask,
            prefix_len,
            network_u32,
            hosts,
        });
    }

    subnets
}

/// Devuelve información de las subredes activas detectadas en la PC para la interfaz de usuario.
pub fn list_local_network_subnets() -> Vec<LocalSubnetInfo> {
    detect_active_subnets()
        .into_iter()
        .map(|s| LocalSubnetInfo {
            interface_name: s.interface_name,
            local_ip: s.local_ip.to_string(),
            netmask: s.netmask.to_string(),
            prefix_len: s.prefix_len,
            cidr: format!("{}/{}", Ipv4Addr::from(s.network_u32), s.prefix_len),
        })
        .collect()
}

/// Normaliza la lista de puertos candidatos conservando el orden de prioridad y evitando duplicados.
fn normalize_candidate_ports(requested_ports: Option<&[u16]>) -> Vec<u16> {
    let mut seen = HashSet::new();
    let mut ports = Vec::new();

    if let Some(list) = requested_ports {
        for &port in list {
            if port > 0 && seen.insert(port) {
                ports.push(port);
            }
        }
    }

    if seen.insert(DEFAULT_ANDROID_PORT) {
        ports.push(DEFAULT_ANDROID_PORT);
    }

    ports
}

struct PersistentMdnsBrowser {
    daemon: ServiceDaemon,
    receiver: MdnsReceiver<ServiceEvent>,
    /// Mapa `fullname` -> `Vec<(Ipv4Addr, u16, MdnsTxtMetadata)>` para mantener vivos los anuncios resueltos.
    resolved_by_fullname: HashMap<String, Vec<(Ipv4Addr, u16, MdnsTxtMetadata)>>,
}

static MDNS_BROWSER_STORE: OnceLock<AsyncMutex<Option<PersistentMdnsBrowser>>> = OnceLock::new();

/// Configura las interfaces del `ServiceDaemon` para usar IPv4 en subredes LAN reales e ignorar túneles/contenedores.
fn configure_daemon_interfaces(daemon: &ServiceDaemon, subnets: &[LocalSubnet]) {
    let _ = daemon.disable_interface(IfKind::IPv6);

    if let Ok(ifaces) = get_if_addrs() {
        for iface in ifaces {
            if is_virtual_interface(&iface.name) {
                let _ = daemon.disable_interface(iface.name.as_str());
            }
        }
    }

    for subnet in subnets {
        let _ = daemon.enable_interface(subnet.local_ip);
    }
}

/// Inicializa el navegador mDNS persistente conservando vivo su `Receiver<ServiceEvent>`.
fn init_persistent_mdns_browser(subnets: &[LocalSubnet]) -> Option<PersistentMdnsBrowser> {
    let daemon = match ServiceDaemon::new() {
        Ok(d) => d,
        Err(err) => {
            eprintln!(
                "[WARN][NetworkDiscovery] No se pudo iniciar el daemon mDNS: {:?}",
                err
            );
            return None;
        }
    };

    let _ = daemon.accept_unsolicited(true);
    configure_daemon_interfaces(&daemon, subnets);

    let receiver = match daemon.browse(AKATSUKI_MDNS_SERVICE_TYPE) {
        Ok(rx) => rx,
        Err(err) => {
            eprintln!(
                "[WARN][NetworkDiscovery] Fallo al suscribir browse({}): {:?}",
                AKATSUKI_MDNS_SERVICE_TYPE, err
            );
            let _ = daemon.shutdown();
            return None;
        }
    };

    eprintln!(
        "[INFO][NetworkDiscovery] Navegador mDNS persistente inicializado ({})",
        AKATSUKI_MDNS_SERVICE_TYPE
    );

    Some(PersistentMdnsBrowser {
        daemon,
        receiver,
        resolved_by_fullname: HashMap::new(),
    })
}

/// Descubre nodos Android anunciados mediante mDNS / DNS-SD manteniendo viva la suscripción y la caché.
async fn discover_via_mdns(
    subnets: &[LocalSubnet],
    window: Duration,
) -> Vec<(Ipv4Addr, u16, MdnsTxtMetadata)> {
    let store = MDNS_BROWSER_STORE.get_or_init(|| AsyncMutex::new(None));
    let mut guard = store.lock().await;

    if guard.is_none() {
        *guard = init_persistent_mdns_browser(subnets);
    }

    let Some(browser) = guard.as_mut() else {
        return Vec::new();
    };

    configure_daemon_interfaces(&browser.daemon, subnets);

    let deadline = Instant::now() + window;
    let mut should_reset = false;

    while let Some(remaining) = deadline.checked_duration_since(Instant::now()) {
        match timeout(remaining, browser.receiver.recv_async()).await {
            Ok(Ok(ServiceEvent::SearchStarted(msg))) => {
                eprintln!("[INFO][NetworkDiscovery] mDNS SearchStarted: {}", msg);
            }
            Ok(Ok(ServiceEvent::ServiceFound(ty, fullname))) => {
                eprintln!(
                    "[INFO][NetworkDiscovery] mDNS ServiceFound: {} ({}) — esperando resolución SRV/A...",
                    fullname, ty
                );
            }
            Ok(Ok(ServiceEvent::ServiceResolved(info))) => {
                let port = info.get_port();
                if port == 0 {
                    continue;
                }

                let v4_addrs = info.get_addresses_v4();
                if v4_addrs.is_empty() {
                    eprintln!(
                        "[WARN][NetworkDiscovery] Servicio mDNS {} resuelto sin IPv4 aún; direcciones={:?}",
                        info.get_fullname(),
                        info.get_addresses()
                    );
                    continue;
                }

                let meta = MdnsTxtMetadata {
                    device_id: info
                        .get_property_val_str("id")
                        .map(str::trim)
                        .filter(|s| !s.is_empty())
                        .map(String::from),
                    fingerprint_sha256: info
                        .get_property_val_str("fp")
                        .map(str::trim)
                        .filter(|s| !s.is_empty())
                        .map(String::from),
                    device_name: info
                        .get_property_val_str("name")
                        .map(str::trim)
                        .filter(|s| !s.is_empty())
                        .map(String::from),
                };

                let mut entries = Vec::new();
                for ipv4 in v4_addrs {
                    if is_scannable_ipv4(ipv4) {
                        eprintln!(
                            "[INFO][NetworkDiscovery] Servicio mDNS resuelto ({}): {}:{} (id={:?}, fp={:?})",
                            info.get_fullname(),
                            ipv4,
                            port,
                            meta.device_id,
                            meta.fingerprint_sha256
                        );
                        entries.push((ipv4, port, meta.clone()));
                    }
                }

                if !entries.is_empty() {
                    browser
                        .resolved_by_fullname
                        .insert(info.get_fullname().to_string(), entries);
                }
            }
            Ok(Ok(ServiceEvent::ServiceRemoved(ty, fullname))) => {
                eprintln!(
                    "[INFO][NetworkDiscovery] mDNS ServiceRemoved: {} ({})",
                    fullname, ty
                );
                browser.resolved_by_fullname.remove(&fullname);
            }
            Ok(Ok(_)) => {}
            Ok(Err(err)) => {
                eprintln!(
                    "[WARN][NetworkDiscovery] Canal mDNS cerrado inesperadamente ({:?}); se reiniciará",
                    err
                );
                should_reset = true;
                break;
            }
            Err(_) => break,
        }
    }

    if should_reset {
        if let Some(old) = guard.take() {
            let _ = old.daemon.shutdown();
        }
        return Vec::new();
    }

    browser
        .resolved_by_fullname
        .values()
        .flatten()
        .cloned()
        .collect()
}

/// Realiza un sondeo TCP concurrente y acotado por semáforo sobre las subredes y puertos dados.
async fn scan_subnets_tcp(subnets: &[LocalSubnet], ports: &[u16]) -> Vec<(Ipv4Addr, u16)> {
    if subnets.is_empty() || ports.is_empty() {
        return Vec::new();
    }

    let semaphore = Arc::new(Semaphore::new(MAX_CONCURRENT_TCP_PROBES));
    let mut set = JoinSet::new();

    for subnet in subnets {
        for &ip in &subnet.hosts {
            for &port in ports {
                let permit_sem = Arc::clone(&semaphore);
                set.spawn(async move {
                    let Ok(_permit) = permit_sem.acquire_owned().await else {
                        return None;
                    };
                    let tcp_ok = timeout(TCP_PROBE_TIMEOUT, TcpStream::connect((ip, port)))
                        .await
                        .map(|res| res.is_ok())
                        .unwrap_or(false);
                    tcp_ok.then_some((ip, port))
                });
            }
        }
    }

    let mut open_endpoints = Vec::new();
    while let Some(res) = set.join_next().await {
        if let Ok(Some(endpoint)) = res {
            open_endpoints.push(endpoint);
        }
    }

    open_endpoints
}

/// Consulta `GET /api/v1/health` en paralelo para validar cada endpoint `(ip, port)` descubierto,
/// utilizando los atributos TXT de mDNS como respaldo si el endpoint provino de mDNS.
async fn verify_discovered_endpoints(
    endpoints: BTreeMap<(Ipv4Addr, u16), CandidateEndpoint>,
) -> Vec<AndroidDeviceNode> {
    let mut set = JoinSet::new();

    for ((ip, port), candidate) in endpoints {
        set.spawn(async move {
            let ip_str = ip.to_string();
            match check_android_health(&ip_str, port).await {
                Ok(health) if health.status.eq_ignore_ascii_case("online") => {
                    Some(AndroidDeviceNode {
                        device_id: health.device_id,
                        device_name: health.device_name,
                        model: health.model,
                        ip_address: ip_str,
                        port: health.port,
                        tls_cert_fingerprint_sha256: health.tls_cert_fingerprint_sha256,
                        discovered_via: candidate.via.to_string(),
                        paired: false,
                    })
                }
                Ok(health) => {
                    eprintln!(
                        "[WARN][NetworkDiscovery] Nodo {}:{} respondió estado no operativo: '{}'",
                        ip_str, port, health.status
                    );
                    None
                }
                Err(err) => {
                    eprintln!(
                        "[WARN][NetworkDiscovery] Fallo verificando GET /api/v1/health en {}:{} (vía {}): {:?}",
                        ip_str, port, candidate.via, err
                    );
                    // Respaldo mDNS: si el nodo se anunció por _akatsuki._tcp.local. con TXT "id" y "fp",
                    // no lo descartamos aunque check_android_health falle por certificado TLS autofirmado.
                    if candidate.via == "mdns" {
                        if let Some(meta) = candidate.mdns_meta {
                            if let (Some(id), Some(fp)) = (meta.device_id, meta.fingerprint_sha256) {
                                let fallback_name = meta
                                    .device_name
                                    .unwrap_or_else(|| format!("AkatsukiNode ({})", ip_str));
                                eprintln!(
                                    "[INFO][NetworkDiscovery] Usando metadatos TXT mDNS de respaldo para {}:{} (id={})",
                                    ip_str, port, id
                                );
                                return Some(AndroidDeviceNode {
                                    device_id: id,
                                    device_name: fallback_name,
                                    model: "Android mDNS Node".to_string(),
                                    ip_address: ip_str,
                                    port,
                                    tls_cert_fingerprint_sha256: fp,
                                    discovered_via: "mdns".to_string(),
                                    paired: false,
                                });
                            }
                        }
                    }
                    None
                }
            }
        });
    }

    let mut by_device_id: BTreeMap<String, AndroidDeviceNode> = BTreeMap::new();
    while let Some(res) = set.join_next().await {
        if let Ok(Some(node)) = res {
            by_device_id
                .entry(node.device_id.clone())
                .and_modify(|existing| {
                    if existing.discovered_via != "mdns" && node.discovered_via == "mdns" {
                        *existing = node.clone();
                    }
                })
                .or_insert(node);
        }
    }

    let mut discovered: Vec<AndroidDeviceNode> = by_device_id.into_values().collect();
    discovered.sort_by(|a, b| {
        a.ip_address
            .cmp(&b.ip_address)
            .then_with(|| a.port.cmp(&b.port))
    });
    discovered
}

/// Descubre dispositivos Android activos combinando mDNS (cualquier puerto) y sondeo TCP
/// multi-subred sobre los segmentos de red reales conectados en la PC.
pub async fn discover_android_nodes_on_lan(
    requested_ports: Option<Vec<u16>>,
) -> Vec<AndroidDeviceNode> {
    let started_at = Instant::now();
    let subnets = detect_active_subnets();
    let candidate_ports = normalize_candidate_ports(requested_ports.as_deref());

    eprintln!(
        "[INFO][NetworkDiscovery] Iniciando descubrimiento LAN: {} subred(es) activa(s), puertos candidatos TCP = {:?}, mDNS = {}",
        subnets.len(),
        candidate_ports,
        AKATSUKI_MDNS_SERVICE_TYPE
    );

    let (mdns_endpoints, tcp_endpoints) = if ENABLE_TCP_SUBNET_SCAN {
        if subnets.is_empty() {
            eprintln!(
                "[WARN][NetworkDiscovery] No se detectaron interfaces IPv4 LAN activas; intentando solo vía mDNS"
            );
        }
        tokio::join!(
            discover_via_mdns(&subnets, MDNS_BROWSE_WINDOW),
            scan_subnets_tcp(&subnets, &candidate_ports)
        )
    } else {
        eprintln!(
            "[INFO][NetworkDiscovery] Barrido TCP multi-subred desactivado temporalmente; buscando únicamente vía mDNS"
        );
        (discover_via_mdns(&subnets, MDNS_BROWSE_WINDOW).await, Vec::new())
    };

    let mut combined: BTreeMap<(Ipv4Addr, u16), CandidateEndpoint> = BTreeMap::new();
    for (ip, port) in tcp_endpoints {
        combined.insert(
            (ip, port),
            CandidateEndpoint {
                via: "lan-scan",
                mdns_meta: None,
            },
        );
    }
    for (ip, port, meta) in mdns_endpoints {
        combined.insert(
            (ip, port),
            CandidateEndpoint {
                via: "mdns",
                mdns_meta: Some(meta),
            },
        );
    }

    if combined.is_empty() {
        eprintln!(
            "[INFO][NetworkDiscovery] Descubrimiento finalizado en {:?}: 0 endpoints abiertos encontrados",
            started_at.elapsed()
        );
        return Vec::new();
    }

    let candidate_count = combined.len();
    let nodes = verify_discovered_endpoints(combined).await;

    eprintln!(
        "[INFO][NetworkDiscovery] Descubrimiento finalizado en {:?}: {} candidato(s) sondeados, {} nodo(s) Akatsuki verificados",
        started_at.elapsed(),
        candidate_count,
        nodes.len()
    );

    nodes
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn computes_hosts_for_arbitrary_slash_24_subnet() {
        let local_ip = Ipv4Addr::new(10, 42, 0, 15);
        let netmask = Ipv4Addr::new(255, 255, 255, 0);
        let mut own = HashSet::new();
        own.insert(local_ip);

        let (prefix, network, hosts) = compute_subnet_hosts(local_ip, netmask, &own);
        assert_eq!(prefix, 24);
        assert_eq!(Ipv4Addr::from(network), Ipv4Addr::new(10, 42, 0, 0));
        assert_eq!(hosts.len(), 253);
        assert!(!hosts.contains(&local_ip));
        assert!(hosts.contains(&Ipv4Addr::new(10, 42, 0, 1)));
        assert!(hosts.contains(&Ipv4Addr::new(10, 42, 0, 254)));
    }

    #[test]
    fn bounds_large_slash_16_subnet_to_local_slash_24_window() {
        let local_ip = Ipv4Addr::new(172, 20, 10, 5);
        let netmask = Ipv4Addr::new(255, 255, 0, 0);
        let mut own = HashSet::new();
        own.insert(local_ip);

        let (prefix, network, hosts) = compute_subnet_hosts(local_ip, netmask, &own);
        assert_eq!(prefix, 16);
        assert_eq!(Ipv4Addr::from(network), Ipv4Addr::new(172, 20, 0, 0));
        assert_eq!(hosts.len(), 253);
        assert!(hosts.contains(&Ipv4Addr::new(172, 20, 10, 1)));
        assert!(!hosts.contains(&Ipv4Addr::new(172, 20, 11, 1)));
    }

    #[test]
    fn computes_hosts_for_slash_26_subnet() {
        let local_ip = Ipv4Addr::new(192, 168, 50, 70);
        let netmask = Ipv4Addr::new(255, 255, 255, 192);
        let mut own = HashSet::new();
        own.insert(local_ip);

        let (prefix, network, hosts) = compute_subnet_hosts(local_ip, netmask, &own);
        assert_eq!(prefix, 26);
        assert_eq!(Ipv4Addr::from(network), Ipv4Addr::new(192, 168, 50, 64));
        assert_eq!(hosts.len(), 61);
        assert!(hosts.contains(&Ipv4Addr::new(192, 168, 50, 65)));
        assert!(hosts.contains(&Ipv4Addr::new(192, 168, 50, 126)));
        assert!(!hosts.contains(&Ipv4Addr::new(192, 168, 50, 127)));
    }

    #[test]
    fn normalizes_candidate_ports_without_duplicates() {
        let ports = normalize_candidate_ports(Some(&[9000, 0, 8443, 9000, 5001]));
        assert_eq!(ports, vec![9000, 8443, 5001]);

        let default_only = normalize_candidate_ports(None);
        assert_eq!(default_only, vec![DEFAULT_ANDROID_PORT]);
    }

    #[test]
    fn filters_virtual_interfaces_correctly() {
        assert!(is_virtual_interface("docker0"));
        assert!(is_virtual_interface("veth1a2b3c"));
        assert!(is_virtual_interface("virbr0"));
        assert!(!is_virtual_interface("wlp2s0"));
        assert!(!is_virtual_interface("enp3s0"));
        assert!(!is_virtual_interface("Wi-Fi"));
    }
}
```

### Paso 4.3: Verificación obligatoria de `check_android_health` en `src-tauri/src/infrastructure/android_client.rs`
El Agente de IA en el proyecto Tauri v2 también debe abrir `src/infrastructure/android_client.rs` y verificar que el cliente `reqwest::Client` usado por `check_android_health`:
1. Tenga habilitado `.danger_accept_invalid_certs(true)` (dado que los nodos Android generan certificados TLS autofirmados para el protocolo TOFU cuya huella SHA-256 se valida contra `tls_cert_fingerprint_sha256` o el atributo TXT `fp`).
2. Tenga un timeout razonable en LAN (`Duration::from_millis(1200)` a `1500 ms`), ya que el handshake TLS en un dispositivo Android en ahorro de energía puede superar los `300 ms`.

## 5. Lista de Verificación de Pruebas para el Agente de IA

### En el Proyecto Android (Kotlin / JDK Temurin)
1. Ejecuta `./gradlew testDebugUnitTest` y verifica que cualquier test unitario de `AndroidNsdPublisherTest` espere `"_akatsuki._tcp"` (sin punto final) como salida de `normalizeAndroidServiceType("_akatsuki._tcp.local.")`.
2. Compila e instala la app (`./gradlew installDebug`) y confirma en `adb logcat | grep -E "mDNS|Nsd"` que aparece el log `Servicio mDNS publicado: AkatsukiNode._akatsuki._tcp.local.` y que no aparece el warning de `MulticastLock`.

### En el Proyecto Desktop (Tauri v2 / Rust)
1. Ejecuta `cargo test` dentro de `src-tauri/` para confirmar que los 5 tests unitarios de `mdns_discovery::tests` compilan y pasan sin errores.
2. En Linux, verifica que el firewall permita tráfico multicast mDNS (`sudo ufw allow 5353/udp`) y comprueba desde terminal con `avahi-browse -rt _akatsuki._tcp` que el anuncio del teléfono es visible en la red local.

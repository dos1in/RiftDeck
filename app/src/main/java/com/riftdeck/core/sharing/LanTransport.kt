package com.riftdeck.core.sharing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class ShareKind { Rom, Save }

data class SharedFile(val path: String, val size: Long, val sha256: String)

data class LanPeer(val id: String, val name: String, val host: String, val port: Int)

data class PairResult(val peer: LanPeer, val key: ByteArray)

/** Canonical hashes as observed by the initiating device; the receiver stores them in reverse. */
data class SaveAcknowledgement(val path: String, val localSha256: String, val remoteSha256: String)

/** Only explicitly paired peers can read files or upload to the selected folders. */
class LanServer(
    private val deviceId: String,
    private val deviceName: String,
    private val handler: Handler,
    port: Int = 0,
    bindAddress: InetAddress? = null,
) : Closeable {
    interface Handler {
        fun currentPairingCode(): String?
        fun pairedKey(peerId: String): ByteArray?
        fun onPaired(peerId: String, name: String, key: ByteArray) {
            throw IOException("Pairing unsupported")
        }
        /** Atomically verify this exact active code before storing the peer and closing pairing. */
        fun onPaired(peerId: String, name: String, key: ByteArray, authenticatedCode: String) = onPaired(peerId, name, key)
        fun manifest(peerId: String, kind: ShareKind): List<SharedFile>
        fun describe(peerId: String, kind: ShareKind, path: String): SharedFile =
            manifest(peerId, kind).firstOrNull { it.path == path } ?: throw IOException("File unavailable")
        fun acknowledge(peerId: String, acknowledgements: List<SaveAcknowledgement>) {
            throw IOException("Synchronization acknowledgement unsupported")
        }
        fun openRead(peerId: String, kind: ShareKind, path: String): InputStream

        /** Consume the complete stream into staging, then recheck the destination before committing. */
        fun receive(
            peerId: String,
            kind: ShareKind,
            path: String,
            expectedSha256: String,
            size: Long,
            expectedCurrentSha256: String?,
            input: InputStream,
        )
    }

    private val server = ServerSocket()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val socketPeers = ConcurrentHashMap<Socket, String>()
    private val workers = ThreadPoolExecutor(
        0, 4, 30, TimeUnit.SECONDS, SynchronousQueue(),
        { task -> Thread(task, "RiftDeck LAN transfer").apply { isDaemon = true } },
    )
    @Volatile private var running = false
    val port: Int get() = server.localPort

    init {
        validateIdentity(deviceId, deviceName)
        server.reuseAddress = true
        server.bind(InetSocketAddress(bindAddress, port), 4)
    }

    @Synchronized
    fun start() {
        check(!server.isClosed)
        if (running) return
        running = true
        Thread({
            while (running) {
                val socket = try { server.accept() } catch (_: SocketException) { break }
                if (!isLocalAddress(socket.inetAddress)) {
                    socket.close()
                    continue
                }
                sockets.add(socket)
                try {
                    workers.execute {
                        socket.use {
                            try { handle(socket) } catch (_: IOException) {
                                // Authentication and incomplete transfers close without exposing details.
                            } catch (_: IllegalArgumentException) {
                                // Reject malformed metadata before opening any storage document.
                            } finally { sockets.remove(socket); socketPeers.remove(socket) }
                        }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    sockets.remove(socket)
                    socket.close()
                }
            }
        }, "RiftDeck LAN listener").apply { isDaemon = true }.start()
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        socket.tcpNoDelay = true
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        if (input.readInt() != MAGIC || input.readInt() != VERSION) throw IOException("Unsupported protocol")
        val pairing = input.readBoolean()
        val peerId = input.readText(80)
        val peerName = input.readText(256)
        validateIdentity(peerId, peerName)
        socketPeers[socket] = peerId
        if (peerId == deviceId) throw IOException("Invalid peer")
        val clientNonce = ByteArray(32).also(input::readFully)
        val serverNonce = randomBytes(32)
        output.writeText(deviceId)
        output.writeText(deviceName)
        output.write(serverNonce)
        output.flush()
        val activeCode = if (pairing) {
            if (handler.pairedKey(peerId) != null) throw IOException("Peer already paired")
            normalizePairingCode(handler.currentPairingCode() ?: throw IOException("Pairing closed"))
        } else null
        val baseKey = if (activeCode != null) pairingKey(activeCode)
            else handler.pairedKey(peerId) ?: throw IOException("Peer not paired")
        val channel = EncryptedChannel(input, output, baseKey, transcript(
            pairing, peerId, peerName, deviceId, deviceName, clientNonce, serverNonce,
        ), client = false)
        if (!channel.read().contentEquals(AUTH)) throw IOException("Invalid authentication")
        if (pairing) {
            // The code may have expired while the connection was being authenticated.
            val stillActive = handler.currentPairingCode()?.let(::pairingKey)
            if (stillActive == null || !MessageDigest.isEqual(baseKey, stillActive)) throw IOException("Pairing closed")
            val peerKey = randomBytes(32)
            handler.onPaired(peerId, peerName, peerKey.copyOf(), requireNotNull(activeCode))
            channel.write(peerKey)
            return
        }
        channel.write(AUTH)
        try {
            val request = channel.read().reader()
            if (handler.pairedKey(peerId)?.let { MessageDigest.isEqual(baseKey, it) } != true) throw IOException("Peer authorization changed")
            val operation = request.readInt()
            val kind = request.readKind()
            when (operation) {
                OP_MANIFEST -> {
                    request.requireEnd()
                    val files = handler.manifest(peerId, kind)
                    require(files.size <= MAX_MANIFEST_FILES)
                    require(files.map { it.path }.distinct().size == files.size)
                    files.forEach { validateFile(kind, it) }
                    channel.write(packet { writeBoolean(true); writeInt(files.size) })
                    files.forEach { file -> channel.write(packet { writeFile(file) }) }
                }
                OP_DOWNLOAD -> {
                    val file = request.readFile(kind)
                    request.requireEnd()
                    handler.openRead(peerId, kind, file.path).use { stream ->
                        channel.write(packet { writeBoolean(true) })
                        sendFile(channel, file, stream)
                    }
                }
                OP_DESCRIBE -> {
                    val path = request.readText(1024).also(::validateSharedPath)
                    request.requireEnd()
                    channel.write(packet { writeBoolean(true) })
                    val file = handler.describe(peerId, kind, path)
                    validateFile(kind, file)
                    require(file.path == path)
                    channel.write(packet { writeFile(file) })
                }
                OP_ACK_SYNC -> {
                    require(kind == ShareKind.Save)
                    val count = request.readInt()
                    require(count in 0..MAX_MANIFEST_FILES)
                    request.requireEnd()
                    val acknowledgements = List(count) {
                        channel.read().reader().let { row ->
                            row.readAcknowledgement().also { row.requireEnd() }
                        }
                    }
                    require(acknowledgements.map { it.path }.distinct().size == acknowledgements.size)
                    handler.acknowledge(peerId, acknowledgements)
                    channel.write(packet { writeBoolean(true) })
                }
                OP_UPLOAD -> {
                    val file = request.readFile(kind)
                    val currentHash = if (request.readBoolean()) request.readText(64).also(::validateHash) else null
                    request.requireEnd()
                    val stream = ChunkInputStream(channel, file)
                    handler.receive(peerId, kind, file.path, file.sha256, file.size, currentHash, stream)
                    stream.requireComplete()
                    channel.write(packet { writeBoolean(true) })
                }
                else -> throw IOException("Unknown operation")
            }
        } catch (_: Exception) {
            // A generic encrypted response lets the caller discard its staging file.
            runCatching { channel.write(packet { writeBoolean(false) }) }
        }
    }

    override fun close() {
        running = false
        server.close()
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
        socketPeers.clear()
        workers.shutdownNow()
    }

    /** Invoke after removing the persisted peer, so an old authenticated session cannot survive re-pairing. */
    fun disconnectPeer(peerId: String) {
        socketPeers.entries.filter { it.value == peerId }.forEach { runCatching { it.key.close() } }
    }
}

/** Blocking operations: invoke on Dispatchers.IO, with downloads written to a staging file. */
class LanClient(private val localId: String, private val localName: String) : Closeable {
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    init { validateIdentity(localId, localName) }

    fun pair(peer: LanPeer, code: String): PairResult = connect(peer, pairingKey(code), pairing = true) { channel, actualPeer ->
        PairResult(actualPeer, channel.read().also { if (it.size != 32) throw IOException("Invalid pairing response") })
    }

    fun manifest(peer: LanPeer, key: ByteArray, kind: ShareKind): List<SharedFile> = connect(peer, key) { channel, _ ->
        channel.write(packet { writeInt(OP_MANIFEST); writeInt(kind.ordinal) })
        val response = channel.read().reader()
        response.requireSuccess()
        val count = response.readInt()
        if (count !in 0..MAX_MANIFEST_FILES) throw IOException("Invalid manifest length")
        response.requireEnd()
        val paths = mutableSetOf<String>()
        List(count) { channel.read().reader().let { response ->
            response.readFile(kind).also { file ->
                response.requireEnd()
                if (!paths.add(file.path)) throw IOException("Ambiguous shared path")
            }
        } }
    }

    /** ROM lists can use a placeholder digest; hash only the ROM selected for transfer. */
    fun describe(peer: LanPeer, key: ByteArray, kind: ShareKind, path: String): SharedFile {
        validateSharedPath(path)
        return connect(peer, key, readTimeoutMs = DESCRIBE_TIMEOUT_MS) { channel, _ ->
            channel.write(packet { writeInt(OP_DESCRIBE); writeInt(kind.ordinal); writeText(path) })
            channel.read().reader().apply { requireSuccess(); requireEnd() }
            channel.read().reader().let { response ->
                response.readFile(kind).also { file ->
                    response.requireEnd()
                    if (file.path != path) throw IOException("File identity changed")
                }
            }
        }
    }

    /** Complete the handshake on both devices only after their canonical revisions were verified. */
    fun acknowledge(peer: LanPeer, key: ByteArray, acknowledgements: List<SaveAcknowledgement>) {
        require(acknowledgements.size <= MAX_MANIFEST_FILES)
        require(acknowledgements.map { it.path }.distinct().size == acknowledgements.size)
        acknowledgements.forEach(::validateAcknowledgement)
        connect(peer, key) { channel, _ ->
            channel.write(packet { writeInt(OP_ACK_SYNC); writeInt(ShareKind.Save.ordinal); writeInt(acknowledgements.size) })
            acknowledgements.forEach { row -> channel.write(packet {
                writeText(row.path); writeText(row.localSha256); writeText(row.remoteSha256)
            }) }
            channel.read().reader().apply { requireSuccess(); requireEnd() }
        }
    }

    fun download(peer: LanPeer, key: ByteArray, kind: ShareKind, file: SharedFile, output: OutputStream) {
        validateFile(kind, file)
        connect(peer, key) { channel, _ ->
            channel.write(packet { writeInt(OP_DOWNLOAD); writeInt(kind.ordinal); writeFile(file) })
            channel.read().reader().apply { requireSuccess(); requireEnd() }
            val input = ChunkInputStream(channel, file)
            input.copyTo(output, CHUNK_SIZE)
            input.requireComplete()
        }
    }

    fun upload(
        peer: LanPeer,
        key: ByteArray,
        kind: ShareKind,
        file: SharedFile,
        input: InputStream,
        expectedCurrentSha256: String? = null,
    ) {
        validateFile(kind, file)
        expectedCurrentSha256?.let(::validateHash)
        connect(peer, key) { channel, _ ->
            channel.write(packet {
                writeInt(OP_UPLOAD); writeInt(kind.ordinal); writeFile(file)
                writeBoolean(expectedCurrentSha256 != null)
                expectedCurrentSha256?.let(::writeText)
            })
            sendFile(channel, file, input)
            channel.read().reader().apply { requireSuccess(); requireEnd() }
        }
    }

    fun cancelAll() { sockets.forEach { runCatching { it.close() } } }

    override fun close() = cancelAll()

    private fun <T> connect(peer: LanPeer, key: ByteArray, pairing: Boolean = false, readTimeoutMs: Int = SOCKET_TIMEOUT_MS, block: (EncryptedChannel, LanPeer) -> T): T {
        if (!(pairing && peer.id.isEmpty())) validateIdentity(peer.id, peer.name)
        require(peer.port in 1..65535 && key.size == 32)
        val address = InetAddress.getAllByName(peer.host).firstOrNull(::isLocalAddress)
            ?: throw IOException("Peer is outside the local network")
        val socket = Socket()
        sockets.add(socket)
        try { socket.use {
            socket.connect(InetSocketAddress(address, peer.port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.tcpNoDelay = true
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())
            val clientNonce = randomBytes(32)
            output.writeInt(MAGIC); output.writeInt(VERSION); output.writeBoolean(pairing)
            output.writeText(localId); output.writeText(localName); output.write(clientNonce); output.flush()
            val serverId = input.readText(80)
            val serverName = input.readText(256)
            validateIdentity(serverId, serverName)
            if (serverId == localId || (peer.id.isNotEmpty() && serverId != peer.id)) throw IOException("Peer identity changed")
            val serverNonce = ByteArray(32).also(input::readFully)
            val channel = EncryptedChannel(input, output, key, transcript(
                pairing, localId, localName, serverId, serverName, clientNonce, serverNonce,
            ), client = true)
            channel.write(AUTH)
            if (!pairing && !channel.read().contentEquals(AUTH)) throw IOException("Invalid authentication")
            socket.soTimeout = readTimeoutMs
            return block(channel, peer.copy(id = serverId, name = serverName))
        } } finally { sockets.remove(socket) }
    }
}

fun newPairingCode(): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val random = SecureRandom()
    return buildString { repeat(16) { append(alphabet[random.nextInt(alphabet.length)]) } }
}

fun normalizePairingCode(code: String): String = code.uppercase(java.util.Locale.ROOT).filterNot { it == '-' || it.isWhitespace() }

internal fun isLocalAddress(address: InetAddress): Boolean {
    val bytes = address.address
    val uniqueLocalV6 = bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
    return !address.isAnyLocalAddress && (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || uniqueLocalV6)
}

internal fun validateSharedPath(path: String) {
    require(path.toByteArray(StandardCharsets.UTF_8).size in 1..1024)
    require(path.none { it == '\\' || it.code < 32 || it.code == 127 })
    val segments = path.split('/')
    require(segments.size <= 16 && segments.all { it.isNotEmpty() && it != "." && it != ".." })
}

internal class EncryptedChannel(
    private val input: DataInputStream,
    private val output: DataOutputStream,
    key: ByteArray,
    private val transcript: ByteArray,
    private val client: Boolean,
) {
    private val sessionKey = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(transcript + "RiftDeck session v1".toByteArray(StandardCharsets.US_ASCII))
    }
    private var readSequence = 0L
    private var writeSequence = 0L

    fun write(bytes: ByteArray) {
        require(bytes.size <= CHUNK_SIZE)
        val encrypted = crypt(Cipher.ENCRYPT_MODE, bytes, writeSequence++, if (client) 0 else 1)
        output.writeInt(encrypted.size)
        output.write(encrypted)
        output.flush()
    }

    fun read(): ByteArray {
        val length = input.readInt()
        if (length !in 16..(CHUNK_SIZE + 16)) throw IOException("Invalid encrypted frame length")
        val encrypted = ByteArray(length).also(input::readFully)
        return crypt(Cipher.DECRYPT_MODE, encrypted, readSequence++, if (client) 1 else 0)
    }

    private fun crypt(mode: Int, bytes: ByteArray, sequence: Long, direction: Int): ByteArray {
        if (sequence < 0) throw IOException("Session exhausted")
        val nonce = byteArrayOf(direction.toByte()) + hash(transcript).copyOf(3) + packet { writeLong(sequence) }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(transcript + packet { writeInt(direction); writeLong(sequence) })
                doFinal(bytes)
            }
        } catch (error: java.security.GeneralSecurityException) {
            throw IOException("Authentication failed", error)
        }
    }
}

private class ChunkInputStream(private val channel: EncryptedChannel, private val file: SharedFile) : InputStream() {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var remaining = file.size
    private var chunk = ByteArray(0)
    private var position = 0
    private var complete = false

    override fun read(): Int = ByteArray(1).let { if (read(it, 0, 1) < 0) -1 else it[0].toInt() and 0xff }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        if (position == chunk.size) {
            if (remaining == 0L) {
                if (!complete) {
                    if (channel.read().isNotEmpty()) throw IOException("File exceeded declared length")
                    if (digest.digest().hex() != file.sha256) throw IOException("File checksum changed")
                    complete = true
                }
                return -1
            }
            chunk = channel.read()
            position = 0
            if (chunk.isEmpty() || chunk.size > remaining) throw EOFException("Incomplete file")
        }
        val count = minOf(length, chunk.size - position)
        chunk.copyInto(bytes, offset, position, position + count)
        digest.update(chunk, position, count)
        position += count
        remaining -= count
        return count
    }

    fun requireComplete() {
        if (!complete) {
            if (remaining != 0L || read() != -1) throw IOException("File was not completely consumed")
        }
    }
}

private fun sendFile(channel: EncryptedChannel, file: SharedFile, input: InputStream) {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(CHUNK_SIZE)
    var remaining = file.size
    while (remaining > 0) {
        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (count < 0) throw EOFException("Incomplete file")
        if (count == 0) continue
        digest.update(buffer, 0, count)
        channel.write(buffer.copyOf(count))
        remaining -= count
    }
    if (input.read() != -1 || digest.digest().hex() != file.sha256) throw IOException("File checksum changed")
    channel.write(ByteArray(0))
}

private fun validateIdentity(id: String, name: String) {
    require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
    require(name.toByteArray(StandardCharsets.UTF_8).size in 1..256 && name.none { it.code < 32 || it.code == 127 })
}

private fun validateHash(hash: String) { require(hash.matches(Regex("[0-9a-f]{64}"))) }

private fun validateAcknowledgement(row: SaveAcknowledgement) {
    validateSharedPath(row.path)
    require(isSupportedSavePath(row.path))
    validateHash(row.localSha256)
    validateHash(row.remoteSha256)
}

private fun validateFile(kind: ShareKind, file: SharedFile) {
    validateSharedPath(file.path)
    validateHash(file.sha256)
    require(file.size in 0..if (kind == ShareKind.Rom) MAX_ROM_BYTES else MAX_SAVE_BYTES)
}

private fun pairingKey(code: String): ByteArray {
    val normalized = normalizePairingCode(code)
    require(normalized.matches(Regex("[A-Z2-7]{16}")))
    return hash("RiftDeck pairing v1:".toByteArray() + normalized.toByteArray(StandardCharsets.US_ASCII))
}

private fun transcript(pairing: Boolean, clientId: String, clientName: String, serverId: String, serverName: String, clientNonce: ByteArray, serverNonce: ByteArray): ByteArray = hash(packet {
    writeInt(MAGIC); writeInt(VERSION); writeBoolean(pairing)
    writeText(clientId); writeText(clientName); writeText(serverId); writeText(serverName)
    write(clientNonce); write(serverNonce)
})

private fun DataOutputStream.writeText(value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    writeInt(bytes.size); write(bytes)
}

private fun DataInputStream.readText(limit: Int): String {
    val length = readInt()
    if (length !in 0..limit) throw IOException("Invalid text length")
    val bytes = ByteArray(length).also(::readFully)
    val value = String(bytes, StandardCharsets.UTF_8)
    if (!value.toByteArray(StandardCharsets.UTF_8).contentEquals(bytes)) throw IOException("Invalid UTF-8")
    return value
}

private fun DataOutputStream.writeFile(file: SharedFile) { writeText(file.path); writeLong(file.size); writeText(file.sha256) }
private fun DataInputStream.readFile(kind: ShareKind): SharedFile = SharedFile(readText(1024), readLong(), readText(64)).also { validateFile(kind, it) }
private fun DataInputStream.readAcknowledgement(): SaveAcknowledgement = SaveAcknowledgement(readText(1024), readText(64), readText(64)).also(::validateAcknowledgement)
private fun DataInputStream.readKind(): ShareKind = ShareKind.entries.getOrNull(readInt()) ?: throw IOException("Invalid share kind")
private fun DataInputStream.requireSuccess() { if (!readBoolean()) throw IOException("Peer could not complete the transfer") }
private fun DataInputStream.requireEnd() { if (available() != 0) throw IOException("Unexpected metadata") }
private fun ByteArray.reader() = DataInputStream(ByteArrayInputStream(this))
private fun packet(block: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().also { stream -> DataOutputStream(stream).use(block) }.toByteArray()
private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
private fun hash(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

private const val MAGIC = 0x52444654
private const val VERSION = 1
private const val OP_MANIFEST = 1
private const val OP_DOWNLOAD = 2
private const val OP_UPLOAD = 3
private const val OP_DESCRIBE = 4
private const val OP_ACK_SYNC = 5
private const val CHUNK_SIZE = 64 * 1024
private const val MAX_MANIFEST_FILES = 10_000
private const val MAX_ROM_BYTES = 2L * 1024 * 1024 * 1024
private const val MAX_SAVE_BYTES = 64L * 1024 * 1024
private const val CONNECT_TIMEOUT_MS = 5_000
private const val SOCKET_TIMEOUT_MS = 30_000
private const val DESCRIBE_TIMEOUT_MS = 120_000
private val AUTH = "RiftDeck authenticated v1".toByteArray(StandardCharsets.US_ASCII)

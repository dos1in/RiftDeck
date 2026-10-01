package com.riftdeck.core.sharing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LanTransportTest {
    @Test(timeout = 10_000)
    fun pairedPeersStreamFilesAndRequireDestinationHash() {
        val handler = MemoryHandler()
        val bytes = ByteArray(2 * 1024 * 1024 + 123) { (it * 31).toByte() }
        val rom = SharedFile("GBA/Original.gba", bytes.size.toLong(), sha256(bytes))
        handler.files[rom.path] = bytes
        LanServer("host", "Host handheld", handler).use { server ->
            server.start()
            val peer = LanPeer("", "", "127.0.0.1", server.port)
            LanClient("client", "Other handheld").use { client ->
                val paired = client.pair(peer, handler.code.chunked(4).joinToString("-"))
                assertEquals("host", paired.peer.id)
                assertEquals("Host handheld", paired.peer.name)
                assertArrayEquals(handler.keys["client"], paired.key)
                assertEquals(listOf(rom), client.manifest(paired.peer, paired.key, ShareKind.Rom))
                assertEquals(rom, client.describe(paired.peer, paired.key, ShareKind.Rom, rom.path))
                val output = ByteArrayOutputStream()
                client.download(paired.peer, paired.key, ShareKind.Rom, rom, output)
                assertArrayEquals(bytes, output.toByteArray())

                val first = "original save".toByteArray()
                val save = SharedFile("Original.sav", first.size.toLong(), sha256(first))
                client.upload(paired.peer, paired.key, ShareKind.Save, save, ByteArrayInputStream(first))
                assertArrayEquals(first, handler.files[save.path])
                val second = "updated save".toByteArray()
                val updated = save.copy(size = second.size.toLong(), sha256 = sha256(second))
                assertThrows(IOException::class.java) {
                    client.upload(paired.peer, paired.key, ShareKind.Save, updated, ByteArrayInputStream(second))
                }
                assertArrayEquals(first, handler.files[save.path])
                client.upload(paired.peer, paired.key, ShareKind.Save, updated, ByteArrayInputStream(second), save.sha256)
                assertArrayEquals(second, handler.files[save.path])
                val acknowledgement = SaveAcknowledgement(save.path, updated.sha256, updated.sha256)
                client.acknowledge(paired.peer, paired.key, listOf(acknowledgement))
                assertEquals(listOf(acknowledgement), handler.acknowledgements)
                assertThrows(IOException::class.java) {
                    client.acknowledge(paired.peer, paired.key, listOf(acknowledgement.copy(remoteSha256 = "0".repeat(64))))
                }
                assertEquals(listOf(acknowledgement), handler.acknowledgements)
            }
        }
    }

    @Test(timeout = 10_000)
    fun wrongCodeOrUnpairedKeyCannotReadOrReplacePairing() {
        val handler = MemoryHandler()
        LanServer("host", "Host", handler).use { server ->
            server.start()
            val peer = LanPeer("host", "Host", "127.0.0.1", server.port)
            LanClient("client", "Client").use { client ->
                assertThrows(IOException::class.java) { client.pair(peer, "AAAAAAAAAAAAAAAA") }
                assertTrue(handler.keys.isEmpty())
                assertThrows(IOException::class.java) { client.manifest(peer, ByteArray(32), ShareKind.Save) }
                val paired = client.pair(peer, handler.code)
                assertThrows(IOException::class.java) { client.pair(peer, handler.code) }
                assertArrayEquals(paired.key, handler.keys["client"])
                assertThrows(IOException::class.java) { client.manifest(peer.copy(id = "different"), paired.key, ShareKind.Save) }
            }
        }
    }

    @Test(timeout = 10_000)
    fun changedOrTruncatedFilesAreNeverCommitted() {
        val handler = MemoryHandler()
        LanServer("host", "Host", handler).use { server ->
            server.start()
            val peer = LanPeer("host", "Host", "127.0.0.1", server.port)
            LanClient("client", "Client").use { client ->
                val key = client.pair(peer, handler.code).key
                val expected = "correct save".toByteArray()
                val file = SharedFile("test.sav", expected.size.toLong(), sha256(expected))
                assertThrows(IOException::class.java) {
                    client.upload(peer, key, ShareKind.Save, file, ByteArrayInputStream("changed save".toByteArray()))
                }
                assertFalse(handler.files.containsKey(file.path))
                assertThrows(IOException::class.java) {
                    client.upload(peer, key, ShareKind.Save, file, ByteArrayInputStream(expected.copyOf(3)))
                }
                assertFalse(handler.files.containsKey(file.path))
                handler.files[file.path] = "other contents".toByteArray()
                assertThrows(IOException::class.java) {
                    client.download(peer, key, ShareKind.Save, file, ByteArrayOutputStream())
                }
            }
        }
    }

    @Test
    fun encryptedFramesRejectTamperingReplayReorderingAndReflection() {
        val key = ByteArray(32) { it.toByte() }
        val transcript = ByteArray(32) { (it + 31).toByte() }
        val output = ByteArrayOutputStream()
        val writer = EncryptedChannel(DataInputStream(ByteArrayInputStream(ByteArray(0))), DataOutputStream(output), key, transcript, client = true)
        writer.write("first".toByteArray())
        writer.write("second".toByteArray())
        val frames = output.toByteArray()
        fun reader(bytes: ByteArray, stamp: ByteArray = transcript, client: Boolean = false) = EncryptedChannel(
            DataInputStream(ByteArrayInputStream(bytes)), DataOutputStream(ByteArrayOutputStream()), key, stamp, client,
        )
        reader(frames).apply {
            assertEquals("first", String(read()))
            assertEquals("second", String(read()))
        }
        val firstLength = DataInputStream(ByteArrayInputStream(frames)).readInt() + 4
        val first = frames.copyOfRange(0, firstLength)
        val second = frames.copyOfRange(firstLength, frames.size)
        assertThrows(IOException::class.java) { reader(second + first).read() }
        assertThrows(IOException::class.java) { reader(first + first).apply { read(); read() } }
        assertThrows(IOException::class.java) { reader(first, transcript, client = true).read() }
        assertThrows(IOException::class.java) { reader(first, transcript.copyOf().apply { this[0] = 99 }).read() }
        assertThrows(IOException::class.java) { reader(first.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }).read() }
    }

    @Test
    fun oversizedFramesAndUnsafeMetadataAreRejected() {
        val oversized = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(Int.MAX_VALUE) }.toByteArray()
        val channel = EncryptedChannel(DataInputStream(ByteArrayInputStream(oversized)), DataOutputStream(ByteArrayOutputStream()), ByteArray(32), ByteArray(32), false)
        assertThrows(IOException::class.java) { channel.read() }
        listOf("../save.sav", "/save.sav", "a//b", "a/./b", "a\\b", "a\u0000b", "a/../../b").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { validateSharedPath(path) }
        }
        validateSharedPath("GBA/Game name.sav")
        LanClient("client", "Client").use { client ->
            val publicPeer = LanPeer("host", "Host", "8.8.8.8", 12345)
            assertThrows(IOException::class.java) { client.manifest(publicPeer, ByteArray(32), ShareKind.Save) }
            assertThrows(IllegalArgumentException::class.java) {
                client.download(publicPeer, ByteArray(32), ShareKind.Save, SharedFile("huge.sav", 64L * 1024 * 1024 + 1, "0".repeat(64)), ByteArrayOutputStream())
            }
        }
        assertTrue(isLocalAddress(InetAddress.getByName("192.168.1.1")))
        assertTrue(isLocalAddress(InetAddress.getByName("fd00::1")))
        assertFalse(isLocalAddress(InetAddress.getByName("0.0.0.0")))
    }

    @Test(timeout = 10_000)
    fun cancellingClientClosesPendingNetworkRead() {
        val accepted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val serverTask = executor.submit {
                server.accept().use {
                    accepted.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }
            LanClient("client", "Client").use { client ->
                val task = executor.submit<Boolean> {
                    try {
                        client.manifest(LanPeer("host", "Host", "127.0.0.1", server.localPort), ByteArray(32), ShareKind.Save)
                        false
                    } catch (_: IOException) { true }
                }
                assertTrue(accepted.await(2, TimeUnit.SECONDS))
                client.cancelAll()
                assertTrue(task.get(2, TimeUnit.SECONDS))
            }
            release.countDown()
            serverTask.get(2, TimeUnit.SECONDS)
        }
        executor.shutdownNow()
    }

    @Test(timeout = 10_000)
    fun duplicateCatalogAndAcknowledgementPathsAreRejected() {
        val handler = MemoryHandler()
        val file = SharedFile("Game.gba", 0, sha256(ByteArray(0)))
        val duplicateCatalog = object : LanServer.Handler by handler {
            override fun manifest(peerId: String, kind: ShareKind) = listOf(file, file)
        }
        LanServer("host", "Host", duplicateCatalog).use { server ->
            server.start()
            val peer = LanPeer("host", "Host", "127.0.0.1", server.port)
            LanClient("client", "Client").use { client ->
                val key = client.pair(peer, handler.code).key
                assertThrows(IOException::class.java) { client.manifest(peer, key, ShareKind.Rom) }
                val row = SaveAcknowledgement("Game.sav", file.sha256, file.sha256)
                assertThrows(IllegalArgumentException::class.java) { client.acknowledge(peer, key, listOf(row, row)) }
            }
        }
    }

    private class MemoryHandler : LanServer.Handler {
        val code = "ABCDEFGH234567AB"
        val keys = ConcurrentHashMap<String, ByteArray>()
        val files = ConcurrentHashMap<String, ByteArray>()
        var acknowledgements = emptyList<SaveAcknowledgement>()
        override fun currentPairingCode() = code
        override fun pairedKey(peerId: String) = keys[peerId]
        override fun onPaired(peerId: String, name: String, key: ByteArray) { keys[peerId] = key }
        override fun manifest(peerId: String, kind: ShareKind) = files.map { (path, bytes) -> SharedFile(path, bytes.size.toLong(), sha256(bytes)) }
        override fun openRead(peerId: String, kind: ShareKind, path: String): InputStream = ByteArrayInputStream(files[path] ?: throw IOException("Not found"))
        override fun acknowledge(peerId: String, acknowledgements: List<SaveAcknowledgement>) {
            if (acknowledgements.any { files[it.path]?.let(::sha256) != it.remoteSha256 }) throw IOException("Save changed")
            this.acknowledgements = acknowledgements
        }
        override fun receive(peerId: String, kind: ShareKind, path: String, expectedSha256: String, size: Long, expectedCurrentSha256: String?, input: InputStream) {
            val currentHash = files[path]?.let(::sha256)
            if (currentHash != expectedCurrentSha256) throw IOException("Destination changed")
            val staging = input.readBytes()
            files[path] = staging
        }
    }

    private companion object {
        fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

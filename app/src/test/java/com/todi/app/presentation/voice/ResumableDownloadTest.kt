package com.todi.app.presentation.voice

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ResumableDownloadTest {
    private val payload = ByteArray(200_000) { (it % 251).toByte() }

    private var server: HttpServer? = null

    @After
    fun stopServer() {
        server?.stop(0)
    }

    @Test
    fun aTruncatedBodyIsContinuedRatherThanRestarted() =
        runTest {
            val calls = AtomicInteger()
            val url =
                serve { exchange ->
                    val attempt = calls.incrementAndGet()
                    val range = exchange.requestHeaders.getFirst("Range")
                    if (attempt == 1) {
                        val cut = payload.size / 5
                        exchange.sendResponseHeaders(200, payload.size.toLong())
                        exchange.responseBody.write(payload, 0, cut)
                        exchange.responseBody.flush()
                        exchange.close()
                    } else {
                        respondWithRange(exchange, range)
                    }
                }
            val file = tempFile()

            val total = ResumableDownload(client(), url).into(file)

            assertEquals(payload.size.toLong(), total)
            assertEquals(payload.size.toLong(), file.length())
            assertArrayEquals(payload, file.readBytes())
            assertEquals("the first attempt must be followed by a second", 2, calls.get())
        }

    @Test
    fun aServerThatIgnoresRange_restartsInsteadOfBuildingFrankenstein() =
        runTest {
            val calls = AtomicInteger()
            val url =
                serve { exchange ->
                    val attempt = calls.incrementAndGet()
                    if (attempt == 1) {
                        exchange.sendResponseHeaders(200, payload.size.toLong())
                        exchange.responseBody.write(payload, 0, payload.size / 4)
                        exchange.responseBody.flush()
                        exchange.close()
                    } else {
                        exchange.sendResponseHeaders(200, payload.size.toLong())
                        exchange.responseBody.write(payload)
                        exchange.close()
                    }
                }
            val file = tempFile()

            ResumableDownload(client(), url).into(file)

            assertArrayEquals(payload, file.readBytes())
        }

    @Test
    fun aConnectionThatNeverMoves_givesUp() =
        runTest {
            val url =
                serve { exchange ->
                    exchange.sendResponseHeaders(200, payload.size.toLong())
                    exchange.responseBody.flush()
                    exchange.close()
                }
            try {
                ResumableDownload(client(), url).into(tempFile())
                fail("a download that never moved should not be reported as done")
            } catch (_: IOException) {
            }
        }

    @Test
    fun aFileThatIsAlreadyWhole_isNotFetchedAgain() =
        runTest {
            val calls = AtomicInteger()
            val url =
                serve { exchange ->
                    calls.incrementAndGet()
                    exchange.sendResponseHeaders(416, -1)
                    exchange.close()
                }
            val file = tempFile().apply { writeBytes(payload) }

            ResumableDownload(client(), url).into(file)

            assertEquals(payload.size.toLong(), file.length())
            assertEquals(1, calls.get())
        }

    @Test
    fun progressIsReportedAgainstTheTotalTheServerGave() =
        runTest {
            val url = serve { exchange -> respondWithRange(exchange, exchange.requestHeaders.getFirst("Range")) }
            val seen = mutableListOf<Pair<Long, Long>>()

            ResumableDownload(client(), url).into(tempFile()) { written, total ->
                seen += written to total
            }

            assertEquals(payload.size.toLong(), seen.last().first)
            assertEquals(payload.size.toLong(), seen.last().second)
            assertEquals("progress must only ever go up", seen.map { it.first }.sorted(), seen.map { it.first })
        }

    private fun respondWithRange(
        exchange: HttpExchange,
        range: String?,
    ) {
        val start = range?.substringAfter("bytes=", "")?.substringBefore('-')?.toLongOrNull() ?: 0L
        val body = payload.copyOfRange(start.toInt(), payload.size)
        if (start > 0) {
            exchange.responseHeaders.add(
                "Content-Range",
                "bytes $start-${payload.size - 1}/${payload.size}",
            )
            exchange.sendResponseHeaders(206, body.size.toLong())
        } else {
            exchange.sendResponseHeaders(200, body.size.toLong())
        }
        exchange.responseBody.write(body)
        exchange.close()
    }

    private fun serve(handler: (HttpExchange) -> Unit): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/model.zip") { exchange -> handler(exchange) }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}/model.zip"
    }

    private fun client(): OkHttpClient =
        OkHttpClient
            .Builder()
            .proxy(Proxy.NO_PROXY)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    private fun tempFile(): File = Files.createTempFile("vosk-download-test", ".part").toFile().apply { delete() }
}

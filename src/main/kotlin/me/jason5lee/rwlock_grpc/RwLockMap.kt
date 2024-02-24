package me.jason5lee.rwlock_grpc

import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicLong

class RwLockMap : RwLockMapGrpcKt.RwLockMapCoroutineImplBase() {
    override suspend fun get(request: GetRequest): GetResponse {
        var oldStatus: Long
        do {
            oldStatus = status.get()
            var newStatus: Long = oldStatus

            // Check if there is a writer
            if (newStatus and WRITER_MASK != 0L) {
                // Increment the want-to-read count
                newStatus += WAIT_TO_READ_ONE
                // Check overflow
                if (newStatus and WAIT_TO_READ_MASK == 0L) {
                    throw StatusRuntimeException(TOO_MANY_READER)
                }
            } else {
                // Increment the read count
                newStatus += READER_ONE
                // Check overflow
                if (newStatus and READER_MASK == 0L) {
                    throw StatusRuntimeException(TOO_MANY_READER)
                }
            }
        } while (!status.weakCompareAndSetVolatile(oldStatus, newStatus))

        // Check if there is a writer
        if (oldStatus and WRITER_MASK != 0L) {
            readSemaphore.acquire()
        }

        val valueOrNull = map[request.key]

        // Decrement a reader
        oldStatus = status.getAndAdd(-READER_ONE)
        assert(oldStatus and READER_MASK > 0)
        // If it is the last reader and there is a writer waiting, release the writer.
        if ((oldStatus and READER_MASK) == READER_ONE && oldStatus and WRITER_MASK != 0L) {
            writeSemaphore.release()
        }

        val value = valueOrNull ?: throw StatusRuntimeException(KEY_NOT_FOUND)

        return GetResponse.newBuilder()
            .setValue(value)
            .build()
    }

    override suspend fun update(request: UpdateRequest): UpdateResponse {
        // Increment the writer count
        var oldStatus = status.getAndAdd(WRITER_ONE)
        // Check overflow
        if ((oldStatus + WRITER_ONE) and WRITER_MASK == 0L) {
            // Reset writer count if overflow
            status.getAndAdd(-WRITER_ONE)
            throw StatusRuntimeException(TOO_MANY_WRITER)
        }
        // If there is any reader or writer.
        if (oldStatus and (READER_MASK or WRITER_MASK) != 0L) {
            writeSemaphore.acquire()
        }

        var errorStatus: Status? = null
        when (request.type) {
            UpdateType.CREATE -> {
                val notCreated = map.putIfAbsent(request.key, request.value) != null
                if (notCreated) {
                    errorStatus = KEY_ALREADY_EXISTS
                }
            }

            UpdateType.UPDATE -> {
                if (map.containsKey(request.key)) {
                    map[request.key] = request.value
                } else {
                    errorStatus = KEY_NOT_FOUND
                }
            }

            UpdateType.UPSERT -> {
                map[request.key] = request.value
            }

            UpdateType.DELETE -> {
                if (map.remove(request.key) == null) {
                    errorStatus = KEY_NOT_FOUND
                }
            }

            UpdateType.UNRECOGNIZED -> errorStatus = UNRECOGNIZED_UPDATE_TYPE
            null -> errorStatus = UNRECOGNIZED_UPDATE_TYPE
        }

        var newStatus: Long
        var waitToRead: Long
        do {
            oldStatus = status.get()
            assert(oldStatus and READER_MASK == 0L)
            newStatus = oldStatus
            newStatus -= WRITER_ONE
            waitToRead = oldStatus and WAIT_TO_READ_MASK
            if (waitToRead > 0) {
                // Moves wait-to-read to readers by clearing wait-to-read,
                newStatus = newStatus and WAIT_TO_READ_MASK.inv()
                // then set it to readers.
                newStatus = newStatus or (waitToRead * READER_ONE)
            }
        } while (!status.weakCompareAndSetVolatile(oldStatus, newStatus))

        // Release the waiting for read
        if (waitToRead > 0) {
            for (i in 0 until waitToRead) {
                readSemaphore.release()
            }
        } else if (oldStatus and WRITER_MASK > WRITER_ONE) {
            // If there is another writer, release it.
            writeSemaphore.release()
        }
        if (errorStatus != null) {
            throw StatusRuntimeException(errorStatus)
        }
        return UpdateResponse.newBuilder().build()
    }

    private val map = mutableMapOf<String, String>()
    private val readSemaphore = Semaphore(Int.MAX_VALUE, Int.MAX_VALUE)
    private val writeSemaphore = Semaphore(1, 1)

    // First 21 bits: reader count,
    // Next 21 bits: wait-to-read count,
    // Last 21 bits: writer count
    private val status: AtomicLong = AtomicLong(0)

    companion object {
        private const val READER_ONE = 1L
        private const val WAIT_TO_READ_ONE = 1L shl 21
        private const val WRITER_ONE = 1L shl 42

        private const val READER_MASK = WAIT_TO_READ_ONE - 1
        private const val WAIT_TO_READ_MASK = WRITER_ONE - WAIT_TO_READ_ONE
        private const val WRITER_MASK = (1L shl 63) - WRITER_ONE

        private val TOO_MANY_READER = Status.RESOURCE_EXHAUSTED
            .withDescription("Too many readers")
        private val TOO_MANY_WRITER = Status.RESOURCE_EXHAUSTED
            .withDescription("Too many writers")

        private val KEY_NOT_FOUND = Status.NOT_FOUND
            .withDescription("Key not found")
        private val KEY_ALREADY_EXISTS = Status.ALREADY_EXISTS
            .withDescription("Key already exists")

        private val UNRECOGNIZED_UPDATE_TYPE = Status.INVALID_ARGUMENT
            .withDescription("Unrecognized update type")
    }
}

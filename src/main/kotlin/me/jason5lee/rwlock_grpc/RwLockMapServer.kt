package me.jason5lee.rwlock_grpc

import io.grpc.Server
import io.grpc.ServerBuilder

class RwLockMapServer(private val port: Int) {
    val server: Server =
        ServerBuilder
            .forPort(port)
            .addService(RwLockMap())
            .build()

    fun start() {
        server.start()
        println("Server started, listening on $port")
        Runtime.getRuntime().addShutdownHook(
            Thread {
                println("*** shutting down gRPC server since JVM is shutting down")
                this@RwLockMapServer.stop()
                println("*** server shut down")
            },
        )
    }

    private fun stop() {
        server.shutdown()
    }

    fun blockUntilShutdown() {
        server.awaitTermination()
    }
}

fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 50051
    val server = RwLockMapServer(port)
    server.start()
    server.blockUntilShutdown()
}

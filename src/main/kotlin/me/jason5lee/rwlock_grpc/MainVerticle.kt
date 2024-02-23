package me.jason5lee.rwlock_grpc

import io.vertx.grpc.server.GrpcServer
import io.vertx.grpc.server.GrpcServiceBridge
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait

class MainVerticle : CoroutineVerticle() {
    override suspend fun start() {
        val grpcServer = GrpcServer.server(vertx)
        val service = RwLockMap()
        val serverBridge = GrpcServiceBridge.bridge(service)
        serverBridge.bind(grpcServer)
        vertx
            .createHttpServer()
            .requestHandler(grpcServer)
            .listen(8888)
            .coAwait()

        println("HTTP server started on port 8888")
    }
}

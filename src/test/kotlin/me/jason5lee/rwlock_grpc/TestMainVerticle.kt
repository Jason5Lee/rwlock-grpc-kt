package me.jason5lee.flash_sale_vertx_kt

import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import me.jason5lee.rwlock_grpc.MainVerticle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class TestMainVerticle {

    @BeforeEach
    fun deploy_verticle(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding<String> { _ -> testContext.completeNow() })
    }

    @Test
    fun verticle_deployed(vertx: Vertx, testContext: VertxTestContext) {
        testContext.completeNow()
    }
}

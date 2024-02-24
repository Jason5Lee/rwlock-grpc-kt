# rwlock-grpc-kt

An example of gRPC server with Kotlin, implementing a read-write lock using semaphores.

The server serves as a key-value store, implemented by a mutable map guarded by a read-write lock.
Since `kotlinx.coroutines` does not have a built-in read-write lock,
we use semaphores to implement a read-write lock, inspired by [this C++ implementation](https://github.com/preshing/cpp11-on-multicore/blob/master/common/rwlock.h).

The `vertx` branch contains an implementation using `vertx` as the gRPC server.

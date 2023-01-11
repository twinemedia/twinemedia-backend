#!/usr/bin/env bash

echo "This script is a proof-of-concept for building TwineMedia with GraalVM Native Image. Do not expect it to work properly."

/usr/lib/jvm/java-17-graalvm/bin/native-image -jar app/build/libs/app-all.jar \
	--no-fallback \
	--initialize-at-build-time=org.slf4j.LoggerFactory \
	--initialize-at-build-time=org.slf4j.simple.SimpleLogger \
	--initialize-at-build-time=org.slf4j.impl.StaticLoggerBinder \
	--initialize-at-run-time=io.netty.util.internal.logging.Log4JLogger \
	--initialize-at-run-time=io.netty.util.AbstractReferenceCounted \
	--initialize-at-run-time=io.netty.channel.epoll \
	--initialize-at-run-time=io.netty.handler.ssl \
	--initialize-at-run-time=io.netty.channel.unix

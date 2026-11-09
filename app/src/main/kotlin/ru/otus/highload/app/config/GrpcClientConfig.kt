package ru.otus.highload.app.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.client.GrpcChannelFactory
import org.springframework.grpc.client.ImportGrpcClients
import ru.otus.highload.grpc.feed.v1.FeedServiceGrpc
import ru.otus.highload.grpc.friend.v1.FriendServiceGrpc
import ru.otus.highload.grpc.post.v1.PostServiceGrpc
import ru.otus.highload.grpc.session.v1.SessionServiceGrpc
import ru.otus.highload.grpc.user.v1.UserServiceGrpc

@Configuration
@ImportGrpcClients(types = [SessionServiceGrpc.SessionServiceBlockingStub::class])
class GrpcClientConfig {

    @Bean
    fun userServiceStub(factory: GrpcChannelFactory): UserServiceGrpc.UserServiceBlockingStub =
        UserServiceGrpc.newBlockingStub(factory.createChannel("user-service"))

    @Bean
    fun postServiceStub(factory: GrpcChannelFactory): PostServiceGrpc.PostServiceBlockingStub =
        PostServiceGrpc.newBlockingStub(factory.createChannel("post-service"))

    @Bean
    fun friendServiceStub(factory: GrpcChannelFactory): FriendServiceGrpc.FriendServiceBlockingStub =
        FriendServiceGrpc.newBlockingStub(factory.createChannel("friend-service"))

    @Bean
    fun feedServiceStub(factory: GrpcChannelFactory): FeedServiceGrpc.FeedServiceBlockingStub =
        FeedServiceGrpc.newBlockingStub(factory.createChannel("feed-service"))
}

package ru.otus.highload.app.config

import brave.Tracing
import brave.context.slf4j.MDCScopeDecorator
import brave.propagation.B3Propagation
import brave.propagation.ThreadLocalCurrentTraceContext
import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration
class TracingConfig {

    @Bean
    fun tracing(): Tracing {
        return Tracing.newBuilder()
            .localServiceName("otus-highload")
            .currentTraceContext(
                ThreadLocalCurrentTraceContext.newBuilder()
                    .addScopeDecorator(MDCScopeDecorator.newBuilder().build())
                    .build()
            )
            .propagationFactory(B3Propagation.newFactoryBuilder().build())
            .build()
    }

    @Bean
    fun tracingFilter(tracing: Tracing): FilterRegistrationBean<Filter> {
        val filter = Filter { request, response, chain ->
            val req = request as HttpServletRequest
            val span = tracing.tracer().nextSpan()
                .name(req.method + " " + req.requestURI)
                .kind(brave.Span.Kind.SERVER)
                .start()
            span.tag("http.method", req.method)
            span.tag("http.path", req.requestURI)
            tracing.tracer().withSpanInScope(span).use {
                try {
                    chain.doFilter(request, response)
                } catch (e: Exception) {
                    span.error(e)
                    throw e
                } finally {
                    span.finish()
                }
            }
        }
        return FilterRegistrationBean<Filter>(filter).apply {
            order = Ordered.HIGHEST_PRECEDENCE + 1
        }
    }
}

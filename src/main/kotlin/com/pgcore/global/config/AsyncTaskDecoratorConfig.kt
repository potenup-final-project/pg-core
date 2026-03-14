package com.pgcore.global.config

import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator

@Configuration
class AsyncTaskDecoratorConfig {

    @Bean
    fun pgcoreTaskDecorator(): TaskDecorator {
        return TaskDecorator { runnable ->
            val contextMap = MDC.getCopyOfContextMap()

            Runnable {
                val previousContext = MDC.getCopyOfContextMap()
                try {
                    if (contextMap == null) {
                        MDC.clear()
                    } else {
                        MDC.setContextMap(contextMap)
                    }

                    runnable.run()
                } finally {
                    if (previousContext == null) {
                        MDC.clear()
                    } else {
                        MDC.setContextMap(previousContext)
                    }
                }
            }
        }
    }
}

package com.pgcore.webhook.infra.persistence

import com.pgcore.webhook.domain.WebhookEndpoint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface WebhookEndpointJpaRepository : JpaRepository<WebhookEndpoint, Long>

package com.pgcore.webhook.infra.persistence

import com.pgcore.webhook.application.usecase.repository.WebhookEndpointRepository
import com.pgcore.webhook.domain.QWebhookEndpoint
import com.pgcore.webhook.domain.WebhookEndpoint
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class WebhookEndpointRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
    private val springDataRepo: WebhookEndpointJpaRepository,
) : WebhookEndpointRepository {

    private val qEndpoint = QWebhookEndpoint.webhookEndpoint

    override fun findByMerchantIdAndIsActiveTrue(merchantId: Long): List<WebhookEndpoint> =
        queryFactory.selectFrom(qEndpoint)
            .where(
                qEndpoint.merchantId.eq(merchantId),
                qEndpoint.isActive.isTrue,
            )
            .fetch()

    override fun findByMerchantId(merchantId: Long): List<WebhookEndpoint> =
        queryFactory.selectFrom(qEndpoint)
            .where(qEndpoint.merchantId.eq(merchantId))
            .fetch()

    override fun existsByMerchantIdAndUrl(merchantId: Long, url: String): Boolean =
        queryFactory.selectOne()
            .from(qEndpoint)
            .where(
                qEndpoint.merchantId.eq(merchantId),
                qEndpoint.url.eq(url),
            )
            .fetchFirst() != null

    override fun findByMerchantIdAndEndpointId(merchantId: Long, endpointId: Long): WebhookEndpoint? =
        queryFactory.selectFrom(qEndpoint)
            .where(
                qEndpoint.merchantId.eq(merchantId),
                qEndpoint.endpointId.eq(endpointId),
            )
            .fetchOne()

    override fun save(endpoint: WebhookEndpoint): WebhookEndpoint =
        springDataRepo.save(endpoint)

    override fun findByMerchantIdAndEndpointIds(
        merchantId: Long,
        endpointIds: Collection<Long>
    ): List<WebhookEndpoint> {
        if (endpointIds.isEmpty()) return emptyList()

        return queryFactory.selectFrom(qEndpoint)
            .where(
                qEndpoint.merchantId.eq(merchantId),
                qEndpoint.endpointId.`in`(endpointIds)
            ).fetch()
    }
}

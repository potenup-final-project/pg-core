### Application layer
Application layer는 비지니스 로직을 적는 곳.

├── usecase
│   ├── command
│   │   ├── ClaimPaymentUseCase.kt
│   │   ├── ConfirmPaymentUseCase.kt
│   │   ├── CancelPaymentUseCase.kt
│   │   └── dto
│   │       ├── ClaimPaymentCommand.kt
│   │       ├── ConfirmPaymentCommand.kt
│   │       └── CancelPaymentCommand.kt
│   │
│   ├── query
│   │   ├── GetPaymentUseCase.kt
│   │   ├── GetPaymentsByMerchantUseCase.kt
│   │   └── dto
│   │       ├── PaymentDetailDto.kt
│   │       └── PaymentSummaryDto.kt
│   └── repository
│       ├── PaymentRepository.kt
│       ├── PaymentTransactionRepository.kt
│       ├── IdempotencyRepository.kt
│       ├── PgProviderGateway.kt
│       └── OutboxEventRepository.kt

package com.ttu.etl.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ETL TTU API")
                        .description("""
                                API untuk proses ETL data penjualan dari file Excel (.xlsx) ke database PostgreSQL.

                                **Alur penggunaan:**
                                1. Upload file Excel via `POST /etl/jobs`
                                2. Pantau status via `GET /etl/jobs/{jobId}`
                                3. Lihat data transaksi via `/etl/transactions`
                                4. Analisa penjualan via `/etl/analytics`
                                """)
                        .version("1.0.0"))
                .tags(List.of(
                        new Tag().name("ETL Jobs").description("Upload file dan pantau status proses ETL"),
                        new Tag().name("Transactions").description("Query data transaksi hasil ETL"),
                        new Tag().name("Products").description("Katalog produk yang ter-ekstrak"),
                        new Tag().name("Analytics").description("Laporan dan analisa data penjualan"),
                        new Tag().name("Export").description("Export data ke format CSV")
                ));
    }
}

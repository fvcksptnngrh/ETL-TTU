package com.ttu.etl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class EtlTtuApplication {

    public static void main(String[] args) {
        SpringApplication.run(EtlTtuApplication.class, args);
    }
}

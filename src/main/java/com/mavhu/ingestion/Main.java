package com.mavhu.ingestion;

import com.mavhu.ingestion.config.PostgresDatabaseInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
 
@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Main.class);
        application.addInitializers(new PostgresDatabaseInitializer());
        application.run(args);
    }
}

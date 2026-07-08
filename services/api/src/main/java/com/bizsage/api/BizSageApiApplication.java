package com.bizsage.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BizSageApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(BizSageApiApplication.class, args);
  }
}

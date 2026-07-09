package com.bizsage.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan("com.bizsage.api")
public class BizSageApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(BizSageApiApplication.class, args);
  }
}

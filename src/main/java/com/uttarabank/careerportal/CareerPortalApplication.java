package com.uttarabank.careerportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CareerPortalApplication {
  public static void main(String[] args) {
    SpringApplication.run(CareerPortalApplication.class, args);
  }
}

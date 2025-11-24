package com.aitaskmanager;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.aitaskmanager.repository")
public class AitaskmanagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AitaskmanagerApplication.class, args);
	}

}

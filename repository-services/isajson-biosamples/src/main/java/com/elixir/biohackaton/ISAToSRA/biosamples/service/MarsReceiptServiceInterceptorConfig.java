/** Elixir BioHackathon 2022 */
package com.elixir.biohackaton.ISAToSRA.biosamples.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MarsReceiptServiceInterceptorConfig implements WebMvcConfigurer {

  @Autowired private MarsReceiptService marsReceiptService;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(marsReceiptService);
  }
}

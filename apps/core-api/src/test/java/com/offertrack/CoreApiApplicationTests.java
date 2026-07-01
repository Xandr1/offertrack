package com.offertrack;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.ai-draft-cache.enabled=false")
class CoreApiApplicationTests {

  @Test
  void contextLoads() {}
}

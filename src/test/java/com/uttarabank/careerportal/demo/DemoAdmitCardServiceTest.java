package com.uttarabank.careerportal.demo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DemoAdmitCardServiceTest {
  @Test
  void etaIsCalculatingBeforeTheFirstPdfCompletes() {
    assertNull(DemoAdmitCardService.estimateRemaining(30_000, 0));
  }

  @Test
  void etaIsZeroOnlyWhenNoWorkRemains() {
    assertEquals(0L, DemoAdmitCardService.estimateRemaining(0, 0));
  }

  @Test
  void etaUsesTheMeasuredRate() {
    assertEquals(20L, DemoAdmitCardService.estimateRemaining(100, 5));
  }
}

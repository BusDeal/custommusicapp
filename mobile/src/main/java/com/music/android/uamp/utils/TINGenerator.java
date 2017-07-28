package com.music.android.uamp.utils;

import org.apache.commons.lang3.RandomStringUtils;

import java.util.Calendar;

public class TINGenerator {

  private static final int size = 6;
  // taking 2017 as base year
  private static final int YEAR = 2017;

  // we are removing 0,1, i, o, w and l for the sake of legibility.
  private static final char[] characterSet = "23456789ABCDEFGHJKMNPQRSTUVXYZ".toCharArray();

  public static String getNextTIN() {
    Calendar calendar = Calendar.getInstance();
    String monthIdentifier = String.valueOf(characterSet[calendar.get(calendar.MONTH)]);
    String yearIdentifier = String.valueOf(characterSet[calendar.get(calendar.YEAR) - YEAR]);
    return monthIdentifier.concat(yearIdentifier)
            .concat(RandomStringUtils.random(size, characterSet));

  }

}
